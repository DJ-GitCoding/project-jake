/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.ArrayList;

/**
 * Defines all available RDAP fields for import mapping.
 * Organized by category matching the UI's PARAMETER_GROUPS structure.
 */
public class RdapSchemaFields {

    @Data
    @Builder
    public static class SchemaField {
        private String name;
        private String type;
        private String description;
        private boolean required;
        private String example;
        private String category;
        private String jsonPath;  // For nested fields like contacts
    }

    /**
     * Get all schema fields for a given entity type
     */
    public static List<SchemaField> getSchemaFields(String type) {
        List<SchemaField> fields = new ArrayList<>();
        
        // Common fields for all types
        fields.addAll(getCommonFields());
        
        switch (type.toLowerCase()) {
            case "domain", "domains" -> fields.addAll(getDomainFields());
            case "ip", "ips", "ip_network" -> fields.addAll(getIpFields());
            case "asn", "asns", "autnum" -> fields.addAll(getAsnFields());
        }
        
        // Add contact fields (applicable to all types)
        fields.addAll(getRegistrantFields());
        fields.addAll(getAdminFields());
        fields.addAll(getTechFields());
        fields.addAll(getBillingFields());
        fields.addAll(getRegistrarFields());
        fields.addAll(getAbuseFields());
        
        // Add event fields
        fields.addAll(getEventFields());
        
        // Add other fields
        fields.addAll(getOtherFields());
        
        return fields;
    }

    private static List<SchemaField> getCommonFields() {
        return List.of(
            SchemaField.builder().name("handle").type("string").description("Unique handle/ID").required(true).example("DOM-123").category("common").build(),
            SchemaField.builder().name("status").type("string[]").description("Status values (comma-separated)").required(false).example("active,ok").category("common").build(),
            SchemaField.builder().name("port43").type("string").description("Port 43 WHOIS server").required(false).example("whois.example.com").category("common").build(),
            SchemaField.builder().name("isTestData").type("boolean").description("Mark as test data").required(false).example("true").category("common").build(),
            SchemaField.builder().name("policyExpressionId").type("integer").description("Policy expression ID for redaction").required(false).example("1").category("common").build()
        );
    }

    private static List<SchemaField> getDomainFields() {
        return List.of(
            SchemaField.builder().name("ldhName").type("string").description("Domain name (LDH format)").required(true).example("example.com").category("domain").build(),
            SchemaField.builder().name("unicodeName").type("string").description("Unicode/IDN name").required(false).example("例え.jp").category("domain").build(),
            SchemaField.builder().name("secureDnsDelegationSigned").type("boolean").description("DNSSEC delegation signed").required(false).example("true").category("dnssec").build(),
            SchemaField.builder().name("secureDnsZoneSigned").type("boolean").description("DNSSEC zone signed").required(false).example("true").category("dnssec").build(),
            // Nameserver fields (for simple import - up to 4 nameservers)
            SchemaField.builder().name("ns1").type("string").description("Nameserver 1").required(false).example("ns1.example.com").category("nameserver").build(),
            SchemaField.builder().name("ns1Ipv4").type("string").description("Nameserver 1 IPv4").required(false).example("192.0.2.1").category("nameserver").build(),
            SchemaField.builder().name("ns1Ipv6").type("string").description("Nameserver 1 IPv6").required(false).example("2001:db8::1").category("nameserver").build(),
            SchemaField.builder().name("ns2").type("string").description("Nameserver 2").required(false).example("ns2.example.com").category("nameserver").build(),
            SchemaField.builder().name("ns2Ipv4").type("string").description("Nameserver 2 IPv4").required(false).example("192.0.2.2").category("nameserver").build(),
            SchemaField.builder().name("ns2Ipv6").type("string").description("Nameserver 2 IPv6").required(false).example("2001:db8::2").category("nameserver").build(),
            SchemaField.builder().name("ns3").type("string").description("Nameserver 3").required(false).example("ns3.example.com").category("nameserver").build(),
            SchemaField.builder().name("ns3Ipv4").type("string").description("Nameserver 3 IPv4").required(false).example("").category("nameserver").build(),
            SchemaField.builder().name("ns3Ipv6").type("string").description("Nameserver 3 IPv6").required(false).example("").category("nameserver").build(),
            SchemaField.builder().name("ns4").type("string").description("Nameserver 4").required(false).example("ns4.example.com").category("nameserver").build(),
            SchemaField.builder().name("ns4Ipv4").type("string").description("Nameserver 4 IPv4").required(false).example("").category("nameserver").build(),
            SchemaField.builder().name("ns4Ipv6").type("string").description("Nameserver 4 IPv6").required(false).example("").category("nameserver").build()
        );
    }

    private static List<SchemaField> getIpFields() {
        return List.of(
            SchemaField.builder().name("startAddress").type("string").description("Start IP address").required(true).example("192.0.2.0").category("network").build(),
            SchemaField.builder().name("endAddress").type("string").description("End IP address").required(false).example("192.0.2.255").category("network").build(),
            SchemaField.builder().name("ipVersion").type("string").description("IP version (v4 or v6)").required(false).example("v4").category("network").build(),
            SchemaField.builder().name("networkName").type("string").description("Network name").required(false).example("EXAMPLE-NET").category("network").build(),
            SchemaField.builder().name("networkType").type("string").description("Network type").required(false).example("ALLOCATED").category("network").build(),
            SchemaField.builder().name("country").type("string").description("Country code").required(false).example("US").category("network").build(),
            SchemaField.builder().name("parentHandle").type("string").description("Parent network handle").required(false).example("NET-192").category("network").build(),
            SchemaField.builder().name("cidr").type("string").description("CIDR notation").required(false).example("192.0.2.0/24").category("network").build()
        );
    }

    private static List<SchemaField> getAsnFields() {
        return List.of(
            SchemaField.builder().name("startAutnum").type("integer").description("Start ASN").required(true).example("64496").category("autnum").build(),
            SchemaField.builder().name("endAutnum").type("integer").description("End ASN").required(false).example("64496").category("autnum").build(),
            SchemaField.builder().name("autnumName").type("string").description("AS name").required(false).example("EXAMPLE-AS").category("autnum").build(),
            SchemaField.builder().name("autnumType").type("string").description("AS type").required(false).example("DIRECT ALLOCATION").category("autnum").build(),
            SchemaField.builder().name("country").type("string").description("Country code").required(false).example("US").category("autnum").build()
        );
    }

    // ==================== Contact Fields ====================

    private static List<SchemaField> getRegistrantFields() {
        return List.of(
            SchemaField.builder().name("registrantHandle").type("string").description("Registrant handle").required(false).example("CONT-REG-1").category("registrant").build(),
            SchemaField.builder().name("registrantName").type("string").description("Registrant name").required(false).example("John Smith").category("registrant").build(),
            SchemaField.builder().name("registrantOrganization").type("string").description("Registrant organization").required(false).example("Example Corp").category("registrant").build(),
            SchemaField.builder().name("registrantEmail").type("string").description("Registrant email").required(false).example("registrant@example.com").category("registrant").build(),
            SchemaField.builder().name("registrantPhone").type("string").description("Registrant phone").required(false).example("+1.5551234567").category("registrant").build(),
            SchemaField.builder().name("registrantFax").type("string").description("Registrant fax").required(false).example("+1.5551234568").category("registrant").build(),
            SchemaField.builder().name("registrantStreet").type("string").description("Registrant street address").required(false).example("123 Main St").category("registrant").build(),
            SchemaField.builder().name("registrantStreet2").type("string").description("Registrant street line 2").required(false).example("Suite 100").category("registrant").build(),
            SchemaField.builder().name("registrantCity").type("string").description("Registrant city").required(false).example("San Francisco").category("registrant").build(),
            SchemaField.builder().name("registrantStateProvince").type("string").description("Registrant state/province").required(false).example("CA").category("registrant").build(),
            SchemaField.builder().name("registrantPostalCode").type("string").description("Registrant postal code").required(false).example("94102").category("registrant").build(),
            SchemaField.builder().name("registrantCountry").type("string").description("Registrant country").required(false).example("US").category("registrant").build()
        );
    }

    private static List<SchemaField> getAdminFields() {
        return List.of(
            SchemaField.builder().name("adminHandle").type("string").description("Admin handle").required(false).example("CONT-ADMIN-1").category("admin").build(),
            SchemaField.builder().name("adminName").type("string").description("Admin name").required(false).example("Jane Admin").category("admin").build(),
            SchemaField.builder().name("adminOrganization").type("string").description("Admin organization").required(false).example("Example Corp").category("admin").build(),
            SchemaField.builder().name("adminEmail").type("string").description("Admin email").required(false).example("admin@example.com").category("admin").build(),
            SchemaField.builder().name("adminPhone").type("string").description("Admin phone").required(false).example("+1.5552222222").category("admin").build(),
            SchemaField.builder().name("adminFax").type("string").description("Admin fax").required(false).example("").category("admin").build(),
            SchemaField.builder().name("adminStreet").type("string").description("Admin street address").required(false).example("123 Main St").category("admin").build(),
            SchemaField.builder().name("adminStreet2").type("string").description("Admin street line 2").required(false).example("").category("admin").build(),
            SchemaField.builder().name("adminCity").type("string").description("Admin city").required(false).example("San Francisco").category("admin").build(),
            SchemaField.builder().name("adminStateProvince").type("string").description("Admin state/province").required(false).example("CA").category("admin").build(),
            SchemaField.builder().name("adminPostalCode").type("string").description("Admin postal code").required(false).example("94102").category("admin").build(),
            SchemaField.builder().name("adminCountry").type("string").description("Admin country").required(false).example("US").category("admin").build()
        );
    }

    private static List<SchemaField> getTechFields() {
        return List.of(
            SchemaField.builder().name("techHandle").type("string").description("Tech handle").required(false).example("CONT-TECH-1").category("tech").build(),
            SchemaField.builder().name("techName").type("string").description("Tech name").required(false).example("Tech Support").category("tech").build(),
            SchemaField.builder().name("techOrganization").type("string").description("Tech organization").required(false).example("Example IT").category("tech").build(),
            SchemaField.builder().name("techEmail").type("string").description("Tech email").required(false).example("tech@example.com").category("tech").build(),
            SchemaField.builder().name("techPhone").type("string").description("Tech phone").required(false).example("+1.5553333333").category("tech").build(),
            SchemaField.builder().name("techFax").type("string").description("Tech fax").required(false).example("").category("tech").build(),
            SchemaField.builder().name("techStreet").type("string").description("Tech street address").required(false).example("456 Tech Park").category("tech").build(),
            SchemaField.builder().name("techStreet2").type("string").description("Tech street line 2").required(false).example("").category("tech").build(),
            SchemaField.builder().name("techCity").type("string").description("Tech city").required(false).example("San Jose").category("tech").build(),
            SchemaField.builder().name("techStateProvince").type("string").description("Tech state/province").required(false).example("CA").category("tech").build(),
            SchemaField.builder().name("techPostalCode").type("string").description("Tech postal code").required(false).example("95110").category("tech").build(),
            SchemaField.builder().name("techCountry").type("string").description("Tech country").required(false).example("US").category("tech").build()
        );
    }

    private static List<SchemaField> getBillingFields() {
        return List.of(
            SchemaField.builder().name("billingHandle").type("string").description("Billing handle").required(false).example("CONT-BILL-1").category("billing").build(),
            SchemaField.builder().name("billingName").type("string").description("Billing name").required(false).example("Billing Dept").category("billing").build(),
            SchemaField.builder().name("billingOrganization").type("string").description("Billing organization").required(false).example("Example Corp").category("billing").build(),
            SchemaField.builder().name("billingEmail").type("string").description("Billing email").required(false).example("billing@example.com").category("billing").build(),
            SchemaField.builder().name("billingPhone").type("string").description("Billing phone").required(false).example("+1.5554444444").category("billing").build(),
            SchemaField.builder().name("billingFax").type("string").description("Billing fax").required(false).example("").category("billing").build(),
            SchemaField.builder().name("billingStreet").type("string").description("Billing street address").required(false).example("123 Main St").category("billing").build(),
            SchemaField.builder().name("billingStreet2").type("string").description("Billing street line 2").required(false).example("").category("billing").build(),
            SchemaField.builder().name("billingCity").type("string").description("Billing city").required(false).example("San Francisco").category("billing").build(),
            SchemaField.builder().name("billingStateProvince").type("string").description("Billing state/province").required(false).example("CA").category("billing").build(),
            SchemaField.builder().name("billingPostalCode").type("string").description("Billing postal code").required(false).example("94102").category("billing").build(),
            SchemaField.builder().name("billingCountry").type("string").description("Billing country").required(false).example("US").category("billing").build()
        );
    }

    private static List<SchemaField> getRegistrarFields() {
        return List.of(
            SchemaField.builder().name("registrarHandle").type("string").description("Registrar handle").required(false).example("REG-EXAMPLE").category("registrar").build(),
            SchemaField.builder().name("registrarName").type("string").description("Registrar name").required(false).example("Example Registrar Inc").category("registrar").build(),
            SchemaField.builder().name("registrarEmail").type("string").description("Registrar email").required(false).example("support@registrar.com").category("registrar").build(),
            SchemaField.builder().name("registrarPhone").type("string").description("Registrar phone").required(false).example("+1.8881234567").category("registrar").build(),
            SchemaField.builder().name("registrarUrl").type("string").description("Registrar URL").required(false).example("https://www.registrar.com").category("registrar").build(),
            SchemaField.builder().name("registrarAbuseEmail").type("string").description("Registrar abuse email").required(false).example("abuse@registrar.com").category("registrar").build(),
            SchemaField.builder().name("registrarAbusePhone").type("string").description("Registrar abuse phone").required(false).example("+1.8881234568").category("registrar").build()
        );
    }

    private static List<SchemaField> getAbuseFields() {
        return List.of(
            SchemaField.builder().name("abuseHandle").type("string").description("Abuse contact handle").required(false).example("CONT-ABUSE-1").category("abuse").build(),
            SchemaField.builder().name("abuseName").type("string").description("Abuse contact name").required(false).example("Abuse Team").category("abuse").build(),
            SchemaField.builder().name("abuseEmail").type("string").description("Abuse email").required(false).example("abuse@example.com").category("abuse").build(),
            SchemaField.builder().name("abusePhone").type("string").description("Abuse phone").required(false).example("+1.5559999999").category("abuse").build()
        );
    }

    // ==================== Event Fields ====================

    private static List<SchemaField> getEventFields() {
        return List.of(
            SchemaField.builder().name("registrationDate").type("datetime").description("Registration date").required(false).example("2020-01-15T00:00:00Z").category("events").build(),
            SchemaField.builder().name("expirationDate").type("datetime").description("Expiration date").required(false).example("2025-01-15T00:00:00Z").category("events").build(),
            SchemaField.builder().name("lastChangedDate").type("datetime").description("Last changed date").required(false).example("2024-06-01T12:00:00Z").category("events").build(),
            SchemaField.builder().name("lastUpdateOfRdapDb").type("datetime").description("Last RDAP DB update").required(false).example("2024-06-15T00:00:00Z").category("events").build(),
            SchemaField.builder().name("transferDate").type("datetime").description("Transfer date").required(false).example("2022-03-01T00:00:00Z").category("events").build(),
            SchemaField.builder().name("lockedDate").type("datetime").description("Locked date").required(false).example("").category("events").build(),
            SchemaField.builder().name("unlockedDate").type("datetime").description("Unlocked date").required(false).example("").category("events").build()
        );
    }

    // ==================== Other Fields ====================

    private static List<SchemaField> getOtherFields() {
        return List.of(
            // Remarks
            SchemaField.builder().name("remarkTitle").type("string").description("Remark title").required(false).example("Terms of Use").category("remarks").build(),
            SchemaField.builder().name("remarkDescription").type("string").description("Remark description").required(false).example("This data is provided for informational purposes.").category("remarks").build(),
            SchemaField.builder().name("remark2Title").type("string").description("Remark 2 title").required(false).example("").category("remarks").build(),
            SchemaField.builder().name("remark2Description").type("string").description("Remark 2 description").required(false).example("").category("remarks").build(),
            // Links
            SchemaField.builder().name("selfLink").type("string").description("Self link URL").required(false).example("https://rdap.example.com/domain/example.com").category("links").build(),
            SchemaField.builder().name("relatedLink").type("string").description("Related link URL").required(false).example("https://www.example.com").category("links").build(),
            SchemaField.builder().name("relatedLinkTitle").type("string").description("Related link title").required(false).example("Website").category("links").build(),
            // Public IDs
            SchemaField.builder().name("ianaId").type("string").description("IANA ID").required(false).example("1234").category("publicIds").build(),
            SchemaField.builder().name("icannId").type("string").description("ICANN ID").required(false).example("registrar-123").category("publicIds").build()
        );
    }
}