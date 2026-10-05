/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.service;

import com.requestormanager.entity.RequestorGroupUserField;
import com.requestormanager.entity.RequestorUserFieldValue;
import com.requestormanager.entity.RequestorUserProfile;
import com.requestormanager.entity.SubscriptionRequest;
import com.requestormanager.repository.RequestorGroupUserFieldRepository;
import com.requestormanager.repository.RequestorUserFieldValueRepository;
import com.requestormanager.repository.RequestorUserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RequestorUserFieldService {

    public static final String STANDARD_PREFIX = "std:";
    public static final String CUSTOM_PREFIX = "custom:";

    public static final Map<String, String> STANDARD_FIELDS = standardFields();

    private final RequestorUserProfileRepository profileRepository;
    private final RequestorUserFieldValueRepository valueRepository;
    private final RequestorGroupUserFieldRepository groupFieldRepository;

    public record RequiredField(String key, String label, boolean standard) {}

    public record MemberFields(Map<String, String> values, List<RequiredField> missing) {
        public boolean complete() {
            return missing.isEmpty();
        }
    }

    /** The member fields the subscription's pinned template requires, in the template's order. */
    public List<RequiredField> requiredFields(SubscriptionRequest subscription) {
        return requiredFields(subscription.getTemplateSnapshot());
    }

    /** The member fields a template snapshot (or template response) requires. */
    public List<RequiredField> requiredFields(Map<String, Object> template) {
        if (template == null || !(template.get("userFields") instanceof List<?> list)) return List.of();
        List<RequiredField> fields = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m && m.get("key") instanceof String key && !key.isBlank()) {
                String label = m.get("label") instanceof String l && !l.isBlank() ? l : key;
                fields.add(new RequiredField(key, label, Boolean.TRUE.equals(m.get("standard"))));
            }
        }
        return fields;
    }

    /** The group field answering a template field: the explicit mapping, or the standard field of the same key. */
    public String resolveReference(RequiredField field, Map<String, String> mapping) {
        String explicit = mapping != null ? mapping.get(field.key()) : null;
        if (explicit != null && !explicit.isBlank()) return explicit;
        if (field.standard() || STANDARD_FIELDS.containsKey(field.key())) {
            return STANDARD_PREFIX + field.key();
        }
        return null;
    }

    /** Every member field value one member has, by reference (std: or custom:). */
    @Transactional(readOnly = true)
    public Map<String, String> memberValues(String keycloakUserId, String firstName, String lastName, String email) {
        Map<String, String> values = new LinkedHashMap<>();
        put(values, STANDARD_PREFIX + "first_name", firstName);
        put(values, STANDARD_PREFIX + "last_name", lastName);
        put(values, STANDARD_PREFIX + "email", email);
        profileRepository.findById(keycloakUserId).ifPresent(p -> {
            put(values, STANDARD_PREFIX + "phone", p.getPhone());
            put(values, STANDARD_PREFIX + "street_address", p.getStreetAddress());
            put(values, STANDARD_PREFIX + "city", p.getCity());
            put(values, STANDARD_PREFIX + "state_province", p.getStateProvince());
            put(values, STANDARD_PREFIX + "postal_code", p.getPostalCode());
            put(values, STANDARD_PREFIX + "country", p.getCountry());
        });
        for (RequestorUserFieldValue v : valueRepository.findByKeycloakUserId(keycloakUserId)) {
            put(values, CUSTOM_PREFIX + v.getField().getId(), v.getValue());
        }
        return values;
    }

    /** A member's answers to what the subscription's template requires. */
    @Transactional(readOnly = true)
    public MemberFields memberFields(SubscriptionRequest subscription, Map<String, String> memberValues) {
        List<RequiredField> required = requiredFields(subscription);
        if (required.isEmpty()) return new MemberFields(Map.of(), List.of());

        Long groupId = subscription.getRequestorGroup().getId();
        List<Long> groupFieldIds = groupFieldRepository.findByRequestorGroupIdOrderBySortOrderAscIdAsc(groupId)
                .stream().map(RequestorGroupUserField::getId).toList();

        Map<String, String> values = new LinkedHashMap<>();
        List<RequiredField> missing = new ArrayList<>();
        for (RequiredField field : required) {
            String ref = resolveReference(field, subscription.getUserFieldMapping());
            boolean usable = ref != null && (ref.startsWith(STANDARD_PREFIX) || belongsTo(ref, groupFieldIds));
            String value = usable ? memberValues.get(ref) : null;
            if (value == null) missing.add(field);
            else values.put(field.key(), value);
        }
        return new MemberFields(Collections.unmodifiableMap(values), List.copyOf(missing));
    }

    /** Whether a mapping reference names a valid field of the subscription's requestor group. */
    public boolean isValidReference(String ref, Long requestorGroupId) {
        if (ref == null) return false;
        if (ref.startsWith(STANDARD_PREFIX)) return STANDARD_FIELDS.containsKey(ref.substring(STANDARD_PREFIX.length()));
        List<Long> ids = groupFieldRepository.findByRequestorGroupIdOrderBySortOrderAscIdAsc(requestorGroupId)
                .stream().map(RequestorGroupUserField::getId).toList();
        return belongsTo(ref, ids);
    }

    private static boolean belongsTo(String ref, List<Long> groupFieldIds) {
        if (!ref.startsWith(CUSTOM_PREFIX)) return false;
        try {
            return groupFieldIds.contains(Long.parseLong(ref.substring(CUSTOM_PREFIX.length())));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void put(Map<String, String> values, String ref, String value) {
        if (value != null && !value.isBlank()) values.put(ref, value.trim());
    }

    private static Map<String, String> standardFields() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("first_name", "First Name");
        m.put("last_name", "Last Name");
        m.put("email", "Email");
        m.put("phone", "Phone Number");
        m.put("street_address", "Street Address");
        m.put("city", "City");
        m.put("state_province", "State / Province");
        m.put("postal_code", "Postal Code");
        m.put("country", "Country");
        return Collections.unmodifiableMap(m);
    }

    /** The profile row for a member, new and unsaved when they have none yet. */
    public RequestorUserProfile profileFor(String keycloakUserId) {
        return profileRepository.findById(keycloakUserId)
                .orElseGet(() -> RequestorUserProfile.builder().keycloakUserId(keycloakUserId).build());
    }
}
