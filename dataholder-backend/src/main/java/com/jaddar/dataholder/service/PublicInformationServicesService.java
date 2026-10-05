/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.entity.DataHolderConfig;
import com.jaddar.dataholder.repository.DataHolderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The "Public Information Services" notice: joining links for the requestor groups this data
 * holder serves, offered to someone who asked as the public.
 *
 * <p>A public answer carries only the public record. Someone who wants more needs to belong to a
 * requestor group with an agreement, so when the data holder opts in
 * ({@link DataHolderConfig#getOfferRequestorGroupLinks()}) every public answer lists where to
 * apply. The groups are those with an active subscription to this data holder or to a data
 * holder group it belongs to. Each group's requestor manager publishes its link through token
 * introspection (see {@link TokenIntrospectionService#requestorGroupJoiningLinks()}), so it is read
 * with the introspection credentials this data holder already holds for the group.
 *
 * <p>Public queries are unauthenticated and can be frequent, so the list is cached rather than
 * gathered by introspection on each one. It is carried as a standard RDAP notice (RFC 9083
 * §4.3) so any RDAP client shows it, not only Jaddar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicInformationServicesService {

    public static final String NOTICE_TITLE = "Public Information Services";

    private final DataHolderConfigRepository configRepository;
    private final TokenIntrospectionService tokenIntrospectionService;

    @Value("${dataholder.public-information-services.cache-seconds:300}")
    private long cacheSeconds;

    private volatile List<Map<String, Object>> cachedLinks;
    private volatile long cachedAtMillis;

    /**
     * The notice for a public answer, or empty when it is not offered or there is nothing to list.
     *
     * @param contextUri the URI of the RDAP query being answered, which RFC 9083 §4.2 makes each
     *                   link's {@code value}
     */
    public Optional<Map<String, Object>> noticeForPublicAnswer(String contextUri) {
        boolean offered = configRepository.findById(DataHolderConfig.SINGLETON_ID)
                .map(c -> Boolean.TRUE.equals(c.getOfferRequestorGroupLinks()))
                .orElse(false);
        if (!offered) return Optional.empty();

        List<Map<String, Object>> links = joinLinks();
        if (links.isEmpty()) return Optional.empty();

        Map<String, Object> notice = new LinkedHashMap<>();
        notice.put("title", NOTICE_TITLE);
        notice.put("description", List.of(
                "This answer contains only the public record. Requestor groups with an agreement "
                        + "with this data holder can request more; these groups accept applications to join."));
        notice.put("links", links.stream().<Map<String, Object>>map(link -> {
            Map<String, Object> withContext = new LinkedHashMap<>();
            withContext.put("value", contextUri);
            withContext.putAll(link);
            return withContext;
        }).toList());
        return Optional.of(notice);
    }

    private List<Map<String, Object>> joinLinks() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> links = cachedLinks;
        if (links != null && now - cachedAtMillis < cacheSeconds * 1000) return links;

        synchronized (this) {
            if (cachedLinks != null && now - cachedAtMillis < cacheSeconds * 1000) return cachedLinks;
            cachedLinks = fetchJoinLinks();
            cachedAtMillis = now;
            return cachedLinks;
        }
    }

    /** One RDAP link per group, deduplicated across subscriptions that report the same link. */
    private List<Map<String, Object>> fetchJoinLinks() {
        List<Map<String, Object>> links = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (TokenIntrospectionService.JoiningLink joining : tokenIntrospectionService.requestorGroupJoiningLinks()) {
            String url = joining.url();
            if (!seen.add(url)) continue;

            String name = joining.requestorGroupName();
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("rel", "related");
            link.put("href", url);
            link.put("title", name != null && !name.isBlank() ? "Join " + name : "Join this requestor group");
            link.put("type", "text/html");
            links.add(link);
        }
        log.debug("Loaded {} requestor group joining link(s) for public answers", links.size());
        return List.copyOf(links);
    }
}
