/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One line of deployment-defined text shown under the sign-in form.
 *
 * <p>Used for whatever a deployment needs to say there — a privacy notice, an acceptable-use
 * statement, a link to terms. The list is empty by default and the login screen renders
 * nothing at all until an administrator defines entries.
 *
 * <p>{@link #text} is the visible wording and is always plain text — never HTML, since this
 * renders on a page anonymous visitors can reach. When {@link #url} is set the text becomes
 * the label of a link instead.
 */
@Entity
@Table(name = "branding_notice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrandingNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Display position, ascending. Assigned from list order when notices are saved. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "text", nullable = false, length = 500)
    private String text;

    /** Optional http/https target; when present the text renders as a link. */
    @Column(name = "url", length = 500)
    private String url;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
