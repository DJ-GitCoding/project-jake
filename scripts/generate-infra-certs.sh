#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
# SPDX-License-Identifier: AGPL-3.0-only
#
# Author: Derek Jenkins <derek@pure-code.net>
# Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.

#
# TLS for the shared infrastructure servers: Postgres and Keycloak. One CA signs
# their server certs; the backends trust that CA so their database and Keycloak connections
# validate. SANs cover the docker name + localhost.
#
# The applications do NOT appear here. They authenticate to one another with credentials
# exchanged through the admin UI and stored in the database, so there is no per-app CA,
# no client certificate, and nothing to redistribute when a peer is added.
#
# The script decides for itself whether there is anything to do, so it is safe to run on
# every deploy. It generates only what is missing, and it will not generate at all when the
# certificates were supplied by someone else -- see "Deciding what to do" below.
#
# Usage:
#   scripts/generate-infra-certs.sh            generate what is missing, verify the rest
#   scripts/generate-infra-certs.sh --check    report only; exit 2 if action is needed
#   scripts/generate-infra-certs.sh --verify   never generate; fail if anything is wrong
#   scripts/generate-infra-certs.sh --force    regenerate everything (refused in verify mode)
#   scripts/generate-infra-certs.sh --stage-only
#                                              stage the CA into the build contexts and stop.
#                                              For a build host, which needs only the CA to
#                                              bake into the images -- not the server keys.
#
# Verify mode is selected automatically in production -- see is_verify_only().

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CERT_DIR="${INFRA_CERT_DIR:-${MTLS_CERT_DIR:-$ROOT_DIR/certs}}"

DAYS_CA="${INFRA_CA_DAYS:-3650}"
DAYS_CERT="${INFRA_CERT_DAYS:-825}"
# How close to expiry a certificate may get before this script calls it a problem.
RENEW_WITHIN_DAYS="${INFRA_RENEW_WITHIN_DAYS:-30}"

CA_NAME="infra-ca"
LEAVES=(
  "postgres|DNS:postgres,DNS:postgres-db,DNS:localhost,IP:127.0.0.1"
  "keycloak|DNS:keycloak,DNS:localhost,IP:127.0.0.1"
)
# Build contexts whose Dockerfiles bake the CA into the image's JVM truststore.
BACKENDS=(dataholder-backend dh-group-admin-backend requestor-manager-backend jaddar-backend)

MODE="generate"
for arg in "$@"; do
  case "$arg" in
    --check)  MODE="check" ;;
    --stage-only) MODE="stage-only" ;;
    --verify) MODE="verify" ;;
    --force)  MODE="force" ;;
    -h|--help) sed -n '9,27p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "ERROR: unknown argument '$arg' (try --help)" >&2; exit 1 ;;
  esac
done

if ! command -v openssl >/dev/null 2>&1; then
  echo "ERROR: openssl is required but was not found on PATH." >&2
  exit 1
fi

red()  { printf '\033[31m%s\033[0m\n' "$*"; }
warn() { printf '\033[33m%s\033[0m\n' "$*"; }
ok()   { printf '\033[32m%s\033[0m\n' "$*"; }

#
# Deciding what to do.
#
# Generating a self-signed CA over a real deployment would replace certificates the rest of
# the infrastructure already trusts, so there are two ways to be told not to: an explicit
# request, or the deployment simply looking like production.
#
is_verify_only() {
  [[ "$MODE" == "verify" ]] && return 0
  [[ "${INFRA_CERTS_VERIFY_ONLY:-}" == "true" ]] && return 0
  case "${APP_ENV:-${ENVIRONMENT:-${DEPLOY_ENV:-}}}" in
    prod|production|PROD|PRODUCTION) return 0 ;;
  esac
  #
  # The CA private key is the only thing that can sign new leaves, and it is never shipped.
  # Leaf certificates present without it therefore came from somewhere else -- a managed
  # database's CA bundle, or certificates issued by a real authority. Reissuing them here
  # would break every client that already trusts them.
  #
  if [[ ! -f "$CERT_DIR/$CA_NAME.key" ]]; then
    local have_leaf=false
    for entry in "${LEAVES[@]}"; do
      [[ -f "$CERT_DIR/${entry%%|*}.crt" ]] && have_leaf=true
    done
    $have_leaf && return 0
  fi
  return 1
}

fingerprint() { openssl x509 -in "$1" -noout -fingerprint -sha256 2>/dev/null | cut -d= -f2; }

# True when the certificate is valid for at least RENEW_WITHIN_DAYS more days.
not_expiring() {
  openssl x509 -in "$1" -noout -checkend $(( RENEW_WITHIN_DAYS * 86400 )) >/dev/null 2>&1
}

# True when the leaf actually chains to our CA. Catches the case where the CA was
# regenerated but the leaves were not, which otherwise fails much later as a PKIX error.
chains_to_ca() {
  openssl verify -CAfile "$CERT_DIR/$CA_NAME.crt" "$1" >/dev/null 2>&1
}

# Everything that is missing, expiring or no longer chains. Empty means nothing to do.
problems() {
  local out=()
  if [[ ! -f "$CERT_DIR/$CA_NAME.crt" ]]; then
    out+=("$CA_NAME: missing")
  elif ! not_expiring "$CERT_DIR/$CA_NAME.crt"; then
    out+=("$CA_NAME: expires within ${RENEW_WITHIN_DAYS}d")
  fi
  for entry in "${LEAVES[@]}"; do
    local name="${entry%%|*}"
    if [[ ! -f "$CERT_DIR/$name.crt" || ! -f "$CERT_DIR/$name.key" ]]; then
      out+=("$name: missing")
    elif ! not_expiring "$CERT_DIR/$name.crt"; then
      out+=("$name: expires within ${RENEW_WITHIN_DAYS}d")
    elif [[ -f "$CERT_DIR/$CA_NAME.crt" ]] && ! chains_to_ca "$CERT_DIR/$name.crt"; then
      out+=("$name: does not chain to $CA_NAME")
    fi
  done
  # Printing an empty array would still emit a blank line, which reads as a problem.
  if [[ ${#out[@]} -gt 0 ]]; then
    printf '%s\n' "${out[@]}"
  fi
}

#
# The CA staged into each build context is baked into the image at build time. When it drifts
# from the live CA, every TLS call the backends make fails PKIX validation at runtime -- and
# because the copy is optional in the Dockerfile, a missing one fails silently instead. This
# is checked on every run for exactly that reason.
#
stage_ca() {
  local live stale=()
  live="$(fingerprint "$CERT_DIR/$CA_NAME.crt")"
  for svc in "${BACKENDS[@]}"; do
    local dest="$ROOT_DIR/$svc/infra-ca.crt"
    if [[ ! -f "$dest" ]] || [[ "$(fingerprint "$dest")" != "$live" ]]; then
      stale+=("$svc")
      [[ "$MODE" != "check" ]] && cp -f "$CERT_DIR/$CA_NAME.crt" "$dest"
    fi
  done

  if [[ ${#stale[@]} -eq 0 ]]; then
    ok "==> Staged CA matches the live CA in all ${#BACKENDS[@]} build contexts"
    return 0
  fi
  if [[ "$MODE" == "check" ]]; then
    warn "==> Staged CA is stale or missing in: ${stale[*]}"
    return 2
  fi
  warn "==> Refreshed the staged CA in: ${stale[*]}"
  warn "    These images MUST be rebuilt, or their TLS calls will fail PKIX validation:"
  warn "      docker compose build ${stale[*]}"
  return 0
}

mkdir -p "$CERT_DIR"

#
# A build host bakes the CA into the images and never serves TLS itself, so it holds the CA
# certificate but none of the server private keys. Asking it to verify a full set it was
# never given would fail for the wrong reason.
#
if [[ "$MODE" == "stage-only" ]]; then
  if [[ ! -f "$CERT_DIR/$CA_NAME.crt" ]]; then
    red "ERROR: $CERT_DIR/$CA_NAME.crt is missing."
    red "       The build needs the CA that signs your infrastructure certificates, so that"
    red "       the images trust your database and Keycloak at runtime."
    exit 1
  fi
  stage_ca
  exit $?
fi

mapfile -t ISSUES < <(problems)

# ---------------------------------------------------------------- verify / check / no-op

if is_verify_only; then
  if [[ "$MODE" == "force" ]]; then
    red "ERROR: --force refused: these certificates were supplied externally or this is production."
    red "       Replace them through whatever issues them, not with a self-signed dev CA."
    exit 1
  fi
  echo "==> Verify mode: this deployment supplies its own certificates; nothing will be generated."
  if [[ ${#ISSUES[@]} -gt 0 ]]; then
    red "ERROR: the infrastructure certificates are not usable:"
    printf '       - %s\n' "${ISSUES[@]}" >&2
    red "       Supply valid certificates in $CERT_DIR before starting the stack."
    exit 1
  fi
  ok "==> Infrastructure certificates present and valid in $CERT_DIR"
  [[ -f "$CERT_DIR/$CA_NAME.crt" ]] && { stage_ca || exit $?; }
  exit 0
fi

if [[ "$MODE" == "check" ]]; then
  rc=0
  if [[ ${#ISSUES[@]} -gt 0 ]]; then
    warn "==> Certificates need to be generated:"
    printf '    - %s\n' "${ISSUES[@]}"
    rc=2
  else
    ok "==> Infrastructure certificates present and valid in $CERT_DIR"
  fi
  if [[ -f "$CERT_DIR/$CA_NAME.crt" ]]; then
    stage_ca || rc=2
  fi
  exit $rc
fi

if [[ ${#ISSUES[@]} -eq 0 && "$MODE" != "force" ]]; then
  ok "==> Infrastructure certificates are present and valid; nothing to generate."
  stage_ca
  exit 0
fi

# ---------------------------------------------------------------- generate

cd "$CERT_DIR"

if [[ ! -f "$CA_NAME.key" || ! -f "$CA_NAME.crt" || "$MODE" == "force" ]]; then
  echo "==> Generating CA: $CA_NAME"
  openssl genrsa -out "$CA_NAME.key" 4096
  openssl req -x509 -new -nodes -key "$CA_NAME.key" -sha256 -days "$DAYS_CA" \
    -subj "/C=US/O=ICANN-Dev/CN=ICANN Dev $CA_NAME" \
    -out "$CA_NAME.crt"
  # A new CA invalidates every leaf it used to sign, so they are all reissued below.
  MODE="force"
else
  echo "==> Reusing existing CA: $CA_NAME"
fi

gen_service() {
  local name="$1" sans="$2"

  if [[ -f "$name.crt" && -f "$name.key" && "$MODE" != "force" ]] \
     && not_expiring "$name.crt" && chains_to_ca "$name.crt"; then
    echo "==> $name: certificate is valid, skipping"
    return
  fi

  echo "==> $name: generating key + cert signed by $CA_NAME"
  openssl genrsa -out "$name.key" 2048
  openssl req -new -key "$name.key" -subj "/C=US/O=ICANN-Dev/CN=$name" -out "$name.csr"

  cat > "$name.ext" <<EOF
basicConstraints = CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = $sans
EOF

  openssl x509 -req -in "$name.csr" \
    -CA "$CA_NAME.crt" -CAkey "$CA_NAME.key" -CAcreateserial \
    -out "$name.crt" -days "$DAYS_CERT" -sha256 \
    -extfile "$name.ext"

  rm -f "$name.csr" "$name.ext"
}

for entry in "${LEAVES[@]}"; do
  IFS='|' read -r lname lsans <<< "$entry"
  gen_service "$lname" "$lsans"
done

rm -f ./*.srl

stage_ca

echo
ok "Done. Infrastructure certificates written to: $CERT_DIR"
echo
echo "NOTE: certs/ is git-ignored. $CA_NAME.key is a CA private key — never commit or ship it."
