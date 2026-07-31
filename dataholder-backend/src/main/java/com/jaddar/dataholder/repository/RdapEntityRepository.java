/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.RdapEntity;
import com.jaddar.dataholder.entity.RdapEntity.ObjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RdapEntityRepository extends JpaRepository<RdapEntity, Long> {

    // ==================== Basic Lookups ====================
    
    Optional<RdapEntity> findByHandle(String handle);
    
    Optional<RdapEntity> findByHandleAndObjectType(String handle, ObjectType objectType);

    // ==================== Domain Lookups ====================
    
    @Query("SELECT r FROM RdapEntity r WHERE LOWER(r.ldhName) = LOWER(:ldhName) AND r.objectType = 'DOMAIN'")
    Optional<RdapEntity> findDomainByLdhName(@Param("ldhName") String ldhName);

    @Query("SELECT r.ldhName FROM RdapEntity r WHERE r.objectType = 'DOMAIN' ORDER BY r.ldhName")
    List<String> findAllDomainNames();

    // ==================== IP Network Lookups ====================
    
    @Query("SELECT r FROM RdapEntity r WHERE r.startAddress = :address AND r.objectType = 'IP_NETWORK'")
    Optional<RdapEntity> findIpNetworkByStartAddress(@Param("address") String address);

    @Query("SELECT r FROM RdapEntity r WHERE r.objectType = 'IP_NETWORK' AND r.startAddress <= :address AND (r.endAddress >= :address OR r.endAddress IS NULL)")
    List<RdapEntity> findIpNetworksContainingAddress(@Param("address") String address);

    // ==================== ASN Lookups ====================
    
    @Query("SELECT r FROM RdapEntity r WHERE r.objectType = 'AUTNUM' AND r.startAutnum <= :asn AND (r.endAutnum >= :asn OR r.endAutnum = r.startAutnum)")
    Optional<RdapEntity> findByAutnum(@Param("asn") Long asn);

    @Query("SELECT r FROM RdapEntity r WHERE r.handle = :handle AND r.objectType = 'AUTNUM'")
    Optional<RdapEntity> findAsnByHandle(@Param("handle") String handle);

    // ==================== Type-based Queries ====================
    
    List<RdapEntity> findByObjectType(ObjectType objectType);
    
    List<RdapEntity> findByObjectTypeAndParentIsNullOrderByLdhNameAsc(ObjectType objectType);
    
    long countByObjectType(ObjectType objectType);

    // ==================== Hierarchy Queries ====================
    
    List<RdapEntity> findByParentIsNullOrderByObjectTypeAscLdhNameAsc();
    
    List<RdapEntity> findByParentIdOrderByObjectTypeAsc(Long parentId);
    
    /**
     * Find all child entities for a given parent entity ID.
     * Used by import to find existing contact entities and by response builder
     * to include child entities in RDAP responses.
     */
    List<RdapEntity> findByParentId(Long parentId);
    
    /**
     * Find child entities by parent and role (returns list).
     * Used to find all contacts with a specific role (registrant, admin, tech, etc.)
     */
    @Query("SELECT r FROM RdapEntity r WHERE r.parent.id = :parentId AND :role MEMBER OF r.roles")
    List<RdapEntity> findByParentIdAndRole(@Param("parentId") Long parentId, @Param("role") String role);
    
    /**
     * Find first child entity by parent and role (returns Optional).
     * Used by import to check for existing contact with specific role to avoid duplicates.
     */
    @Query("SELECT r FROM RdapEntity r WHERE r.parent.id = :parentId AND :role MEMBER OF r.roles ORDER BY r.id ASC LIMIT 1")
    Optional<RdapEntity> findFirstByParentIdAndRole(@Param("parentId") Long parentId, @Param("role") String role);
    
    /**
     * Find all child entities of a specific object type under a parent.
     * Useful for fetching all nameserver or entity children.
     */
    @Query("SELECT r FROM RdapEntity r WHERE r.parent.id = :parentId AND r.objectType = :objectType ORDER BY r.handle")
    List<RdapEntity> findByParentIdAndObjectType(@Param("parentId") Long parentId, @Param("objectType") ObjectType objectType);
    
    /**
     * Count children for a parent entity.
     * Used for statistics and validation.
     */
    @Query("SELECT COUNT(r) FROM RdapEntity r WHERE r.parent.id = :parentId")
    long countByParentId(@Param("parentId") Long parentId);

    // ==================== Migration/Source Tracking ====================
    
    List<RdapEntity> findBySourceDomainId(Long sourceDomainId);
    
    List<RdapEntity> findBySourceIpId(Long sourceIpId);
    
    List<RdapEntity> findBySourceAsnId(Long sourceAsnId);

    // ==================== Search ====================
    
    @Query("SELECT r FROM RdapEntity r WHERE " +
           "(LOWER(r.ldhName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(r.handle) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(r.contactName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(r.organization) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY r.objectType, r.ldhName")
    List<RdapEntity> searchByNameOrHandle(@Param("search") String search);

    // ==================== Test Data ====================

    /**
     * Records marked as test data via their {@code TestDataFlag}, limited to the object types an
     * RDAP query can target. Used as the fallback set when a template defines no test data of its own.
     */
    @Query("SELECT r FROM RdapEntity r WHERE r.testDataFlag IS NOT NULL AND r.testDataFlag.isTestData = true " +
           "AND r.objectType IN ('DOMAIN', 'IP_NETWORK', 'AUTNUM') " +
           "ORDER BY r.objectType, r.handle")
    List<RdapEntity> findFlaggedTestData();
}