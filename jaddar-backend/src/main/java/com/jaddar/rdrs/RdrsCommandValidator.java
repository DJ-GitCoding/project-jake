/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.rdrs;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/**
 * Enforces ICANN's request rules before anything is sent.
 *
 * <p>These mirror the validation in ICANN's own client. Repeating them here is not
 * belt-and-braces: RDRS answers {@code 200} with an empty body on success and gives
 * almost nothing back on rejection, so a malformed request would otherwise surface to
 * the requestor as an unexplained failure. Catching it here lets us say which field
 * is wrong.
 */
@Component
public class RdrsCommandValidator {

    static final Set<String> CATEGORIES = Set.of(
            "Law Enforcement",
            "Security Researcher",
            "Computer Security Incident Response Team (CSIRT)",
            "Cybersecurity Incident Response Team (non-CSIRT)",
            "Consumer Protection",
            "Research (non-security)",
            "Domain Investor",
            "IP Holder",
            "Dispute Resolution Service Provider",
            "Litigation/Dispute Resolution (non-IP)",
            "Other");

    static final String CATEGORY_OTHER = "Other";
    static final String CATEGORY_LAW_ENFORCEMENT = "Law Enforcement";

    static final Set<String> PRIORITIES = Set.of("Standard Request", "Expedited Review Request");
    static final String PRIORITY_EXPEDITED = "Expedited Review Request";

    static final String PARTY_SELF = "I am submitting this request on my own behalf";
    static final String PARTY_THIRD_PARTY =
            "I am authorized to act on behalf of a third party in submitting this request";

    static final String LEGAL_BASIS_OTHER = "Other applicable law legal basis";

    static final int MAX_ADDITIONAL_ATTACHMENTS = 3;

    /** Every upload on ICANN's form is labelled "Choose a File (5MB Maximum)". */
    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    /**
     * @throws RdrsException with a 400 status and a message naming the offending field
     */
    public void validate(RdrsDto.Command c,
                         MultipartFile lawEnforcement,
                         MultipartFile powerOfAttorney,
                         List<MultipartFile> additionalAttachments) {

        requireText(c.getDomainSubject(), "a domain name");

        // Contact block
        if (c.getUser() == null) {
            throw bad("Your contact details are required.");
        }
        requireText(c.getUser().getName(), "your name");
        requireText(c.getUser().getEmail(), "your email address");

        // Category
        requireText(c.getCategory(), "a request category");
        if (!CATEGORIES.contains(c.getCategory())) {
            throw bad("'" + c.getCategory() + "' is not a request category ICANN recognises.");
        }
        if (CATEGORY_OTHER.equals(c.getCategory())) {
            lengthBetween(c.getCategoryOtherDescription(), 5, 100,
                    "A description of your category is required (5–100 characters).");
        }

        // Priority
        requireText(c.getRequestPriority(), "a request priority");
        if (!PRIORITIES.contains(c.getRequestPriority())) {
            throw bad("'" + c.getRequestPriority() + "' is not a request priority ICANN recognises.");
        }
        if (PRIORITY_EXPEDITED.equals(c.getRequestPriority())) {
            lengthBetween(c.getExpeditedPriorityReason(), 50, 2000,
                    "A justification for the expedited request is required (50–2000 characters).");
        }

        // Data elements
        if (c.getRequestedDataElements() == null || c.getRequestedDataElements().isEmpty()) {
            throw bad("Select at least one registration data element to request.");
        }

        // Countries the data will be processed in
        if (c.getProcessedCountries() == null || c.getProcessedCountries().isEmpty()) {
            throw bad("Select at least one country or territory where the data will be processed.");
        }

        // Issue description, ICANN's form requires 50–2000 characters and ASCII only.
        lengthBetween(c.getIssueDescription(), 50, 2000,
                "Describe your request in 50–2000 characters.");
        if (!c.getIssueDescription().chars().allMatch(ch ->
                (ch >= 0x20 && ch < 0x7F) || ch == '\n' || ch == '\r')) {
            throw bad("The description may only contain standard ASCII characters.");
        }

        // Legal basis
        if (Boolean.TRUE.equals(c.getAssertingLegalBasis())) {
            requireText(c.getLegalBasisReason(), "a legal basis");
            if (LEGAL_BASIS_OTHER.equals(c.getLegalBasisReason())) {
                lengthBetween(c.getLegalBasisOtherInformation(), 50, 300,
                        "Details of the other legal basis are required (50–300 characters).");
            }
        }

        // Party representation
        requireText(c.getPartyRepresentation(), "who you are submitting on behalf of");
        if (!PARTY_SELF.equals(c.getPartyRepresentation())
                && !PARTY_THIRD_PARTY.equals(c.getPartyRepresentation())) {
            throw bad("That party representation is not one ICANN recognises.");
        }
        if (PARTY_THIRD_PARTY.equals(c.getPartyRepresentation())
                && (powerOfAttorney == null || powerOfAttorney.isEmpty())) {
            throw bad("Acting on behalf of a third party requires a power of attorney "
                    + "(or equivalent) attachment.");
        }

        // Law enforcement
        if (c.getLawEnforcementRequestForData() == null) {
            throw bad("State whether a law enforcement request has been issued for this data.");
        }

        // Acknowledgements
        if (!Boolean.TRUE.equals(c.getAgreeDataIsCorrect())
                || !Boolean.TRUE.equals(c.getAgreeDataProcessTransferCompliance())) {
            throw bad("Both acknowledgements must be accepted before the request can be sent.");
        }

        // Attachments, ICANN accepts PDFs up to 5MB on every upload.
        if (additionalAttachments != null && additionalAttachments.size() > MAX_ADDITIONAL_ATTACHMENTS) {
            throw bad("At most " + MAX_ADDITIONAL_ATTACHMENTS + " additional attachments may be included.");
        }
        validateFile(lawEnforcement);
        validateFile(powerOfAttorney);
        if (additionalAttachments != null) {
            additionalAttachments.forEach(this::validateFile);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment";
        if (!name.toLowerCase().endsWith(".pdf")) {
            throw bad("'" + name + "' is not a PDF. ICANN accepts PDF attachments only.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw bad("'" + name + "' is larger than " + (MAX_FILE_BYTES / (1024 * 1024)) + "MB.");
        }
    }

    private void requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw bad("Please provide " + what + ".");
        }
    }

    private void lengthBetween(String value, int min, int max, String message) {
        int length = value == null ? 0 : value.trim().length();
        if (length < min || length > max) {
            throw bad(message);
        }
    }

    private RdrsException bad(String message) {
        return new RdrsException(message, 400);
    }
}
