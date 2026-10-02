/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.config;

import com.jaddar.dataholder.entity.RdapEntity;
import com.jaddar.dataholder.entity.RdapEntity.ObjectType;
import com.jaddar.dataholder.repository.RdapEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the rdap_entities table with sample data on startup.
 * Only runs if the table is empty.
 * 
 * To disable: add @Profile("!prod") or remove this class
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class RdapDataSeeder {

    private final RdapEntityRepository entityRepository;

    @Bean
    @Profile("!test") // Don't run during tests
    public CommandLineRunner seedRdapData() {
        return args -> {
            long existingCount = entityRepository.count();
            if (existingCount > 0) {
                log.info("RdapEntity table already has {} records, skipping seed", existingCount);
                return;
            }

            log.info("Seeding RdapEntity table with sample data...");

            List<RdapEntity> entities = new ArrayList<>();

            // Domains
            entities.add(createDomain("DOM-EXAMPLE-COM", "example.com"));
            entities.add(createDomain("DOM-TEST-COM", "test.com"));
            entities.add(createDomain("DOM-SAMPLE-ORG", "sample.org"));
            entities.add(createDomain("DOM-DEMO-NET", "demo.net"));
            entities.add(createDomain("DOM-MYSITE-IO", "mysite.io"));

            // IP Networks
            entities.add(createIpNetwork("NET-192-0-2-0", "192.0.2.0", "192.0.2.255", "v4", "TEST-NET-1", "US"));
            entities.add(createIpNetwork("NET-198-51-100-0", "198.51.100.0", "198.51.100.255", "v4", "TEST-NET-2", "US"));
            entities.add(createIpNetwork("NET-203-0-113-0", "203.0.113.0", "203.0.113.255", "v4", "TEST-NET-3", "US"));
            entities.add(createIpNetwork("NET-2001-DB8", "2001:db8::", "2001:db8:ffff:ffff:ffff:ffff:ffff:ffff", "v6", "DOC-IPV6", "US"));

            // ASNs
            entities.add(createAsn("AS64496", 64496L, 64496L, "EXAMPLE-AS-1", "US"));
            entities.add(createAsn("AS64497", 64497L, 64497L, "EXAMPLE-AS-2", "US"));
            entities.add(createAsn("AS64498", 64498L, 64500L, "EXAMPLE-AS-RANGE", "GB"));
            entities.add(createAsn("AS65536", 65536L, 65536L, "PRIVATE-AS", "DE"));

            entityRepository.saveAll(entities);

            log.info("Seeded {} RdapEntity records", entities.size());
            log.info("  - Domains: {}", entities.stream().filter(e -> e.getObjectType() == ObjectType.DOMAIN).count());
            log.info("  - IP Networks: {}", entities.stream().filter(e -> e.getObjectType() == ObjectType.IP_NETWORK).count());
            log.info("  - ASNs: {}", entities.stream().filter(e -> e.getObjectType() == ObjectType.AUTNUM).count());
        };
    }

    private RdapEntity createDomain(String handle, String ldhName) {
        RdapEntity entity = new RdapEntity();
        entity.setObjectType(ObjectType.DOMAIN);
        entity.setObjectClassName("domain");
        entity.setHandle(handle);
        entity.setLdhName(ldhName);
        entity.setStatus(new ArrayList<>(List.of("active")));
        return entity;
    }

    private RdapEntity createIpNetwork(String handle, String startAddress, String endAddress, 
                                        String ipVersion, String networkName, String country) {
        RdapEntity entity = new RdapEntity();
        entity.setObjectType(ObjectType.IP_NETWORK);
        entity.setObjectClassName("ip network");
        entity.setHandle(handle);
        entity.setStartAddress(startAddress);
        entity.setEndAddress(endAddress);
        entity.setIpVersion(ipVersion);
        entity.setNetworkName(networkName);
        entity.setCountry(country);
        entity.setStatus(new ArrayList<>(List.of("active")));
        return entity;
    }

    private RdapEntity createAsn(String handle, Long startAutnum, Long endAutnum, 
                                  String autnumName, String country) {
        RdapEntity entity = new RdapEntity();
        entity.setObjectType(ObjectType.AUTNUM);
        entity.setObjectClassName("autnum");
        entity.setHandle(handle);
        entity.setStartAutnum(startAutnum);
        entity.setEndAutnum(endAutnum);
        entity.setAutnumName(autnumName);
        entity.setCountry(country);
        entity.setStatus(new ArrayList<>(List.of("active")));
        return entity;
    }
}