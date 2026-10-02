/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.rdrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.entity.RdrsMailbox;
import com.jaddar.enums.LogCategory;
import com.jaddar.enums.LogLevel;
import com.jaddar.service.LoggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Requestor-facing endpoints for ICANN's Registration Data Request Service.
 *
 * <p>The flow a requestor sees is: sign in to ICANN, look a domain up, fill the form,
 * send. Each requestor uses their own ICANN credentials — nothing here is shared, and
 * no ICANN credential is ever persisted. The RDRS session cookie lives only in
 * {@link RdrsSessionStore}, keyed by the caller's Keycloak subject, and never reaches
 * the browser.
 */
@Slf4j
@RestController
@RequestMapping("/api/rdrs")
@RequiredArgsConstructor
public class RdrsController {

    private final RdrsMailboxService mailboxes;
    private final RdrsSidecarClient sidecar;
    private final RdrsClient rdrs;
    private final RdrsSessionStore sessions;
    private final RdrsCommandValidator validator;
    private final LoggingService logging;
    private final ObjectMapper objectMapper;

    /** Anything that reaches here is already authenticated by the resource server. */
    private String subject(Jwt jwt) {
        return jwt.getSubject();
    }

    // ─── Session ────────────────────────────────────────────────────

    /** Whether the caller already has a live RDRS session. */
    @GetMapping("/session")
    public RdrsDto.SessionResponse session(@AuthenticationPrincipal Jwt jwt) {
        RdrsDto.SessionResponse out = new RdrsDto.SessionResponse();
        sessions.get(subject(jwt)).ifPresentOrElse(session -> {
            out.setStatus("active");
            out.setExpiresAt(session.getExpiresAt().getEpochSecond());
            out.setProfile(session.getProfile());
        }, () -> out.setStatus("none"));
        return out;
    }

    /**
     * Sign in to ICANN. May answer asking for a multi-factor code.
     *
     * <p>The address is this caller's assigned Jaddar RDRS address, taken from their own
     * mailbox rather than from the request body: signing in under any other address would
     * send ICANN's correspondence to a mailbox Jaddar cannot read. Whatever the body
     * carries is ignored, so the read-only field in the UI is a real constraint and not
     * merely a suggestion.
     */
    @PostMapping("/session")
    public RdrsDto.SessionResponse login(@RequestBody RdrsDto.LoginRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new RdrsException("Enter your ICANN password.", 400);
        }
        RdrsDto.SidecarResponse result = sidecar.login(assignedAddress(jwt), request.getPassword());
        return completeLogin(result, jwt);
    }

    /** This caller's assigned Jaddar RDRS address, the only address they may sign in as. */
    private String assignedAddress(Jwt jwt) {
        RdrsMailbox mailbox = mailboxes.findOrAssign(
                subject(jwt),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"),
                jwt.getClaimAsString("email"));
        String address = mailboxes.address(mailbox);
        if (address == null || address.isBlank()) {
            throw new RdrsException(
                    "No Jaddar RDRS address has been assigned to your account yet.", 409);
        }
        return address;
    }

    /** Answer a multi-factor challenge raised by {@link #login}. */
    @PostMapping("/session/mfa")
    public RdrsDto.SessionResponse mfa(@RequestBody RdrsDto.MfaRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        if (request.getChallengeId() == null || request.getChallengeId().isBlank()
                || request.getCode() == null || request.getCode().isBlank()) {
            throw new RdrsException("Enter the verification code from your ICANN device.", 400);
        }
        RdrsDto.SidecarResponse result = sidecar.mfa(request.getChallengeId().trim(), request.getCode().trim());
        return completeLogin(result, jwt);
    }

    /**
     * Turn a sidecar answer into either a live session or an MFA prompt. The Okta token
     * is exchanged for an RDRS cookie here and then discarded — only the cookie is kept.
     */
    private RdrsDto.SessionResponse completeLogin(RdrsDto.SidecarResponse result, Jwt jwt) {
        RdrsDto.SessionResponse out = new RdrsDto.SessionResponse();

        if ("mfa_required".equals(result.getStatus())) {
            out.setStatus("mfa_required");
            out.setChallengeId(result.getChallengeId());
            out.setPrompt(result.getPrompt());
            return out;
        }

        String appId = result.getAppId() != null ? result.getAppId() : rdrs.appId();
        String cookie = rdrs.authenticate(result.getAccessToken(), appId);
        String apiBaseUrl = result.getRestBackendUrl() != null ? result.getRestBackendUrl() : rdrs.apiBaseUrl();

        RdrsSessionStore.Session session =
                sessions.put(subject(jwt), cookie, apiBaseUrl, result.getExpiresAt());

        // Prefill the form from the requestor's own RDRS profile. Not fatal if it fails.
        try {
            session.setProfile(rdrs.profile(session));
        } catch (RuntimeException e) {
            log.warn("Could not load the RDRS profile: {}", e.getMessage());
        }

        logging.log(LogLevel.INFO, LogCategory.AUTH, "RDRS session opened",
                Map.of("user", jwt.getClaimAsString("preferred_username") != null
                        ? jwt.getClaimAsString("preferred_username") : "unknown"));

        out.setStatus("active");
        out.setExpiresAt(session.getExpiresAt().getEpochSecond());
        out.setProfile(session.getProfile());
        return out;
    }

    /** Close the RDRS session, and abandon any half-finished login. */
    @DeleteMapping("/session")
    public Map<String, Object> logout(@RequestParam(required = false) String challengeId,
                                      @AuthenticationPrincipal Jwt jwt) {
        sidecar.abandon(challengeId);
        sessions.remove(subject(jwt));
        return Map.of("success", true);
    }

    // ─── Lookup ─────────────────────────────────────────────────────

    /**
     * Look a domain up at ICANN. Mandatory before submitting: it supplies the registrar,
     * the IANA id and the lookup record id, and says whether the registrar takes part
     * in RDRS at all.
     */
    @GetMapping("/lookup/{domain}")
    public RdrsDto.LookupResponse lookup(@PathVariable String domain,
                                         @AuthenticationPrincipal Jwt jwt) {
        RdrsSessionStore.Session session = requireSession(jwt);
        String cleaned = domain.trim().toLowerCase();
        if (cleaned.isEmpty() || !cleaned.matches("[a-z0-9.-]{1,253}")) {
            throw new RdrsException("'" + domain + "' is not a valid domain name.", 400);
        }
        RdrsDto.LookupResponse lookup = signedIn(jwt, () -> rdrs.lookup(session, cleaned));
        // Log the verdict: it decides whether the request can be sent at all, and its
        // values are ICANN's own lowercase phrases rather than anything self-evident.
        log.info("RDRS lookup {} -> result='{}' registrar='{}' ianaId={}",
                cleaned,
                lookup != null ? lookup.getResult() : null,
                lookup != null ? lookup.getRegistrar() : null,
                lookup != null ? lookup.getIanaId() : null);
        return lookup;
    }

    // ─── Submit ─────────────────────────────────────────────────────

    /**
     * Send the request to ICANN.
     *
     * <p>Multipart, because ICANN's create endpoint is: {@code command} carries the JSON
     * and the remaining parts are optional attachments. The registrar fields in the
     * command are overwritten server-side from a fresh lookup, so a client cannot claim
     * a registrar the domain does not actually use.
     */
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RdrsDto.SubmitResponse submit(
            @RequestPart("command") String commandJson,
            @RequestPart(value = "law_enforcement", required = false) MultipartFile lawEnforcement,
            @RequestPart(value = "power_of_attorney", required = false) MultipartFile powerOfAttorney,
            @RequestPart(value = "additional_attachments", required = false) List<MultipartFile> additionalAttachments,
            @AuthenticationPrincipal Jwt jwt) {

        RdrsSessionStore.Session session = requireSession(jwt);

        RdrsDto.Command command;
        try {
            command = objectMapper.readValue(commandJson, RdrsDto.Command.class);
        } catch (Exception e) {
            throw new RdrsException("The request could not be read. Please try again.", 400);
        }

        validator.validate(command, lawEnforcement, powerOfAttorney, additionalAttachments);

        // Re-run the lookup rather than trusting whatever the client sent.
        String domain = command.getDomainSubject().trim().toLowerCase();
        RdrsDto.LookupResponse lookup = signedIn(jwt, () -> rdrs.lookup(session, domain));
        if (lookup == null || lookup.getResult() == null) {
            throw new RdrsException("ICANN could not look up that domain. Please try again.", 502);
        }
        if (!RdrsLookup.isSuccess(lookup.getResult())) {
            throw new RdrsException(
                    RdrsLookup.explain(lookup.getResult(), command.getDomainSubject()), 409);
        }

        if (RdrsLookup.isReservedIanaId(lookup.getIanaId())) {
            throw new RdrsException("The registrar of record for " + command.getDomainSubject()
                    + " is a reserved ICANN entry, not a registrar that can receive a request.", 409);
        }
        command.setLookupRecordId(lookup.getLookupRecordId());
        command.setRegistrarName(lookup.getRegistrar());
        command.setIanaId(lookup.getIanaId());

        signedIn(jwt, () -> {
            rdrs.create(session, command, lawEnforcement, powerOfAttorney, additionalAttachments);
            return null;
        });

        // Audit the submission. Deliberately no token, cookie, password or personal detail.
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("domain", command.getDomainSubject());
        details.put("category", command.getCategory());
        details.put("registrar", lookup.getRegistrar());
        details.put("dataElements", command.getRequestedDataElements());
        logging.log(LogLevel.INFO, LogCategory.RDAP, "RDRS request submitted", details);

        RdrsDto.SubmitResponse out = new RdrsDto.SubmitResponse();
        out.setSuccess(true);
        out.setRegistrarName(lookup.getRegistrar());
        out.setIanaId(lookup.getIanaId());
        out.setMessage("Your request has been sent to the registrar of record, who may contact you "
                + "directly for more information. Any data disclosure takes place outside Jaddar, "
                + "between the registrar and you.");
        return out;
    }

    private RdrsSessionStore.Session requireSession(Jwt jwt) {
        requireRegisteredMailbox(jwt);
        return sessions.get(subject(jwt))
                .orElseThrow(() -> new RdrsException("Sign in to ICANN RDRS to continue.",
                        403, RdrsException.SIGN_IN_REQUIRED));
    }

    /**
     * Run an ICANN call, and forget the local session if ICANN no longer honours its
     * cookie. Otherwise GET /session keeps answering "active" for a session ICANN has
     * dropped, and the UI shows the requestor as signed in while every call fails.
     */
    private <T> T signedIn(Jwt jwt, Supplier<T> call) {
        try {
            return call.get();
        } catch (RdrsException e) {
            if (RdrsException.SIGN_IN_REQUIRED.equals(e.getCode())) {
                sessions.remove(subject(jwt));
            }
            throw e;
        }
    }

    // RDRS requests stay blocked until the user registers their Jaddar address with ICANN.
    private void requireRegisteredMailbox(Jwt jwt) {
        boolean registered = mailboxes.find(subject(jwt))
                .map(RdrsMailbox::isRegistered)
                .orElse(false);
        if (!registered) {
            throw new RdrsException(
                    "Register your Jaddar RDRS address with ICANN before making requests.", 409);
        }
    }

    // ─── Errors ─────────────────────────────────────────────────────

    /**
     * RDRS failures carry a message written for the requestor, so pass it through as-is.
     * Everything diagnostic has already gone to the log.
     */
    @ExceptionHandler(RdrsException.class)
    public ResponseEntity<Map<String, Object>> handleRdrs(RdrsException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", e.getMessage());
        if (e.getCode() != null) body.put("code", e.getCode());
        HttpStatus status = HttpStatus.resolve(e.getStatus());
        return ResponseEntity.status(status != null ? status : HttpStatus.BAD_GATEWAY).body(body);
    }
}
