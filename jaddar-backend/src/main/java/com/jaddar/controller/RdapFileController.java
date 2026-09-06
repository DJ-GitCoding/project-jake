/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.dto.AgreementSummary;
import com.jaddar.dto.CustomParameterSummary;
import com.jaddar.dto.RdapResolution;
import com.jaddar.dto.RdapResponse;
import com.jaddar.dto.RequestTypeSummary;
import com.jaddar.enums.QueryType;
import com.jaddar.exception.ApiException;
import com.jaddar.service.AgreementService;
import com.jaddar.service.IanaBootstrapService;
import com.jaddar.service.RdapFileService;
import com.jaddar.service.RdapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * File upload / download routes and the multipart query-with-file route, ported
 * from the FILE UPLOAD / DOWNLOAD section of rdap_routes.py.
 */
@Slf4j
@RestController
@RequestMapping("/api/rdap")
public class RdapFileController {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RdapFileService fileService;
    private final RdapService rdapService;
    private final IanaBootstrapService ianaBootstrapService;
    private final AgreementService agreementService;
    private final WebClient rdapWebClient;
    private final ObjectMapper objectMapper;

    public RdapFileController(RdapFileService fileService,
                             RdapService rdapService,
                             IanaBootstrapService ianaBootstrapService,
                             AgreementService agreementService,
                             @Qualifier("rdapWebClient") WebClient rdapWebClient,
                             ObjectMapper objectMapper) {
        this.fileService = fileService;
        this.rdapService = rdapService;
        this.ianaBootstrapService = ianaBootstrapService;
        this.agreementService = agreementService;
        this.rdapWebClient = rdapWebClient;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadRdapFile(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.getOriginalFilename() == null || file.getOriginalFilename().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No filename provided");
        }
        Map<String, Object> meta = fileService.processAndStoreFile(
                file.getOriginalFilename(), file.getBytes(), file.getContentType());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("fileId", meta.get("fileId"));
        out.put("originalFilename", meta.get("originalFilename"));
        out.put("fileType", meta.get("fileType"));
        out.put("mimeType", meta.get("mimeType"));
        out.put("fileSize", meta.get("fileSize"));
        out.put("fileHash", meta.get("fileHash"));
        out.put("scanStatus", "CLEAN");
        return out;
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<ByteArrayResource> downloadRdapFile(
            @PathVariable String fileId,
            @RequestParam(value = "inline", defaultValue = "false") boolean inline) throws IOException {
        validateUuid(fileId);
        Map<String, Object> meta = fileService.readMeta(fileId);
        if (meta == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
        }
        if ("MALICIOUS".equals(meta.get("scanStatus"))) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "This file was flagged as malicious and cannot be downloaded");
        }
        String storedFilename = String.valueOf(meta.get("storedFilename"));
        byte[] content;
        try {
            content = fileService.readStored(storedFilename);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File content not found");
        }

        String originalFilename = String.valueOf(meta.get("originalFilename"));
        String mimeType = String.valueOf(meta.get("mimeType"));

        ContentDisposition disposition = (inline
                ? ContentDisposition.inline()
                : ContentDisposition.attachment()).filename(originalFilename).build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, mimeType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-File-Type", String.valueOf(meta.get("fileType")))
                .header("X-File-Hash", String.valueOf(meta.get("fileHash")))
                .header("X-Scan-Status", String.valueOf(meta.getOrDefault("scanStatus", "CLEAN")))
                .header("Access-Control-Expose-Headers", "X-File-Type, X-File-Hash, X-Scan-Status")
                .body(new ByteArrayResource(content));
    }

    @GetMapping("/files/{fileId}/meta")
    public Map<String, Object> getRdapFileMeta(@PathVariable String fileId) {
        validateUuid(fileId);
        Map<String, Object> meta = fileService.readMeta(fileId);
        if (meta == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
        }
        return Map.of("success", true, "file", meta);
    }

    /** Normalise a data holder file-rejection body into a UI-friendly per-file failure entry. */
    private Map<String, Object> toFileRejection(String filename, String paramName, Map<String, Object> dhBody) {
        List<Object> messages = new ArrayList<>();
        Object failures = dhBody.get("failures");
        if (failures instanceof List) {
            messages.addAll((List<Object>) failures);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("filename", filename);
        out.put("paramName", paramName);
        out.put("error", dhBody.get("error") != null
                ? dhBody.get("error") : "The file was rejected by the data holder.");
        out.put("messages", messages);
        return out;
    }

    /** Parse a response body as JSON, returning null rather than throwing on malformed input. */
    private Map<String, Object> safeParseJson(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return objectMapper.readValue(body, MAP_TYPE);
        } catch (Exception e) {
            return null;
        }
    }

    private void rejectUndeclaredFileParams(Integer requestTypeCode, List<String> paramNames, String authHeader) {
        if (requestTypeCode == null || authHeader == null) return;

        List<RequestTypeSummary> matches;
        try {
            matches = agreementService.getUserAgreements(authHeader).getAgreements().stream()
                    .map(AgreementSummary::getRequestTypes)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .filter(rt -> requestTypeCode.equals(rt.getTypeCode()))
                    .toList();
        } catch (Exception e) {
            log.warn("Could not resolve request type {} to validate file parameters: {}",
                    requestTypeCode, e.getMessage());
            return;
        }

        if (matches.isEmpty()) {
            log.warn("Request type {} not found in the caller's catalog; skipping file parameter validation",
                    requestTypeCode);
            return;
        }

        Set<String> declared = matches.stream()
                .map(RequestTypeSummary::getCustomParameters)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(p -> "file".equalsIgnoreCase(p.getDataType()))
                .map(CustomParameterSummary::getName)
                .collect(Collectors.toSet());

        for (String pname : paramNames) {
            if (!declared.contains(pname)) {
                log.warn("Rejected upload for undeclared file parameter '{}' on request type {}",
                        pname, requestTypeCode);
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "The attachment '" + pname + "' is not accepted by this request type. "
                                + "Please reselect your files and try again.");
            }
        }
    }

    @PostMapping(value = "/query-with-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object rdapQueryWithFile(
            @RequestParam("query") String query,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "queryType", required = false) String queryTypeParam,
            @RequestParam(value = "dataHolderGroup", required = false) String dataHolderGroup,
            @RequestParam(value = "requestorGroup", required = false) String requestorGroup,
            @RequestParam(value = "requestType", required = false) Integer requestType,
            @RequestParam(value = "jakeCompliance", defaultValue = "false") boolean jakeCompliance,
            @RequestParam(value = "confidential", defaultValue = "false") boolean confidential,
            @RequestParam(value = "exigent", defaultValue = "false") boolean exigent,
            @RequestParam(value = "customParams", required = false) String customParams,
            @RequestParam(value = "agreements", required = false) String agreements,
            @RequestParam(value = "fileParamNames", required = false) String fileParamNames,
            @RequestParam(value = "fileParamName", required = false) String fileParamName,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No files provided");
        }

        // Resolve a parameter name for each uploaded file.
        List<String> paramNames = new ArrayList<>();
        if (fileParamNames != null) {
            for (String n : fileParamNames.split(",")) {
                paramNames.add(n.trim());
            }
        } else if (fileParamName != null) {
            paramNames.add(fileParamName);
        }
        while (paramNames.size() < files.size()) {
            paramNames.add("file_" + (paramNames.size() + 1));
        }

        // Extract user info for logging (best-effort).
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        rejectUndeclaredFileParams(requestType, paramNames, authHeader);

        // Scan + store every file.
        List<Map<String, Object>> allFileMeta = new ArrayList<>();
        for (MultipartFile uploadFile : files) {
            if (uploadFile.getOriginalFilename() == null || uploadFile.getOriginalFilename().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "One of the uploaded files has no filename");
            }
            Map<String, Object> meta = fileService.processAndStoreFile(
                    uploadFile.getOriginalFilename(), uploadFile.getBytes(), uploadFile.getContentType());
            allFileMeta.add(meta);
        }

        // Build customParams with every file reference injected.
        Map<String, Object> parsedCustomParams = new LinkedHashMap<>();
        if (customParams != null && !customParams.isEmpty()) {
            try {
                parsedCustomParams = objectMapper.readValue(customParams, MAP_TYPE);
            } catch (Exception ignored) {
                parsedCustomParams = new LinkedHashMap<>();
            }
        }

        List<Map<String, Object>> attachedFilesSummary = new ArrayList<>();
        for (int idx = 0; idx < allFileMeta.size(); idx++) {
            Map<String, Object> meta = allFileMeta.get(idx);
            String pname = paramNames.get(idx);
            String originalFilename = String.valueOf(meta.get("originalFilename"));
            parsedCustomParams.put(pname, originalFilename);
            parsedCustomParams.put("__" + pname + "_fileId", meta.get("fileId"));
            parsedCustomParams.put("__" + pname + "_fileType", meta.get("fileType"));
            parsedCustomParams.put("__" + pname + "_fileSize", meta.get("fileSize"));
            parsedCustomParams.put("__" + pname + "_fileHash", meta.get("fileHash"));

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("fileId", meta.get("fileId"));
            summary.put("filename", originalFilename);
            summary.put("fileType", meta.get("fileType"));
            summary.put("scanStatus", "CLEAN");
            attachedFilesSummary.add(summary);
        }

        // Determine query type.
        QueryType qt = null;
        if (queryTypeParam != null) {
            try {
                qt = QueryType.fromValue(queryTypeParam);
            } catch (Exception ignored) {
                qt = null;
            }
        }
        if (qt == null) {
            qt = detectQueryType(query);
        }

        // Files the data holder refused because they don't meet the request type's format criteria.
        List<Map<String, Object>> fileFailures = new ArrayList<>();

        // Forward files to the data holder so they live on its server.
        try {
            RdapResolution resolution = resolveFor(qt, query);
            if (resolution != null && resolution.getBaseUrls() != null && !resolution.getBaseUrls().isEmpty()) {
                String dhBase = stripTrailingSlash(resolution.getBaseUrls().get(0));
                URI parsed = URI.create(dhBase);
                String dhOrigin = parsed.getScheme() + "://" + parsed.getAuthority();
                String uploadUrl = dhOrigin + "/api/admin/files/upload";
                log.info("Forwarding {} file(s) to data holder at {}", allFileMeta.size(), uploadUrl);

                for (int idx = 0; idx < allFileMeta.size(); idx++) {
                    Map<String, Object> meta = allFileMeta.get(idx);
                    String pname = paramNames.get(idx);
                    String storedFilename = String.valueOf(meta.get("storedFilename"));
                    byte[] content;
                    try {
                        content = fileService.readStored(storedFilename);
                    } catch (IOException e) {
                        log.warn("File not found for forwarding: {}", storedFilename);
                        continue;
                    }

                    MultipartBodyBuilder builder = new MultipartBodyBuilder();
                    String originalFilename = String.valueOf(meta.get("originalFilename"));
                    builder.part("file", new ByteArrayResource(content) {
                        @Override
                        public String getFilename() {
                            return originalFilename;
                        }
                    }).contentType(MediaType.parseMediaType(String.valueOf(meta.get("mimeType"))));

                    /* Carry the parameter context so the data holder can look up the format
                     * criteria authored on this request type's file parameter and validate
                     * the document against them. */
                    if (requestorGroup != null) builder.part("requestorGroupCode", requestorGroup);
                    if (requestType != null) builder.part("requestTypeCode", String.valueOf(requestType));
                    if (pname != null) builder.part("paramName", pname);

                    try {
                        String respBody = rdapWebClient.post()
                                .uri(uploadUrl)
                                .contentType(MediaType.MULTIPART_FORM_DATA)
                                .body(BodyInserters.fromMultipartData(builder.build()))
                                .retrieve()
                                .bodyToMono(String.class)
                                .block(Duration.ofSeconds(30));
                        if (respBody != null) {
                            Map<String, Object> dhResult = objectMapper.readValue(respBody, MAP_TYPE);
                            if (Boolean.TRUE.equals(dhResult.get("success"))) {
                                Object dhFileId = dhResult.get("fileId");
                                log.info("File forwarded to data holder: {} -> {}", originalFilename, dhFileId);
                                parsedCustomParams.put("__" + pname + "_fileId", dhFileId);
                                attachedFilesSummary.get(idx).put("fileId", dhFileId);
                            } else {
                                log.warn("Data holder rejected file {}: {}", originalFilename, dhResult);
                                fileFailures.add(toFileRejection(originalFilename, pname, dhResult));
                            }
                        }
                    } catch (WebClientResponseException e) {
                        // A format rejection comes back as 400 with the specific criteria that failed.
                        Map<String, Object> body = safeParseJson(e.getResponseBodyAsString());
                        if (e.getStatusCode().is4xxClientError() && body != null) {
                            log.warn("Data holder rejected file {}: {}", originalFilename, body);
                            fileFailures.add(toFileRejection(originalFilename, pname, body));
                        } else {
                            log.warn("Data holder file upload failed for {}: {}", originalFilename, e.toString());
                        }
                    } catch (Exception e) {
                        log.warn("Data holder file upload failed for {}: {}", originalFilename, e.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not forward files to data holder: {}", e.toString());
        }

        /* Stop the submission when an attachment fails the data holder's format requirements.
         * Continuing would run the query without the required document and leave the requestor
         * with no idea why, so surface the exact criteria that failed instead. */
        if (!fileFailures.isEmpty()) {
            List<String> reasons = new ArrayList<>();
            for (Map<String, Object> f : fileFailures) {
                Object msgs = f.get("messages");
                if (msgs instanceof List) {
                    for (Object m : (List<?>) msgs) reasons.add(String.valueOf(m));
                }
            }
            String summary = reasons.isEmpty()
                    ? "An attached file does not meet the required format for this request type."
                    : String.join(" ", reasons);
            log.warn("Submission blocked — {} attachment(s) failed format requirements", fileFailures.size());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "File format requirements not met");
            body.put("message", summary);
            body.put("fileFailures", fileFailures);
            return ResponseEntity.badRequest().body(body);
        }

        if (requestorGroup != null) {
            validateGroupCode(requestorGroup, "requestorGroup");
        }
        if (dataHolderGroup != null) {
            validateGroupCode(dataHolderGroup, "dataHolderGroup");
        }
        if (requestType != null) {
            validateRequestTypeCode(requestType);
        }

        boolean useNewParams = (requestorGroup != null && requestType != null);

        RdapService.QueryOptions opt = new RdapService.QueryOptions();
        opt.token = token;
        opt.agreements = useNewParams ? null : agreements;
        opt.dataHolderGroup = useNewParams ? dataHolderGroup : null;
        opt.requestorGroup = useNewParams ? requestorGroup : null;
        opt.requestType = useNewParams ? requestType : null;
        opt.jakeCompliance = jakeCompliance;
        opt.confidential = confidential;
        opt.exigent = exigent;
        opt.customParams = parsedCustomParams;

        RdapResponse result = runQuery(qt, query, opt);
        if (result == null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("error", true);
            r.put("errorCode", "400");
            r.put("errorMessage", "Unsupported query type: " + qt);
            return r;
        }

        if (!result.isSuccess()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("error", true);
            r.put("errorCode", "error");
            r.put("errorMessage", result.getErrorMessage() != null
                    ? result.getErrorMessage() : "No RDAP data found for " + query);
            r.put("queryType", qt.getValue());
            r.put("queryValue", query);
            r.put("source", result.getSource() != null ? result.getSource() : "RDAP");
            r.put("attachedFiles", attachedFilesSummary);
            return r;
        }

        // NOTE: the Python here also calls _save_query_to_history; that logic lives in
        // RdapController. We return the successful RDAPResponse directly (the Python
        // returned result.dict()). History save for the file path is a known gap — see TODO.
        return result;
    }

    // ---- helpers ----

    private RdapResolution resolveFor(QueryType qt, String query) {
        return switch (qt) {
            case DOMAIN -> ianaBootstrapService.resolveDomain(query);
            case IP -> ianaBootstrapService.resolveIp(query);
            case ASN -> ianaBootstrapService.resolveAsn(query);
            case ENTITY -> ianaBootstrapService.resolveEntity(query);
            default -> null;
        };
    }

    private RdapResponse runQuery(QueryType qt, String query, RdapService.QueryOptions opt) {
        return switch (qt) {
            case DOMAIN -> rdapService.queryDomain(query, opt);
            case IP -> rdapService.queryIp(query, opt);
            case ASN -> rdapService.queryAsn(query, opt);
            case ENTITY -> rdapService.queryEntity(query, opt);
            default -> null;
        };
    }

    private QueryType detectQueryType(String queryRaw) {
        String q = queryRaw.trim();
        if (q.toUpperCase().startsWith("AS") || q.matches("^\\d+$")) {
            var m = java.util.regex.Pattern.compile("^(?:AS)?(\\d+)$",
                    java.util.regex.Pattern.CASE_INSENSITIVE).matcher(q);
            if (m.matches()) {
                try {
                    long n = Long.parseLong(m.group(1));
                    if (n >= 0 && n <= 4294967295L) {
                        return QueryType.ASN;
                    }
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }
        String hostPart = q.contains("/") ? q.substring(0, q.indexOf('/')) : q;
        if (hostPart.matches("[0-9a-fA-F:.]+")) {
            try {
                java.net.InetAddress.getByName(hostPart);
                return QueryType.IP;
            } catch (Exception ignored) {
                // not an IP
            }
        }
        if (q.contains("-") && !q.contains(".")) {
            return QueryType.ENTITY;
        }
        return QueryType.DOMAIN;
    }

    private void validateGroupCode(String code, String fieldName) {
        if (code == null || code.isEmpty()) {
            return;
        }
        if (code.length() > 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must be ≤10 characters");
        }
        if (!code.matches("^[A-Za-z0-9]([A-Za-z0-9\\-]{0,8}[A-Za-z0-9])?$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName
                    + " must contain only letters, digits, and hyphens, and cannot begin or end with a hyphen");
        }
    }

    private void validateRequestTypeCode(Integer code) {
        if (code != null && (code < 0 || code > 999)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "requestType must be between 0 and 999");
        }
    }

    private void validateUuid(String fileId) {
        try {
            UUID.fromString(fileId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid file ID");
        }
    }

    private static String stripTrailingSlash(String url) {
        String u = url;
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
