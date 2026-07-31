#!/bin/sh
# SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
# SPDX-License-Identifier: AGPL-3.0-only
#
# Author: Derek Jenkins <derek@pure-code.net>
# Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.

# provisioning-sidecar entrypoint. Builds the gunicorn command; when
# MTLS_ENABLED=true it terminates TLS and requires a CA-signed client cert
# (--cert-reqs 2 = ssl.CERT_REQUIRED), else plain HTTP. No browser traffic here,
# so the whole port (9500) flips to mTLS.
set -e

# Cert paths (overridable via env; coordinator mounts them into /certs).
MTLS_CERT_FILE="${MTLS_CERT_FILE:-/certs/provisioning-sidecar.crt}"
MTLS_KEY_FILE="${MTLS_KEY_FILE:-/certs/provisioning-sidecar.key}"
MTLS_CA_FILE="${MTLS_CA_FILE:-/certs/provisioning-sidecar-truststore.crt}"

# Base gunicorn command.
set -- gunicorn -b 0.0.0.0:9500 -w 1 --timeout 300 app:app

# Lowercase for case-insensitive compare.
MTLS_FLAG="$(printf '%s' "${MTLS_ENABLED:-}" | tr '[:upper:]' '[:lower:]')"

if [ "$MTLS_FLAG" = "true" ]; then
    echo "[entrypoint] mTLS ENABLED — requiring client certs (cert=$MTLS_CERT_FILE key=$MTLS_KEY_FILE ca=$MTLS_CA_FILE)"
    set -- "$@" \
        --certfile "$MTLS_CERT_FILE" \
        --keyfile "$MTLS_KEY_FILE" \
        --ca-certs "$MTLS_CA_FILE" \
        --cert-reqs 2
else
    echo "[entrypoint] mTLS DISABLED — serving plain HTTP on :9500"
fi

# exec so gunicorn becomes PID 1 and gets signals directly.
exec "$@"
