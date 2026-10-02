/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Service for exporting RDAP request data to Excel and PDF formats.
 * Ported from backend/services/rdap_export_service.py.
 *
 * RDAP data is rendered semantically (events, contacts, nameservers, etc.)
 * rather than as flattened key-value pairs, making it human-readable.
 *
 * Python used openpyxl (Excel) / reportlab (PDF); reimplemented here with
 * Apache POI (XSSFWorkbook) and OpenPDF (com.lowagie.text.*).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RdapExportService {

    private final ObjectMapper objectMapper;

    // ============================================================
    // RDAP Label Maps (mirror the Python module-level dicts)
    // ============================================================

    private static final Map<String, String> EVENT_LABELS = mapOf(
            "registration", "Registered",
            "reregistration", "Re-registered",
            "last changed", "Last Modified",
            "last update of RDAP database", "RDAP DB Updated",
            "expiration", "Expires",
            "deletion", "Deleted",
            "reinstantiation", "Reinstated",
            "transfer", "Transferred",
            "locked", "Locked",
            "unlocked", "Unlocked");

    private static final Map<String, String> STATUS_MAP = mapOf(
            "active", "Active",
            "inactive", "Inactive",
            "validated", "Validated",
            "renew prohibited", "Renew Prohibited",
            "update prohibited", "Update Prohibited",
            "transfer prohibited", "Transfer Prohibited",
            "delete prohibited", "Delete Prohibited",
            "proxy", "Proxy",
            "private", "Private",
            "removed", "Removed",
            "obscured", "Obscured",
            "associated", "Associated",
            "locked", "Locked",
            "pending create", "Pending Create",
            "pending renew", "Pending Renew",
            "pending transfer", "Pending Transfer",
            "pending update", "Pending Update",
            "pending delete", "Pending Delete",
            "server hold", "Server Hold",
            "client hold", "Client Hold",
            "server renew prohibited", "Server Renew Prohibited",
            "server update prohibited", "Server Update Prohibited",
            "server transfer prohibited", "Server Transfer Prohibited",
            "server delete prohibited", "Server Delete Prohibited",
            "client renew prohibited", "Client Renew Prohibited",
            "client update prohibited", "Client Update Prohibited",
            "client transfer prohibited", "Client Transfer Prohibited",
            "client delete prohibited", "Client Delete Prohibited");

    private static final Map<String, String> ROLE_MAP = mapOf(
            "registrant", "Registrant",
            "technical", "Technical Contact",
            "administrative", "Administrative Contact",
            "abuse", "Abuse Contact",
            "billing", "Billing Contact",
            "registrar", "Registrar",
            "reseller", "Reseller",
            "sponsor", "Sponsor",
            "proxy", "Proxy",
            "notifications", "Notifications",
            "noc", "NOC");

    private static final Map<Integer, String> ACCESS_MAP = Map.of(
            0, "Level 0 — Minimal",
            1, "Level 1 — Basic",
            2, "Level 2 — Standard",
            3, "Level 3 — Full");

    private static final Set<String> CATCH_ALL_SKIP = Set.of(
            "objectClassName", "ldhName", "unicodeName", "handle", "name", "type",
            "startAddress", "endAddress", "ipVersion", "country", "parentHandle",
            "startAutnum", "endAutnum", "port43", "accessLevel", "status", "events",
            "entities", "nameservers", "secureDNS", "remarks", "notices", "links",
            "rdapConformance", "lang");

    // ============================================================
    // Small value-holder mirroring Python's (value, level) tuples
    // ============================================================

    /** Mirror of Python (inner_value, required_access_level). */
    private record Unwrapped(Object value, Integer level) {
    }

    /** A label/value pair (mirror of Python's 2-tuple rows). */
    private record Pair(String label, String value) {
    }

    /** A titled section with its rows. */
    private record Section(String title, List<Pair> rows) {
    }

    // ============================================================
    // Annotated-value helpers (mirror _is_annotated/_unwrap/_fmt_with_level)
    // ============================================================

    @SuppressWarnings("unchecked")
    private static boolean isAnnotated(Object v) {
        if (!(v instanceof Map<?, ?> m)) {
            return false;
        }
        return m.containsKey("value") && m.containsKey("requiredAccessLevel") && m.size() == 2;
    }

    @SuppressWarnings("unchecked")
    private static Unwrapped unwrap(Object v) {
        if (isAnnotated(v)) {
            Map<String, Object> m = (Map<String, Object>) v;
            return new Unwrapped(m.get("value"), toInt(m.get("requiredAccessLevel")));
        }
        return new Unwrapped(v, null);
    }

    private static String fmtWithLevel(String displayVal, Integer reqLevel) {
        if (reqLevel != null) {
            return displayVal + "  [Required Access Level: " + reqLevel + "]";
        }
        return displayVal;
    }

    // ============================================================
    // String / date formatting helpers
    // ============================================================

    private static String humanize(String k) {
        if (k == null) {
            return "";
        }
        String spaced = k.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').trim();
        return titleCase(spaced);
    }

    /** Mirror of Python str.title(): title-case each word boundary. */
    private static String titleCase(String s) {
        if (s == null || s.isEmpty()) {
            return s == null ? "" : s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        boolean prevAlpha = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                sb.append(prevAlpha ? Character.toLowerCase(c) : Character.toUpperCase(c));
                prevAlpha = true;
            } else {
                sb.append(c);
                prevAlpha = false;
            }
        }
        return sb.toString();
    }

    /** Mirror of Python _tc: replace underscores with spaces, then title-case. */
    private static String tc(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return titleCase(s.replace('_', ' '));
    }

    private static OffsetDateTime tryParseIso(String v) {
        try {
            // Python: datetime.fromisoformat(v.replace("Z","+00:00"))
            return OffsetDateTime.parse(v.replace("Z", "+00:00"));
        } catch (Exception e) {
            return null;
        }
    }

    private static String fmtRdapDate(Object v) {
        if (v == null || (v instanceof String s && s.isEmpty())) {
            return "-";
        }
        if (v instanceof String s) {
            OffsetDateTime dt = tryParseIso(s);
            if (dt == null) {
                return s;
            }
            // "%B %d, %Y at %H:%M UTC"
            return dt.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH))
                    + " at " + dt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)) + " UTC";
        }
        return String.valueOf(v);
    }

    private static String fmtDt(Object v) {
        if (v == null || (v instanceof String s && s.isEmpty())) {
            return "-";
        }
        if (v instanceof String s) {
            OffsetDateTime dt = tryParseIso(s);
            if (dt == null) {
                return s;
            }
            return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH));
        }
        return String.valueOf(v);
    }

    private static String fmtDtShort(Object v) {
        if (v == null || (v instanceof String s && s.isEmpty())) {
            return "-";
        }
        if (v instanceof String s) {
            OffsetDateTime dt = tryParseIso(s);
            if (dt == null) {
                return s;
            }
            return dt.format(DateTimeFormatter.ofPattern("MM/dd/yy HH:mm", Locale.ENGLISH));
        }
        return String.valueOf(v);
    }

    private static String nowStamp(String pattern) {
        return OffsetDateTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
    }

    private static Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String accessDisp(Map<String, Object> req) {
        Object g = req.get("access_level_granted");
        Object r = req.get("access_level_requested");
        if (g != null) {
            Integer gi = toInt(g);
            return ACCESS_MAP.getOrDefault(gi, "Level " + str(g));
        }
        if (r != null) {
            return "Requested: " + str(r);
        }
        return "-";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        if (o instanceof List<?> l) {
            return (List<Object>) l;
        }
        return null;
    }

    // ============================================================
    // RDAP semantic parsing (mirror _parse_entity / _parse_rdap_sections)
    // ============================================================

    @SuppressWarnings("unchecked")
    private List<Pair> parseEntity(Map<String, Object> ent) {
        List<Pair> rows = new ArrayList<>();
        Object h = ent.get("handle");
        if (h != null && !str(h).isEmpty()) {
            rows.add(new Pair("Handle", str(h)));
        }
        Object vcRaw = ent.get("vcardArray");
        Object vc = vcRaw;
        if (vcRaw != null) {
            Unwrapped u = unwrap(vcRaw);
            vc = u.value();
        }
        List<Object> vcList = asList(vc);
        if (vcList != null && vcList.size() >= 2) {
            List<Object> props = asList(vcList.get(1));
            if (props != null) {
                for (Object pObj : props) {
                    List<Object> p = asList(pObj);
                    if (p == null || p.size() < 4) {
                        continue;
                    }
                    String n = str(p.get(0)).toLowerCase(Locale.ROOT);
                    Object rawV = p.get(3);
                    Unwrapped uv = unwrap(rawV);
                    Object v = uv.value();
                    Integer lvl = uv.level();
                    switch (n) {
                        case "version" -> {
                            // skip
                        }
                        case "fn" -> rows.add(new Pair("Full Name", fmtWithLevel(str(v), lvl)));
                        case "org" -> rows.add(new Pair("Organization", fmtWithLevel(str(v), lvl)));
                        case "role" -> rows.add(new Pair("Role", fmtWithLevel(str(v), lvl)));
                        case "title" -> rows.add(new Pair("Title", fmtWithLevel(str(v), lvl)));
                        case "email" -> rows.add(new Pair("Email", fmtWithLevel(str(v), lvl)));
                        case "tel" -> {
                            Map<String, Object> pm = asMap(p.get(1));
                            Object t = pm != null ? pm.get("type") : null;
                            String tt;
                            List<Object> tl = asList(t);
                            if (tl != null) {
                                List<String> parts = new ArrayList<>();
                                for (Object x : tl) {
                                    parts.add(str(x));
                                }
                                tt = " (" + String.join(", ", parts) + ")";
                            } else if (t != null && !str(t).isEmpty()) {
                                tt = " (" + str(t) + ")";
                            } else {
                                tt = "";
                            }
                            rows.add(new Pair("Phone" + tt, fmtWithLevel(str(v), lvl)));
                        }
                        case "adr" -> {
                            List<Object> vl = asList(v);
                            if (vl != null) {
                                List<String> pts = new ArrayList<>();
                                for (Object x : vl) {
                                    if (x != null && !str(x).isEmpty()) {
                                        pts.add(str(x));
                                    }
                                }
                                if (!pts.isEmpty()) {
                                    rows.add(new Pair("Address", fmtWithLevel(String.join(", ", pts), lvl)));
                                }
                            } else if (v != null && !str(v).isEmpty()) {
                                rows.add(new Pair("Address", fmtWithLevel(str(v), lvl)));
                            }
                        }
                        case "url" -> rows.add(new Pair("URL", fmtWithLevel(str(v), lvl)));
                        case "kind" -> rows.add(new Pair("Kind", fmtWithLevel(str(v), lvl)));
                        default -> rows.add(new Pair(tc(n), fmtWithLevel(str(v), lvl)));
                    }
                }
            }
        }
        List<Object> events = asList(ent.get("events"));
        if (events != null) {
            for (Object evObj : events) {
                Map<String, Object> ev = asMap(evObj);
                if (ev != null) {
                    Unwrapped ed = unwrap(ev.getOrDefault("eventDate", ""));
                    String action = str(ev.getOrDefault("eventAction", ""));
                    String label = EVENT_LABELS.getOrDefault(action, tc(action));
                    rows.add(new Pair(label, fmtWithLevel(fmtRdapDate(ed.value()), ed.level())));
                }
            }
        }
        List<Object> subs = asList(ent.get("entities"));
        if (subs != null) {
            for (Object subObj : subs) {
                Map<String, Object> sub = asMap(subObj);
                if (sub != null) {
                    List<Object> sr = asList(sub.get("roles"));
                    String pfx;
                    if (sr != null && !sr.isEmpty()) {
                        List<String> mapped = new ArrayList<>();
                        for (Object r : sr) {
                            mapped.add(ROLE_MAP.getOrDefault(str(r), tc(str(r))));
                        }
                        pfx = String.join(", ", mapped);
                    } else {
                        pfx = "Sub-Contact";
                    }
                    for (Pair pr : parseEntity(sub)) {
                        rows.add(new Pair("  " + pfx + " — " + pr.label(), pr.value()));
                    }
                }
            }
        }
        return rows;
    }

    private List<Section> parseRdapSections(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return List.of(new Section("No Data",
                    List.of(new Pair("Info", "No RDAP data available"))));
        }
        List<Section> sections = new ArrayList<>();

        // Identity
        List<Pair> idRows = new ArrayList<>();
        Unwrapped oc = unwrap(data.getOrDefault("objectClassName", ""));
        if (oc.value() != null && !str(oc.value()).isEmpty()) {
            idRows.add(new Pair("Object Type", fmtWithLevel(tc(str(oc.value())), oc.level())));
        }
        String[][] idFields = {
                {"ldhName", "Domain Name"}, {"unicodeName", "Unicode Name"},
                {"handle", "Handle / ID"}, {"name", "Name"}, {"type", "Type"},
                {"startAddress", "Start Address"}, {"endAddress", "End Address"},
                {"ipVersion", "IP Version"}, {"country", "Country"},
                {"parentHandle", "Parent Handle"}, {"startAutnum", "Start ASN"},
                {"endAutnum", "End ASN"}};
        for (String[] fl : idFields) {
            Object raw = data.get(fl[0]);
            if (raw != null) {
                Unwrapped u = unwrap(raw);
                if (u.value() != null) {
                    idRows.add(new Pair(fl[1], fmtWithLevel(str(u.value()), u.level())));
                }
            }
        }
        Object alRaw = data.get("accessLevel");
        if (alRaw != null) {
            Unwrapped al = unwrap(alRaw);
            if (al.value() != null) {
                Integer ali = toInt(al.value());
                String disp = ACCESS_MAP.getOrDefault(ali, "Level " + str(al.value()));
                idRows.add(new Pair("Access Level", fmtWithLevel(disp, al.level())));
            }
        }
        Object p43Raw = data.get("port43");
        if (p43Raw != null && !str(unwrap(p43Raw).value()).isEmpty()) {
            Unwrapped p43 = unwrap(p43Raw);
            if (p43.value() != null && !str(p43.value()).isEmpty()) {
                idRows.add(new Pair("WHOIS Server", fmtWithLevel(str(p43.value()), p43.level())));
            }
        }
        if (!idRows.isEmpty()) {
            sections.add(new Section("Registration Details", idRows));
        }

        // Status
        Unwrapped st = unwrap(data.getOrDefault("status", new ArrayList<>()));
        List<Object> stList = asList(st.value());
        if (stList != null && !stList.isEmpty()) {
            List<String> mapped = new ArrayList<>();
            for (Object s : stList) {
                mapped.add(STATUS_MAP.getOrDefault(str(s), tc(str(s))));
            }
            sections.add(new Section("Status", List.of(
                    new Pair("Current Status", fmtWithLevel(String.join(", ", mapped), st.level())))));
        }

        // Events
        List<Object> evts = asList(data.get("events"));
        if (evts != null && !evts.isEmpty()) {
            List<Pair> er = new ArrayList<>();
            for (Object eObj : evts) {
                Map<String, Object> e = asMap(eObj);
                if (e == null) {
                    continue;
                }
                Unwrapped ed = unwrap(e.getOrDefault("eventDate", ""));
                String action = str(e.getOrDefault("eventAction", ""));
                er.add(new Pair(EVENT_LABELS.getOrDefault(action, tc(action)),
                        fmtWithLevel(fmtRdapDate(ed.value()), ed.level())));
            }
            if (!er.isEmpty()) {
                sections.add(new Section("Key Dates", er));
            }
        }

        // Entities
        List<Object> entities = asList(data.get("entities"));
        if (entities != null) {
            for (Object entObj : entities) {
                Map<String, Object> ent = asMap(entObj);
                if (ent == null) {
                    continue;
                }
                List<Object> roles = asList(ent.get("roles"));
                String rs;
                if (roles != null && !roles.isEmpty()) {
                    List<String> mapped = new ArrayList<>();
                    for (Object r : roles) {
                        mapped.add(ROLE_MAP.getOrDefault(str(r), tc(str(r))));
                    }
                    rs = String.join(", ", mapped);
                } else {
                    rs = "Contact";
                }
                List<Pair> cr = parseEntity(ent);
                if (!cr.isEmpty()) {
                    sections.add(new Section(rs, cr));
                }
            }
        }

        // Nameservers
        Unwrapped nssU = unwrap(data.getOrDefault("nameservers", new ArrayList<>()));
        Object nss = nssU.value();
        Integer nssLvl = nssU.level();
        if (nss != null && !(nss instanceof List<?> l && l.isEmpty())) {
            List<Pair> nr = new ArrayList<>();
            List<Object> nsList = asList(nss);
            if (nsList == null) {
                nsList = new ArrayList<>();
                nsList.add(nss);
            }
            for (int i = 0; i < nsList.size(); i++) {
                Object nsObj = nsList.get(i);
                Map<String, Object> ns = asMap(nsObj);
                if (ns != null) {
                    Object nmRaw = ns.get("ldhName");
                    if (nmRaw == null || str(nmRaw).isEmpty()) {
                        nmRaw = ns.get("unicodeName");
                    }
                    if (nmRaw == null || str(nmRaw).isEmpty()) {
                        nmRaw = ns.get("handle");
                    }
                    if (nmRaw == null || str(nmRaw).isEmpty()) {
                        nmRaw = "NS " + (i + 1);
                    }
                    Unwrapped nm = unwrap(nmRaw);
                    Integer eff = nm.level() != null ? nm.level() : nssLvl;
                    nr.add(new Pair("Nameserver", fmtWithLevel(str(nm.value()), eff)));
                    Map<String, Object> ips = asMap(ns.get("ipAddresses"));
                    List<Object> ip4 = ips != null ? asList(ips.get("v4")) : null;
                    List<Object> ip6 = ips != null ? asList(ips.get("v6")) : null;
                    if (ip4 != null && !ip4.isEmpty()) {
                        nr.add(new Pair("  IPv4", joinStr(ip4)));
                    }
                    if (ip6 != null && !ip6.isEmpty()) {
                        nr.add(new Pair("  IPv6", joinStr(ip6)));
                    }
                } else if (nsObj instanceof String s) {
                    nr.add(new Pair("Nameserver", fmtWithLevel(s, nssLvl)));
                }
            }
            if (!nr.isEmpty()) {
                sections.add(new Section("Nameservers", nr));
            }
        }

        // SecureDNS
        Unwrapped sdnsU = unwrap(data.getOrDefault("secureDNS", new LinkedHashMap<>()));
        Map<String, Object> sdns = asMap(sdnsU.value());
        Integer sdnsLvl = sdnsU.level();
        if (sdns != null && !sdns.isEmpty()) {
            List<Pair> dr = new ArrayList<>();
            Object zsRaw = sdns.get("zoneSigned");
            if (zsRaw != null) {
                Unwrapped zs = unwrap(zsRaw);
                Integer eff = zs.level() != null ? zs.level() : sdnsLvl;
                dr.add(new Pair("Zone Signed", fmtWithLevel(truthy(zs.value()) ? "Yes" : "No", eff)));
            }
            Object dsRaw = sdns.get("delegationSigned");
            if (dsRaw != null) {
                Unwrapped ds = unwrap(dsRaw);
                Integer eff = ds.level() != null ? ds.level() : sdnsLvl;
                dr.add(new Pair("Delegation Signed", fmtWithLevel(truthy(ds.value()) ? "Yes" : "No", eff)));
            }
            List<Object> dsData = asList(sdns.get("dsData"));
            if (dsData != null) {
                for (int i = 0; i < dsData.size(); i++) {
                    Map<String, Object> dsd = asMap(dsData.get(i));
                    if (dsd != null) {
                        List<String> pts = new ArrayList<>();
                        for (String k : new String[]{"keyTag", "algorithm", "digestType", "digest"}) {
                            if (dsd.containsKey(k)) {
                                pts.add(humanize(k) + ": " + str(dsd.get(k)));
                            }
                        }
                        if (!pts.isEmpty()) {
                            dr.add(new Pair("DS Record " + (i + 1), String.join("; ", pts)));
                        }
                    }
                }
            }
            if (!dr.isEmpty()) {
                sections.add(new Section("DNSSEC", dr));
            }
        }

        // Remarks
        List<Object> rms = asList(data.get("remarks"));
        if (rms != null && !rms.isEmpty()) {
            List<Pair> rr = new ArrayList<>();
            for (Object rmObj : rms) {
                Map<String, Object> rm = asMap(rmObj);
                if (rm != null) {
                    rr.add(new Pair(str(rm.getOrDefault("title", "Note")), descToStr(rm.get("description"))));
                }
            }
            if (!rr.isEmpty()) {
                sections.add(new Section("Remarks", rr));
            }
        }

        // Notices
        List<Object> nts = asList(data.get("notices"));
        if (nts != null && !nts.isEmpty()) {
            List<Pair> nr2 = new ArrayList<>();
            for (Object ntObj : nts) {
                Map<String, Object> nt = asMap(ntObj);
                if (nt != null) {
                    nr2.add(new Pair(str(nt.getOrDefault("title", "Notice")), descToStr(nt.get("description"))));
                }
            }
            if (!nr2.isEmpty()) {
                sections.add(new Section("Notices", nr2));
            }
        }

        // Links
        List<Object> lks = asList(data.get("links"));
        if (lks != null && !lks.isEmpty()) {
            List<Pair> lr = new ArrayList<>();
            for (Object lkObj : lks) {
                Map<String, Object> lk = asMap(lkObj);
                if (lk != null && lk.get("href") != null && !str(lk.get("href")).isEmpty()) {
                    lr.add(new Pair(tc(str(lk.getOrDefault("rel", "link"))), str(lk.get("href"))));
                }
            }
            if (!lr.isEmpty()) {
                sections.add(new Section("Links", lr));
            }
        }

        // Conformance
        List<Object> conf = asList(data.get("rdapConformance"));
        if (conf != null && !conf.isEmpty()) {
            sections.add(new Section("RDAP Conformance",
                    List.of(new Pair("Extensions", joinStr(conf)))));
        }

        // Catch-all
        List<Pair> extra = new ArrayList<>();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            String k = e.getKey();
            if (CATCH_ALL_SKIP.contains(k) || k.startsWith("_")) {
                continue;
            }
            Unwrapped u = unwrap(e.getValue());
            Object v = u.value();
            Integer lvl = u.level();
            if (v instanceof Map || v instanceof List) {
                String fv = jsonDumps(v);
                if (fv.length() > 300) {
                    fv = fv.substring(0, 297) + "...";
                }
                extra.add(new Pair(humanize(k), fmtWithLevel(fv, lvl)));
            } else {
                extra.add(new Pair(humanize(k), fmtWithLevel(v != null ? str(v) : "-", lvl)));
            }
        }
        if (!extra.isEmpty()) {
            sections.add(new Section("Additional Information", extra));
        }

        if (sections.isEmpty()) {
            String raw = jsonDumps(data);
            if (raw.length() > 500) {
                raw = raw.substring(0, 500);
            }
            sections.add(new Section("RDAP Data", List.of(new Pair("Raw", raw))));
        }
        return sections;
    }

    private static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.doubleValue() != 0;
        }
        if (v instanceof String s) {
            return !s.isEmpty();
        }
        return true;
    }

    private static String descToStr(Object desc) {
        List<Object> l = asList(desc);
        if (l != null) {
            List<String> parts = new ArrayList<>();
            for (Object x : l) {
                parts.add(str(x));
            }
            return String.join(" ", parts);
        }
        return desc != null ? str(desc) : "";
    }

    private static String joinStr(List<Object> l) {
        List<String> parts = new ArrayList<>();
        for (Object x : l) {
            parts.add(str(x));
        }
        return String.join(", ", parts);
    }

    private String jsonDumps(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            return String.valueOf(v);
        }
    }

    // ============================================================
    // Excel (Apache POI) — mirror openpyxl styling
    // ============================================================

    /** Cached style bundle for a single workbook (styles cannot cross workbooks). */
    private static final class XlStyles {
        final XSSFCellStyle header;        // _HF + _HFill + _HA + _TB
        final XSSFCellStyle section;       // _SF + _SFill
        final XSSFCellStyle label;         // _LF + _TB + top
        final XSSFCellStyle data;          // _DF + _DA + _TB
        final XSSFCellStyle titleStyle;    // big title
        final XSSFCellStyle subtitle;      // italic gray
        final XSSFCellStyle sheet2Title;   // 13pt white on dark
        final XSSFCellStyle altFill;       // F8F9F9 zebra (data font + border + wrap/top + fill)
        final Map<String, XSSFCellStyle> statusFills; // status pill fills (data variant)

        XlStyles(XSSFWorkbook wb) {
            XSSFFont hf = wb.createFont();
            hf.setFontName("Arial");
            hf.setBold(true);
            hf.setColor(new XSSFColor(hex("FFFFFF"), null));
            hf.setFontHeightInPoints((short) 11);

            header = wb.createCellStyle();
            header.setFont(hf);
            fillSolid(header, "2C3E50");
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setWrapText(true);
            thinBorder(header);

            XSSFFont sf = wb.createFont();
            sf.setFontName("Arial");
            sf.setBold(true);
            sf.setFontHeightInPoints((short) 11);
            sf.setColor(new XSSFColor(hex("FFFFFF"), null));
            section = wb.createCellStyle();
            section.setFont(sf);
            fillSolid(section, "34495E");
            section.setAlignment(HorizontalAlignment.LEFT);
            section.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFFont lf = wb.createFont();
            lf.setFontName("Arial");
            lf.setBold(true);
            lf.setFontHeightInPoints((short) 10);
            lf.setColor(new XSSFColor(hex("2C3E50"), null));
            label = wb.createCellStyle();
            label.setFont(lf);
            label.setVerticalAlignment(VerticalAlignment.TOP);
            thinBorder(label);

            XSSFFont df = wb.createFont();
            df.setFontName("Arial");
            df.setFontHeightInPoints((short) 10);
            data = wb.createCellStyle();
            data.setFont(df);
            data.setWrapText(true);
            data.setVerticalAlignment(VerticalAlignment.TOP);
            thinBorder(data);

            XSSFFont tf = wb.createFont();
            tf.setFontName("Arial");
            tf.setBold(true);
            tf.setFontHeightInPoints((short) 14);
            tf.setColor(new XSSFColor(hex("2C3E50"), null));
            titleStyle = wb.createCellStyle();
            titleStyle.setFont(tf);
            titleStyle.setAlignment(HorizontalAlignment.LEFT);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFFont subf = wb.createFont();
            subf.setFontName("Arial");
            subf.setFontHeightInPoints((short) 10);
            subf.setItalic(true);
            subf.setColor(new XSSFColor(hex("7F8C8D"), null));
            subtitle = wb.createCellStyle();
            subtitle.setFont(subf);

            XSSFFont s2f = wb.createFont();
            s2f.setFontName("Arial");
            s2f.setBold(true);
            s2f.setFontHeightInPoints((short) 13);
            s2f.setColor(new XSSFColor(hex("FFFFFF"), null));
            sheet2Title = wb.createCellStyle();
            sheet2Title.setFont(s2f);
            fillSolid(sheet2Title, "2C3E50");

            XSSFFont zdf = wb.createFont();
            zdf.setFontName("Arial");
            zdf.setFontHeightInPoints((short) 10);
            altFill = wb.createCellStyle();
            altFill.setFont(zdf);
            altFill.setWrapText(true);
            altFill.setVerticalAlignment(VerticalAlignment.TOP);
            thinBorder(altFill);
            fillSolid(altFill, "F8F9F9");

            statusFills = new LinkedHashMap<>();
            statusFills.put("approved", statusDataStyle(wb, "D5F5E3"));
            statusFills.put("denied", statusDataStyle(wb, "FADBD8"));
            statusFills.put("pending", statusDataStyle(wb, "FEF9E7"));
            statusFills.put("error", statusDataStyle(wb, "E5E7E9"));
        }

        private static XSSFCellStyle statusDataStyle(XSSFWorkbook wb, String hex) {
            // Mirrors: status cell gets the fill + bold Arial 10 + center/top alignment + border
            XSSFCellStyle s = wb.createCellStyle();
            XSSFFont f = wb.createFont();
            f.setFontName("Arial");
            f.setBold(true);
            f.setFontHeightInPoints((short) 10);
            s.setFont(f);
            fillSolid(s, hex);
            s.setAlignment(HorizontalAlignment.CENTER);
            s.setVerticalAlignment(VerticalAlignment.TOP);
            thinBorder(s);
            return s;
        }
    }

    private static byte[] hex(String h) {
        return new byte[]{
                (byte) Integer.parseInt(h.substring(0, 2), 16),
                (byte) Integer.parseInt(h.substring(2, 4), 16),
                (byte) Integer.parseInt(h.substring(4, 6), 16)};
    }

    private static void fillSolid(XSSFCellStyle style, String h) {
        style.setFillForegroundColor(new XSSFColor(hex(h), null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    private static void thinBorder(XSSFCellStyle style) {
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        XSSFColor c = new XSSFColor(hex("D5D8DC"), null);
        style.setLeftBorderColor(c);
        style.setRightBorderColor(c);
        style.setTopBorderColor(c);
        style.setBottomBorderColor(c);
    }

    private static Cell cellAt(XSSFSheet ws, int row0, int col0) {
        Row r = ws.getRow(row0);
        if (r == null) {
            r = ws.createRow(row0);
        }
        Cell c = r.getCell(col0);
        if (c == null) {
            c = r.createCell(col0);
        }
        return c;
    }

    /** Mirror _xl_section: merged section header across 2 columns. Returns next 1-based row. */
    private int xlSection(XSSFSheet ws, XlStyles st, int row1, String title) {
        int r0 = row1 - 1;
        ws.addMergedRegion(new CellRangeAddress(r0, r0, 0, 1));
        Cell c = cellAt(ws, r0, 0);
        c.setCellValue(title);
        c.setCellStyle(st.section);
        cellAt(ws, r0, 1).setCellStyle(st.section);
        ws.getRow(r0).setHeightInPoints(22);
        return row1 + 1;
    }

    /** Mirror _xl_pair: label/value row. Returns next 1-based row. */
    private int xlPair(XSSFSheet ws, XlStyles st, int row1, String label, String value) {
        int r0 = row1 - 1;
        Cell lc = cellAt(ws, r0, 0);
        lc.setCellValue(label);
        lc.setCellStyle(st.label);
        Cell vc = cellAt(ws, r0, 1);
        vc.setCellValue(value != null && !value.isEmpty() ? value : "-");
        vc.setCellStyle(st.data);
        return row1 + 1;
    }

    private int writeRdapXl(XSSFSheet ws, XlStyles st, Map<String, Object> rdapData, int startRow1) {
        int row = startRow1;
        for (Section sec : parseRdapSections(rdapData)) {
            row = xlSection(ws, st, row, sec.title());
            for (Pair p : sec.rows()) {
                row = xlPair(ws, st, row, p.label(), p.value());
            }
            row += 1;
        }
        return row;
    }

    // ============================================================
    // Bulk request export — selectable columns
    // ============================================================

    /**
     * A column available in the bulk request exports. Excel and PDF render the
     * same columns in the same order; they differ only in the header wording and
     * the width each renderer needs.
     */
    public record RequestColumn(
            String key, String excelHeader, int excelWidth, String pdfHeader, float pdfWidth) {
    }

    /** Every column a bulk export can render, in canonical order. */
    public static final List<RequestColumn> REQUEST_COLUMNS = List.of(
            new RequestColumn("status", "Status", 14, "Status", 0.7f),
            new RequestColumn("query_type", "Query Type", 14, "Type", 0.6f),
            new RequestColumn("query_value", "Query Value", 30, "Query", 1.4f),
            new RequestColumn("agreements", "Agreements", 28, "Agreements", 1.4f),
            new RequestColumn("access_level", "Access Level", 16, "Access", 0.65f),
            new RequestColumn("data_holder", "Data Holder", 22, "Data Holder", 1.1f),
            new RequestColumn("created", "Created", 20, "Created", 1.1f),
            new RequestColumn("resolved", "Resolved", 20, "Resolved", 1.1f),
            new RequestColumn("error", "Error / Denial Reason", 35, "Notes", 1.8f));

    /**
     * Resolve caller-supplied column keys to columns, keeping the canonical order
     * and ignoring unknown keys. A null/empty/unrecognised selection means "all".
     */
    public static List<RequestColumn> resolveColumns(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return REQUEST_COLUMNS;
        }
        Set<String> wanted = new LinkedHashSet<>();
        for (String k : keys) {
            if (k != null && !k.isBlank()) {
                wanted.add(k.trim().toLowerCase(Locale.ROOT));
            }
        }
        List<RequestColumn> out = new ArrayList<>();
        for (RequestColumn c : REQUEST_COLUMNS) {
            if (wanted.contains(c.key())) {
                out.add(c);
            }
        }
        return out.isEmpty() ? REQUEST_COLUMNS : out;
    }

    /** A layout needs at least one column; an empty selection falls back to all. */
    private static List<RequestColumn> columnsOrAll(List<RequestColumn> columns) {
        return columns == null || columns.isEmpty() ? REQUEST_COLUMNS : columns;
    }

    /**
     * One cell of the bulk table. {@code compact} is set for the PDF, whose table
     * is much tighter: dates are abbreviated and the data holder shows only its
     * name rather than the "name (id)" form the Excel column has room for.
     */
    private String columnValue(RequestColumn col, Map<String, Object> req, boolean compact) {
        return switch (col.key()) {
            case "status" -> str(req.get("status")).toUpperCase(Locale.ROOT);
            case "query_type" -> str(req.get("query_type")).toUpperCase(Locale.ROOT);
            case "query_value" -> str(req.get("query_value"));
            case "agreements" -> agreementsJoined(req);
            case "access_level" -> accessDisp(req);
            case "data_holder" -> compact
                    ? emptyDash(req.get("data_holder_name"))
                    : dataHolderDisplay(req);
            case "created" -> compact
                    ? fmtDtShort(req.get("created_at"))
                    : fmtDt(req.get("created_at"));
            case "resolved" -> compact
                    ? fmtDtShort(req.get("resolved_at"))
                    : fmtDt(req.get("resolved_at"));
            case "error" -> emptyDash(req.get("error_message"));
            default -> "";
        };
    }

    public byte[] exportRequestsToExcel(List<Map<String, Object>> requests, String title) {
        return exportRequestsToExcel(requests, title, REQUEST_COLUMNS, true);
    }

    public byte[] exportRequestsToExcel(List<Map<String, Object>> requests, String title,
                                        List<RequestColumn> requestedColumns, boolean includeRdapData) {
        List<RequestColumn> columns = columnsOrAll(requestedColumns);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XlStyles st = new XlStyles(wb);
            XSSFSheet ws = wb.createSheet("RDAP Requests");
            int lastCol = columns.size() - 1;

            if (lastCol > 0) {
                ws.addMergedRegion(new CellRangeAddress(0, 0, 0, lastCol));
                ws.addMergedRegion(new CellRangeAddress(1, 1, 0, lastCol));
            }
            Cell a1 = cellAt(ws, 0, 0);
            a1.setCellValue(title);
            a1.setCellStyle(st.titleStyle);
            ws.getRow(0).setHeightInPoints(30);

            Cell a2 = cellAt(ws, 1, 0);
            a2.setCellValue("Generated: " + nowStamp("yyyy-MM-dd HH:mm:ss")
                    + "  |  Total Records: " + requests.size());
            a2.setCellStyle(st.subtitle);

            int hr0 = 3; // header row index (Python hr=4 → 0-based 3)
            Row headerRow = ws.createRow(hr0);
            headerRow.setHeightInPoints(28);
            for (int ci = 0; ci < columns.size(); ci++) {
                Cell c = headerRow.createCell(ci);
                c.setCellValue(columns.get(ci).excelHeader());
                c.setCellStyle(st.header);
            }

            for (int idx = 0; idx < requests.size(); idx++) {
                Map<String, Object> req = requests.get(idx);
                int r0 = hr0 + 1 + idx;
                Row dataRow = ws.createRow(r0);
                String s = str(req.get("status")).toLowerCase(Locale.ROOT);
                boolean evenRow = (idx % 2) == 1; // Python ri%2==0 where ri starts at hr+1=5 (odd)
                for (int ci = 0; ci < columns.size(); ci++) {
                    RequestColumn col = columns.get(ci);
                    Cell c = dataRow.createCell(ci);
                    c.setCellValue(columnValue(col, req, false));
                    if ("status".equals(col.key()) && st.statusFills.containsKey(s)) {
                        c.setCellStyle(st.statusFills.get(s));
                    } else if (evenRow) {
                        // zebra fill F8F9F9 but keep data font/border/alignment
                        c.setCellStyle(st.altFill);
                    } else {
                        c.setCellStyle(st.data);
                    }
                }
            }

            for (int i = 0; i < columns.size(); i++) {
                ws.setColumnWidth(i, columns.get(i).excelWidth() * 256);
            }
            // auto-filter A{hr}:{lastcol}{lr}; freeze panes below header
            int lr0 = hr0 + requests.size();
            ws.setAutoFilter(new CellRangeAddress(hr0, lr0, 0, lastCol));
            ws.createFreezePane(0, hr0 + 1);

            // Second sheet: approved requests with RDAP data
            List<Map<String, Object>> approved = new ArrayList<>();
            if (includeRdapData) {
                for (Map<String, Object> r : requests) {
                    if (r.get("rdap_data") != null
                            && "approved".equals(str(r.get("status")).toLowerCase(Locale.ROOT))) {
                        approved.add(r);
                    }
                }
            }
            if (!approved.isEmpty()) {
                XSSFSheet ws2 = wb.createSheet("RDAP Response Data");
                int row = 1; // 1-based
                for (Map<String, Object> req : approved) {
                    int r0 = row - 1;
                    ws2.addMergedRegion(new CellRangeAddress(r0, r0, 0, 1));
                    Cell c = cellAt(ws2, r0, 0);
                    c.setCellValue(str(req.get("query_type")).toUpperCase(Locale.ROOT)
                            + ": " + str(req.get("query_value")));
                    c.setCellStyle(st.sheet2Title);
                    cellAt(ws2, r0, 1).setCellStyle(st.sheet2Title);
                    ws2.getRow(r0).setHeightInPoints(28);
                    row += 1;
                    row = writeRdapXl(ws2, st, asMapOrEmpty(req.get("rdap_data")), row);
                    row += 1;
                }
                ws2.setColumnWidth(0, 30 * 256);
                ws2.setColumnWidth(1, 70 * 256);
            }

            return toBytes(wb);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Excel export", e);
        }
    }

    public byte[] exportSingleRequestToExcel(Map<String, Object> request) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XlStyles st = new XlStyles(wb);
            XSSFSheet ws = wb.createSheet("RDAP Report");

            String qt = str(orDefault(request.get("query_type"), "RDAP")).toUpperCase(Locale.ROOT);
            String qv = str(request.get("query_value"));
            if (qv.isEmpty()) {
                Map<String, Object> raw = asMap(request.get("raw_data"));
                qv = raw != null ? str(raw.get("ldhName")) : "";
            }

            ws.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
            Cell a1 = cellAt(ws, 0, 0);
            a1.setCellValue("RDAP Report: " + qt + " — " + qv);
            a1.setCellStyle(st.titleStyle);
            ws.getRow(0).setHeightInPoints(30);

            ws.addMergedRegion(new CellRangeAddress(1, 1, 0, 1));
            Cell a2 = cellAt(ws, 1, 0);
            a2.setCellValue("Generated: " + nowStamp("yyyy-MM-dd HH:mm:ss"));
            a2.setCellStyle(st.subtitle);

            int row = 4;
            row = xlSection(ws, st, row, "Request Summary");
            for (String[] lv : summaryRows(request, qt, qv)) {
                row = xlPair(ws, st, row, lv[0], lv[1]);
            }

            Map<String, Object> rdap = asMap(request.get("rdap_data"));
            if (rdap == null) {
                rdap = asMap(request.get("raw_data"));
            }
            if (rdap != null && !rdap.isEmpty()) {
                row += 1;
                writeRdapXl(ws, st, rdap, row);
            }

            ws.setColumnWidth(0, 30 * 256);
            ws.setColumnWidth(1, 70 * 256);
            return toBytes(wb);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Excel export", e);
        }
    }

    private byte[] toBytes(XSSFWorkbook wb) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    // ============================================================
    // PDF (OpenPDF) — mirror reportlab layout
    // ============================================================

    private static final Color C_DARK = hexColor("2C3E50");
    private static final Color C_GRAY = hexColor("7F8C8D");
    private static final Color C_RULE = hexColor("BDC3C7");
    private static final Color C_GRID = hexColor("D5D8DC");
    private static final Color C_LABELBG = hexColor("F2F4F4");
    private static final Color C_VALBG = hexColor("FAFAFA");
    private static final Color C_ZEBRA = hexColor("F8F9F9");
    private static final Color C_SUMMARYBG = hexColor("EBF5FB");

    private static Color hexColor(String h) {
        return new Color(
                Integer.parseInt(h.substring(0, 2), 16),
                Integer.parseInt(h.substring(2, 4), 16),
                Integer.parseInt(h.substring(4, 6), 16));
    }

    private static final Map<String, Color> PDF_STATUS_COLORS = Map.of(
            "APPROVED", hexColor("D5F5E3"),
            "DENIED", hexColor("FADBD8"),
            "PENDING", hexColor("FEF9E7"),
            "ERROR", hexColor("E5E7E9"));

    private Font titleFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, C_DARK);
    }

    private Font title2Font() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, C_DARK);
    }

    private Font subFont() {
        return FontFactory.getFont(FontFactory.HELVETICA, 10, C_GRAY);
    }

    private Font sectionFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, C_DARK);
    }

    private Font cellFont() {
        return FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
    }

    private Font cellBoldFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);
    }

    /** Footer event handler mirroring _pdf_footer. */
    private static final class FooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            var cb = writer.getDirectContent();
            cb.saveState();
            cb.beginText();
            cb.setFontAndSize(safeBaseFont(), 7);
            cb.setColorFill(hexColor("95A5A6"));
            float y = 0.4f * 72;
            cb.setTextMatrix(document.leftMargin(), y);
            cb.showText("RDAP Export — Page " + writer.getPageNumber());
            cb.endText();
            String right = "Generated " + nowStamp("yyyy-MM-dd HH:mm");
            cb.beginText();
            cb.setFontAndSize(safeBaseFont(), 7);
            cb.setColorFill(hexColor("95A5A6"));
            float rx = document.getPageSize().getWidth() - document.rightMargin();
            cb.showTextAligned(Element.ALIGN_RIGHT, right, rx, y, 0);
            cb.endText();
            cb.restoreState();
        }

        private static com.lowagie.text.pdf.BaseFont safeBaseFont() {
            try {
                return com.lowagie.text.pdf.BaseFont.createFont(
                        com.lowagie.text.pdf.BaseFont.HELVETICA,
                        com.lowagie.text.pdf.BaseFont.WINANSI,
                        com.lowagie.text.pdf.BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private PdfPCell pdfCell(String text, Font font, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setVerticalAlignment(Element.ALIGN_TOP);
        c.setBorderColor(C_GRID);
        c.setBorderWidth(0.5f);
        c.setPaddingTop(3);
        c.setPaddingBottom(3);
        c.setPaddingLeft(5);
        if (bg != null) {
            c.setBackgroundColor(bg);
        }
        return c;
    }

    private void addRdapPdf(Document doc, Map<String, Object> request) throws Exception {
        Map<String, Object> rdap = asMap(request.get("rdap_data"));
        if (rdap == null) {
            rdap = asMap(request.get("raw_data"));
        }
        if (rdap == null || rdap.isEmpty()) {
            return;
        }
        for (Section sec : parseRdapSections(rdap)) {
            Paragraph p = new Paragraph(sec.title(), sectionFont());
            p.setSpacingBefore(14);
            p.setSpacingAfter(6);
            doc.add(p);
            if (sec.rows().isEmpty()) {
                continue;
            }
            PdfPTable t = new PdfPTable(new float[]{2.2f, 4.6f});
            t.setWidthPercentage(100);
            int i = 0;
            for (Pair pr : sec.rows()) {
                String vs = pr.value() != null && !pr.value().isEmpty() ? pr.value() : "-";
                if (vs.length() > 300) {
                    vs = vs.substring(0, 297) + "...";
                }
                t.addCell(pdfCell(pr.label(), cellBoldFont(), C_LABELBG));
                // Python adds the value-cell background only on even (0-based) rows.
                Color valBg = (i % 2 == 0) ? C_VALBG : null;
                t.addCell(pdfCell(vs, cellFont(), valBg));
                i++;
            }
            doc.add(t);
            doc.add(spacer(8));
        }
    }

    private Paragraph spacer(float pts) {
        Paragraph p = new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 1));
        p.setSpacingAfter(pts);
        return p;
    }

    private void hr(Document doc) throws Exception {
        var line = new com.lowagie.text.pdf.draw.LineSeparator(1f, 100f, C_RULE, Element.ALIGN_CENTER, 0);
        Paragraph p = new Paragraph();
        p.add(new com.lowagie.text.Chunk(line));
        p.setSpacingAfter(12);
        doc.add(p);
    }

    public byte[] exportRequestsToPdf(List<Map<String, Object>> requests, String title) {
        return exportRequestsToPdf(requests, title, REQUEST_COLUMNS, true);
    }

    public byte[] exportRequestsToPdf(List<Map<String, Object>> requests, String title,
                                      List<RequestColumn> requestedColumns, boolean includeRdapData) {
        List<RequestColumn> columns = columnsOrAll(requestedColumns);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Rectangle pageSize = PageSize.LETTER.rotate(); // landscape
            Document doc = new Document(pageSize, 0.5f * 72, 0.5f * 72, 0.6f * 72, 0.6f * 72);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new FooterEvent());
            doc.open();

            Paragraph t = new Paragraph(title, titleFont());
            t.setSpacingAfter(6);
            doc.add(t);
            Paragraph sub = new Paragraph("Generated: " + nowStamp("yyyy-MM-dd HH:mm:ss")
                    + "  |  Total Records: " + requests.size(), subFont());
            sub.setSpacingAfter(18);
            doc.add(sub);
            hr(doc);

            // Stats summary table
            Map<String, Integer> sc = new LinkedHashMap<>();
            for (Map<String, Object> r : requests) {
                String s = str(orDefault(r.get("status"), "unknown")).toLowerCase(Locale.ROOT);
                sc.merge(s, 1, Integer::sum);
            }
            PdfPTable stats = new PdfPTable(5);
            stats.setWidths(new float[]{1.5f, 1.5f, 1.5f, 1.5f, 1.5f});
            stats.setWidthPercentage(100 * (7.5f / 10f));
            stats.setHorizontalAlignment(Element.ALIGN_LEFT);
            String[] statHdrs = {"Total", "Pending", "Approved", "Denied", "Error"};
            Font statHdrFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            for (String h : statHdrs) {
                PdfPCell c = new PdfPCell(new Phrase(h, statHdrFont));
                c.setBackgroundColor(C_DARK);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                c.setBorderColor(C_RULE);
                c.setBorderWidth(0.5f);
                c.setPaddingTop(6);
                c.setPaddingBottom(6);
                stats.addCell(c);
            }
            Font statValFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.BLACK);
            String[] statVals = {
                    String.valueOf(requests.size()),
                    String.valueOf(sc.getOrDefault("pending", 0)),
                    String.valueOf(sc.getOrDefault("approved", 0)),
                    String.valueOf(sc.getOrDefault("denied", 0)),
                    String.valueOf(sc.getOrDefault("error", 0))};
            Color[] statBgs = {hexColor("EBF5FB"), hexColor("FEF9E7"),
                    hexColor("D5F5E3"), hexColor("FADBD8"), hexColor("E5E7E9")};
            for (int i = 0; i < statVals.length; i++) {
                PdfPCell c = new PdfPCell(new Phrase(statVals[i], statValFont));
                c.setBackgroundColor(statBgs[i]);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                c.setBorderColor(C_RULE);
                c.setBorderWidth(0.5f);
                c.setPaddingTop(6);
                c.setPaddingBottom(6);
                stats.addCell(c);
            }
            doc.add(stats);
            doc.add(spacer(16));

            // Detail table
            Paragraph det = new Paragraph("Request Details", sectionFont());
            det.setSpacingBefore(14);
            det.setSpacingAfter(6);
            doc.add(det);

            float[] cw = new float[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                cw[i] = columns.get(i).pdfWidth();
            }
            PdfPTable dt = new PdfPTable(cw);
            dt.setWidthPercentage(100);
            dt.setHeaderRows(1);
            Font thFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
            for (RequestColumn col : columns) {
                PdfPCell c = new PdfPCell(new Phrase(col.pdfHeader(), thFont));
                c.setBackgroundColor(C_DARK);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                c.setVerticalAlignment(Element.ALIGN_TOP);
                c.setBorderColor(C_GRID);
                c.setBorderWidth(0.5f);
                c.setPadding(4);
                dt.addCell(c);
            }
            Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.BLACK);
            for (int i = 0; i < requests.size(); i++) {
                Map<String, Object> req = requests.get(i);
                String s = str(req.get("status")).toUpperCase(Locale.ROOT);
                boolean zebra = ((i + 1) % 2) == 0; // Python i starts at 1
                Color statusBg = PDF_STATUS_COLORS.get(s);
                for (int ci = 0; ci < columns.size(); ci++) {
                    RequestColumn col = columns.get(ci);
                    String val = columnValue(col, req, true);
                    if ("error".equals(col.key()) && val.length() > 60) {
                        val = val.substring(0, 57) + "...";
                    }
                    Color bg = null;
                    if ("status".equals(col.key())) {
                        bg = statusBg; // status keeps its own colour, or none
                    } else if (zebra) {
                        bg = C_ZEBRA;
                    }
                    PdfPCell c = new PdfPCell(new Phrase(val, rowFont));
                    c.setVerticalAlignment(Element.ALIGN_TOP);
                    c.setBorderColor(C_GRID);
                    c.setBorderWidth(0.5f);
                    c.setPadding(4);
                    if (bg != null) {
                        c.setBackgroundColor(bg);
                    }
                    dt.addCell(c);
                }
            }
            doc.add(dt);

            // RDAP data pages
            List<Map<String, Object>> approved = new ArrayList<>();
            if (includeRdapData) {
                for (Map<String, Object> r : requests) {
                    if (r.get("rdap_data") != null
                            && "approved".equals(str(r.get("status")).toLowerCase(Locale.ROOT))) {
                        approved.add(r);
                    }
                }
            }
            if (!approved.isEmpty()) {
                doc.newPage();
                Paragraph rt = new Paragraph("RDAP Response Data", titleFont());
                rt.setSpacingAfter(6);
                doc.add(rt);
                hr(doc);
                for (Map<String, Object> req : approved) {
                    String qv = str(req.get("query_value"));
                    String qt = str(req.get("query_type")).toUpperCase(Locale.ROOT);
                    Paragraph hp = sectionHeading(qt + ": ", qv);
                    doc.add(hp);
                    addRdapPdf(doc, req);
                    doc.add(spacer(20));
                }
            }

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build PDF export", e);
        }
    }

    public byte[] exportSingleRequestToPdf(Map<String, Object> request) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.LETTER, 0.6f * 72, 0.6f * 72, 0.6f * 72, 0.6f * 72);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new FooterEvent());
            doc.open();

            String qt = str(orDefault(request.get("query_type"), "RDAP")).toUpperCase(Locale.ROOT);
            String qv = str(request.get("query_value"));
            if (qv.isEmpty()) {
                Map<String, Object> raw = asMap(request.get("raw_data"));
                qv = raw != null ? str(raw.get("ldhName")) : "";
            }

            Paragraph t = new Paragraph("RDAP Report", title2Font());
            t.setSpacingAfter(4);
            doc.add(t);
            Paragraph sub = new Paragraph();
            sub.setFont(subFont());
            sub.add(new com.lowagie.text.Chunk(qt + ": ", subFont()));
            sub.add(new com.lowagie.text.Chunk(qv, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, C_GRAY)));
            sub.add(new com.lowagie.text.Chunk("  |  Generated: " + nowStamp("yyyy-MM-dd HH:mm:ss"), subFont()));
            sub.setSpacingAfter(18);
            doc.add(sub);
            hr(doc);

            Paragraph sh = new Paragraph("Request Summary", sectionFont());
            sh.setSpacingBefore(14);
            sh.setSpacingAfter(6);
            doc.add(sh);

            PdfPTable st = new PdfPTable(new float[]{1.8f, 5f});
            st.setWidthPercentage(100);
            Font kFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);
            Font vFont = cellFont();
            for (String[] lv : summaryRows(request, qt, qv)) {
                PdfPCell kc = new PdfPCell(new Phrase(lv[0], kFont));
                kc.setBackgroundColor(C_SUMMARYBG);
                kc.setVerticalAlignment(Element.ALIGN_TOP);
                kc.setBorderColor(C_GRID);
                kc.setBorderWidth(0.5f);
                kc.setPaddingTop(5);
                kc.setPaddingBottom(5);
                kc.setPaddingLeft(6);
                st.addCell(kc);
                PdfPCell vc = new PdfPCell(new Phrase(lv[1], vFont));
                vc.setVerticalAlignment(Element.ALIGN_TOP);
                vc.setBorderColor(C_GRID);
                vc.setBorderWidth(0.5f);
                vc.setPaddingTop(5);
                vc.setPaddingBottom(5);
                vc.setPaddingLeft(6);
                st.addCell(vc);
            }
            doc.add(st);

            addRdapPdf(doc, request);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build PDF export", e);
        }
    }

    private Paragraph sectionHeading(String prefix, String boldPart) {
        Paragraph p = new Paragraph();
        p.setFont(sectionFont());
        p.add(new com.lowagie.text.Chunk(prefix, sectionFont()));
        p.add(new com.lowagie.text.Chunk(boldPart, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, C_DARK)));
        p.setSpacingBefore(14);
        p.setSpacingAfter(6);
        return p;
    }

    // ============================================================
    // Summary rows shared by single Excel + single PDF
    // ============================================================

    private List<String[]> summaryRows(Map<String, Object> request, String qt, String qv) {
        Object accObj = request.containsKey("accessLevel")
                ? request.get("accessLevel")
                : request.get("access_level_granted");
        String accessDisp;
        Integer accInt = toInt(accObj);
        if (accInt != null && ACCESS_MAP.containsKey(accInt)) {
            accessDisp = ACCESS_MAP.get(accInt);
        } else {
            accessDisp = accObj != null ? str(accObj) : "N/A";
        }
        Object server = request.containsKey("rdapServer")
                ? request.get("rdapServer")
                : request.getOrDefault("rdap_server", "N/A");
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Status", str(orDefault(request.get("status"), "N/A")).toUpperCase(Locale.ROOT)});
        rows.add(new String[]{"Query Type", qt});
        rows.add(new String[]{"Query Value", qv});
        rows.add(new String[]{"Source", str(request.getOrDefault("source", "N/A"))});
        rows.add(new String[]{"Access Level", accessDisp});
        rows.add(new String[]{"Agreement", str(request.getOrDefault("agreement_name", "N/A"))});
        rows.add(new String[]{"Timestamp", fmtDt(request.get("timestamp"))});
        rows.add(new String[]{"RDAP Server", str(orDefault(server, "N/A"))});
        return rows;
    }

    // ============================================================
    // Misc small helpers
    // ============================================================

    private static Object orDefault(Object v, Object def) {
        return v != null ? v : def;
    }

    private static String emptyDash(Object v) {
        String s = v != null ? str(v) : "";
        return s.isEmpty() ? "-" : s;
    }

    @SuppressWarnings("unchecked")
    private static String agreementsJoined(Map<String, Object> req) {
        Object a = req.get("agreements_used");
        List<Object> l = asList(a);
        if (l == null || l.isEmpty()) {
            return "-";
        }
        List<String> parts = new ArrayList<>();
        for (Object x : l) {
            parts.add(str(x));
        }
        String joined = String.join(", ", parts);
        return joined.isEmpty() ? "-" : joined;
    }

    private static String dataHolderDisplay(Map<String, Object> req) {
        Object name = req.get("data_holder_name");
        if (name != null && !str(name).isEmpty()) {
            return str(name);
        }
        Object id = req.get("data_holder_id");
        if (id != null && !str(id).isEmpty()) {
            return str(id);
        }
        return "-";
    }

    private static Map<String, Object> asMapOrEmpty(Object o) {
        Map<String, Object> m = asMap(o);
        return m != null ? m : new LinkedHashMap<>();
    }

    private static Map<String, String> mapOf(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
