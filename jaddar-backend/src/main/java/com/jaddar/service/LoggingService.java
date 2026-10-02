/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.service;

import com.jaddar.dto.LogEntry;
import com.jaddar.dto.LogQueryParams;
import com.jaddar.dto.LogStats;
import com.jaddar.dto.LogsResponse;
import com.jaddar.enums.LogCategory;
import com.jaddar.enums.LogLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for managing application logs.
 *
 * <p>Faithful port of the Python {@code services.logging_service.LoggingService}. Storage is an
 * in-memory bounded ring buffer (a {@link Deque} capped at {@code maxEntries}); when full, the
 * oldest entry is evicted on append — mirroring Python's {@code collections.deque(maxlen=...)}.
 * Access is guarded by a {@link ReentrantLock}, mirroring Python's {@code threading.Lock}.
 *
 * <p>This is a process-local singleton (one instance per Spring context), matching the Python
 * module-level singleton {@code logging_service}.
 */
@Slf4j
@Service
public class LoggingService {

    /** Default capacity, matching Python {@code LoggingService(max_entries=10000)}. */
    public static final int DEFAULT_MAX_ENTRIES = 10000;

    private final int maxEntries;
    private final Deque<LogEntry> logs;
    private final ReentrantLock lock = new ReentrantLock();

    public LoggingService() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public LoggingService(int maxEntries) {
        this.maxEntries = maxEntries;
        this.logs = new ArrayDeque<>(maxEntries);
        log.info("LoggingService initialized with maxEntries={}", maxEntries);
    }

    /**
     * Create and store a log entry. Mirrors Python {@code LoggingService.log(...)}.
     *
     * @return the created {@link LogEntry}
     */
    public LogEntry log(LogLevel level,
                        LogCategory category,
                        String message,
                        Map<String, Object> details,
                        String userSub,
                        String clientId,
                        String ipAddress,
                        String requestPath,
                        String requestMethod,
                        Integer responseStatus,
                        Double durationMs) {

        LogEntry entry = LogEntry.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .level(level)
                .category(category)
                .message(message)
                .details(details)
                .userSub(userSub)
                .clientId(clientId)
                .ipAddress(ipAddress)
                .requestPath(requestPath)
                .requestMethod(requestMethod)
                .responseStatus(responseStatus)
                .durationMs(durationMs)
                .build();

        lock.lock();
        try {
            // Bounded ring buffer: evict oldest (head) when at capacity, append newest (tail).
            if (maxEntries > 0 && logs.size() >= maxEntries) {
                logs.pollFirst();
            }
            logs.addLast(entry);
        } finally {
            lock.unlock();
        }

        // Also emit to the standard logger (mirrors Python's getattr(logger, level.value)).
        emitToStandardLogger(level, category, message);

        return entry;
    }

    /** Convenience overload: message + category + level only. */
    public LogEntry log(LogLevel level, LogCategory category, String message) {
        return log(level, category, message, null, null, null, null, null, null, null, null);
    }

    /** Convenience overload: message + details. */
    public LogEntry log(LogLevel level, LogCategory category, String message, Map<String, Object> details) {
        return log(level, category, message, details, null, null, null, null, null, null, null);
    }

    /**
     * Record an inbound API request. Called by the foundation's RequestLoggingFilter.
     * Maps to the {@link LogCategory#API} category.
     */
    public void logApiRequest(String method, String path, int status, String ip, double durationMs) {
        LogLevel level = status >= 500 ? LogLevel.ERROR
                : status >= 400 ? LogLevel.WARNING
                : LogLevel.INFO;

        String message = method + " " + path + " -> " + status;

        log(level,
                LogCategory.API,
                message,
                null,
                null,
                null,
                ip,
                path,
                method,
                status,
                durationMs);
    }

    /**
     * Log a token introspection event. Mirrors Python {@code log_introspection}.
     */
    public LogEntry logIntrospection(String clientId,
                                     boolean tokenActive,
                                     String tokenSub,
                                     String ipAddress,
                                     String error) {
        String message;
        LogLevel level;
        Map<String, Object> details = new HashMap<>();

        if (error != null && !error.isEmpty()) {
            message = "Token introspection failed: " + error;
            level = LogLevel.WARNING;
            details.put("error", error);
            details.put("token_active", false);
        } else if (tokenActive) {
            message = "Token introspection successful for subject: " + tokenSub;
            level = LogLevel.INFO;
            details.put("token_active", true);
            details.put("token_sub", tokenSub);
        } else {
            message = "Token introspection: token is not active";
            level = LogLevel.INFO;
            details.put("token_active", false);
        }

        return log(level, LogCategory.INTROSPECTION, message, details,
                tokenSub, clientId, ipAddress, null, null, null, null);
    }

    /**
     * Log an authentication event. Mirrors Python {@code log_auth_event}.
     */
    public LogEntry logAuthEvent(String eventType,
                                 boolean success,
                                 String userSub,
                                 String clientId,
                                 String ipAddress,
                                 Map<String, Object> details) {
        LogLevel level = success ? LogLevel.INFO : LogLevel.WARNING;
        String message = "Auth event: " + eventType + " - " + (success ? "success" : "failed");

        Map<String, Object> eventDetails = new HashMap<>();
        eventDetails.put("event_type", eventType);
        eventDetails.put("success", success);
        if (details != null) {
            eventDetails.putAll(details);
        }

        return log(level, LogCategory.AUTH, message, eventDetails,
                userSub, clientId, ipAddress, null, null, null, null);
    }

    /**
     * Log an admin action. Mirrors Python {@code log_admin_action}.
     */
    public LogEntry logAdminAction(String action,
                                   String adminSub,
                                   String target,
                                   String ipAddress,
                                   Map<String, Object> details) {
        String message = "Admin action: " + action;
        if (target != null) {
            message += " on " + target;
        }

        Map<String, Object> actionDetails = new HashMap<>();
        actionDetails.put("action", action);
        if (target != null) {
            actionDetails.put("target", target);
        }
        if (details != null) {
            actionDetails.putAll(details);
        }

        return log(LogLevel.INFO, LogCategory.ADMIN, message, actionDetails,
                adminSub, null, ipAddress, null, null, null, null);
    }

    /**
     * Query logs with filtering and pagination. Mirrors Python {@code get_logs}.
     * Results are returned newest-first.
     */
    public LogsResponse getLogs(LogQueryParams params) {
        List<LogEntry> allLogs;
        lock.lock();
        try {
            // newest first (iterate tail -> head)
            allLogs = new ArrayList<>(logs.size());
            Iterator<LogEntry> it = logs.descendingIterator();
            while (it.hasNext()) {
                allLogs.add(it.next());
            }
        } finally {
            lock.unlock();
        }

        List<LogEntry> filtered = applyFilters(allLogs, params);
        int total = filtered.size();

        int start = Math.max(0, params.getOffset());
        int end = Math.min(total, start + params.getLimit());
        List<LogEntry> paginated = start >= total
                ? new ArrayList<>()
                : new ArrayList<>(filtered.subList(start, end));

        return LogsResponse.builder()
                .logs(paginated)
                .total(total)
                .limit(params.getLimit())
                .offset(params.getOffset())
                .build();
    }

    private List<LogEntry> applyFilters(List<LogEntry> logsIn, LogQueryParams params) {
        List<LogEntry> filtered = new ArrayList<>(logsIn);

        if (params.getLevel() != null) {
            filtered.removeIf(l -> l.getLevel() != params.getLevel());
        }
        if (params.getCategory() != null) {
            filtered.removeIf(l -> l.getCategory() != params.getCategory());
        }
        if (params.getStartDate() != null) {
            filtered.removeIf(l -> l.getTimestamp().isBefore(params.getStartDate()));
        }
        if (params.getEndDate() != null) {
            filtered.removeIf(l -> l.getTimestamp().isAfter(params.getEndDate()));
        }
        if (params.getUserSub() != null) {
            filtered.removeIf(l -> !params.getUserSub().equals(l.getUserSub()));
        }
        if (params.getClientId() != null) {
            filtered.removeIf(l -> !params.getClientId().equals(l.getClientId()));
        }
        if (params.getSearch() != null) {
            String searchLower = params.getSearch().toLowerCase();
            filtered.removeIf(l -> {
                boolean inMessage = l.getMessage() != null
                        && l.getMessage().toLowerCase().contains(searchLower);
                boolean inDetails = l.getDetails() != null
                        && l.getDetails().toString().toLowerCase().contains(searchLower);
                return !(inMessage || inDetails);
            });
        }

        return filtered;
    }

    /**
     * Aggregate statistics about stored logs. Mirrors Python {@code get_stats}.
     */
    public LogStats getStats() {
        List<LogEntry> allLogs;
        lock.lock();
        try {
            allLogs = new ArrayList<>(logs);
        } finally {
            lock.unlock();
        }

        // Count by level (preserve enum declaration order, like Python's `for level in LogLevel`).
        Map<String, Integer> entriesByLevel = new LinkedHashMap<>();
        for (LogLevel level : LogLevel.values()) {
            int count = 0;
            for (LogEntry l : allLogs) {
                if (l.getLevel() == level) {
                    count++;
                }
            }
            entriesByLevel.put(level.getValue(), count);
        }

        // Count by category.
        Map<String, Integer> entriesByCategory = new LinkedHashMap<>();
        for (LogCategory category : LogCategory.values()) {
            int count = 0;
            for (LogEntry l : allLogs) {
                if (l.getCategory() == category) {
                    count++;
                }
            }
            entriesByCategory.put(category.getValue(), count);
        }

        // Recent errors (last 24 hours).
        Instant yesterday = Instant.now().minus(24, ChronoUnit.HOURS);
        int recentErrors = 0;
        for (LogEntry l : allLogs) {
            if ((l.getLevel() == LogLevel.ERROR || l.getLevel() == LogLevel.CRITICAL)
                    && !l.getTimestamp().isBefore(yesterday)) {
                recentErrors++;
            }
        }

        int introspectionCount = entriesByCategory.getOrDefault(LogCategory.INTROSPECTION.getValue(), 0);

        Set<String> uniqueUsers = new HashSet<>();
        Set<String> uniqueClients = new HashSet<>();
        for (LogEntry l : allLogs) {
            if (l.getUserSub() != null) {
                uniqueUsers.add(l.getUserSub());
            }
            if (l.getClientId() != null) {
                uniqueClients.add(l.getClientId());
            }
        }

        return LogStats.builder()
                .totalEntries(allLogs.size())
                .entriesByLevel(entriesByLevel)
                .entriesByCategory(entriesByCategory)
                .recentErrors(recentErrors)
                .introspectionCount(introspectionCount)
                .uniqueUsers(uniqueUsers.size())
                .uniqueClients(uniqueClients.size())
                .build();
    }

    /**
     * Clear all logs. Mirrors Python {@code clear_logs}.
     *
     * @return number of logs cleared
     */
    public int clearLogs() {
        int count;
        lock.lock();
        try {
            count = logs.size();
            logs.clear();
        } finally {
            lock.unlock();
        }
        log.info("Cleared {} log entries", count);
        return count;
    }

    private void emitToStandardLogger(LogLevel level, LogCategory category, String message) {
        String line = "[" + category.getValue() + "] " + message;
        switch (level) {
            case DEBUG -> log.debug(line);
            case INFO -> log.info(line);
            case WARNING -> log.warn(line);
            case ERROR, CRITICAL -> log.error(line);
        }
    }
}
