/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generic envelope for a single page of results. Returned by list endpoints when the
 * caller supplies pagination params (page/size); endpoints keep returning a plain List
 * when no page param is supplied so existing server-to-server callers are unaffected.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PagedResponse", description = "A single page of results with pagination metadata")
public class PagedResponse<T> {

    @Schema(description = "The items on this page")
    private List<T> content;

    @Schema(description = "Zero-based page index")
    private int page;

    @Schema(description = "Requested page size")
    private int size;

    @Schema(description = "Total number of matching items across all pages")
    private long totalElements;

    @Schema(description = "Total number of pages")
    private int totalPages;

    @Schema(description = "Whether this is the first page")
    private boolean first;

    @Schema(description = "Whether this is the last page")
    private boolean last;

    @Schema(description = "Whether a next page exists")
    private boolean hasNext;

    @Schema(description = "Whether a previous page exists")
    private boolean hasPrevious;

    /** Build from a Spring Data Page whose element type is already the response type. */
    public static <T> PagedResponse<T> of(Page<T> page) {
        return of(page, Function.identity());
    }

    /** Build from a Spring Data Page, mapping each entity to its response DTO. */
    public static <E, T> PagedResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return PagedResponse.<T>builder()
                .content(page.getContent().stream().map(mapper).collect(Collectors.toList()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }
}
