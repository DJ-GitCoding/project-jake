/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.dto.RdapResolution;
import com.jaddar.dto.RdapResponse;
import com.jaddar.enums.QueryType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RDAP (Registration Data Access Protocol) service for domain/IP/ASN/entity lookups.
 *
 * Ported from backend/services/rdap_service.py. The Python used the `whoisit`
 * library + httpx; there is no Java equivalent, so RDAP is reimplemented directly
 * over HTTP using the injected rdapWebClient. The IANA bootstrap (+ custom data
 * holders) resolves the base URL, then we GET the RDAP JSON and parse it.
 */
@Slf4j
@Service
public class RdapService {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final WebClient rdapWebClient;
    private final IanaBootstrapService ianaBootstrapService;
    private final ObjectMapper objectMapper;

    /** Mirrors the Python rdap_service.requestor_agent_id (exposed via /health). */
    private final String requestorAgentId = "JADDAR-001";

    public RdapService(@Qualifier("rdapWebClient") WebClient rdapWebClient,
                       IanaBootstrapService ianaBootstrapService,
                       ObjectMapper objectMapper) {
        this.rdapWebClient = rdapWebClient;
        this.ianaBootstrapService = ianaBootstrapService;
        this.objectMapper = objectMapper;
        log.info("RDAP Service initialized with requestor ID: {}", requestorAgentId);
    }

    public String getRequestorAgentId() {
        return requestorAgentId;
    }

    /**
     * Options forwarded from the controller into a query. Replaces the long
     * keyword-argument list on the Python query_* methods.
     */
    public static class QueryOptions {
        public String token;
        public String agreements;
        public Map<String, Object> userInfo;
        public String requestorGroup;
        public Integer requestType;
        public String dataHolderGroup;
        public boolean jakeCompliance = false;
        public boolean confidential = false;
        public boolean exigent = false;
        public Map<String, Object> customParams;
    }

    // ------------------------------------------------------------------ //
    //  Public query methods
    // ------------------------------------------------------------------ //

    public RdapResponse queryDomain(String domain, QueryOptions opt) {
        opt = opt != null ? opt : new QueryOptions();
        try {
            RdapResolution resolution = ianaBootstrapService.resolveDomain(domain);
            if (resolution == null || resolution.getBaseUrls() == null || resolution.getBaseUrls().isEmpty()) {
                return errorResponse(domain, QueryType.DOMAIN,
                        "No RDAP server found for domain: " + domain);
            }
            String baseUrl = stripTrailingSlash(resolution.getBaseUrls().get(0));
            String rdapUrl = baseUrl + "/domain/" + domain;
            log.info("Querying RDAP domain: {} (source: {})", rdapUrl, resolution.getSource());

            FetchResult result = fetchRdap(rdapUrl, resolution, opt);
            if (result.error != null) {
                return buildErrorResponse(domain, QueryType.DOMAIN, result.error, baseUrl, resolution.getSource());
            }
            Map<String, Object> parsed = parseDomainDict(result.data);
            return successResponse(domain, QueryType.DOMAIN, result.data, parsed, baseUrl, resolution.getSource());
        } catch (Exception e) {
            log.error("RDAP domain query failed for {}", domain, e);
            return errorResponse(domain, QueryType.DOMAIN, "RDAP lookup failed for the requested query.");
        }
    }

    public RdapResponse queryIp(String ipAddress, QueryOptions opt) {
        opt = opt != null ? opt : new QueryOptions();
        try {
            RdapResolution resolution = ianaBootstrapService.resolveIp(ipAddress);
            if (resolution == null || resolution.getBaseUrls() == null || resolution.getBaseUrls().isEmpty()) {
                return errorResponse(ipAddress, QueryType.IP, "No RDAP server found for IP: " + ipAddress);
            }
            String baseUrl = stripTrailingSlash(resolution.getBaseUrls().get(0));
            String rdapUrl = baseUrl + "/ip/" + ipAddress;
            log.info("Querying RDAP IP: {} (source: {})", rdapUrl, resolution.getSource());

            FetchResult result = fetchRdap(rdapUrl, resolution, opt);
            if (result.error != null) {
                return buildErrorResponse(ipAddress, QueryType.IP, result.error, baseUrl, resolution.getSource());
            }
            Map<String, Object> parsed = parseIpDict(result.data);
            return successResponse(ipAddress, QueryType.IP, result.data, parsed, baseUrl, resolution.getSource());
        } catch (Exception e) {
            log.error("RDAP IP query failed for {}", ipAddress, e);
            return errorResponse(ipAddress, QueryType.IP, "RDAP lookup failed for the requested query.");
        }
    }

    public RdapResponse queryAsn(String asn, QueryOptions opt) {
        opt = opt != null ? opt : new QueryOptions();
        try {
            String asnClean = asn.toUpperCase().startsWith("AS")
                    ? asn.toUpperCase().replace("AS", "") : asn;
            RdapResolution resolution = ianaBootstrapService.resolveAsn(asn);
            if (resolution == null || resolution.getBaseUrls() == null || resolution.getBaseUrls().isEmpty()) {
                return errorResponse("AS" + asnClean, QueryType.ASN, "No RDAP server found for ASN: " + asn);
            }
            String baseUrl = stripTrailingSlash(resolution.getBaseUrls().get(0));
            String rdapUrl = baseUrl + "/autnum/" + asnClean;
            log.info("Querying RDAP ASN: {} (source: {})", rdapUrl, resolution.getSource());

            FetchResult result = fetchRdap(rdapUrl, resolution, opt);
            if (result.error != null) {
                return buildErrorResponse("AS" + asnClean, QueryType.ASN, result.error, baseUrl, resolution.getSource());
            }
            Map<String, Object> parsed = parseAsnDict(result.data);
            return successResponse("AS" + asnClean, QueryType.ASN, result.data, parsed, baseUrl, resolution.getSource());
        } catch (Exception e) {
            log.error("RDAP ASN query failed for {}", asn, e);
            return errorResponse("AS" + asn, QueryType.ASN, "RDAP lookup failed for the requested query.");
        }
    }

    public RdapResponse queryEntity(String entityHandle, QueryOptions opt) {
        opt = opt != null ? opt : new QueryOptions();
        try {
            RdapResolution resolution = ianaBootstrapService.resolveEntity(entityHandle);
            if (resolution == null || resolution.getBaseUrls() == null || resolution.getBaseUrls().isEmpty()) {
                return errorResponse(entityHandle, QueryType.ENTITY, "No RDAP server found for entity: " + entityHandle);
            }
            String baseUrl = stripTrailingSlash(resolution.getBaseUrls().get(0));
            String rdapUrl = baseUrl + "/entity/" + entityHandle;
            log.info("Querying RDAP entity: {} (source: {})", rdapUrl, resolution.getSource());

            FetchResult result = fetchRdap(rdapUrl, resolution, opt);
            if (result.error != null) {
                return buildErrorResponse(entityHandle, QueryType.ENTITY, result.error, baseUrl, resolution.getSource());
            }
            // Entity responses are returned raw (no structured parsing in the Python).
            return successResponse(entityHandle, QueryType.ENTITY, result.data, new HashMap<>(), baseUrl, resolution.getSource());
        } catch (Exception e) {
            log.error("RDAP entity query failed for {}", entityHandle, e);
            return errorResponse(entityHandle, QueryType.ENTITY, "RDAP lookup failed for the requested query.");
        }
    }

    // ------------------------------------------------------------------ //
    //  Response builders
    // ------------------------------------------------------------------ //

    private RdapResponse errorResponse(String query, QueryType type, String message) {
        RdapResponse r = new RdapResponse();
        r.setQuery(query);
        r.setQueryType(type);
        r.setRawData(new HashMap<>());
        r.setParsedData(new HashMap<>());
        r.setSuccess(false);
        r.setErrorMessage(message);
        return r;
    }

    private RdapResponse successResponse(String query, QueryType type, Map<String, Object> raw,
                                         Map<String, Object> parsed, String rdapServer, String source) {
        RdapResponse r = new RdapResponse();
        r.setQuery(query);
        r.setQueryType(type);
        r.setRawData(raw);
        r.setParsedData(parsed);
        r.setSuccess(true);
        r.setSource(source);
        r.setRdapServer(rdapServer);
        return r;
    }

    private RdapResponse buildErrorResponse(String query, QueryType type, FetchError fe,
                                            String rdapServer, String source) {
        String label = "RDAP query for " + type.getValue() + ": " + query;
        RdapResponse r = new RdapResponse();
        r.setQuery(query);
        r.setQueryType(type);
        r.setRawData(fe.toRawData());
        r.setParsedData(new HashMap<>());
        r.setSuccess(false);
        r.setErrorMessage(fe.toErrorMessage(label));
        r.setSource(source);
        r.setRdapServer(rdapServer);
        return r;
    }

    // ------------------------------------------------------------------ //
    //  Query parameter building
    // ------------------------------------------------------------------ //

    static Map<String, Object> buildQueryParams(QueryOptions opt, ObjectMapper mapper) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (opt.requestorGroup != null && opt.requestType != null) {
            params.put("requestorGroup", opt.requestorGroup);
            params.put("requestType", opt.requestType);
            if (opt.dataHolderGroup != null && !opt.dataHolderGroup.isEmpty()) {
                params.put("dataHolderGroup", opt.dataHolderGroup);
            }
        } else if (opt.agreements != null && !opt.agreements.isEmpty()) {
            params.put("agreements", opt.agreements);
        }
        if (opt.jakeCompliance) {
            params.put("jakeCompliance", true);
        }
        if (opt.confidential) {
            params.put("confidential", true);
        }
        if (opt.exigent) {
            params.put("exigent", true);
        }
        if (opt.customParams != null && !opt.customParams.isEmpty()) {
            try {
                params.put("customParams", mapper.writeValueAsString(opt.customParams));
            } catch (Exception e) {
                log.warn("Failed to serialize customParams: {}", e.toString());
            }
        }
        return params;
    }

    // ------------------------------------------------------------------ //
    //  HTTP fetch
    // ------------------------------------------------------------------ //

    private static class FetchResult {
        Map<String, Object> data;
        FetchError error;
    }

    private FetchResult fetchRdap(String url, RdapResolution resolution, QueryOptions opt) {
        boolean requiresAuth = resolution.isRequiresAuth();
        Map<String, Object> params = buildQueryParams(opt, objectMapper);

        // Forward requestor identity headers to custom data holders only.
        Map<String, String> extraHeaders = new HashMap<>();
        if (opt.userInfo != null && "custom".equals(resolution.getSource())) {
            Object email = opt.userInfo.get("email");
            Object sub = opt.userInfo.get("sub");
            if (email != null) {
                extraHeaders.put("X-Requestor-Email", String.valueOf(email));
            }
            if (sub != null) {
                extraHeaders.put("X-Requestor-Sub", String.valueOf(sub));
            }
        }

        FetchResult fr = new FetchResult();

        /* Build the final URI with query params.
         *
         * Must be an encoded java.net.URI, not a String: customParams is serialized JSON, so the
         * value contains '{' and '}'. WebClient's uri(String) overload treats those as URI
         * template placeholders and fails with "Not enough variable values available to expand".
         * encode() percent-encodes them and the URI overload skips template expansion entirely. */
        UriComponentsBuilder ub = UriComponentsBuilder.fromHttpUrl(url);
        for (Map.Entry<String, Object> e : params.entrySet()) {
            ub.queryParam(e.getKey(), e.getValue());
        }
        URI finalUri = ub.build().encode().toUri();

        log.info("RDAP fetch: url={} requires_auth={} token_present={}", url, requiresAuth, opt.token != null);

        try {
            WebClient.RequestHeadersSpec<?> spec = rdapWebClient.get()
                    .uri(finalUri)
                    .header(HttpHeaders.ACCEPT, "application/rdap+json, application/json");
            // header(...) mutates and returns the same builder; call for side effect.
            if (requiresAuth && opt.token != null) {
                spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + opt.token);
            } else if (requiresAuth) {
                log.warn("RDAP fetch: Auth required but no token available");
            }
            for (Map.Entry<String, String> h : extraHeaders.entrySet()) {
                spec.header(h.getKey(), h.getValue());
            }

            String body = spec.retrieve()
                    .bodyToMono(String.class)
                    .block(HTTP_TIMEOUT);

            fr.data = body == null ? new HashMap<>() : objectMapper.readValue(body, MAP_TYPE);
            return fr;
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();
            Map<String, Object> jsonBody = null;
            String message = "HTTP " + status;
            try {
                jsonBody = objectMapper.readValue(responseBody, MAP_TYPE);
                String serverMsg = firstNonNull(jsonBody,
                        "errorMessage", "description", "title", "message", "detail");
                if (serverMsg != null) {
                    message = serverMsg;
                }
            } catch (Exception ignored) {
                // Non-JSON body — keep the raw text (truncated below).
            }
            log.error("RDAP HTTP {} for {}", status, url);
            fr.error = new FetchError(status, message,
                    jsonBody == null ? truncate(responseBody, 2000) : null, jsonBody, "http");
            return fr;
        } catch (WebClientRequestException e) {
            // Connection-level failure (DNS, refused, reset, etc.)
            log.error("RDAP connection failed for {}", url, e);
            fr.error = new FetchError(null, "Connection to the data holder failed", null, null, "connection");
            return fr;
        } catch (IllegalStateException e) {
            // .block() timeout surfaces here
            log.error("RDAP timeout for {}", url, e);
            fr.error = new FetchError(null, "The data holder did not respond in time", null, null, "timeout");
            return fr;
        } catch (Exception e) {
            log.error("RDAP fetch failed for {}", url, e);
            fr.error = new FetchError(null, "The data holder request could not be completed", null, null, "unknown");
            return fr;
        }
    }

    // ------------------------------------------------------------------ //
    //  FetchError (rich error detail), ported from rdap_service.FetchError
    // ------------------------------------------------------------------ //

    static class FetchError {
        final Integer statusCode;
        final String message;
        final String responseBody;
        final Map<String, Object> jsonBody;
        final String errorType; // http, connection, timeout, parse, unknown

        FetchError(Integer statusCode, String message, String responseBody,
                   Map<String, Object> jsonBody, String errorType) {
            this.statusCode = statusCode;
            this.message = message;
            this.responseBody = responseBody;
            this.jsonBody = jsonBody;
            this.errorType = errorType;
        }

        String toErrorMessage(String queryLabel) {
            if ("http".equals(errorType) && statusCode != null) {
                String label = statusLabel(statusCode);
                if (jsonBody != null) {
                    String serverMsg = firstNonNull(jsonBody,
                            "errorMessage", "description", "title", "message", "detail");
                    if (serverMsg != null) {
                        return queryLabel + ": " + label + " — " + serverMsg;
                    }
                }
                return queryLabel + ": " + label;
            }
            if ("connection".equals(errorType)) {
                return queryLabel + ": Connection failed — the data holder server is unreachable";
            }
            if ("timeout".equals(errorType)) {
                return queryLabel + ": Request timed out — the data holder did not respond in time";
            }
            if ("parse".equals(errorType)) {
                return queryLabel + ": Invalid response — the data holder returned non-JSON data";
            }
            return queryLabel + ": " + message;
        }

        Map<String, Object> toRawData() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("errorType", errorType);
            data.put("errorMessage", message);
            if (statusCode != null) {
                data.put("httpStatusCode", statusCode);
            }
            if (jsonBody != null) {
                for (String key : new String[]{"errorCode", "errorMessage", "description", "title",
                        "message", "detail", "status", "type", "notices", "remarks"}) {
                    Object v = jsonBody.get(key);
                    if (v != null) {
                        data.put("server_" + key, v);
                    }
                }
                if (jsonBody.containsKey("rdapConformance")) {
                    data.put("rdapConformance", jsonBody.get("rdapConformance"));
                }
                return data;
            }
            if (responseBody != null) {
                String body = responseBody;
                if (body.toLowerCase().contains("<html")) {
                    Matcher titleM = Pattern.compile("<title>(.*?)</title>",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(body);
                    if (titleM.find()) {
                        data.put("serverTitle", titleM.group(1).trim());
                    }
                    Matcher pM = Pattern.compile("<p>(.*?)</p>",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(body);
                    StringBuilder msgs = new StringBuilder();
                    while (pM.find()) {
                        String text = pM.group(1).replaceAll("<[^>]+>", "").trim();
                        if (!text.isEmpty()) {
                            if (msgs.length() > 0) {
                                msgs.append(' ');
                            }
                            msgs.append(text);
                        }
                    }
                    if (msgs.length() > 0) {
                        data.put("serverMessage", msgs.toString().trim());
                    }
                    Matcher addrM = Pattern.compile("<address>(.*?)</address>",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(body);
                    if (addrM.find()) {
                        data.put("serverSignature", addrM.group(1).replaceAll("<[^>]+>", "").trim());
                    }
                } else {
                    data.put("serverResponse", truncate(body, 1000));
                }
            }
            return data;
        }

        private static String statusLabel(int code) {
            return switch (code) {
                case 400 -> "Bad Request";
                case 401 -> "Unauthorized — the data holder rejected your credentials";
                case 403 -> "Forbidden — you do not have permission to access this resource";
                case 404 -> "Not Found — the data holder has no record for this query";
                case 405 -> "Method Not Allowed";
                case 406 -> "Not Acceptable";
                case 429 -> "Too Many Requests — rate limited by the data holder";
                case 500 -> "Internal Server Error at the data holder";
                case 502 -> "Bad Gateway — the data holder's upstream server failed";
                case 503 -> "Service Unavailable — the data holder is temporarily down";
                case 504 -> "Gateway Timeout — the data holder took too long to respond";
                default -> "HTTP " + code;
            };
        }
    }

    @SuppressWarnings("unchecked")
    private static String firstNonNull(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object v = body.get(key);
            if (v != null) {
                if (v instanceof List<?> list) {
                    List<String> parts = new ArrayList<>();
                    for (Object o : list) {
                        parts.add(String.valueOf(o));
                    }
                    return String.join("; ", parts);
                }
                String s = String.valueOf(v);
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String stripTrailingSlash(String url) {
        String u = url;
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    // ------------------------------------------------------------------ //
    //  Parsing helpers — port of _parse_domain_dict / _parse_ip_dict /
    //  _parse_asn_dict / _parse_vcard, including access-level unwrapping.
    // ------------------------------------------------------------------ //

    /** Unwrap access-level-annotated values from custom data holders ({value, requiredAccessLevel}). */
    @SuppressWarnings("unchecked")
    static Object unwrap(Object value) {
        if (value instanceof Map<?, ?> m
                && m.containsKey("value") && m.containsKey("requiredAccessLevel") && m.size() == 2) {
            return ((Map<String, Object>) m).get("value");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDomainDict(Map<String, Object> result) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("domain", asString(unwrap(result.get("ldhName")), ""));
        info.put("handle", asString(unwrap(result.get("handle")), null));
        info.put("status", new ArrayList<String>());
        info.put("nameservers", new ArrayList<String>());

        Object statusVal = unwrap(result.get("status"));
        if (statusVal != null) {
            List<String> status = new ArrayList<>();
            if (statusVal instanceof List<?> list) {
                for (Object o : list) {
                    status.add(String.valueOf(o));
                }
            } else {
                status.add(String.valueOf(statusVal));
            }
            info.put("status", status);
        }

        Object nsVal = unwrap(result.get("nameservers"));
        if (nsVal instanceof List<?> nsList) {
            List<String> nameservers = new ArrayList<>();
            for (Object nsObj : nsList) {
                Object ns = unwrap(nsObj);
                if (ns instanceof Map<?, ?> nsMap) {
                    Object name = unwrap(((Map<String, Object>) nsMap).get("ldhName"));
                    if (name != null) {
                        nameservers.add(String.valueOf(name));
                    }
                }
            }
            info.put("nameservers", nameservers);
        }

        parseEvents(result, info, true);

        Object entities = unwrap(result.get("entities"));
        if (entities instanceof List<?> entList) {
            for (Object entObj : entList) {
                Object entity = unwrap(entObj);
                if (entity instanceof Map<?, ?> entMap) {
                    Object roles = unwrap(((Map<String, Object>) entMap).get("roles"));
                    if (roles instanceof List<?> roleList && roleList.contains("registrar")) {
                        Map<String, Object> vcard = parseVcard(unwrap(((Map<String, Object>) entMap).get("vcardArray")));
                        if (!vcard.isEmpty()) {
                            Object org = vcard.get("org");
                            info.put("registrar", org != null ? org : vcard.get("fn"));
                        }
                    }
                }
            }
        }
        return info;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseIpDict(Map<String, Object> result) {
        Object networkObj = result.getOrDefault("network", result);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("ip", asString(unwrap(result.get("handle")), ""));
        info.put("handle", asString(unwrap(result.get("handle")), null));
        info.put("name", asString(unwrap(result.get("name")), null));
        info.put("country", asString(unwrap(result.get("country")), null));

        if (networkObj instanceof Map<?, ?> netMap) {
            Object cidrsObj = unwrap(((Map<String, Object>) netMap).get("cidr0_cidrs"));
            if (cidrsObj instanceof List<?> cidrs && !cidrs.isEmpty()) {
                Object cidr = unwrap(cidrs.get(0));
                if (cidr instanceof Map<?, ?> cidrMap) {
                    Map<String, Object> cm = (Map<String, Object>) cidrMap;
                    if (cm.containsKey("v4prefix")) {
                        info.put("network", unwrap(cm.get("v4prefix")) + "/" + asString(unwrap(cm.get("length")), ""));
                    } else if (cm.containsKey("v6prefix")) {
                        info.put("network", unwrap(cm.get("v6prefix")) + "/" + asString(unwrap(cm.get("length")), ""));
                    }
                }
            }
        }
        if (!info.containsKey("network")) {
            Object start = unwrap(result.get("startAddress"));
            Object end = unwrap(result.get("endAddress"));
            if (start != null && end != null) {
                info.put("network", start + " - " + end);
            }
        }

        parseEvents(result, info, false);

        Object entities = unwrap(result.get("entities"));
        if (entities instanceof List<?> entList) {
            for (Object entObj : entList) {
                Object entity = unwrap(entObj);
                if (entity instanceof Map<?, ?> entMap) {
                    Object roles = unwrap(((Map<String, Object>) entMap).get("roles"));
                    if (roles instanceof List<?> roleList) {
                        if (roleList.contains("registrant") || roleList.contains("administrative")) {
                            Map<String, Object> vcard = parseVcard(unwrap(((Map<String, Object>) entMap).get("vcardArray")));
                            if (!vcard.isEmpty()) {
                                Object org = vcard.get("org");
                                info.put("org_name", org != null ? org : vcard.get("fn"));
                                info.put("org_address", vcard.get("adr"));
                            }
                        }
                        if (roleList.contains("abuse")) {
                            Map<String, Object> vcard = parseVcard(unwrap(((Map<String, Object>) entMap).get("vcardArray")));
                            if (vcard.containsKey("email")) {
                                info.put("abuse_email", vcard.get("email"));
                            }
                        }
                    }
                }
            }
        }
        parseRemarks(result, info);
        return info;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAsnDict(Map<String, Object> result) {
        Object asnNum = unwrap(result.get("handle"));
        if ((asnNum == null || String.valueOf(asnNum).isEmpty()) && result.containsKey("autnums")) {
            asnNum = String.valueOf(unwrap(result.get("startAutnum")));
        }
        Map<String, Object> info = new LinkedHashMap<>();
        String asnStr;
        if (asnNum != null && !String.valueOf(asnNum).isEmpty()
                && !String.valueOf(asnNum).startsWith("AS")) {
            asnStr = "AS" + asnNum;
        } else {
            asnStr = String.valueOf(asnNum);
        }
        info.put("asn", asnStr);
        info.put("handle", asString(unwrap(result.get("handle")), null));
        info.put("name", asString(unwrap(result.get("name")), null));
        info.put("country", asString(unwrap(result.get("country")), null));

        parseEvents(result, info, false);

        Object entities = unwrap(result.get("entities"));
        if (entities instanceof List<?> entList) {
            for (Object entObj : entList) {
                Object entity = unwrap(entObj);
                if (entity instanceof Map<?, ?> entMap) {
                    Object roles = unwrap(((Map<String, Object>) entMap).get("roles"));
                    if (roles instanceof List<?> roleList && roleList.contains("registrant")) {
                        Map<String, Object> vcard = parseVcard(unwrap(((Map<String, Object>) entMap).get("vcardArray")));
                        if (!vcard.isEmpty()) {
                            Object org = vcard.get("org");
                            info.put("org_name", org != null ? org : vcard.get("fn"));
                        }
                    }
                }
            }
        }
        parseRemarks(result, info);
        return info;
    }

    @SuppressWarnings("unchecked")
    private void parseEvents(Map<String, Object> result, Map<String, Object> info, boolean includeExpiration) {
        Object events = unwrap(result.get("events"));
        if (events instanceof List<?> eventList) {
            for (Object evObj : eventList) {
                Object event = unwrap(evObj);
                if (event instanceof Map<?, ?> evMap) {
                    Object action = unwrap(((Map<String, Object>) evMap).get("eventAction"));
                    Object date = unwrap(((Map<String, Object>) evMap).get("eventDate"));
                    if ("registration".equals(action)) {
                        info.put("created_date", date);
                    } else if ("last changed".equals(action)) {
                        info.put("updated_date", date);
                    } else if (includeExpiration && "expiration".equals(action)) {
                        info.put("expiration_date", date);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void parseRemarks(Map<String, Object> result, Map<String, Object> info) {
        Object remarks = unwrap(result.get("remarks"));
        if (remarks instanceof List<?> remarkList && !remarkList.isEmpty()) {
            List<String> descriptions = new ArrayList<>();
            for (Object rObj : remarkList) {
                Object remark = unwrap(rObj);
                if (remark instanceof Map<?, ?> rMap && rMap.containsKey("description")) {
                    Object desc = unwrap(((Map<String, Object>) rMap).get("description"));
                    if (desc instanceof List<?> descList) {
                        for (Object d : descList) {
                            descriptions.add(String.valueOf(d));
                        }
                    } else if (desc != null) {
                        descriptions.add(String.valueOf(desc));
                    }
                }
            }
            if (!descriptions.isEmpty()) {
                info.put("description", String.join(" ", descriptions));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseVcard(Object vcardArrayObj) {
        Object vcardArray = unwrap(vcardArrayObj);
        Map<String, Object> vcardData = new LinkedHashMap<>();
        if (!(vcardArray instanceof List<?> arr) || arr.size() < 2) {
            return vcardData;
        }
        Object itemsObj = arr.get(1);
        if (!(itemsObj instanceof List<?> items)) {
            return vcardData;
        }
        for (Object itemObj : items) {
            if (!(itemObj instanceof List<?> item) || item.size() < 4) {
                continue;
            }
            String type = String.valueOf(item.get(0)).toLowerCase();
            Object value = unwrap(item.get(3));
            switch (type) {
                case "fn" -> {
                    vcardData.put("name", value);
                    vcardData.put("fn", value);
                }
                case "org" -> vcardData.put("org", value);
                case "email" -> vcardData.put("email", value);
                case "tel" -> vcardData.put("phone", value);
                case "adr" -> {
                    if (value instanceof List<?> parts) {
                        List<String> nonEmpty = new ArrayList<>();
                        for (Object p : parts) {
                            if (p != null && !String.valueOf(p).isEmpty()) {
                                nonEmpty.add(String.valueOf(p));
                            }
                        }
                        vcardData.put("adr", String.join(", ", nonEmpty));
                    } else {
                        vcardData.put("adr", value);
                    }
                }
                default -> {
                    // ignore other vcard fields
                }
            }
        }
        return vcardData;
    }

    private static String asString(Object o, String dflt) {
        return o != null ? String.valueOf(o) : dflt;
    }
}
