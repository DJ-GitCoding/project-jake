/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.rdrs;

import com.jaddar.entity.RdrsMailbox;
import com.jaddar.repository.RdrsMailboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RdrsMailboxResolver {

    private final RdrsMailboxRepository mailboxes;

    public Optional<RdrsMailbox> resolve(String recipient) {
        if (recipient == null || !recipient.contains("@")) return Optional.empty();
        String local = recipient.substring(0, recipient.indexOf('@')).trim().toLowerCase();
        int plus = local.indexOf('+');
        if (plus > 0) local = local.substring(0, plus);
        if (local.isEmpty()) return Optional.empty();
        return mailboxes.findByLocalPart(local);
    }
}
