/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.repository;

import com.jaddar.entity.RdrsMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RdrsMessageRepository extends JpaRepository<RdrsMessage, Long> {

    boolean existsByStorageKey(String storageKey);

    Page<RdrsMessage> findByMailboxIdOrderByReceivedAtDesc(Long mailboxId, Pageable pageable);

    Optional<RdrsMessage> findByIdAndMailboxId(Long id, Long mailboxId);

    long countByMailboxIdAndReadAtIsNull(Long mailboxId);

    @Modifying
    @Query("UPDATE RdrsMessage m SET m.readAt = :now WHERE m.mailbox.id = :mailboxId AND m.readAt IS NULL")
    int markAllRead(@Param("mailboxId") Long mailboxId, @Param("now") Instant now);

    // Retention purge; never called when retention is configured as indefinite.
    @Modifying
    @Query("DELETE FROM RdrsMessage m WHERE m.receivedAt < :cutoff")
    int deleteReceivedBefore(@Param("cutoff") Instant cutoff);
}
