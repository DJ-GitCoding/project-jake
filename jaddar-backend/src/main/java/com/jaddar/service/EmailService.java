/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Transactional email via the Twilio SendGrid Web API (v3).
 *
 * Ported from backend/services/email_service.py.
 *
 * If {@code sendgrid.api-key} is blank the message is logged instead of sent, so
 * local development does not require SendGrid credentials.
 */
@Slf4j
@Service
public class EmailService {

    private final String apiKey;
    private final String fromEmail;
    private final String fromName;

    public EmailService(@Value("${sendgrid.api-key:}") String apiKey,
                        @Value("${sendgrid.from-email:}") String fromEmail,
                        @Value("${sendgrid.from-name:Jaddar}") String fromName) {
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    private static String escape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Send a password reset email containing a one-time reset link.
     */
    public void sendPasswordReset(String toEmail, String displayName, String resetLink) {
        String subject = "Reset your Jaddar password";
        String greeting = displayName != null ? displayName : "";
        String text = "Hello " + greeting + ",\n\n"
                + "We received a request to reset your password. Use the link below to "
                + "choose a new one. This link expires in 1 hour and can only be used once.\n\n"
                + resetLink + "\n\n"
                + "If you did not request this, you can safely ignore this email.\n";
        String html = "<p>Hello " + escape(greeting) + ",</p>"
                + "<p>We received a request to reset your password. Click the button below to "
                + "choose a new one. This link expires in <strong>1 hour</strong> and can only "
                + "be used once.</p>"
                + "<p><a href=\"" + resetLink + "\" "
                + "style=\"display:inline-block;padding:10px 18px;background:#2563eb;color:#fff;"
                + "text-decoration:none;border-radius:6px\">Reset password</a></p>"
                + "<p>Or paste this link into your browser:<br><a href=\"" + resetLink + "\">" + resetLink + "</a></p>"
                + "<p style=\"color:#888\">If you did not request this, you can safely ignore this email.</p>";
        send(toEmail, subject, text, html);
    }

    private void send(String toEmail, String subject, String text, String html) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("SENDGRID_API_KEY not configured — email to {} NOT sent. Subject: {}", toEmail, subject);
            log.info("[DEV EMAIL] To: {}\n{}", toEmail, text);
            return;
        }

        try {
            Mail mail = new Mail();
            mail.setFrom(new Email(fromEmail, fromName));
            mail.setSubject(subject);

            com.sendgrid.helpers.mail.objects.Personalization personalization =
                    new com.sendgrid.helpers.mail.objects.Personalization();
            personalization.addTo(new Email(toEmail));
            mail.addPersonalization(personalization);

            mail.addContent(new Content("text/plain", text));
            mail.addContent(new Content("text/html", html));

            SendGrid sg = new SendGrid(apiKey);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);

            if (response.getStatusCode() >= 400) {
                log.error("SendGrid returned {} sending to {}", response.getStatusCode(), toEmail);
            } else {
                log.info("Password reset email sent to {} (status {})", toEmail, response.getStatusCode());
            }
        } catch (Exception e) {
            // never propagate email failures to the caller
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }
}
