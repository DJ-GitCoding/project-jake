/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.FileAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileAttachmentRepository extends JpaRepository<FileAttachment, Long> {

    Optional<FileAttachment> findByFileId(UUID fileId);

    List<FileAttachment> findByRequestIdOrderByUploadedAtDesc(Long requestId);

    List<FileAttachment> findByRequest_RequestIdOrderByUploadedAtDesc(UUID requestId);

    List<FileAttachment> findByScanStatus(FileAttachment.ScanStatus scanStatus);

    long countByRequest_RequestId(UUID requestId);
}
