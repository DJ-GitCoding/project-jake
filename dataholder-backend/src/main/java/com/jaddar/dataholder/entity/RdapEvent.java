/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * RDAP Event entity for storing events like registration, expiration, last changed, etc.
 */
@Entity
@Table(name = "rdap_events", indexes = {
    @Index(name = "idx_rdap_events_entity", columnList = "rdap_entity_id"),
    @Index(name = "idx_rdap_events_action", columnList = "event_action"),
    @Index(name = "idx_rdap_events_date", columnList = "event_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RdapEvent {

    /**
     * Standard RDAP event actions
     */
    public enum EventAction {
        REGISTRATION,
        REREGISTRATION,
        LAST_CHANGED,
        EXPIRATION,
        DELETION,
        REINSTANTIATION,
        TRANSFER,
        LOCKED,
        UNLOCKED,
        LAST_UPDATE_OF_RDAP_DATABASE,
        REGISTRAR_EXPIRATION,
        ENUM_VALIDATION_EXPIRATION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The RDAP entity this event belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rdap_entity_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "events"})
    private RdapEntity rdapEntity;

    /**
     * The type of event
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_action", nullable = false)
    private EventAction eventAction;

    /**
     * Custom event action name (if not a standard action)
     */
    @Column(name = "event_action_custom")
    private String eventActionCustom;

    /**
     * When the event occurred
     */
    @Column(name = "event_date")
    private LocalDateTime eventDate;

    /**
     * Actor who performed the event (optional)
     */
    @Column(name = "event_actor")
    private String eventActor;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Get the action name for output
     */
    public String getActionName() {
        if (eventActionCustom != null && !eventActionCustom.isBlank()) {
            return eventActionCustom;
        }
        return eventAction.name().toLowerCase().replace('_', ' ');
    }
}