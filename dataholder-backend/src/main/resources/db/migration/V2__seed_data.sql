-- V2__seed_data.sql
-- Consolidated seed data: domains, agreements, templates, request types, policies, redaction rules

-- =====================================================
-- Domain 1: testdomain1.example
-- =====================================================
INSERT INTO rdap_domains (ldh_name, handle, status, registration_date, last_changed_date, expiration_date, nameservers, dnssec_enabled,
    rdap_data_level_0, rdap_data_level_1, rdap_data_level_2, rdap_data_level_3)
VALUES (
    'testdomain1.example',
    'D1234567-EX',
    ARRAY['active', 'client transfer prohibited'],
    '2020-01-15 10:30:00',
    '2024-06-20 14:45:00',
    '2025-01-15 10:30:00',
    '["ns1.testdomain1.example", "ns2.testdomain1.example"]',
    true,
    -- Level 0: Basic info only
    '{"objectClassName": "domain", "ldhName": "testdomain1.example", "handle": "D1234567-EX", "status": ["active"]}',
    -- Level 1: + Dates, nameservers
    '{"objectClassName": "domain", "ldhName": "testdomain1.example", "handle": "D1234567-EX", "status": ["active", "client transfer prohibited"], "events": [{"eventAction": "registration", "eventDate": "2020-01-15T10:30:00Z"}, {"eventAction": "last changed", "eventDate": "2024-06-20T14:45:00Z"}, {"eventAction": "expiration", "eventDate": "2025-01-15T10:30:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.testdomain1.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.testdomain1.example"}]}',
    -- Level 2: + Registrar info
    '{"objectClassName": "domain", "ldhName": "testdomain1.example", "handle": "D1234567-EX", "status": ["active", "client transfer prohibited"], "events": [{"eventAction": "registration", "eventDate": "2020-01-15T10:30:00Z"}, {"eventAction": "last changed", "eventDate": "2024-06-20T14:45:00Z"}, {"eventAction": "expiration", "eventDate": "2025-01-15T10:30:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.testdomain1.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.testdomain1.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-001", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Example Country Domain Registrar Inc."]]]}], "secureDNS": {"delegationSigned": true}}',
    -- Level 3: Full data with registrant PII
    '{"objectClassName": "domain", "ldhName": "testdomain1.example", "handle": "D1234567-EX", "status": ["active", "client transfer prohibited"], "events": [{"eventAction": "registration", "eventDate": "2020-01-15T10:30:00Z"}, {"eventAction": "last changed", "eventDate": "2024-06-20T14:45:00Z"}, {"eventAction": "expiration", "eventDate": "2025-01-15T10:30:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.testdomain1.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.testdomain1.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-001", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Example Country Domain Registrar Inc."]]]}, {"objectClassName": "entity", "handle": "CONT-001", "roles": ["registrant"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "John Smith"], ["org", {}, "text", "Test Company Ltd."], ["email", {}, "text", "john.smith@testcompany.example"], ["tel", {}, "text", "+1-555-12345678"], ["adr", {}, "text", ["", "", "123 Test Street", "Example City", "Example Country", "100", "XX"]]]]}, {"objectClassName": "entity", "handle": "TECH-001", "roles": ["technical"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Tech Support"], ["email", {}, "text", "tech@testcompany.example"]]]}], "secureDNS": {"delegationSigned": true}}'
);

-- =====================================================
-- Domain 2: example-corp.example
-- =====================================================
INSERT INTO rdap_domains (ldh_name, handle, status, registration_date, last_changed_date, expiration_date, nameservers, dnssec_enabled,
    rdap_data_level_0, rdap_data_level_1, rdap_data_level_2, rdap_data_level_3)
VALUES (
    'example-corp.example',
    'D2345678-EX',
    ARRAY['active'],
    '2019-03-22 09:15:00',
    '2024-03-22 11:30:00',
    '2025-03-22 09:15:00',
    '["dns1.example-corp.example", "dns2.example-corp.example"]',
    false,
    '{"objectClassName": "domain", "ldhName": "example-corp.example", "handle": "D2345678-EX", "status": ["active"]}',
    '{"objectClassName": "domain", "ldhName": "example-corp.example", "handle": "D2345678-EX", "status": ["active"], "events": [{"eventAction": "registration", "eventDate": "2019-03-22T09:15:00Z"}, {"eventAction": "expiration", "eventDate": "2025-03-22T09:15:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "dns1.example-corp.example"}, {"objectClassName": "nameserver", "ldhName": "dns2.example-corp.example"}]}',
    '{"objectClassName": "domain", "ldhName": "example-corp.example", "handle": "D2345678-EX", "status": ["active"], "events": [{"eventAction": "registration", "eventDate": "2019-03-22T09:15:00Z"}, {"eventAction": "expiration", "eventDate": "2025-03-22T09:15:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "dns1.example-corp.example"}, {"objectClassName": "nameserver", "ldhName": "dns2.example-corp.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-002", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Example Accredited Registrar"]]]}]}',
    '{"objectClassName": "domain", "ldhName": "example-corp.example", "handle": "D2345678-EX", "status": ["active"], "events": [{"eventAction": "registration", "eventDate": "2019-03-22T09:15:00Z"}, {"eventAction": "expiration", "eventDate": "2025-03-22T09:15:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "dns1.example-corp.example"}, {"objectClassName": "nameserver", "ldhName": "dns2.example-corp.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-002", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Example Accredited Registrar"]]]}, {"objectClassName": "entity", "handle": "CONT-002", "roles": ["registrant"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Example Corporation"], ["org", {}, "text", "Example Corporation"], ["email", {}, "text", "admin@example-corp.example"], ["tel", {}, "text", "+1-555-87654321"], ["adr", {}, "text", ["", "", "456 Business Ave", "Example Town", "Example Country", "800", "XX"]]]]}]}'
);

-- =====================================================
-- Domain 3: secure-bank.example
-- =====================================================
INSERT INTO rdap_domains (ldh_name, handle, status, registration_date, last_changed_date, expiration_date, nameservers, dnssec_enabled,
    rdap_data_level_0, rdap_data_level_1, rdap_data_level_2, rdap_data_level_3)
VALUES (
    'secure-bank.example',
    'D3456789-EX',
    ARRAY['active', 'server delete prohibited', 'server transfer prohibited', 'server update prohibited'],
    '2015-07-10 08:00:00',
    '2024-07-10 16:20:00',
    '2026-07-10 08:00:00',
    '["ns1.secure-bank.example", "ns2.secure-bank.example", "ns3.secure-bank.example"]',
    true,
    '{"objectClassName": "domain", "ldhName": "secure-bank.example", "handle": "D3456789-EX", "status": ["active", "server delete prohibited"]}',
    '{"objectClassName": "domain", "ldhName": "secure-bank.example", "handle": "D3456789-EX", "status": ["active", "server delete prohibited", "server transfer prohibited", "server update prohibited"], "events": [{"eventAction": "registration", "eventDate": "2015-07-10T08:00:00Z"}, {"eventAction": "expiration", "eventDate": "2026-07-10T08:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.secure-bank.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.secure-bank.example"}, {"objectClassName": "nameserver", "ldhName": "ns3.secure-bank.example"}], "secureDNS": {"delegationSigned": true}}',
    '{"objectClassName": "domain", "ldhName": "secure-bank.example", "handle": "D3456789-EX", "status": ["active", "server delete prohibited", "server transfer prohibited", "server update prohibited"], "events": [{"eventAction": "registration", "eventDate": "2015-07-10T08:00:00Z"}, {"eventAction": "expiration", "eventDate": "2026-07-10T08:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.secure-bank.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.secure-bank.example"}, {"objectClassName": "nameserver", "ldhName": "ns3.secure-bank.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-003", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Premium Domain Services"]]]}], "secureDNS": {"delegationSigned": true}}',
    '{"objectClassName": "domain", "ldhName": "secure-bank.example", "handle": "D3456789-EX", "status": ["active", "server delete prohibited", "server transfer prohibited", "server update prohibited"], "events": [{"eventAction": "registration", "eventDate": "2015-07-10T08:00:00Z"}, {"eventAction": "expiration", "eventDate": "2026-07-10T08:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.secure-bank.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.secure-bank.example"}, {"objectClassName": "nameserver", "ldhName": "ns3.secure-bank.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-003", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Premium Domain Services"]]]}, {"objectClassName": "entity", "handle": "CONT-003", "roles": ["registrant"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Example Country Secure Bank"], ["org", {}, "text", "Example Country Secure Bank Co., Ltd."], ["email", {}, "text", "it-security@secure-bank.example"], ["tel", {}, "text", "+1-555-55555555"], ["adr", {}, "text", ["", "", "789 Finance Tower", "Example City", "Example Country", "110", "XX"]]]]}], "secureDNS": {"delegationSigned": true}}'
);

-- =====================================================
-- Domain 4: tech-startup.example
-- =====================================================
INSERT INTO rdap_domains (ldh_name, handle, status, registration_date, last_changed_date, expiration_date, nameservers, dnssec_enabled,
    rdap_data_level_0, rdap_data_level_1, rdap_data_level_2, rdap_data_level_3)
VALUES (
    'tech-startup.example',
    'D4567890-EX',
    ARRAY['active'],
    '2023-01-05 14:00:00',
    '2024-01-05 10:00:00',
    '2025-01-05 14:00:00',
    '["ns1.cloudflare.com", "ns2.cloudflare.com"]',
    false,
    '{"objectClassName": "domain", "ldhName": "tech-startup.example", "handle": "D4567890-EX", "status": ["active"]}',
    '{"objectClassName": "domain", "ldhName": "tech-startup.example", "handle": "D4567890-EX", "status": ["active"], "events": [{"eventAction": "registration", "eventDate": "2023-01-05T14:00:00Z"}, {"eventAction": "expiration", "eventDate": "2025-01-05T14:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.cloudflare.com"}, {"objectClassName": "nameserver", "ldhName": "ns2.cloudflare.com"}]}',
    '{"objectClassName": "domain", "ldhName": "tech-startup.example", "handle": "D4567890-EX", "status": ["active"], "events": [{"eventAction": "registration", "eventDate": "2023-01-05T14:00:00Z"}, {"eventAction": "expiration", "eventDate": "2025-01-05T14:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.cloudflare.com"}, {"objectClassName": "nameserver", "ldhName": "ns2.cloudflare.com"}], "entities": [{"objectClassName": "entity", "handle": "REG-004", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Domain Registration Co."]]]}]}',
    '{"objectClassName": "domain", "ldhName": "tech-startup.example", "handle": "D4567890-EX", "status": ["active"], "events": [{"eventAction": "registration", "eventDate": "2023-01-05T14:00:00Z"}, {"eventAction": "expiration", "eventDate": "2025-01-05T14:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.cloudflare.com"}, {"objectClassName": "nameserver", "ldhName": "ns2.cloudflare.com"}], "entities": [{"objectClassName": "entity", "handle": "REG-004", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Domain Registration Co."]]]}, {"objectClassName": "entity", "handle": "CONT-004", "roles": ["registrant"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Alice Chen"], ["org", {}, "text", "Tech Startup Inc."], ["email", {}, "text", "alice@tech-startup.example"], ["tel", {}, "text", "+1-555-11112222"]]]}]}'
);

-- =====================================================
-- Domain 5: government-agency.gov.example
-- =====================================================
INSERT INTO rdap_domains (ldh_name, handle, status, registration_date, last_changed_date, expiration_date, nameservers, dnssec_enabled,
    rdap_data_level_0, rdap_data_level_1, rdap_data_level_2, rdap_data_level_3)
VALUES (
    'government-agency.gov.example',
    'D5678901-EX',
    ARRAY['active', 'server delete prohibited', 'server transfer prohibited'],
    '2010-05-20 00:00:00',
    '2024-05-20 09:00:00',
    '2030-05-20 00:00:00',
    '["ns1.gov.example", "ns2.gov.example"]',
    true,
    '{"objectClassName": "domain", "ldhName": "government-agency.gov.example", "handle": "D5678901-EX", "status": ["active"]}',
    '{"objectClassName": "domain", "ldhName": "government-agency.gov.example", "handle": "D5678901-EX", "status": ["active", "server delete prohibited", "server transfer prohibited"], "events": [{"eventAction": "registration", "eventDate": "2010-05-20T00:00:00Z"}, {"eventAction": "expiration", "eventDate": "2030-05-20T00:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.gov.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.gov.example"}], "secureDNS": {"delegationSigned": true}}',
    '{"objectClassName": "domain", "ldhName": "government-agency.gov.example", "handle": "D5678901-EX", "status": ["active", "server delete prohibited", "server transfer prohibited"], "events": [{"eventAction": "registration", "eventDate": "2010-05-20T00:00:00Z"}, {"eventAction": "expiration", "eventDate": "2030-05-20T00:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.gov.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.gov.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-GOV", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Example Country Government Domain Registry"]]]}], "secureDNS": {"delegationSigned": true}}',
    '{"objectClassName": "domain", "ldhName": "government-agency.gov.example", "handle": "D5678901-EX", "status": ["active", "server delete prohibited", "server transfer prohibited"], "events": [{"eventAction": "registration", "eventDate": "2010-05-20T00:00:00Z"}, {"eventAction": "expiration", "eventDate": "2030-05-20T00:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.gov.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.gov.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-GOV", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Example Country Government Domain Registry"]]]}, {"objectClassName": "entity", "handle": "CONT-GOV", "roles": ["registrant"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Example Country Government Agency"], ["org", {}, "text", "Ministry of Digital Affairs"], ["email", {}, "text", "contact@government-agency.gov.example"], ["tel", {}, "text", "+1-555-33334444"], ["adr", {}, "text", ["", "", "1 Government Plaza", "Example City", "Example Country", "100", "XX"]]]]}], "secureDNS": {"delegationSigned": true}}'
);

-- =====================================================
-- Domain 6: ecommerce-shop.example
-- =====================================================
INSERT INTO rdap_domains (ldh_name, handle, status, registration_date, last_changed_date, expiration_date, nameservers, dnssec_enabled,
    rdap_data_level_0, rdap_data_level_1, rdap_data_level_2, rdap_data_level_3)
VALUES (
    'ecommerce-shop.example',
    'D6789012-EX',
    ARRAY['active', 'client transfer prohibited'],
    '2021-11-11 11:11:00',
    '2024-11-11 11:11:00',
    '2025-11-11 11:11:00',
    '["ns1.ecommerce-shop.example", "ns2.ecommerce-shop.example"]',
    false,
    '{"objectClassName": "domain", "ldhName": "ecommerce-shop.example", "handle": "D6789012-EX", "status": ["active"]}',
    '{"objectClassName": "domain", "ldhName": "ecommerce-shop.example", "handle": "D6789012-EX", "status": ["active", "client transfer prohibited"], "events": [{"eventAction": "registration", "eventDate": "2021-11-11T11:11:00Z"}, {"eventAction": "expiration", "eventDate": "2025-11-11T11:11:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.ecommerce-shop.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.ecommerce-shop.example"}]}',
    '{"objectClassName": "domain", "ldhName": "ecommerce-shop.example", "handle": "D6789012-EX", "status": ["active", "client transfer prohibited"], "events": [{"eventAction": "registration", "eventDate": "2021-11-11T11:11:00Z"}, {"eventAction": "expiration", "eventDate": "2025-11-11T11:11:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.ecommerce-shop.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.ecommerce-shop.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-005", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "E-Business Domains Ltd."]]]}]}',
    '{"objectClassName": "domain", "ldhName": "ecommerce-shop.example", "handle": "D6789012-EX", "status": ["active", "client transfer prohibited"], "events": [{"eventAction": "registration", "eventDate": "2021-11-11T11:11:00Z"}, {"eventAction": "expiration", "eventDate": "2025-11-11T11:11:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.ecommerce-shop.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.ecommerce-shop.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-005", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "E-Business Domains Ltd."]]]}, {"objectClassName": "entity", "handle": "CONT-005", "roles": ["registrant"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Bob Wang"], ["org", {}, "text", "E-Commerce Shop Co."], ["email", {}, "text", "bob@ecommerce-shop.example"], ["tel", {}, "text", "+1-555-99998888"], ["adr", {}, "text", ["", "", "888 Shopping Street", "Taichung", "Example Country", "400", "XX"]]]]}]}'
);

-- =====================================================
-- Domain 7: university-edu.edu.example
-- =====================================================
INSERT INTO rdap_domains (ldh_name, handle, status, registration_date, last_changed_date, expiration_date, nameservers, dnssec_enabled,
    rdap_data_level_0, rdap_data_level_1, rdap_data_level_2, rdap_data_level_3)
VALUES (
    'university-edu.edu.example',
    'D7890123-EX',
    ARRAY['active'],
    '2005-09-01 00:00:00',
    '2024-09-01 08:00:00',
    '2028-09-01 00:00:00',
    '["dns1.university-edu.edu.example", "dns2.university-edu.edu.example"]',
    true,
    '{"objectClassName": "domain", "ldhName": "university-edu.edu.example", "handle": "D7890123-EX", "status": ["active"]}',
    '{"objectClassName": "domain", "ldhName": "university-edu.edu.example", "handle": "D7890123-EX", "status": ["active"], "events": [{"eventAction": "registration", "eventDate": "2005-09-01T00:00:00Z"}, {"eventAction": "expiration", "eventDate": "2028-09-01T00:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "dns1.university-edu.edu.example"}, {"objectClassName": "nameserver", "ldhName": "dns2.university-edu.edu.example"}], "secureDNS": {"delegationSigned": true}}',
    '{"objectClassName": "domain", "ldhName": "university-edu.edu.example", "handle": "D7890123-EX", "status": ["active"], "events": [{"eventAction": "registration", "eventDate": "2005-09-01T00:00:00Z"}, {"eventAction": "expiration", "eventDate": "2028-09-01T00:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "dns1.university-edu.edu.example"}, {"objectClassName": "nameserver", "ldhName": "dns2.university-edu.edu.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-EDU", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Example Country Academic Network"]]]}], "secureDNS": {"delegationSigned": true}}',
    '{"objectClassName": "domain", "ldhName": "university-edu.edu.example", "handle": "D7890123-EX", "status": ["active"], "events": [{"eventAction": "registration", "eventDate": "2005-09-01T00:00:00Z"}, {"eventAction": "expiration", "eventDate": "2028-09-01T00:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "dns1.university-edu.edu.example"}, {"objectClassName": "nameserver", "ldhName": "dns2.university-edu.edu.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-EDU", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Example Country Academic Network"]]]}, {"objectClassName": "entity", "handle": "CONT-EDU", "roles": ["registrant"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Example Country National University"], ["org", {}, "text", "Example Country National University"], ["email", {}, "text", "admin@university-edu.edu.example"], ["tel", {}, "text", "+1-555-77778888"], ["adr", {}, "text", ["", "", "1 University Road", "Example City", "Example Country", "106", "XX"]]]]}], "secureDNS": {"delegationSigned": true}}'
);

-- =====================================================
-- Domain 8: media-news.example
-- =====================================================
INSERT INTO rdap_domains (ldh_name, handle, status, registration_date, last_changed_date, expiration_date, nameservers, dnssec_enabled,
    rdap_data_level_0, rdap_data_level_1, rdap_data_level_2, rdap_data_level_3)
VALUES (
    'media-news.example',
    'D8901234-EX',
    ARRAY['active'],
    '2018-06-15 12:00:00',
    '2024-06-15 15:30:00',
    '2025-06-15 12:00:00',
    '["ns1.media-news.example", "ns2.media-news.example"]',
    false,
    '{"objectClassName": "domain", "ldhName": "media-news.example", "handle": "D8901234-EX", "status": ["active"]}',
    '{"objectClassName": "domain", "ldhName": "media-news.example", "handle": "D8901234-EX", "status": ["active"], "events": [{"eventAction": "registration", "eventDate": "2018-06-15T12:00:00Z"}, {"eventAction": "expiration", "eventDate": "2025-06-15T12:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.media-news.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.media-news.example"}]}',
    '{"objectClassName": "domain", "ldhName": "media-news.example", "handle": "D8901234-EX", "status": ["active"], "events": [{"eventAction": "registration", "eventDate": "2018-06-15T12:00:00Z"}, {"eventAction": "expiration", "eventDate": "2025-06-15T12:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.media-news.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.media-news.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-006", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Media Domain Services"]]]}]}',
    '{"objectClassName": "domain", "ldhName": "media-news.example", "handle": "D8901234-EX", "status": ["active"], "events": [{"eventAction": "registration", "eventDate": "2018-06-15T12:00:00Z"}, {"eventAction": "expiration", "eventDate": "2025-06-15T12:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.media-news.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.media-news.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-006", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Media Domain Services"]]]}, {"objectClassName": "entity", "handle": "CONT-006", "roles": ["registrant"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Example Country Media Group"], ["org", {}, "text", "Example Country Media News Corp."], ["email", {}, "text", "editor@media-news.example"], ["tel", {}, "text", "+1-555-66665555"]]]}]}'
);

-- =====================================================
-- Domain 9: healthcare-clinic.example
-- =====================================================
INSERT INTO rdap_domains (ldh_name, handle, status, registration_date, last_changed_date, expiration_date, nameservers, dnssec_enabled,
    rdap_data_level_0, rdap_data_level_1, rdap_data_level_2, rdap_data_level_3)
VALUES (
    'healthcare-clinic.example',
    'D9012345-EX',
    ARRAY['active', 'client delete prohibited'],
    '2022-02-28 10:00:00',
    '2024-02-28 14:00:00',
    '2026-02-28 10:00:00',
    '["ns1.healthcare-clinic.example", "ns2.healthcare-clinic.example"]',
    true,
    '{"objectClassName": "domain", "ldhName": "healthcare-clinic.example", "handle": "D9012345-EX", "status": ["active"]}',
    '{"objectClassName": "domain", "ldhName": "healthcare-clinic.example", "handle": "D9012345-EX", "status": ["active", "client delete prohibited"], "events": [{"eventAction": "registration", "eventDate": "2022-02-28T10:00:00Z"}, {"eventAction": "expiration", "eventDate": "2026-02-28T10:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.healthcare-clinic.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.healthcare-clinic.example"}], "secureDNS": {"delegationSigned": true}}',
    '{"objectClassName": "domain", "ldhName": "healthcare-clinic.example", "handle": "D9012345-EX", "status": ["active", "client delete prohibited"], "events": [{"eventAction": "registration", "eventDate": "2022-02-28T10:00:00Z"}, {"eventAction": "expiration", "eventDate": "2026-02-28T10:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.healthcare-clinic.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.healthcare-clinic.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-007", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Healthcare Domains Inc."]]]}], "secureDNS": {"delegationSigned": true}}',
    '{"objectClassName": "domain", "ldhName": "healthcare-clinic.example", "handle": "D9012345-EX", "status": ["active", "client delete prohibited"], "events": [{"eventAction": "registration", "eventDate": "2022-02-28T10:00:00Z"}, {"eventAction": "expiration", "eventDate": "2026-02-28T10:00:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.healthcare-clinic.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.healthcare-clinic.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-007", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Healthcare Domains Inc."]]]}, {"objectClassName": "entity", "handle": "CONT-007", "roles": ["registrant"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Dr. Mary Lin"], ["org", {}, "text", "Healthcare Clinic"], ["email", {}, "text", "admin@healthcare-clinic.example"], ["tel", {}, "text", "+1-555-22223333"], ["adr", {}, "text", ["", "", "100 Health Street", "Taichung", "Example Country", "403", "XX"]]]]}], "secureDNS": {"delegationSigned": true}}'
);

-- =====================================================
-- Domain 10: gaming-platform.example
-- =====================================================
INSERT INTO rdap_domains (ldh_name, handle, status, registration_date, last_changed_date, expiration_date, nameservers, dnssec_enabled,
    rdap_data_level_0, rdap_data_level_1, rdap_data_level_2, rdap_data_level_3)
VALUES (
    'gaming-platform.example',
    'D0123456-EX',
    ARRAY['active'],
    '2023-08-08 08:08:00',
    '2024-08-08 08:08:00',
    '2025-08-08 08:08:00',
    '["ns1.gaming-platform.example", "ns2.gaming-platform.example"]',
    false,
    '{"objectClassName": "domain", "ldhName": "gaming-platform.example", "handle": "D0123456-EX", "status": ["active"]}',
    '{"objectClassName": "domain", "ldhName": "gaming-platform.example", "handle": "D0123456-EX", "status": ["active"], "events": [{"eventAction": "registration", "eventDate": "2023-08-08T08:08:00Z"}, {"eventAction": "expiration", "eventDate": "2025-08-08T08:08:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.gaming-platform.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.gaming-platform.example"}]}',
    '{"objectClassName": "domain", "ldhName": "gaming-platform.example", "handle": "D0123456-EX", "status": ["active"], "events": [{"eventAction": "registration", "eventDate": "2023-08-08T08:08:00Z"}, {"eventAction": "expiration", "eventDate": "2025-08-08T08:08:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.gaming-platform.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.gaming-platform.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-008", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Digital Entertainment Domains"]]]}]}',
    '{"objectClassName": "domain", "ldhName": "gaming-platform.example", "handle": "D0123456-EX", "status": ["active"], "events": [{"eventAction": "registration", "eventDate": "2023-08-08T08:08:00Z"}, {"eventAction": "expiration", "eventDate": "2025-08-08T08:08:00Z"}], "nameservers": [{"objectClassName": "nameserver", "ldhName": "ns1.gaming-platform.example"}, {"objectClassName": "nameserver", "ldhName": "ns2.gaming-platform.example"}], "entities": [{"objectClassName": "entity", "handle": "REG-008", "roles": ["registrar"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Digital Entertainment Domains"]]]}, {"objectClassName": "entity", "handle": "CONT-008", "roles": ["registrant"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "GameMaster Inc."], ["org", {}, "text", "Gaming Platform Co., Ltd."], ["email", {}, "text", "support@gaming-platform.example"], ["tel", {}, "text", "+1-555-88889999"]]]}]}'
);

-- =====================================================
-- IP Data - Sample entries
-- =====================================================
INSERT INTO rdap_ips (handle, start_address, end_address, ip_version, name, country, registration_date, last_changed_date,
    rdap_data_level_0, rdap_data_level_1, rdap_data_level_2, rdap_data_level_3)
VALUES (
    'NET-192-168-1-0',
    '192.168.1.0',
    '192.168.1.255',
    'v4',
    'TEST-NET-1',
    'TW',
    '2020-01-01 00:00:00',
    '2024-01-01 00:00:00',
    '{"objectClassName": "ip network", "handle": "NET-192-168-1-0", "startAddress": "192.168.1.0", "endAddress": "192.168.1.255", "ipVersion": "v4"}',
    '{"objectClassName": "ip network", "handle": "NET-192-168-1-0", "startAddress": "192.168.1.0", "endAddress": "192.168.1.255", "ipVersion": "v4", "name": "TEST-NET-1", "country": "XX", "events": [{"eventAction": "registration", "eventDate": "2020-01-01T00:00:00Z"}]}',
    '{"objectClassName": "ip network", "handle": "NET-192-168-1-0", "startAddress": "192.168.1.0", "endAddress": "192.168.1.255", "ipVersion": "v4", "name": "TEST-NET-1", "country": "XX", "events": [{"eventAction": "registration", "eventDate": "2020-01-01T00:00:00Z"}], "entities": [{"objectClassName": "entity", "handle": "ORG-TEST", "roles": ["registrant"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Test Organization"]]]}]}',
    '{"objectClassName": "ip network", "handle": "NET-192-168-1-0", "startAddress": "192.168.1.0", "endAddress": "192.168.1.255", "ipVersion": "v4", "name": "TEST-NET-1", "country": "XX", "events": [{"eventAction": "registration", "eventDate": "2020-01-01T00:00:00Z"}], "entities": [{"objectClassName": "entity", "handle": "ORG-TEST", "roles": ["registrant"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Test Organization"], ["org", {}, "text", "Test Org Ltd."], ["email", {}, "text", "netadmin@test.example"], ["tel", {}, "text", "+1-555-12340000"]]]}]}'
);

-- =====================================================
-- ASN Data - Sample entries
-- =====================================================
INSERT INTO rdap_asns (handle, start_autnum, end_autnum, name, country, registration_date, last_changed_date,
    rdap_data_level_0, rdap_data_level_1, rdap_data_level_2, rdap_data_level_3)
VALUES (
    'AS12345',
    12345,
    12345,
    'TEST-ASN',
    'TW',
    '2018-05-15 00:00:00',
    '2024-05-15 00:00:00',
    '{"objectClassName": "autnum", "handle": "AS12345", "startAutnum": 12345, "endAutnum": 12345}',
    '{"objectClassName": "autnum", "handle": "AS12345", "startAutnum": 12345, "endAutnum": 12345, "name": "TEST-ASN", "country": "XX", "events": [{"eventAction": "registration", "eventDate": "2018-05-15T00:00:00Z"}]}',
    '{"objectClassName": "autnum", "handle": "AS12345", "startAutnum": 12345, "endAutnum": 12345, "name": "TEST-ASN", "country": "XX", "events": [{"eventAction": "registration", "eventDate": "2018-05-15T00:00:00Z"}], "entities": [{"objectClassName": "entity", "handle": "ORG-ASN", "roles": ["registrant"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "ASN Test Organization"]]]}]}',
    '{"objectClassName": "autnum", "handle": "AS12345", "startAutnum": 12345, "endAutnum": 12345, "name": "TEST-ASN", "country": "XX", "events": [{"eventAction": "registration", "eventDate": "2018-05-15T00:00:00Z"}], "entities": [{"objectClassName": "entity", "handle": "ORG-ASN", "roles": ["registrant"], "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "ASN Test Organization"], ["org", {}, "text", "Test Network Provider"], ["email", {}, "text", "noc@testasn.example"], ["tel", {}, "text", "+1-555-55556666"]]]}]}'
);

-- =====================================================
-- Default Access Policy
-- =====================================================
INSERT INTO access_policies (name, description, default_access_level, requires_manual_verification, manual_verification_threshold, requires_agreement)
VALUES ('Default Policy', 'Default access policy for all requests', 0, false, 3, true);

-- =====================================================
-- Pending Request Controls
-- =====================================================
INSERT INTO pending_request_controls (enabled, wait_time_ms, default_response, denial_reason, admin_notes) VALUES
(true, 0, 'PENDING', NULL, 'Default control - requires manual review'),
(true, 0, 'APPROVED', NULL, 'Auto-approve control for trusted domains'),
(true, 0, 'DENIED', 'Access restricted for this record', 'Auto-deny control for restricted records'),
(true, 5000, 'APPROVED', NULL, 'Slow response control - 5 second delay for testing'),
(true, 10000, 'PENDING', NULL, 'Very slow response - 10 second delay');

-- =====================================================
-- Test Data Flags
-- =====================================================
INSERT INTO test_data_flags (is_test_data, description, test_label) VALUES
(false, 'Real/Production data', 'production'),
(true, 'General test data', 'test'),
(true, 'Integration test data', 'integration-test'),
(true, 'Load test data', 'load-test'),
(true, 'Demo/Sample data', 'demo');

-- =====================================================
-- Agreements (standalone)
-- =====================================================
INSERT INTO agreements (external_id, name, description, access_level, is_active, created_by) VALUES
('JADDAR-Public-Access', 'JADDAR Public Access Agreement', 'Basic authenticated access for general users. Provides Level 1 access to public RDAP information plus basic contact details.', 1, true, 'SYSTEM'),
('JADDAR-Law-Enforcement', 'JADDAR Law Enforcement Agreement', 'Full access for verified law enforcement agencies. Provides Level 3 access including all registrant PII.', 3, true, 'SYSTEM'),
('JADDAR-Registrar-Access', 'JADDAR Registrar Access Agreement', 'Enhanced access for domain registrars and registries. Provides Level 2 access.', 2, true, 'SYSTEM'),
('JADDAR-Security-Researcher', 'JADDAR Security Researcher Agreement', 'Enhanced access for security professionals. Provides Level 2 access for threat intelligence.', 2, true, 'SYSTEM'),
('JADDAR-IP-Intelligence', 'JADDAR IP Intelligence Agreement', 'Enhanced access for network operators and IP intelligence providers. Provides Level 2 access.', 2, true, 'SYSTEM'),
('JADDAR-Admin-Full-Access', 'JADDAR Administrator Full Access Agreement', 'Full administrative access for data holder operators. Provides Level 3 access to all data.', 3, true, 'SYSTEM'),
('JADDAR-Academic-Research', 'JADDAR Academic Research Agreement', 'Basic access for academic and research purposes. Provides Level 1 access.', 1, true, 'SYSTEM'),
('JADDAR-Brand-Protection', 'JADDAR Brand Protection Agreement', 'Enhanced access for brand protection and trademark enforcement. Provides Level 2 access.', 2, true, 'SYSTEM');

-- Create default RDAP parameters for each agreement
DO $$
DECLARE
    agreement_record RECORD;
    new_param_id BIGINT;
BEGIN
    FOR agreement_record IN SELECT id FROM agreements WHERE rdap_parameters_id IS NULL
    LOOP
        INSERT INTO agreement_rdap_parameters (created_at, updated_at) VALUES (NOW(), NOW()) RETURNING id INTO new_param_id;
        UPDATE agreements SET rdap_parameters_id = new_param_id WHERE id = agreement_record.id;
    END LOOP;
END $$;

-- =====================================================
-- Agreement Templates
-- =====================================================
INSERT INTO agreement_templates (template_id, name, description, access_level, required_group_types, terms_and_conditions, requires_manual_approval, is_published) VALUES
('TPL-PUBLIC-ACCESS', 'Public Access Agreement', 'Basic access for authenticated users. Provides Level 1 access to public RDAP information plus basic contact details.', 1, NULL, 'Standard terms apply. Data may only be used for legitimate purposes.', false, true),
('TPL-LAW-ENFORCEMENT', 'Law Enforcement Access Agreement', 'Full access for verified law enforcement agencies. Provides Level 3 access including all registrant PII.', 3, 'law-enforcement,police,government', 'Restricted to verified law enforcement use only. All queries are logged and audited.', true, true),
('TPL-REGISTRAR', 'Domain Registrar Access Agreement', 'Enhanced access for ICANN-accredited registrars. Provides Level 2 access.', 2, 'registrar,registry', 'For use by ICANN-accredited registrars only.', true, true),
('TPL-SECURITY', 'Security Research Access Agreement', 'Enhanced access for security professionals. Provides Level 2 access for threat intelligence.', 2, 'security,cert,csirt', 'For legitimate security research purposes only.', true, true),
('TPL-BRAND-PROTECTION', 'Brand Protection Access Agreement', 'Enhanced access for brand protection and IP enforcement. Provides Level 2 access.', 2, 'brand-protection,legal,trademark', 'For legitimate brand protection and trademark enforcement only.', true, true)
ON CONFLICT (template_id) DO NOTHING;

-- Create default RDAP parameters for each template
DO $$
DECLARE
    template_record RECORD;
    new_param_id BIGINT;
BEGIN
    FOR template_record IN SELECT id FROM agreement_templates WHERE rdap_parameters_id IS NULL
    LOOP
        INSERT INTO agreement_rdap_parameters (created_at, updated_at) VALUES (NOW(), NOW()) RETURNING id INTO new_param_id;
        UPDATE agreement_templates SET rdap_parameters_id = new_param_id WHERE id = template_record.id;
    END LOOP;
END $$;

-- =====================================================
-- Seed Request Types for Agreement Templates
-- =====================================================

-- TPL-PUBLIC-ACCESS (access_level 1) - Standard only
INSERT INTO agreement_request_types (template_id, name, type_code, description, access_level, supports_confidential, supports_exigent, sort_order, is_active)
SELECT id, 'standard', 1, 'Standard public access request', 1, false, false, 0, true
FROM agreement_templates WHERE template_id = 'TPL-PUBLIC-ACCESS';

-- TPL-LAW-ENFORCEMENT (access_level 3) - Standard, Confidential, Exigent
INSERT INTO agreement_request_types (template_id, name, type_code, description, access_level, supports_confidential, supports_exigent, sort_order, is_active)
SELECT id, 'standard', 1, 'Standard law enforcement request', 3, false, false, 0, true
FROM agreement_templates WHERE template_id = 'TPL-LAW-ENFORCEMENT';

INSERT INTO agreement_request_types (template_id, name, type_code, description, access_level, supports_confidential, supports_exigent, sort_order, is_active)
SELECT id, 'confidential', 2, 'Confidential disclosure request', 3, true, false, 1, true
FROM agreement_templates WHERE template_id = 'TPL-LAW-ENFORCEMENT';

INSERT INTO agreement_request_types (template_id, name, type_code, description, access_level, supports_confidential, supports_exigent, sort_order, is_active)
SELECT id, 'exigent', 3, 'Exigent/emergency disclosure request', 3, false, true, 2, true
FROM agreement_templates WHERE template_id = 'TPL-LAW-ENFORCEMENT';

-- TPL-REGISTRAR (access_level 2) - Standard only
INSERT INTO agreement_request_types (template_id, name, type_code, description, access_level, supports_confidential, supports_exigent, sort_order, is_active)
SELECT id, 'standard', 1, 'Standard registrar access request', 2, false, false, 0, true
FROM agreement_templates WHERE template_id = 'TPL-REGISTRAR';

-- TPL-SECURITY (access_level 2) - Standard only
INSERT INTO agreement_request_types (template_id, name, type_code, description, access_level, supports_confidential, supports_exigent, sort_order, is_active)
SELECT id, 'standard', 1, 'Standard security research request', 2, false, false, 0, true
FROM agreement_templates WHERE template_id = 'TPL-SECURITY';

-- TPL-BRAND-PROTECTION (access_level 2) - Standard only
INSERT INTO agreement_request_types (template_id, name, type_code, description, access_level, supports_confidential, supports_exigent, sort_order, is_active)
SELECT id, 'standard', 1, 'Standard brand protection request', 2, false, false, 0, true
FROM agreement_templates WHERE template_id = 'TPL-BRAND-PROTECTION';

-- =====================================================
-- Default Policy Expression
-- =====================================================
INSERT INTO policy_expressions (name, description, default_access_level, legal_status, protected_status, is_active, is_default, priority)
SELECT 'Default Policy', 'Default policy expression for access control', 1, 'NO_DECISION', 'NO_DECISION', TRUE, TRUE, 0
WHERE NOT EXISTS (SELECT 1 FROM policy_expressions);

-- =====================================================
-- Default Policy Redaction Rules
-- =====================================================
INSERT INTO policy_redaction_rules (policy_expression_id, object_type, field_path, field_display_name, redaction_action, min_access_level, rule_order, is_enabled)
SELECT pe.id, 'ENTITY', field_info.field_path, field_info.display_name, 'REDACT', 2, field_info.rule_order, TRUE
FROM policy_expressions pe
CROSS JOIN (VALUES
    ('vcardArray.email', 'Email Address', 1),
    ('vcardArray.tel', 'Telephone', 2),
    ('vcardArray.adr', 'Physical Address', 3),
    ('vcardArray.fn', 'Full Name', 4)
) AS field_info(field_path, display_name, rule_order)
WHERE NOT EXISTS (
    SELECT 1 FROM policy_redaction_rules prr
    WHERE prr.policy_expression_id = pe.id AND prr.field_path = field_info.field_path
);

-- =====================================================
-- Default Redaction Rules
-- =====================================================
INSERT INTO redaction_rules (element_path, display_name, element_category, description, sensitivity_level, redaction_behavior, is_active, is_system_rule, sort_order) VALUES
('handle', 'Handle', 'COMMON', 'Registry object identifier', 0, 'OK', true, true, 1),
('status', 'Status', 'COMMON', 'Object status values', 0, 'OK', true, true, 2),
('port43', 'WHOIS Server', 'COMMON', 'Port 43 WHOIS server', 0, 'OK', true, true, 3),
('ldhName', 'Domain Name', 'DOMAIN', 'Domain name in LDH format', 0, 'OK', true, true, 10),
('unicodeName', 'Unicode Name', 'DOMAIN', 'Internationalized domain name', 0, 'OK', true, true, 11),
('secureDns.delegationSigned', 'DNSSEC Delegation', 'DOMAIN', 'Whether delegation is signed', 0, 'OK', true, true, 12),
('secureDns.zoneSigned', 'DNSSEC Zone', 'DOMAIN', 'Whether zone is signed', 0, 'OK', true, true, 13),
('startAddress', 'Start Address', 'IP_NETWORK', 'Starting IP address', 0, 'OK', true, true, 20),
('endAddress', 'End Address', 'IP_NETWORK', 'Ending IP address', 0, 'OK', true, true, 21),
('ipVersion', 'IP Version', 'IP_NETWORK', 'v4 or v6', 0, 'OK', true, true, 22),
('name', 'Network Name', 'IP_NETWORK', 'Network name', 0, 'OK', true, true, 23),
('type', 'Network Type', 'IP_NETWORK', 'Allocation type', 0, 'OK', true, true, 24),
('country', 'Country', 'IP_NETWORK', 'Country code', 0, 'OK', true, true, 25),
('parentHandle', 'Parent Handle', 'IP_NETWORK', 'Parent network handle', 0, 'OK', true, true, 26),
('startAutnum', 'Start AS Number', 'AUTNUM', 'Starting autonomous system number', 0, 'OK', true, true, 30),
('endAutnum', 'End AS Number', 'AUTNUM', 'Ending autonomous system number', 0, 'OK', true, true, 31),
('roles', 'Roles', 'ENTITY', 'Entity roles', 0, 'OK', true, true, 40),
('publicIds', 'Public IDs', 'ENTITY', 'Public identifiers', 0, 'OK', true, true, 41),
('entity.fn', 'Full Name', 'ENTITY', 'Contact full name', 2, 'REDACT', true, true, 42),
('entity.org', 'Organization', 'ENTITY', 'Organization name', 1, 'OK', true, true, 43),
('entity.email', 'Email', 'ENTITY', 'Email address', 2, 'REDACT', true, true, 44),
('entity.tel', 'Phone', 'ENTITY', 'Telephone number', 2, 'REDACT', true, true, 45),
('entity.fax', 'Fax', 'ENTITY', 'Fax number', 2, 'REDACT', true, true, 46),
('entity.address.street', 'Street Address', 'ENTITY', 'Street address', 2, 'REDACT', true, true, 47),
('entity.address.city', 'City', 'ENTITY', 'City', 1, 'OK', true, true, 48),
('entity.address.state', 'State/Province', 'ENTITY', 'State or province', 1, 'OK', true, true, 49),
('entity.address.postalCode', 'Postal Code', 'ENTITY', 'Postal/ZIP code', 2, 'REDACT', true, true, 50),
('entity.address.country', 'Country', 'ENTITY', 'Country', 0, 'OK', true, true, 51),
('nameserver.ldhName', 'Nameserver Name', 'NAMESERVER', 'Nameserver hostname', 0, 'OK', true, true, 60),
('nameserver.unicodeName', 'Nameserver Unicode', 'NAMESERVER', 'Internationalized nameserver name', 0, 'OK', true, true, 61),
('nameserver.ipv4', 'IPv4 Addresses', 'NAMESERVER', 'Nameserver IPv4 addresses', 0, 'OK', true, true, 62),
('nameserver.ipv6', 'IPv6 Addresses', 'NAMESERVER', 'Nameserver IPv6 addresses', 0, 'OK', true, true, 63),
('event.registration', 'Registration Date', 'EVENT', 'Registration date', 0, 'OK', true, true, 70),
('event.expiration', 'Expiration Date', 'EVENT', 'Expiration date', 0, 'OK', true, true, 71),
('event.lastChanged', 'Last Changed', 'EVENT', 'Last modification date', 0, 'OK', true, true, 72),
('event.lastUpdateOfRdap', 'Last RDAP Update', 'EVENT', 'Last RDAP database update', 0, 'OK', true, true, 73),
('event.transfer', 'Transfer Date', 'EVENT', 'Transfer date', 1, 'OK', true, true, 74),
('link.self', 'Self Link', 'LINK', 'Self-referential link', 0, 'OK', true, true, 80),
('link.related', 'Related Links', 'LINK', 'Related resource links', 0, 'OK', true, true, 81),
('remark.title', 'Remark Title', 'REMARK', 'Remark/notice title', 0, 'OK', true, true, 90),
('remark.description', 'Remark Description', 'REMARK', 'Remark/notice description', 0, 'OK', true, true, 91)
ON CONFLICT (element_path) DO NOTHING;