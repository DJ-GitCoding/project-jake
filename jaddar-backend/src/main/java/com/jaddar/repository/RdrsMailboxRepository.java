/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.repository;

import com.jaddar.entity.RdrsMailbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RdrsMailboxRepository extends JpaRepository<RdrsMailbox, Long> {

    Optional<RdrsMailbox> findByUserSub(String userSub);

    Optional<RdrsMailbox> findByLocalPart(String localPart);

    // Highest suffix already issued for a first.last pair, so the next duplicate takes the number after it.
    @Query("SELECT COALESCE(MAX(m.sequence), 0) FROM RdrsMailbox m "
         + "WHERE m.localPart = :base OR m.localPart LIKE CONCAT(:base, '.%')")
    int highestSequenceFor(@Param("base") String base);
}
