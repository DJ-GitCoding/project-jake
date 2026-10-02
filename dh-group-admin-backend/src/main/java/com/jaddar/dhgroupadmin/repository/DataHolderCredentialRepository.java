/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.repository;

import com.jaddar.dhgroupadmin.entity.DataHolderCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataHolderCredentialRepository extends JpaRepository<DataHolderCredential, Long> {

    Optional<DataHolderCredential> findByClientId(String clientId);

    Optional<DataHolderCredential> findByClientIdAndIsActiveTrue(String clientId);

    List<DataHolderCredential> findByDataholderId(String dataholderId);

    Optional<DataHolderCredential> findByDataholderIdAndIsActiveTrue(String dataholderId);

    boolean existsByDataholderId(String dataholderId);

    List<DataHolderCredential> findBySecretHashedFalse();

    /**
     * Legacy plaintext-secret rows: {@code secret_hashed = false} OR NULL.
     * NULL occurs on rows created before the secret_hashed column existed, and
     * {@code verifySecret} treats NULL as plaintext, so both must be migrated.
     */
    List<DataHolderCredential> findBySecretHashedFalseOrSecretHashedIsNull();
}
