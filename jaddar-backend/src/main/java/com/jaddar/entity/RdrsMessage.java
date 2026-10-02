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

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "rdrs_messages")
public class RdrsMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mailbox_id", nullable = false)
    private RdrsMailbox mailbox;

    @Column(name = "storage_key", unique = true, nullable = false, length = 512)
    private String storageKey;

    @Column(name = "message_id", length = 512)
    private String messageId;

    @Column(name = "sender", length = 512)
    private String sender;

    @Column(name = "recipient", length = 512)
    private String recipient;

    @Column(name = "subject", length = 1024)
    private String subject;

    @Column(name = "body_plain", columnDefinition = "text")
    private String bodyPlain;

    @Column(name = "body_html", columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    public boolean isRead() {
        return readAt != null;
    }
}
