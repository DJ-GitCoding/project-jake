/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.dto;

import com.jaddar.dataholder.entity.PolicyRedactionRule.RdapObjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PolicyExpressionDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyExpressionResponse {
        private Long id;
        private String name;
        private String description;
        private String scopeConditions;
        private String noteToRequestor;
        private Boolean isActive;
        private Boolean isDefault;
        private List<RedactionRuleResponse> redactionRules;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyExpressionRequest {
        private String name;
        private String description;
        private String scopeConditions;
        private String noteToRequestor;
        private Boolean isActive;
        private Boolean isDefault;
        private List<RedactionRuleRequest> redactionRules;
    }

    /**
     * Response DTO for per-field sensitivity level mapping.
     * No action — the redaction matrix determines behavior.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RedactionRuleResponse {
        private Long id;
        private String objectType;
        private String objectTypeDisplay;
        private String fieldPath;
        private String fieldDisplayName;
        private Integer sensitivityLevel;
        private Integer validationLevel;
        private String description;
        private Integer ruleOrder;
        private Boolean isEnabled;
    }

    /**
     * Request DTO for per-field sensitivity level mapping.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RedactionRuleRequest {
        private Long id;
        private String objectType;
        private String fieldPath;
        private String fieldDisplayName;
        private Integer sensitivityLevel;
        private Integer validationLevel;
        private String description;
        private Integer ruleOrder;
        private Boolean isEnabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyEnumValues {
        private List<EnumOption> objectTypeOptions;
        private List<RdapFieldDefinition> rdapFieldDefinitions;

        public static PolicyEnumValues getAll() {
            return getAll(List.of());
        }

        /**
         * @param customRoles extra contact roles as {roleKey, displayLabel} pairs
         *                    (from active custom roles) to include alongside the built-ins.
         */
        public static PolicyEnumValues getAll(List<String[]> customRoles) {
            return PolicyEnumValues.builder()
                    .objectTypeOptions(
                            Arrays.stream(RdapObjectType.values())
                                    .map(e -> new EnumOption(e.name(), e.getDisplayName()))
                                    .collect(Collectors.toList())
                    )
                    .rdapFieldDefinitions(RdapFieldDefinition.getStandardFields(customRoles))
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnumOption {
        private String value;
        private String displayName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RdapFieldDefinition {
        private String objectType;
        private String fieldPath;
        private String displayName;
        private String description;
        private String category;
        private boolean sensitive;

        public static List<RdapFieldDefinition> getStandardFields() {
            return getStandardFields(List.of());
        }

        public static List<RdapFieldDefinition> getStandardFields(List<String[]> customRoles) {
            List<RdapFieldDefinition> fields = new ArrayList<>();

            // ── Domain (RFC 9083 §5.3) ──
            fields.add(b("DOMAIN","handle","Handle","Registry-unique identifier","Identification",false));
            fields.add(b("DOMAIN","ldhName","Domain Name (LDH)","Domain name in LDH form","Identification",false));
            fields.add(b("DOMAIN","unicodeName","Domain Name (Unicode)","Domain name in Unicode","Identification",false));
            fields.add(b("DOMAIN","status","Status","Domain status codes (EPP)","Status",false));
            fields.add(b("DOMAIN","nameservers","Nameservers","Associated nameservers","Technical",false));
            fields.add(b("DOMAIN","secureDNS","DNSSEC Information","DNSSEC delegation signing data","Technical",false));
            fields.add(b("DOMAIN","secureDNS.delegationSigned","DNSSEC Delegation Signed","Whether the zone is signed","Technical",false));
            fields.add(b("DOMAIN","events","Events","Domain lifecycle events","Events",false));
            fields.add(b("DOMAIN","events.registration","Date Created","Domain creation date","Events",false));
            fields.add(b("DOMAIN","events.expiration","Date Expires","Domain expiration date","Events",false));
            fields.add(b("DOMAIN","events.lastChanged","Date Updated","Last modification date","Events",false));
            fields.add(b("DOMAIN","events.lastUpdateOfRDAPDatabase","RDAP DB Updated","Last RDAP database update","Events",false));
            fields.add(b("DOMAIN","scope.person","Person Scope","Natural or Legal person","Scope",false));
            fields.add(b("DOMAIN","scope.protection","Protection","Normal or Protected","Scope",false));
            fields.add(b("DOMAIN","scope.nexus","Nexus","Resident or Non-Resident","Scope",false));
            fields.add(b("DOMAIN","scope.personal","Personal","Personal or Non-Personal use","Scope",false));
            fields.add(b("DOMAIN","scope.public_suffix","Public Suffix","TLD / public suffix","Scope",false));

            // ── Entity-level (RFC 9083 §5.1) ──
            fields.add(b("ENTITY","handle","Handle","Registry-unique identifier","Identification",false));
            fields.add(b("ENTITY","roles","Roles","Entity roles (registrant, admin, tech, abuse)","Identification",false));
            fields.add(b("ENTITY","vcardArray","Contact Information (vCard)","Full contact vCard","Contact",true));
            fields.add(b("ENTITY","publicIds","Public IDs","Public identifiers (e.g., IANA ID)","Identification",false));
            fields.add(b("ENTITY","events","Events","Entity lifecycle events","Events",false));
            fields.add(b("ENTITY","registrar.fn","Registrar Name","Name of the registrar","Contact",false));
            fields.add(b("ENTITY","registrar.url","Registrar URL","Registrar website","Contact",false));

            // ── Per-role contact fields (RDAP roles per RFC 9083 §10.2.4) ──
            List<String[]> roles = new ArrayList<>(List.of(
                new String[]{"registrant","Registrant"},
                new String[]{"administrative","Admin"},
                new String[]{"technical","Tech"},
                new String[]{"billing","Billing"},
                new String[]{"abuse","Abuse"}
            ));
            // Admin-defined custom roles get the same standard contact field set.
            if (customRoles != null) roles.addAll(customRoles);
            String[][] contactFields = {
                {"fn","Full Name","Contact name"},
                {"org","Organization","Organization name"},
                {"adr.street","Street Address","Street address line(s)"},
                {"adr.city","City","City / locality"},
                {"adr.sp","State/Province","State or province"},
                {"adr.pc","Postal Code","Postal / ZIP code"},
                {"adr.cc","Country","Country code"},
                {"tel","Phone","Telephone number"},
                {"fax","Fax","Facsimile number"},
                {"email","Email","Email address"},
                {"contact_pref","Email or Phone","Contact preference (email or phone)"},
                {"org_id","Organization ID","Organization identifier"},
                {"personal_id","Personal ID","Personal identifier"},
                {"handle","UniqueID","Unique contact identifier"},
            };

            for (String[] role : roles) {
                String roleKey = role[0];
                String roleLabel = role[1];
                for (String[] cf : contactFields) {
                    String path = cf[0];
                    String label = cf[1];
                    String desc = cf[2];
                    boolean isSensitive = !path.equals("handle");

                    fields.add(b("ENTITY", roleKey + "." + path,
                            roleLabel + " " + label,
                            desc, roleLabel, isSensitive));
                }
            }

            // ── Forensic / Payment ──
            fields.add(b("ENTITY","forensic.source___method","Source & Method","Payment source and method","Forensic",true));
            fields.add(b("ENTITY","forensic.payment_history","Payment History","Payment transaction records","Forensic",true));
            fields.add(b("ENTITY","forensic.transaction_history","Transaction History","Registration transaction log","Forensic",true));

            // ── Nameserver (RFC 9083 §5.2) ──
            fields.add(b("NAMESERVER","handle","Handle","Registry-unique identifier","Identification",false));
            fields.add(b("NAMESERVER","ldhName","Nameserver Name (LDH)","Nameserver hostname","Identification",false));
            fields.add(b("NAMESERVER","unicodeName","Nameserver Name (Unicode)","Nameserver hostname in Unicode","Identification",false));
            fields.add(b("NAMESERVER","ipAddresses","IP Addresses","Glue records (IPv4 and IPv6)","Technical",false));
            fields.add(b("NAMESERVER","ipAddresses.v4","IPv4 Addresses","IPv4 glue records","Technical",false));
            fields.add(b("NAMESERVER","ipAddresses.v6","IPv6 Addresses","IPv6 glue records","Technical",false));

            // ── IP Network (RFC 9083 §5.4) ──
            fields.add(b("IP_NETWORK","handle","Handle","Registry-unique identifier","Identification",false));
            fields.add(b("IP_NETWORK","startAddress","Start Address","Starting IP address","Network",false));
            fields.add(b("IP_NETWORK","endAddress","End Address","Ending IP address","Network",false));
            fields.add(b("IP_NETWORK","ipVersion","IP Version","IPv4 or IPv6","Network",false));
            fields.add(b("IP_NETWORK","name","Network Name","Network identifier name","Identification",false));
            fields.add(b("IP_NETWORK","type","Network Type","Allocation type","Network",false));
            fields.add(b("IP_NETWORK","country","Country","Country code","Location",false));
            fields.add(b("IP_NETWORK","parentHandle","Parent Handle","Parent network handle","Hierarchy",false));

            // ── AS Number (RFC 9083 §5.5) ──
            fields.add(b("AUTNUM","handle","Handle","Registry-unique identifier","Identification",false));
            fields.add(b("AUTNUM","startAutnum","Start AS Number","Starting AS number","ASN",false));
            fields.add(b("AUTNUM","endAutnum","End AS Number","Ending AS number","ASN",false));
            fields.add(b("AUTNUM","name","AS Name","AS identifier name","Identification",false));
            fields.add(b("AUTNUM","type","AS Type","Allocation type","ASN",false));
            fields.add(b("AUTNUM","country","Country","Country code","Location",false));

            // ── Common to all (RFC 9083 §4) ──
            fields.add(b("ALL","links","Links","Related resource links","Links",false));
            fields.add(b("ALL","remarks","Remarks","Additional information","Info",false));
            fields.add(b("ALL","notices","Notices","Service notices","Info",false));
            fields.add(b("ALL","entities","Associated Entities","Related contacts/registrars","Contacts",true));
            fields.add(b("ALL","port43","WHOIS Server","Legacy WHOIS server","Technical",false));
            fields.add(b("ALL","lang","Language","Response language tag","Info",false));

            return fields;
        }

        /** Helper to reduce verbosity */
        private static RdapFieldDefinition b(String ot, String fp, String dn, String desc, String cat, boolean sens) {
            return RdapFieldDefinition.builder()
                    .objectType(ot).fieldPath(fp).displayName(dn)
                    .description(desc).category(cat).sensitive(sens).build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyExpressionSummary {
        private Long id;
        private String name;
        private String scopeConditions;
        private String noteToRequestor;
        private Boolean isActive;
        private Boolean isDefault;
        private Integer redactionRuleCount;
    }
}
