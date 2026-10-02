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
import com.jaddar.repository.RdrsMailboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RdrsMailboxService {

    private final RdrsMailboxRepository mailboxes;
    private final JaddarProperties props;

    // The requestor's mailbox, or empty if none has been assigned yet.
    @Transactional(readOnly = true)
    public Optional<RdrsMailbox> find(String userSub) {
        return mailboxes.findByUserSub(userSub);
    }

    // The requestor's mailbox, assigning one on first use. Retries if two calls race on the same local part.
    @Transactional
    public RdrsMailbox findOrAssign(String userSub, String firstName, String lastName, String userEmail) {
        return mailboxes.findByUserSub(userSub).orElseGet(() -> {
            for (int attempt = 0; attempt < 5; attempt++) {
                try {
                    return assign(userSub, firstName, lastName, userEmail);
                } catch (DataIntegrityViolationException e) {
                    Optional<RdrsMailbox> existing = mailboxes.findByUserSub(userSub);
                    if (existing.isPresent()) return existing.get();
                }
            }
            throw new RdrsException("Could not assign an RDRS address. Please try again.", 500);
        });
    }

    private RdrsMailbox assign(String userSub, String firstName, String lastName, String userEmail) {
        String base = baseLocalPart(firstName, lastName, userEmail, userSub);
        int next = mailboxes.highestSequenceFor(base) + 1;

        RdrsMailbox mailbox = new RdrsMailbox();
        mailbox.setUserSub(userSub);
        mailbox.setUserEmail(userEmail);
        mailbox.setFirstName(firstName);
        mailbox.setLastName(lastName);
        mailbox.setSequence(next);
        mailbox.setLocalPart(next <= 1 ? base : base + "." + next);

        RdrsMailbox saved = mailboxes.save(mailbox);
        log.info("Assigned RDRS mailbox {} to subject {}", address(saved), abbreviate(userSub));
        return saved;
    }

    // Builds firstname.lastname, falling back to the email local part then the subject.
    private String baseLocalPart(String firstName, String lastName, String userEmail, String userSub) {
        String first = slug(firstName);
        String last = slug(lastName);

        if (!first.isEmpty() && !last.isEmpty()) return first + "." + last;
        if (!first.isEmpty()) return first;
        if (!last.isEmpty()) return last;

        String fromEmail = userEmail != null && userEmail.contains("@")
                ? slug(userEmail.substring(0, userEmail.indexOf('@')))
                : "";
        if (!fromEmail.isEmpty()) return fromEmail;

        String sub = slug(userSub);
        return "user." + sub.substring(0, Math.min(8, sub.length()));
    }

    // Lowercase, accent-folded, letters and digits only.
    private String slug(String raw) {
        if (raw == null) return "";
        String folded = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return folded.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // Full address for a mailbox, using the configured mail domain.
    public String address(RdrsMailbox mailbox) {
        String domain = props.getRdrs().getMail().getDomain();
        if (domain == null || domain.isBlank()) {
            throw new RdrsException(
                    "No RDRS mail domain is configured on this server; set RDRS_MAIL_DOMAIN.", 503);
        }
        return mailbox.getLocalPart() + "@" + domain;
    }

    // Marks the requestor as having completed ICANN registration with this address.
    @Transactional
    public RdrsMailbox markRegistered(String userSub) {
        RdrsMailbox mailbox = mailboxes.findByUserSub(userSub)
                .orElseThrow(() -> new RdrsException("No RDRS address has been assigned yet.", 404));
        if (mailbox.getRegisteredAt() == null) {
            mailbox.setRegisteredAt(Instant.now());
            mailboxes.save(mailbox);
            log.info("RDRS mailbox {} marked registered", address(mailbox));
        }
        return mailbox;
    }

    private static String abbreviate(String subject) {
        if (subject == null || subject.length() <= 8) return "unknown";
        return subject.substring(0, 8) + "…";
    }
}
