/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "rdrs_mailboxes")
public class RdrsMailbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_sub", unique = true, nullable = false, length = 255)
    private String userSub;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "local_part", unique = true, nullable = false, length = 190)
    private String localPart;

    @Column(name = "first_name", length = 120)
    private String firstName;

    @Column(name = "last_name", length = 120)
    private String lastName;

    @Column(name = "sequence", nullable = false)
    private Integer sequence = 1;

    @Column(name = "registered_at")
    private Instant registeredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // True once the user has confirmed ICANN registration with this address.
    @Transient
    public boolean isRegistered() {
        return registeredAt != null;
    }
}
