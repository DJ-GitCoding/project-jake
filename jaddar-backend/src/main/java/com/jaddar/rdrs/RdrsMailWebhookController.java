/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.rdrs;

import com.jaddar.config.JaddarProperties;
import com.jaddar.entity.RdrsMailbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/rdrs/mail")
@RequiredArgsConstructor
public class RdrsMailWebhookController {

    private static final long MAX_SIGNATURE_AGE_SECONDS = 300;

    private final JaddarProperties props;
    private final RdrsMailIngestService ingest;
    private final RdrsMailboxResolver resolver;

    @PostMapping("/inbound")
    public ResponseEntity<Map<String, Object>> inbound(@RequestParam MultiValueMap<String, String> form) {
        JaddarProperties.Mail mail = props.getRdrs().getMail();
        if (!mail.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("success", false, "error", "Mail capture is disabled"));
        }

        String timestamp = form.getFirst("timestamp");
        String token = form.getFirst("token");
        String signature = form.getFirst("signature");

        if (!verify(timestamp, token, signature)) {
            log.warn("Rejected inbound mail webhook with an invalid signature");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "error", "Invalid signature"));
        }

        String recipient = firstNonBlank(form.getFirst("recipient"), form.getFirst("To"));
        Optional<RdrsMailbox> mailbox = resolver.resolve(recipient);
        if (mailbox.isEmpty()) {
            log.info("Inbound mail for unknown recipient {}", recipient);
            return ResponseEntity.ok(Map.of("success", true, "captured", false));
        }

        String key = dedupeKey(form);
        ingest.store(mailbox.get(), key, normalize(form));
        return ResponseEntity.ok(Map.of("success", true, "captured", true));
    }

    private boolean verify(String timestamp, String token, String signature) {
        String key = props.getRdrs().getMail().getWebhookSigningKey();
        if (key == null || key.isBlank()) {
            log.error("MAILGUN_WEBHOOK_SIGNING_KEY is not set; refusing inbound mail");
            return false;
        }
        if (timestamp == null || token == null || signature == null) return false;

        try {
            long sent = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - sent) > MAX_SIGNATURE_AGE_SECONDS) {
                log.warn("Rejected inbound mail webhook: signature timestamp outside the accepted window");
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal((timestamp + token).getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(hex(computed).getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Could not verify the Mailgun signature", e);
            return false;
        }
    }

    private String dedupeKey(MultiValueMap<String, String> form) {
        String messageId = firstNonBlank(form.getFirst("Message-Id"), form.getFirst("message-id"));
        if (messageId != null && !messageId.isBlank()) return "mid:" + messageId;
        return "tok:" + form.getFirst("token");
    }

    // Maps Mailgun's forwarded field names onto the shape RdrsMailIngestService stores.
    private Map<String, Object> normalize(MultiValueMap<String, String> form) {
        Map<String, Object> out = new HashMap<>();
        out.put("Message-Id", firstNonBlank(form.getFirst("Message-Id"), form.getFirst("message-id")));
        out.put("From", firstNonBlank(form.getFirst("From"), form.getFirst("from"), form.getFirst("sender")));
        out.put("recipient", firstNonBlank(form.getFirst("recipient"), form.getFirst("To")));
        out.put("Subject", firstNonBlank(form.getFirst("Subject"), form.getFirst("subject")));
        out.put("body-plain", firstNonBlank(form.getFirst("body-plain"), form.getFirst("stripped-text")));
        out.put("body-html", firstNonBlank(form.getFirst("body-html"), form.getFirst("stripped-html")));
        out.put("Date", form.getFirst("Date"));
        return out;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
