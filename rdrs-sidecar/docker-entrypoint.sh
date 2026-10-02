#!/bin/sh
# SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
# SPDX-License-Identifier: AGPL-3.0-only
#
# Author: Derek Jenkins <derek@pure-code.net>
# Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.

# rdrs-sidecar entrypoint. Builds the gunicorn command; when MTLS_ENABLED=true it
# terminates TLS and requires a CA-signed client cert (--cert-reqs 2 =
# ssl.CERT_REQUIRED), else plain HTTP. No browser traffic here, so the whole port
# (9700) flips to mTLS.
set -e

MTLS_CERT_FILE="${MTLS_CERT_FILE:-/certs/rdrs-sidecar.crt}"
MTLS_KEY_FILE="${MTLS_KEY_FILE:-/certs/rdrs-sidecar.key}"
MTLS_CA_FILE="${MTLS_CA_FILE:-/certs/rdrs-sidecar-truststore.crt}"

# Exactly ONE worker. A login parked on an MFA prompt keeps its Chromium instance
# and its Python thread in this process; a sibling worker could not resume it, so
# scaling out here would break MFA. Concurrency comes from threads instead — each
# login already runs on its own thread.
#
# A full ICANN login is a multi-hop browser journey, so the timeout is generous;
# it must exceed RDRS_LOGIN_TIMEOUT_MS plus the MFA wait.
THREADS="${RDRS_THREADS:-8}"
set -- gunicorn -b 0.0.0.0:9700 -w 1 --threads "$THREADS" --timeout 600 app:app

MTLS_FLAG="$(printf '%s' "${MTLS_ENABLED:-}" | tr '[:upper:]' '[:lower:]')"

if [ "$MTLS_FLAG" = "true" ]; then
    echo "[entrypoint] mTLS ENABLED — requiring client certs (cert=$MTLS_CERT_FILE key=$MTLS_KEY_FILE ca=$MTLS_CA_FILE)"
    set -- "$@" \
        --certfile "$MTLS_CERT_FILE" \
        --keyfile "$MTLS_KEY_FILE" \
        --ca-certs "$MTLS_CA_FILE" \
        --cert-reqs 2
else
    echo "[entrypoint] mTLS DISABLED — serving plain HTTP on :9700"
fi

exec "$@"
