/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.repository;
import com.requestormanager.entity.SubscriptionRequest;
import com.requestormanager.entity.SubscriptionRequest.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
@Repository
public interface SubscriptionRequestRepository extends JpaRepository<SubscriptionRequest, Long> {
    Optional<SubscriptionRequest> findByInternalRequestId(String internalRequestId);
    Optional<SubscriptionRequest> findByExternalRequestId(String externalRequestId);
    List<SubscriptionRequest> findByRequestorGroupId(Long requestorGroupId);
    List<SubscriptionRequest> findByDataHolderGroupId(Long dataHolderGroupId);
    List<SubscriptionRequest> findByStatus(SubscriptionStatus status);
    List<SubscriptionRequest> findByRequestorGroupIdAndStatus(Long requestorGroupId, SubscriptionStatus status);

    /**
     * Find all subscription requests between a requestor group and a data holder.
     * Used to enforce the one-subscription-per-data-holder-per-group limit.
     */
    List<SubscriptionRequest> findByRequestorGroupIdAndDataHolderGroupId(Long requestorGroupId, Long dataHolderGroupId);

    /**
     * Find subscription requests by requestor group and multiple statuses.
     * Used by External API to get active agreements (APPROVED, ACTIVE).
     */
    List<SubscriptionRequest> findByRequestorGroupIdAndStatusIn(Long requestorGroupId, List<SubscriptionStatus> statuses);
    /**
     * Find subscription requests by multiple statuses.
     * Used to get actionable subscriptions (APPROVED, TESTING) that require user action.
     */
    List<SubscriptionRequest> findByStatusIn(List<SubscriptionStatus> statuses);
    @Query("SELECT sr FROM SubscriptionRequest sr WHERE sr.requestorGroup.id = :groupId " +
           "AND sr.dataHolderGroup.id = :dataHolderGroupId AND sr.templateId = :templateId " +
           "AND sr.status NOT IN ('DECLINED', 'CANCELLED', 'EXPIRED')")
    Optional<SubscriptionRequest> findActiveOrPendingRequest(
            @Param("groupId") Long groupId,
            @Param("dataHolderGroupId") Long dataHolderGroupId,
            @Param("templateId") String templateId);
    @Query("SELECT sr FROM SubscriptionRequest sr WHERE sr.status IN ('SUBMITTED', 'PENDING_REVIEW', 'APPROVED', 'TESTING') " +
           "ORDER BY sr.createdAt DESC")
    List<SubscriptionRequest> findPendingRequests();
    /**
     * Find actionable subscription requests (ready for user action).
     * APPROVED: Ready to start testing
     * TESTING: Ready to run tests and activate
     */
    @Query("SELECT sr FROM SubscriptionRequest sr WHERE sr.status IN ('APPROVED', 'TESTING') " +
           "ORDER BY sr.statusChangedAt DESC")
    List<SubscriptionRequest> findActionableRequests();
    /**
     * Find actionable subscription requests for specific requestor groups.
     */
    @Query("SELECT sr FROM SubscriptionRequest sr WHERE sr.status IN ('APPROVED', 'TESTING') " +
           "AND sr.requestorGroup.id IN :groupIds " +
           "ORDER BY sr.statusChangedAt DESC")
    List<SubscriptionRequest> findActionableRequestsByGroups(@Param("groupIds") List<Long> groupIds);
    @Query("SELECT sr FROM SubscriptionRequest sr WHERE sr.requestorGroup.id IN :groupIds")
    List<SubscriptionRequest> findByRequestorGroupIdIn(@Param("groupIds") List<Long> groupIds);
    @Query("SELECT sr FROM SubscriptionRequest sr WHERE sr.status = :status AND sr.expiresAt < :now")
    List<SubscriptionRequest> findExpiredRequests(
            @Param("status") SubscriptionStatus status,
            @Param("now") LocalDateTime now);
    @Query("SELECT COUNT(sr) FROM SubscriptionRequest sr WHERE sr.requestorGroup.id = :groupId AND sr.status = :status")
    Long countByRequestorGroupIdAndStatus(@Param("groupId") Long groupId, @Param("status") SubscriptionStatus status);
    boolean existsByRequestorGroupIdAndDataHolderGroupIdAndTemplateIdAndStatusIn(
            Long requestorGroupId, Long dataHolderGroupId, String templateId, List<SubscriptionStatus> statuses);

    // ==================== Paginated + searchable finders ====================
    // Search mirrors the frontend client-side filter (template name, internal id,
    // requestor group name, data holder group name). Optional status / requestor-group
    // filters. All filter params are nullable (null = no filtering on that field).

    @Query("SELECT sr FROM SubscriptionRequest sr " +
           "LEFT JOIN sr.requestorGroup rg LEFT JOIN sr.dataHolderGroup dhg WHERE " +
           "(CAST(:search AS string) IS NULL OR LOWER(sr.templateName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(sr.internalRequestId) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(rg.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(dhg.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:status IS NULL OR sr.status = :status) " +
           "AND (:requestorGroupId IS NULL OR rg.id = :requestorGroupId)")
    Page<SubscriptionRequest> searchAll(@Param("search") String search,
                                        @Param("status") SubscriptionStatus status,
                                        @Param("requestorGroupId") Long requestorGroupId,
                                        Pageable pageable);

    @Query("SELECT sr FROM SubscriptionRequest sr " +
           "LEFT JOIN sr.requestorGroup rg LEFT JOIN sr.dataHolderGroup dhg WHERE " +
           "rg.id IN :groupIds AND " +
           "(CAST(:search AS string) IS NULL OR LOWER(sr.templateName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(sr.internalRequestId) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(rg.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(dhg.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:status IS NULL OR sr.status = :status) " +
           "AND (:requestorGroupId IS NULL OR rg.id = :requestorGroupId)")
    Page<SubscriptionRequest> searchByGroups(@Param("groupIds") List<Long> groupIds,
                                             @Param("search") String search,
                                             @Param("status") SubscriptionStatus status,
                                             @Param("requestorGroupId") Long requestorGroupId,
                                             Pageable pageable);

    // ==================== Status counts (for dashboard/stats badges) ====================
    // Each row is [SubscriptionStatus status, Long count]. Scoped and unscoped variants
    // mirror the role scoping used by the paginated finders above.

    @Query("SELECT sr.status, COUNT(sr) FROM SubscriptionRequest sr GROUP BY sr.status")
    List<Object[]> countByStatusAll();

    @Query("SELECT sr.status, COUNT(sr) FROM SubscriptionRequest sr " +
           "WHERE sr.requestorGroup.id IN :groupIds GROUP BY sr.status")
    List<Object[]> countByStatusForGroups(@Param("groupIds") List<Long> groupIds);
}