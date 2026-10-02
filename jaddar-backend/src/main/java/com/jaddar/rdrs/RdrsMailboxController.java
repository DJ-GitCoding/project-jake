/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.rdrs;

import com.jaddar.entity.RdrsMailbox;
import com.jaddar.entity.RdrsMessage;
import com.jaddar.repository.RdrsMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rdrs/mailbox")
@RequiredArgsConstructor
public class RdrsMailboxController {

    private final RdrsMailboxService mailboxes;
    private final RdrsMessageRepository messages;

    // The caller's assigned address and whether RDRS requests are unlocked yet.
    @GetMapping
    public Map<String, Object> mailbox(@AuthenticationPrincipal Jwt jwt) {
        RdrsMailbox mailbox = mailboxes.findOrAssign(
                jwt.getSubject(),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"),
                jwt.getClaimAsString("email"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("address", mailboxes.address(mailbox));
        out.put("localPart", mailbox.getLocalPart());
        out.put("registered", mailbox.isRegistered());
        out.put("registeredAt", mailbox.getRegisteredAt());
        out.put("unreadCount", messages.countByMailboxIdAndReadAtIsNull(mailbox.getId()));
        return out;
    }

    // Records that the user has finished registering this address with ICANN.
    @PostMapping("/registered")
    public Map<String, Object> markRegistered(@AuthenticationPrincipal Jwt jwt) {
        RdrsMailbox mailbox = mailboxes.markRegistered(jwt.getSubject());
        return Map.of(
                "address", mailboxes.address(mailbox),
                "registered", mailbox.isRegistered(),
                "registeredAt", String.valueOf(mailbox.getRegisteredAt()));
    }

    // Captured mail for the caller, newest first.
    @GetMapping("/messages")
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "25") int size,
                                    @AuthenticationPrincipal Jwt jwt) {
        RdrsMailbox mailbox = require(jwt);
        Page<RdrsMessage> found = messages.findByMailboxIdOrderByReceivedAtDesc(
                mailbox.getId(), PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));

        List<Map<String, Object>> items = found.getContent().stream().map(this::summary).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("page", found.getNumber());
        out.put("size", found.getSize());
        out.put("totalElements", found.getTotalElements());
        out.put("totalPages", found.getTotalPages());
        out.put("unreadCount", messages.countByMailboxIdAndReadAtIsNull(mailbox.getId()));
        return out;
    }

    // A single message with its body, marked read on open.
    @GetMapping("/messages/{id}")
    public Map<String, Object> read(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        RdrsMailbox mailbox = require(jwt);
        RdrsMessage message = messages.findByIdAndMailboxId(id, mailbox.getId())
                .orElseThrow(() -> new RdrsException("That message could not be found.", 404));

        if (message.getReadAt() == null) {
            message.setReadAt(Instant.now());
            messages.save(message);
        }

        Map<String, Object> out = new LinkedHashMap<>(summary(message));
        out.put("bodyPlain", message.getBodyPlain());
        out.put("bodyHtml", message.getBodyHtml());
        return out;
    }

    @PostMapping("/messages/read-all")
    public Map<String, Object> readAll(@AuthenticationPrincipal Jwt jwt) {
        RdrsMailbox mailbox = require(jwt);
        int updated = messages.markAllRead(mailbox.getId(), Instant.now());
        return Map.of("success", true, "updated", updated);
    }

    private Map<String, Object> summary(RdrsMessage m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", m.getId());
        out.put("sender", m.getSender());
        out.put("recipient", m.getRecipient());
        out.put("subject", m.getSubject());
        out.put("sentAt", m.getSentAt());
        out.put("receivedAt", m.getReceivedAt());
        out.put("read", m.isRead());
        out.put("preview", preview(m.getBodyPlain()));
        return out;
    }

    private String preview(String body) {
        if (body == null || body.isBlank()) return "";
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() <= 160 ? flat : flat.substring(0, 160) + "…";
    }

    private RdrsMailbox require(Jwt jwt) {
        return mailboxes.find(jwt.getSubject())
                .orElseThrow(() -> new RdrsException("No RDRS address has been assigned yet.", 404));
    }

    @ExceptionHandler(RdrsException.class)
    public org.springframework.http.ResponseEntity<Map<String, Object>> handle(RdrsException e) {
        org.springframework.http.HttpStatus status =
                org.springframework.http.HttpStatus.resolve(e.getStatus());
        return org.springframework.http.ResponseEntity
                .status(status != null ? status : org.springframework.http.HttpStatus.BAD_GATEWAY)
                .body(Map.of("success", false, "error", e.getMessage()));
    }
}
