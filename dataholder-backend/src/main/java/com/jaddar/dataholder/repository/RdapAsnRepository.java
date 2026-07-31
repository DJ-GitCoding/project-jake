/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.RdapAsn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RdapAsnRepository extends JpaRepository<RdapAsn, Long> {
    
    boolean existsById(Long id);
    
    Optional<RdapAsn> findByHandle(String handle);
    
    /**
     * Find ASN by exact autnum value (checks if asn falls within start-end range)
     */
    @Query("SELECT r FROM RdapAsn r WHERE :asn BETWEEN r.startAutnum AND r.endAutnum")
    Optional<RdapAsn> findByAutnum(@Param("asn") Integer asn);
    
    /**
     * Find ASN by exact autnum value (Long version for service compatibility)
     */
    @Query("SELECT r FROM RdapAsn r WHERE :asn BETWEEN r.startAutnum AND r.endAutnum")
    Optional<RdapAsn> findByAsn(@Param("asn") Long asn);
    
    /**
     * Find by start autnum
     */
    Optional<RdapAsn> findByStartAutnum(Integer startAutnum);
    
    /**
     * Search by handle (partial match)
     */
    @Query("SELECT r FROM RdapAsn r WHERE LOWER(r.handle) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<RdapAsn> searchByHandle(@Param("search") String search, Pageable pageable);
    
    /**
     * Search by handle or name
     */
    @Query("SELECT r FROM RdapAsn r WHERE LOWER(r.handle) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<RdapAsn> searchByHandleOrName(@Param("search") String search, Pageable pageable);
}