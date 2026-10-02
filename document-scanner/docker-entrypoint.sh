#!/bin/sh
# SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
# SPDX-License-Identifier: AGPL-3.0-only
#
# Author: Derek Jenkins <derek@pure-code.net>
# Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.

# document-scanner entrypoint. Builds the gunicorn command; when MTLS_ENABLED=true
# it terminates TLS and requires a CA-signed client cert (--cert-reqs 2 =
# ssl.CERT_REQUIRED), else plain HTTP. No browser traffic here, so the whole port
# (9600) flips to mTLS.
set -e

MTLS_CERT_FILE="${MTLS_CERT_FILE:-/certs/document-scanner.crt}"
MTLS_KEY_FILE="${MTLS_KEY_FILE:-/certs/document-scanner.key}"
MTLS_CA_FILE="${MTLS_CA_FILE:-/certs/document-scanner-truststore.crt}"

# OCR is CPU-bound and can take seconds per page, so allow a few workers and a
# generous timeout. Keep workers modest — each rasterised page costs memory.
WORKERS="${SCANNER_WORKERS:-2}"
set -- gunicorn -b 0.0.0.0:9600 -w "$WORKERS" --timeout 300 app:app

MTLS_FLAG="$(printf '%s' "${MTLS_ENABLED:-}" | tr '[:upper:]' '[:lower:]')"

if [ "$MTLS_FLAG" = "true" ]; then
    echo "[entrypoint] mTLS ENABLED — requiring client certs (cert=$MTLS_CERT_FILE key=$MTLS_KEY_FILE ca=$MTLS_CA_FILE)"
    set -- "$@" \
        --certfile "$MTLS_CERT_FILE" \
        --keyfile "$MTLS_KEY_FILE" \
        --ca-certs "$MTLS_CA_FILE" \
        --cert-reqs 2
else
    echo "[entrypoint] mTLS DISABLED — serving plain HTTP on :9600"
fi

exec "$@"
