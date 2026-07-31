/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.service;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Minimal CIDR block representation supporting both IPv4 and IPv6 containment
 * checks. Replaces Python's ipaddress.ip_network(...) usage in the bootstrap
 * service. Parsing is lenient (strict=False) — host bits in the prefix are
 * ignored.
 */
record CidrBlock(BigInteger network, int prefixLen, int bits) {

    /** Parse a CIDR string ("192.0.2.0/24" or "2001:db8::/32"); null on failure. */
    static CidrBlock parse(String cidr) {
        if (cidr == null) {
            return null;
        }
        String s = cidr.trim();
        try {
            String addrPart;
            int prefix;
            if (s.contains("/")) {
                int slash = s.indexOf('/');
                addrPart = s.substring(0, slash);
                prefix = Integer.parseInt(s.substring(slash + 1).trim());
            } else {
                addrPart = s;
                prefix = -1; // full-length host
            }
            InetAddress addr = InetAddress.getByName(addrPart);
            byte[] bytes = addr.getAddress();
            int bits = bytes.length * 8;
            if (prefix < 0) {
                prefix = bits;
            }
            if (prefix > bits) {
                return null;
            }
            BigInteger value = new BigInteger(1, bytes);
            BigInteger mask = maskFor(prefix, bits);
            BigInteger network = value.and(mask);
            return new CidrBlock(network, prefix, bits);
        } catch (UnknownHostException | NumberFormatException e) {
            return null;
        }
    }

    boolean contains(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        if (bytes.length * 8 != bits) {
            return false; // address family mismatch
        }
        BigInteger value = new BigInteger(1, bytes);
        BigInteger mask = maskFor(prefixLen, bits);
        return value.and(mask).equals(network);
    }

    private static BigInteger maskFor(int prefix, int bits) {
        if (prefix == 0) {
            return BigInteger.ZERO;
        }
        BigInteger allOnes = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
        return allOnes.shiftLeft(bits - prefix).and(allOnes);
    }
}
