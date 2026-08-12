#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
# SPDX-License-Identifier: AGPL-3.0-only
#
# Author: Derek Jenkins <derek@pure-code.net>
# Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.

#
# Dev-only PKI for the app suite. Each application is its OWN trust domain: it gets its
# own CA that signs only its own leaf cert, and its own truststore holding the CAs of the
# peers it trusts (full mesh: every app trusts the other three + the shared infra CA).
# A single infra CA signs the shared infrastructure servers (Postgres, Keycloak).
# Leaf certs carry serverAuth+clientAuth (each service is both TLS server and client);
# SANs cover the docker name + localhost. NOT for production — see the plan's risks.
#
# Usage: scripts/generate-mtls-certs.sh [--force]   (--force regenerates everything)
# Passwords: MTLS_KEYSTORE_PASSWORD / MTLS_TRUSTSTORE_PASSWORD (dev default: changeit)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CERT_DIR="${MTLS_CERT_DIR:-$ROOT_DIR/certs}"

KS_PASS="${MTLS_KEYSTORE_PASSWORD:-changeit}"
TS_PASS="${MTLS_TRUSTSTORE_PASSWORD:-changeit}"
DAYS_CA="${MTLS_CA_DAYS:-3650}"
DAYS_CERT="${MTLS_CERT_DAYS:-825}"
FORCE="${1:-}"

if ! command -v openssl >/dev/null 2>&1; then
  echo "ERROR: openssl is required but was not found on PATH." >&2
  exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
  echo "ERROR: keytool (from any JDK/JRE) is required to build the Java truststores." >&2
  echo "       Install a JRE, or run this on a host that has 'java' available." >&2
  exit 1
fi

# Per-app CAs (independent trust roots) plus one shared infra CA for the DB/Keycloak servers.
CAS=(jaddar-ca rm-ca dhg-ca dh-ca infra-ca)

LEAVES=(
  "jaddar-backend|jaddar-ca|DNS:jaddar-backend,DNS:jaddar,DNS:localhost,IP:127.0.0.1"
  "requestor-manager-backend|rm-ca|DNS:requestor-manager-backend,DNS:requestor-manager,DNS:localhost,IP:127.0.0.1"
  "dh-group-admin-backend|dhg-ca|DNS:dh-group-admin-backend,DNS:localhost,IP:127.0.0.1"
  "dataholder-backend|dh-ca|DNS:dataholder-backend,DNS:dataholder,DNS:localhost,IP:127.0.0.1"
  "provisioning-sidecar|dh-ca|DNS:provisioning-sidecar,DNS:localhost,IP:127.0.0.1"
  "postgres|infra-ca|DNS:postgres,DNS:postgres-db,DNS:localhost,IP:127.0.0.1"
  "keycloak|infra-ca|DNS:keycloak,DNS:localhost,IP:127.0.0.1"
)

TRUSTSTORES=(
  "jaddar-truststore|rm-ca dhg-ca dh-ca infra-ca"
  "requestor-manager-truststore|jaddar-ca dhg-ca dh-ca infra-ca"
  "dh-group-admin-truststore|jaddar-ca rm-ca dh-ca infra-ca"
  "dataholder-truststore|jaddar-ca rm-ca dhg-ca infra-ca"
)

mkdir -p "$CERT_DIR"
cd "$CERT_DIR"

# Certificate Authorities (one per app + one infra)
for ca in "${CAS[@]}"; do
  if [[ ! -f "${ca}.key" || ! -f "${ca}.crt" || "$FORCE" == "--force" ]]; then
    echo "==> Generating CA: ${ca}"
    openssl genrsa -out "${ca}.key" 4096
    openssl req -x509 -new -nodes -key "${ca}.key" -sha256 -days "$DAYS_CA" \
      -subj "/C=US/O=ICANN-Dev/CN=ICANN Dev ${ca}" \
      -out "${ca}.crt"
  else
    echo "==> Reusing existing CA: ${ca} (pass --force to regenerate)"
  fi
done

# Per-service leaf certs + keystores, signed by the service's designated CA.
gen_service() {
  local name="$1"
  local ca="$2"
  local sans="$3"

  if [[ -f "${name}.p12" && "$FORCE" != "--force" ]]; then
    echo "==> ${name}: keystore exists, skipping (pass --force to regenerate)"
    return
  fi

  echo "==> ${name}: generating key + cert signed by ${ca}"
  openssl genrsa -out "${name}.key" 2048
  openssl req -new -key "${name}.key" \
    -subj "/C=US/O=ICANN-Dev/CN=${name}" \
    -out "${name}.csr"

  cat > "${name}.ext" <<EOF
basicConstraints = CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth, clientAuth
subjectAltName = ${sans}
EOF

  openssl x509 -req -in "${name}.csr" \
    -CA "${ca}.crt" -CAkey "${ca}.key" -CAcreateserial \
    -out "${name}.crt" -days "$DAYS_CERT" -sha256 \
    -extfile "${name}.ext"

  # PKCS12 keystore: leaf key + leaf cert + signing CA in the chain
  openssl pkcs12 -export \
    -inkey "${name}.key" \
    -in "${name}.crt" \
    -certfile "${ca}.crt" \
    -name "${name}" \
    -out "${name}.p12" \
    -passout pass:"$KS_PASS"

  rm -f "${name}.csr" "${name}.ext"
}

for entry in "${LEAVES[@]}"; do
  IFS='|' read -r lname lca lsans <<< "$entry"
  gen_service "$lname" "$lca" "$lsans"
done

# Per-app truststores. Rebuilt each run so trust changes take effect. Each CA lands as a
# Java trustedCertEntry (keytool import), which Tomcat client-auth requires.
for entry in "${TRUSTSTORES[@]}"; do
  IFS='|' read -r tsname tscas <<< "$entry"
  echo "==> Building ${tsname}.p12 (trusts: ${tscas})"
  rm -f "${tsname}.p12"
  for ca in $tscas; do
    keytool -importcert -noprompt -trustcacerts \
      -alias "$ca" \
      -file "${ca}.crt" \
      -keystore "${tsname}.p12" \
      -storetype PKCS12 \
      -storepass "$TS_PASS"
  done
done

# Client-CA bundle (PEM) for the Python provisioning sidecar's mTLS: it is called by, and
# calls, the app backends, so it trusts all four app CAs.
echo "==> Building provisioning-sidecar-truststore.crt (all app CAs)"
cat jaddar-ca.crt rm-ca.crt dhg-ca.crt dh-ca.crt > provisioning-sidecar-truststore.crt

# Tidy the serial files openssl leaves behind.
rm -f ./*.srl

# Stage the infra CA into each backend build context, where the Dockerfiles bake it into the
# JVM truststore. Every backend makes plain outbound HTTPS calls to infra-CA-signed services
# (Keycloak's 8443 listener in particular) using a RestTemplate that consults the default
# truststore, not the mTLS truststores — without this those calls fail PKIX validation.
# Dynamically-spawned data holder instances additionally get no mounted certs at all.
for svc in dataholder-backend dh-group-admin-backend requestor-manager-backend jaddar-backend; do
  cp -f "$CERT_DIR/infra-ca.crt" "$ROOT_DIR/$svc/infra-ca.crt"
done
echo "==> Staged infra-ca.crt into each backend build context (baked into the images)"

echo
echo "Done. Certificates written to: $CERT_DIR"
echo "  Keystore password:   (MTLS_KEYSTORE_PASSWORD, default 'changeit')"
echo "  Truststore password: (MTLS_TRUSTSTORE_PASSWORD, default 'changeit')"
echo
echo "NOTE: certs/ is git-ignored. The *-ca.key files are dev CA private keys — never commit or ship them."
