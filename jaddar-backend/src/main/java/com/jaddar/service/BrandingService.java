/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.service;

import com.jaddar.entity.BrandingAsset;
import com.jaddar.entity.BrandingNotice;
import com.jaddar.repository.BrandingAssetRepository;
import com.jaddar.repository.BrandingNoticeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Stores and validates the deployment's branding: the custom logo, and the notices shown
 * under the sign-in form.
 *
 * <p>Logo uploads are validated by extension, declared MIME type <em>and</em> leading magic
 * bytes, so a renamed file or a spoofed Content-Type cannot get a non-image past us and
 * be served back from our own origin. SVG is deliberately not accepted: it can carry
 * script and would execute same-origin when the browser loads it as the logo.
 *
 * <p>Notices are plain text with an optional link. Both are rendered on a page anonymous
 * visitors reach, so notice URLs are restricted to http/https — {@code javascript:} and
 * {@code data:} targets would turn an admin-editable field into stored XSS.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrandingService {

    private final BrandingAssetRepository repository;
    private final BrandingNoticeRepository noticeRepository;

    /** Logos are chrome, not content — 2 MB is generous for any sane logo. */
    public static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "png",  "image/png",
            "jpg",  "image/jpeg",
            "jpeg", "image/jpeg",
            "gif",  "image/gif",
            "webp", "image/webp"
    );

    /** Thrown for anything the admin can fix by picking a different file. */
    public static class InvalidBrandingException extends RuntimeException {
        public InvalidBrandingException(String message) {
            super(message);
        }
    }

    public Optional<BrandingAsset> find() {
        return repository.findById(BrandingAsset.SINGLETON_ID);
    }

    public boolean exists() {
        return repository.existsById(BrandingAsset.SINGLETON_ID);
    }

    @Transactional
    public BrandingAsset store(MultipartFile file, String updatedBy) {
        byte[] bytes = readAndValidate(file);
        String contentType = ALLOWED_TYPES.get(extensionOf(file.getOriginalFilename()));

        BrandingAsset asset = find().orElseGet(() ->
                BrandingAsset.builder().id(BrandingAsset.SINGLETON_ID).build());
        asset.setFilename(sanitizeFilename(file.getOriginalFilename()));
        asset.setContentType(contentType);
        asset.setData(bytes);
        asset.setSizeBytes(bytes.length);
        asset.setUpdatedBy(updatedBy);

        BrandingAsset saved = repository.save(asset);
        log.info("Custom logo updated by {} ({} bytes, {})", updatedBy, bytes.length, contentType);
        return saved;
    }

    @Transactional
    public boolean remove(String removedBy) {
        if (!exists()) return false;
        repository.deleteById(BrandingAsset.SINGLETON_ID);
        log.info("Custom logo removed by {} — reverting to the built-in logo", removedBy);
        return true;
    }

    // ==================== Login notices ====================

    /** Notices in display order; empty when the deployment has defined none. */
    public List<BrandingNotice> listNotices() {
        return noticeRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    /**
     * Replace the whole notice list in one shot.
     *
     * <p>Whole-list replacement rather than per-item CRUD: the editor lets an admin add,
     * remove and reorder rows before saving, so the submitted list <em>is</em> the desired
     * end state. That keeps ordering consistent without a separate reorder endpoint.
     * Submitting an empty list clears the notices and the login screen renders nothing.
     */
    @Transactional
    public List<BrandingNotice> replaceNotices(List<NoticeInput> inputs, String updatedBy) {
        List<NoticeInput> cleaned = validateNotices(inputs);

        noticeRepository.deleteAllInBatch();

        List<BrandingNotice> saved = new ArrayList<>();
        for (int i = 0; i < cleaned.size(); i++) {
            NoticeInput in = cleaned.get(i);
            saved.add(BrandingNotice.builder()
                    .sortOrder(i)
                    .text(in.text())
                    .url(in.url())
                    .updatedBy(updatedBy)
                    .build());
        }
        List<BrandingNotice> result = noticeRepository.saveAll(saved);
        log.info("Login notices replaced by {} ({} item(s))", updatedBy, result.size());
        return result;
    }

    /** One submitted notice: visible wording plus an optional link target. */
    public record NoticeInput(String text, String url) {}

    /** At most this many notices; the login screen is not a content management system. */
    public static final int MAX_NOTICES = 10;
    public static final int MAX_NOTICE_TEXT = 500;
    public static final int MAX_NOTICE_URL = 500;

    /** Trim, drop blank rows, and reject anything unsafe or oversized. */
    private List<NoticeInput> validateNotices(List<NoticeInput> inputs) {
        List<NoticeInput> cleaned = new ArrayList<>();
        if (inputs == null) return cleaned;

        for (NoticeInput in : inputs) {
            if (in == null) continue;
            String text = in.text() == null ? "" : in.text().trim();
            String url = in.url() == null ? "" : in.url().trim();

            // A row with neither text nor link is an empty editor row, not an error.
            if (text.isEmpty() && url.isEmpty()) continue;
            if (text.isEmpty()) {
                throw new InvalidBrandingException("Each notice needs text to display.");
            }
            if (text.length() > MAX_NOTICE_TEXT) {
                throw new InvalidBrandingException(
                        "Notice text must be " + MAX_NOTICE_TEXT + " characters or fewer.");
            }
            if (url.length() > MAX_NOTICE_URL) {
                throw new InvalidBrandingException(
                        "Notice links must be " + MAX_NOTICE_URL + " characters or fewer.");
            }
            cleaned.add(new NoticeInput(text, url.isEmpty() ? null : requireWebUrl(url)));
        }

        if (cleaned.size() > MAX_NOTICES) {
            throw new InvalidBrandingException("You can define at most " + MAX_NOTICES + " notices.");
        }
        return cleaned;
    }

    /**
     * Accept only absolute http/https links. Anything else — most importantly
     * {@code javascript:} — would execute in the visitor's browser when clicked.
     */
    private String requireWebUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null) {
                return url;
            }
        } catch (URISyntaxException ignored) {
            // fall through to the shared message below
        }
        throw new InvalidBrandingException(
                "Notice links must be a full web address starting with http:// or https://");
    }

    // ==================== Validation ====================

    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidBrandingException("No image was selected. Choose a PNG, JPEG, GIF or WebP file to upload.");
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new InvalidBrandingException("That image is too large. Logos must be 2 MB or smaller.");
        }

        String extension = extensionOf(file.getOriginalFilename());
        String expectedMime = ALLOWED_TYPES.get(extension);
        if (expectedMime == null) {
            throw new InvalidBrandingException("Unsupported image type. Use a PNG, JPEG, GIF or WebP file.");
        }

        String declared = file.getContentType();
        if (declared != null && !expectedMime.equalsIgnoreCase(declared.trim())) {
            throw new InvalidBrandingException("That file does not look like a " + extension.toUpperCase(Locale.ROOT)
                    + " image. Re-save it and try again.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.warn("Failed to read uploaded logo", e);
            throw new InvalidBrandingException("The image could not be read. Try uploading it again.");
        }

        if (!hasImageMagic(bytes, expectedMime)) {
            throw new InvalidBrandingException("That file is not a valid " + extension.toUpperCase(Locale.ROOT)
                    + " image. Use a PNG, JPEG, GIF or WebP file.");
        }
        return bytes;
    }

    /** Verify the leading bytes actually match the claimed image format. */
    private boolean hasImageMagic(byte[] b, String mime) {
        return switch (mime) {
            case "image/png"  -> startsWith(b, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case "image/jpeg" -> startsWith(b, new int[]{0xFF, 0xD8, 0xFF});
            case "image/gif"  -> startsWith(b, new int[]{0x47, 0x49, 0x46, 0x38}); // GIF8(7|9)a
            // RIFF....WEBP — the 4 size bytes at offset 4 are skipped.
            case "image/webp" -> startsWith(b, new int[]{0x52, 0x49, 0x46, 0x46})
                    && b.length >= 12
                    && b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 && b[11] == 0x50;
            default -> false;
        };
    }

    private boolean startsWith(byte[] bytes, int[] magic) {
        if (bytes.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if ((bytes[i] & 0xFF) != magic[i]) return false;
        }
        return true;
    }

    private String extensionOf(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Strip any path components a client may have sent; the name is display-only. */
    private String sanitizeFilename(String filename) {
        if (filename == null) return null;
        String base = filename.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.length() > 255 ? base.substring(0, 255) : base;
    }
}
