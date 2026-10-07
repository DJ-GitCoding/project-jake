/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.service;

import com.requestormanager.dto.UserDto;
import com.requestormanager.entity.RequestorGroup;
import com.requestormanager.entity.RequestorGroupUserField;
import com.requestormanager.entity.RequestorUserFieldValue;
import com.requestormanager.entity.RequestorUserProfile;
import com.requestormanager.entity.SubscriptionRequest;
import com.requestormanager.entity.SubscriptionRequest.SubscriptionStatus;
import com.requestormanager.enums.UserType;
import com.requestormanager.exception.CustomExceptions.*;
import com.requestormanager.repository.RequestorGroupRepository;
import com.requestormanager.repository.RequestorGroupUserFieldRepository;
import com.requestormanager.repository.RequestorUserFieldValueRepository;
import com.requestormanager.repository.RequestorUserProfileRepository;
import com.requestormanager.repository.SubscriptionRequestRepository;
import com.requestormanager.security.KeycloakAuthService.KeycloakUser;
import com.requestormanager.security.SecurityUtils;
import com.requestormanager.service.RequestorUserFieldService.RequiredField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberProfileService {

    private static final Set<String> DATA_TYPES = Set.of("text", "number", "date", "select");
    private static final List<SubscriptionStatus> LIVE = List.of(
            SubscriptionStatus.SUBMITTED, SubscriptionStatus.PENDING_REVIEW, SubscriptionStatus.APPROVED,
            SubscriptionStatus.TESTING, SubscriptionStatus.ACTIVE, SubscriptionStatus.SUSPENDED);

    private final SecurityUtils securityUtils;
    private final KeycloakUserService keycloakUserService;
    private final RequestorUserFieldService userFieldService;
    private final RequestorUserProfileRepository profileRepository;
    private final RequestorUserFieldValueRepository valueRepository;
    private final RequestorGroupUserFieldRepository groupFieldRepository;
    private final RequestorGroupRepository requestorGroupRepository;
    private final SubscriptionRequestRepository subscriptionRequestRepository;

    /** The signed-in member's profile, with each field's value and the agreements that use it. */
    @Transactional(readOnly = true)
    public Map<String, Object> getOwnProfile() {
        KeycloakUser me = securityUtils.requireCurrentUser();
        KeycloakUserService.Identity identity = keycloakUserService.lookupIdentity(me.getSub());
        String firstName = identity != null ? identity.firstName() : me.getFirstName();
        String lastName = identity != null ? identity.lastName() : me.getLastName();
        String email = identity != null ? identity.email() : me.getEmail();
        return buildProfile(me.getSub(), firstName, lastName, email, groupsNamed(me.getGroups()));
    }

    /** Save the signed-in member's own profile. */
    @Transactional
    public Map<String, Object> updateOwnProfile(Map<String, Object> body) {
        KeycloakUser me = securityUtils.requireCurrentUser();

        String firstName = text(body.get("first_name"));
        String lastName = text(body.get("last_name"));
        if (body.containsKey("first_name") || body.containsKey("last_name")) {
            if (firstName == null || lastName == null) {
                throw new BadRequestException("First and last name are required.");
            }
            keycloakUserService.updateOwnName(me.getSub(), firstName, lastName);
        }

        saveProfileFields(me.getSub(), body, groupsNamed(me.getGroups()));
        log.info("Member {} updated their profile", me.getEmail());
        return getOwnProfile();
    }

    /** A member's profile as an administrator sees it, with whether the caller may edit it. */
    @Transactional(readOnly = true)
    public Map<String, Object> getMemberProfile(String userId) {
        KeycloakUser me = securityUtils.requireCurrentUser();
        UserDto.UserResponse member = keycloakUserService.getUserById(userId);
        Map<String, Object> profile = buildProfile(userId, member.getFirstName(), member.getLastName(),
                member.getEmail(), groupsNamed(member.getGroups()));
        profile.put("editable", canEditMember(me, member));
        return profile;
    }

    /** Save a member's profile fields on their behalf. Names are changed through the user record. */
    @Transactional
    public Map<String, Object> updateMemberProfile(String userId, Map<String, Object> body) {
        KeycloakUser me = securityUtils.requireCurrentUser();
        UserDto.UserResponse member = keycloakUserService.getUserById(userId);
        if (!canEditMember(me, member)) {
            throw new AccessDeniedException("You don't have permission to edit this member's profile");
        }
        List<RequestorGroup> groups = groupsNamed(member.getGroups());
        if (!isAdmin(me)) {
            groups = groups.stream().filter(g -> belongsTo(me, g)).toList();
        }
        Map<String, Object> fields = new HashMap<>(body);
        fields.remove("first_name");
        fields.remove("last_name");
        saveProfileFields(userId, fields, groups);
        log.info("Profile of member {} updated by {}", member.getEmail(), me.getEmail());
        return getMemberProfile(userId);
    }

    /** Whether the caller may edit a member's profile, by the same rules as editing the member. */
    private boolean canEditMember(KeycloakUser me, UserDto.UserResponse member) {
        if (me.getSub().equals(member.getId())) return true;
        UserType mine = me.getUserType();
        UserType theirs = member.getType() != null ? member.getType() : UserType.REQUESTOR_GROUP_USER;
        if (mine == UserType.JADDAR_MASTER_ADMIN) return true;
        if (mine == UserType.GROUP_ADMIN) return theirs.getLevel() > mine.getLevel();
        if (mine == UserType.REQUESTOR_GROUP_ADMIN) {
            return theirs.getLevel() > mine.getLevel() && member.getGroups() != null
                    && me.getGroups() != null && member.getGroups().stream()
                            .anyMatch(g -> me.getGroups().stream().anyMatch(g::equalsIgnoreCase));
        }
        return false;
    }

    /** A member's standard and custom fields, each with its value and the agreements that use it. */
    private Map<String, Object> buildProfile(String userId, String firstName, String lastName, String email,
                                             List<RequestorGroup> groups) {
        Map<String, String> values = userFieldService.memberValues(userId, firstName, lastName, email);
        Map<String, List<Map<String, Object>>> usage = usageByReference(groups);

        List<Map<String, Object>> standard = new ArrayList<>();
        RequestorUserFieldService.STANDARD_FIELDS.forEach((key, label) -> {
            String ref = RequestorUserFieldService.STANDARD_PREFIX + key;
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("key", key);
            field.put("label", label);
            field.put("value", values.getOrDefault(ref, ""));
            field.put("readOnly", "email".equals(key));
            field.put("usedBy", usage.getOrDefault(ref, List.of()));
            standard.add(field);
        });

        List<Map<String, Object>> groupViews = new ArrayList<>();
        for (RequestorGroup group : groups) {
            List<Map<String, Object>> fields = new ArrayList<>();
            for (RequestorGroupUserField f : groupFieldRepository.findByRequestorGroupIdOrderBySortOrderAscIdAsc(group.getId())) {
                String ref = RequestorUserFieldService.CUSTOM_PREFIX + f.getId();
                Map<String, Object> field = fieldDefinition(f);
                field.put("value", values.getOrDefault(ref, ""));
                field.put("usedBy", usage.getOrDefault(ref, List.of()));
                fields.add(field);
            }
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", group.getId());
            view.put("name", group.getName());
            view.put("fields", fields);
            groupViews.add(view);
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", userId);
        profile.put("standardFields", standard);
        profile.put("groups", groupViews);
        return profile;
    }

    /** Save the standard fields in the body, and custom values for fields of the given groups. */
    private void saveProfileFields(String userId, Map<String, Object> body, List<RequestorGroup> groups) {
        RequestorUserProfile profile = userFieldService.profileFor(userId);
        if (body.containsKey("phone")) profile.setPhone(limit(text(body.get("phone")), 50, "Phone Number"));
        if (body.containsKey("street_address")) profile.setStreetAddress(limit(text(body.get("street_address")), 255, "Street Address"));
        if (body.containsKey("city")) profile.setCity(limit(text(body.get("city")), 100, "City"));
        if (body.containsKey("state_province")) profile.setStateProvince(limit(text(body.get("state_province")), 100, "State / Province"));
        if (body.containsKey("postal_code")) profile.setPostalCode(limit(text(body.get("postal_code")), 20, "Postal Code"));
        if (body.containsKey("country")) profile.setCountry(limit(text(body.get("country")), 100, "Country"));
        profileRepository.save(profile);

        if (!(body.get("customValues") instanceof Map<?, ?> custom)) return;
        Map<Long, RequestorGroupUserField> allowed = new HashMap<>();
        List<Long> groupIds = groups.stream().map(RequestorGroup::getId).toList();
        if (!groupIds.isEmpty()) {
            groupFieldRepository.findByRequestorGroupIdInOrderBySortOrderAscIdAsc(groupIds)
                    .forEach(f -> allowed.put(f.getId(), f));
        }
        Map<Long, RequestorUserFieldValue> existing = new HashMap<>();
        valueRepository.findByKeycloakUserId(userId).forEach(v -> existing.put(v.getField().getId(), v));

        for (Map.Entry<?, ?> entry : custom.entrySet()) {
            Long fieldId;
            try {
                fieldId = Long.valueOf(String.valueOf(entry.getKey()));
            } catch (NumberFormatException e) {
                throw new BadRequestException("Unknown member field: " + entry.getKey());
            }
            RequestorGroupUserField field = allowed.get(fieldId);
            if (field == null) {
                throw new BadRequestException("That member field does not belong to a requestor group you can edit for this member.");
            }
            String value = validateValue(field, text(entry.getValue()));
            RequestorUserFieldValue stored = existing.get(fieldId);
            if (value == null) {
                if (stored != null) valueRepository.delete(stored);
            } else if (stored == null) {
                valueRepository.save(RequestorUserFieldValue.builder()
                        .keycloakUserId(userId).field(field).value(value).build());
            } else {
                stored.setValue(value);
                valueRepository.save(stored);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getGroupFields(Long groupId) {
        KeycloakUser me = securityUtils.requireCurrentUser();
        RequestorGroup group = findGroup(groupId);
        if (!isAdmin(me) && !belongsTo(me, group)) {
            throw new AccessDeniedException("You don't have permission to access this requestor group");
        }
        return groupFieldRepository.findByRequestorGroupIdOrderBySortOrderAscIdAsc(groupId).stream()
                .map(this::fieldDefinition).toList();
    }

    /** Replace a requestor group's custom member fields. */
    @Transactional
    public List<Map<String, Object>> replaceGroupFields(Long groupId, List<Map<String, Object>> fields) {
        KeycloakUser me = securityUtils.requireCurrentUser();
        RequestorGroup group = findGroup(groupId);
        boolean groupAdmin = me.getUserType() == UserType.REQUESTOR_GROUP_ADMIN && belongsTo(me, group);
        if (!isAdmin(me) && !groupAdmin) {
            throw new AccessDeniedException("Only this requestor group's admins can change its member fields");
        }

        Map<Long, RequestorGroupUserField> existing = new HashMap<>();
        groupFieldRepository.findByRequestorGroupIdOrderBySortOrderAscIdAsc(groupId).forEach(f -> existing.put(f.getId(), f));

        Set<String> labels = new HashSet<>();
        Set<Long> kept = new HashSet<>();
        int order = 0;
        for (Map<String, Object> input : fields == null ? List.<Map<String, Object>>of() : fields) {
            String label = text(input.get("label"));
            if (label == null) continue;
            if (label.length() > 200) throw new BadRequestException("Member field names are limited to 200 characters.");
            if (!labels.add(label.toLowerCase())) {
                throw new BadRequestException("The member field '" + label + "' is listed twice.");
            }
            if (RequestorUserFieldService.STANDARD_FIELDS.values().stream().anyMatch(label::equalsIgnoreCase)) {
                throw new BadRequestException("'" + label + "' is already a standard member field.");
            }
            String dataType = Optional.ofNullable(text(input.get("dataType"))).orElse("text");
            if (!DATA_TYPES.contains(dataType)) throw new BadRequestException("Unknown field type: " + dataType);
            String options = text(input.get("options"));
            if ("select".equals(dataType) && options == null) {
                throw new BadRequestException("The member field '" + label + "' needs its choices.");
            }

            RequestorGroupUserField field = null;
            if (input.get("id") != null) {
                field = existing.get(Long.valueOf(String.valueOf(input.get("id"))));
                if (field == null) throw new BadRequestException("Unknown member field: " + input.get("id"));
            }
            if (field == null) field = RequestorGroupUserField.builder().requestorGroup(group).build();
            field.setLabel(label);
            field.setDescription(text(input.get("description")));
            field.setDataType(dataType);
            field.setOptions("select".equals(dataType) ? options : null);
            field.setSortOrder(order++);
            field = groupFieldRepository.save(field);
            kept.add(field.getId());
        }

        List<Long> removed = existing.keySet().stream().filter(id -> !kept.contains(id)).toList();
        if (!removed.isEmpty()) {
            valueRepository.deleteByFieldIdIn(removed);
            groupFieldRepository.deleteAllById(removed);
        }
        log.info("Member fields for requestor group {} replaced by {}", group.getName(), me.getEmail());
        return getGroupFields(groupId);
    }

    /** For each member field reference, the agreements that require it. */
    private Map<String, List<Map<String, Object>>> usageByReference(List<RequestorGroup> groups) {
        Map<String, List<Map<String, Object>>> usage = new HashMap<>();
        for (RequestorGroup group : groups) {
            for (SubscriptionRequest sr : subscriptionRequestRepository.findByRequestorGroupIdAndStatusIn(group.getId(), LIVE)) {
                for (RequiredField field : userFieldService.requiredFields(sr)) {
                    String ref = userFieldService.resolveReference(field, sr.getUserFieldMapping());
                    if (ref == null) continue;
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("agreement", agreementName(sr));
                    entry.put("dataHolderGroup", sr.getDataHolderGroup().getName());
                    entry.put("requestorGroup", group.getName());
                    entry.put("asField", field.label());
                    usage.computeIfAbsent(ref, k -> new ArrayList<>()).add(entry);
                }
            }
        }
        return usage;
    }

    private static String agreementName(SubscriptionRequest sr) {
        Map<String, Object> snapshot = sr.getTemplateSnapshot();
        if (snapshot != null && snapshot.get("name") instanceof String name && !name.isBlank()) return name;
        return sr.getTemplateName() != null ? sr.getTemplateName() : sr.getTemplateId();
    }

    private Map<String, Object> fieldDefinition(RequestorGroupUserField f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("label", f.getLabel());
        m.put("description", f.getDescription());
        m.put("dataType", f.getDataType());
        m.put("options", f.getOptions());
        m.put("sortOrder", f.getSortOrder());
        return m;
    }

    /** A value fit for the field's type, trimmed, or null to clear it. */
    private static String validateValue(RequestorGroupUserField field, String value) {
        if (value == null) return null;
        switch (field.getDataType()) {
            case "number" -> {
                try {
                    new java.math.BigDecimal(value);
                } catch (NumberFormatException e) {
                    throw new BadRequestException("'" + field.getLabel() + "' must be a number.");
                }
            }
            case "date" -> {
                try {
                    LocalDate.parse(value);
                } catch (DateTimeParseException e) {
                    throw new BadRequestException("'" + field.getLabel() + "' must be a date (YYYY-MM-DD).");
                }
            }
            case "select" -> {
                List<String> choices = Arrays.stream(Optional.ofNullable(field.getOptions()).orElse("").split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
                if (!choices.contains(value)) {
                    throw new BadRequestException("'" + field.getLabel() + "' must be one of: " + String.join(", ", choices));
                }
            }
            default -> {
                if (value.length() > 2000) {
                    throw new BadRequestException("'" + field.getLabel() + "' is limited to 2000 characters.");
                }
            }
        }
        return value;
    }

    private List<RequestorGroup> groupsNamed(List<String> names) {
        if (names == null || names.isEmpty()) return List.of();
        return requestorGroupRepository.findByNameInIgnoreCase(
                names.stream().map(n -> n.toLowerCase(java.util.Locale.ROOT)).toList());
    }

    private RequestorGroup findGroup(Long groupId) {
        return requestorGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("RequestorGroup", "id", groupId));
    }

    private static boolean isAdmin(KeycloakUser me) {
        return me.getUserType() == UserType.JADDAR_MASTER_ADMIN || me.getUserType() == UserType.GROUP_ADMIN;
    }

    private static boolean belongsTo(KeycloakUser me, RequestorGroup group) {
        return me.getGroups() != null && me.getGroups().stream().anyMatch(g -> g.equalsIgnoreCase(group.getName()));
    }

    private static String text(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static String limit(String value, int max, String label) {
        if (value != null && value.length() > max) {
            throw new BadRequestException("'" + label + "' is limited to " + max + " characters.");
        }
        return value;
    }
}
