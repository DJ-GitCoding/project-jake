/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.rdrs;

import java.util.Map;
import java.util.Set;

/**
 * The outcomes ICANN's domain lookup can return, and what they mean for us.
 *
 * <p>These are the literal wire values, which are lowercase human phrases rather than
 * enum-style constants — {@code "success"}, not {@code "SUCCESS"}. The
 * {@code "third leve not supported"} spelling is ICANN's own typo and must be matched
 * exactly.
 */
public final class RdrsLookup {

    private RdrsLookup() {}

    public static final String SUCCESS = "success";
    public static final String CCTLD_NOT_SUPPORTED = "country code not supported";
    public static final String TLD_NOT_SUPPORTED = "tld not supported";
    public static final String THIRD_LEVEL_NOT_SUPPORTED = "third leve not supported";
    public static final String DOMAIN_NOT_FOUND = "domain not found";
    public static final String DOMAIN_NOT_SUPPORTED = "domain not supported";
    public static final String REGISTRAR_NOT_SUPPORTED = "registrar not supported";
    public static final String INVALID_WHOIS_SERVER_RESPONSE = "Invalid Whois Server Response";
    public static final String LOOKUP_SERVICE_NOT_AVAILABLE = "lookup service not available";
    public static final String SERVICE_ERROR = "service error";

    /**
     * IANA ids that are not real registrars — registry-operator placeholders, test
     * entries and historic reservations. ICANN's own client refuses to submit against
     * any of them regardless of the lookup result.
     */
    private static final Set<String> RESERVED_IANA_IDS = Set.of(
            "1",        // Reserved
            "3",        // Registry Installation
            "8",        // Test Registrar
            "119",      // Reserved for Internal Registry Use
            "365",      // Reserved for historical use (EDUCAUSE)
            "376",      // RESERVED-Internet Assigned Numbers Authority
            "9994",     // Registry Operator acting as Registrar where directed by ICANN
            "9995",     // Pre-Delegation Testing transactions #1 reporting
            "9996",     // Pre-Delegation Testing transactions #2 reporting
            "9997",     // Registry SLA Monitoring System transactions reporting
            "9998",     // Billable transactions where Registry Operator acts as Registrar
            "9999",     // Non-billable transactions where Registry Operator acts as Registrar
            "10009",    // Internet Assigned Numbers Authority (IANA)
            "4000001",  // Historic use by mTLD Registry Operator acting as Registrar
            "8888888"   // Historic use by Registry Operator acting as Registrar
    );

    /** User-facing explanation per lookup outcome. */
    private static final Map<String, String> EXPLANATIONS = Map.ofEntries(
            Map.entry(CCTLD_NOT_SUPPORTED,
                    "RDRS does not cover country-code domains, so a request cannot be sent for %s."),
            Map.entry(TLD_NOT_SUPPORTED,
                    "RDRS does not cover the top-level domain of %s."),
            Map.entry(THIRD_LEVEL_NOT_SUPPORTED,
                    "RDRS does not accept requests for third-level domains such as %s."),
            Map.entry(DOMAIN_NOT_FOUND,
                    "ICANN could not find a registration for %s. Check the spelling."),
            Map.entry(DOMAIN_NOT_SUPPORTED,
                    "RDRS does not accept requests for %s."),
            Map.entry(REGISTRAR_NOT_SUPPORTED,
                    "The registrar for %s does not take part in ICANN's RDRS, so a request cannot be "
                            + "submitted through it. You will need to contact that registrar directly."),
            Map.entry(INVALID_WHOIS_SERVER_RESPONSE,
                    "ICANN could not read the registration data for %s. Try again shortly."),
            Map.entry(LOOKUP_SERVICE_NOT_AVAILABLE,
                    "ICANN's lookup service is unavailable right now. Try again shortly."),
            Map.entry(SERVICE_ERROR,
                    "ICANN's lookup service reported an error for %s. Try again shortly.")
    );

    public static boolean isSuccess(String result) {
        return SUCCESS.equalsIgnoreCase(result);
    }

    public static boolean isReservedIanaId(String ianaId) {
        return ianaId != null && RESERVED_IANA_IDS.contains(ianaId.trim());
    }

    /** A message a requestor can act on, for a lookup that cannot be submitted. */
    public static String explain(String result, String domain) {
        if (result == null || result.isBlank()) {
            return "ICANN did not say whether " + domain + " can be requested. Try again shortly.";
        }
        for (Map.Entry<String, String> e : EXPLANATIONS.entrySet()) {
            if (e.getKey().equalsIgnoreCase(result)) {
                return String.format(e.getValue(), domain);
            }
        }
        return "ICANN cannot accept a request for " + domain + " (" + result + ").";
    }
}
