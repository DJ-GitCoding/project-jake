/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.rdrs;

import com.jaddar.config.JaddarProperties;
import com.jaddar.entity.RdrsMailSettings;
import com.jaddar.entity.RdrsMailbox;
import com.jaddar.entity.RdrsMessage;
import com.jaddar.repository.RdrsMailSettingsRepository;
import com.jaddar.repository.RdrsMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class RdrsMailIngestService {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final WebClient mailgun;
    private final JaddarProperties props;
    private final RdrsMailboxResolver resolver;
    private final RdrsMessageRepository messages;
    private final RdrsMailSettingsRepository settingsRepo;

    public RdrsMailIngestService(@Qualifier("rdrsWebClient") WebClient mailgun,
                                 JaddarProperties props,
                                 RdrsMailboxResolver resolver,
                                 RdrsMessageRepository messages,
                                 RdrsMailSettingsRepository settingsRepo) {
        this.mailgun = mailgun;
        this.props = props;
        this.resolver = resolver;
        this.messages = messages;
        this.settingsRepo = settingsRepo;
    }

    @jakarta.annotation.PostConstruct
    void warnIfMisconfigured() {
        JaddarProperties.Mail mail = props.getRdrs().getMail();
        if (!mail.isEnabled()) return;
        if (mail.getDomain() == null || mail.getDomain().isBlank()) {
            log.warn("RDRS mail capture is enabled but RDRS_MAIL_DOMAIN is not set; "
                    + "no addresses can be assigned and no mail will be captured");
        }
        if (mail.getApiKey() == null || mail.getApiKey().isBlank()) {
            log.warn("RDRS mail capture is enabled but MAILGUN_API_KEY is not set; "
                    + "inbound mail will not be captured");
        }
    }

    public boolean isConfigured() {
        JaddarProperties.Mail mail = props.getRdrs().getMail();
        return mail.isEnabled()
                && mail.getApiKey() != null && !mail.getApiKey().isBlank()
                && mail.getDomain() != null && !mail.getDomain().isBlank();
    }

    private String authHeader() {
        String raw = "api:" + props.getRdrs().getMail().getApiKey();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    // Fetches newly stored mail from Mailgun and persists anything not already captured.
    public int poll() {
        if (!isConfigured()) {
            return 0;
        }

        JaddarProperties.Mail mail = props.getRdrs().getMail();
        String begin = String.valueOf(
                Instant.now().minus(mail.getPollWindowHours(), ChronoUnit.HOURS).getEpochSecond());

        int stored = 0;
        try {
            String url = mail.getApiBaseUrl() + "/v3/" + mail.getDomain()
                    + "/events?event=stored&limit=100&begin=" + begin + "&ascending=yes";

            while (url != null) {
                Map<String, Object> page = getJson(url);
                if (page == null) break;

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items =
                        (List<Map<String, Object>>) page.getOrDefault("items", List.of());
                if (items.isEmpty()) break;

                for (Map<String, Object> item : items) {
                    if (ingest(item)) stored++;
                }

                url = nextPage(page);
            }
            recordPoll("OK", stored + " new message(s)");

        } catch (Exception e) {
            log.error("RDRS mail poll failed", e);
            recordPoll("ERROR", safeMessage(e));
        }
        return stored;
    }

    @SuppressWarnings("unchecked")
    private String nextPage(Map<String, Object> page) {
        Object paging = page.get("paging");
        if (!(paging instanceof Map)) return null;
        Object next = ((Map<String, Object>) paging).get("next");
        return next == null ? null : String.valueOf(next);
    }

    // Persists one stored event's message; false if already captured or unroutable.
    private boolean ingest(Map<String, Object> event) {
        String storageUrl = storageUrl(event);
        if (storageUrl == null) return false;

        String storageKey = storageUrl.substring(storageUrl.lastIndexOf('/') + 1);
        if (messages.existsByStorageKey(storageKey)) return false;

        Map<String, Object> full = getJson(storageUrl);
        if (full == null) {
            log.warn("Stored message {} could not be retrieved", storageKey);
            return false;
        }

        String recipient = firstNonBlank(str(full.get("recipient")), str(event.get("recipient")));
        Optional<RdrsMailbox> mailbox = resolver.resolve(recipient);
        if (mailbox.isEmpty()) {
            log.info("Ignoring stored message for unknown recipient {}", recipient);
            return false;
        }

        store(mailbox.get(), storageKey, full);
        return true;
    }

    // Persists a retrieved message. Shared by the poller and any future webhook.
    @Transactional
    public void store(RdrsMailbox mailbox, String storageKey, Map<String, Object> full) {
        if (messages.existsByStorageKey(storageKey)) return;

        RdrsMessage message = new RdrsMessage();
        message.setMailbox(mailbox);
        message.setStorageKey(storageKey);
        message.setMessageId(truncate(str(full.get("Message-Id")), 512));
        message.setSender(truncate(firstNonBlank(str(full.get("From")), str(full.get("sender"))), 512));
        message.setRecipient(truncate(str(full.get("recipient")), 512));
        message.setSubject(truncate(str(full.get("Subject")), 1024));
        message.setBodyPlain(str(full.get("body-plain")));
        message.setBodyHtml(str(full.get("body-html")));
        message.setSentAt(parseDate(str(full.get("Date"))));
        message.setReceivedAt(Instant.now());

        messages.save(message);
        log.info("Captured RDRS message for {} (subject: {})",
                mailbox.getLocalPart(), abbreviate(message.getSubject()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getJson(String url) {
        return mailgun.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(Map.class)
                .block(TIMEOUT);
    }

    @SuppressWarnings("unchecked")
    private String storageUrl(Map<String, Object> event) {
        Object storage = event.get("storage");
        if (!(storage instanceof Map)) return null;
        Map<String, Object> map = (Map<String, Object>) storage;
        Object url = map.get("url");
        if (url != null) return String.valueOf(url);
        Object urls = map.get("urls");
        if (urls instanceof List<?> list && !list.isEmpty()) return String.valueOf(list.get(0));
        return null;
    }

    // Purges captured mail past the configured retention; no-op when indefinite.
    @Transactional
    public int purgeExpired() {
        Integer days = settings().getRetentionDays();
        if (days == null || days <= 0) return 0;

        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        int removed = messages.deleteReceivedBefore(cutoff);
        if (removed > 0) {
            log.info("Purged {} RDRS message(s) older than {} days", removed, days);
        }
        return removed;
    }

    @Transactional
    public RdrsMailSettings settings() {
        return settingsRepo.findById(RdrsMailSettings.SINGLETON_ID).orElseGet(() -> {
            RdrsMailSettings fresh = new RdrsMailSettings();
            fresh.setId(RdrsMailSettings.SINGLETON_ID);
            fresh.setUpdatedAt(Instant.now());
            return settingsRepo.save(fresh);
        });
    }

    @Transactional
    public void recordPoll(String status, String message) {
        RdrsMailSettings settings = settings();
        settings.setLastPolledAt(Instant.now());
        settings.setLastPollStatus(status);
        settings.setLastPollMessage(truncate(message, 1024));
        settingsRepo.save(settings);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String abbreviate(String s) {
        if (s == null) return "(no subject)";
        return s.length() <= 60 ? s : s.substring(0, 60) + "…";
    }

    private static Instant parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    // Exception text with any credential header redacted.
    private static String safeMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        return truncate(msg.replaceAll("(?i)basic [A-Za-z0-9+/=]+", "Basic <redacted>"), 500);
    }
}
