#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
# SPDX-License-Identifier: AGPL-3.0-only
#
# Author: Derek Jenkins <derek@pure-code.net>
# Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.

# Creates the Mailgun inbound route that RDRS mail capture depends on. Without it,
# Mailgun accepts mail for the domain and then discards it. Idempotent: an existing
# route for the same recipient expression is left alone.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"

read_env() {
  [ -f "$ENV_FILE" ] || return 0
  grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- | tr -d '\r'
}

MAILGUN_API_KEY="${MAILGUN_API_KEY:-$(read_env MAILGUN_API_KEY)}"
RDRS_MAIL_DOMAIN="${RDRS_MAIL_DOMAIN:-$(read_env RDRS_MAIL_DOMAIN)}"
MAILGUN_API_BASE_URL="${MAILGUN_API_BASE_URL:-$(read_env MAILGUN_API_BASE_URL)}"
MAILGUN_API_BASE_URL="${MAILGUN_API_BASE_URL:-https://api.mailgun.net}"

if [ -z "$MAILGUN_API_KEY" ]; then
  echo "ERROR: MAILGUN_API_KEY is not set (looked in $ENV_FILE)." >&2
  exit 1
fi
if [ -z "$RDRS_MAIL_DOMAIN" ]; then
  echo "ERROR: RDRS_MAIL_DOMAIN is not set (looked in $ENV_FILE)." >&2
  exit 1
fi

EXPRESSION="match_recipient(\".*@${RDRS_MAIL_DOMAIN}\")"

echo "==> Checking existing routes for ${RDRS_MAIL_DOMAIN}"
EXISTING=$(curl -s --user "api:${MAILGUN_API_KEY}" "${MAILGUN_API_BASE_URL}/v3/routes?limit=200")

if printf '%s' "$EXISTING" | grep -Fq "$EXPRESSION"; then
  echo "    A route for ${RDRS_MAIL_DOMAIN} already exists; nothing to do."
  exit 0
fi

echo "==> Creating store() route: ${EXPRESSION}"
RESPONSE=$(curl -s -w '\n%{http_code}' --user "api:${MAILGUN_API_KEY}" \
  -X POST "${MAILGUN_API_BASE_URL}/v3/routes" \
  -F priority=10 \
  -F description='Jaddar inbound capture' \
  -F expression="$EXPRESSION" \
  -F action='store()')

STATUS=$(printf '%s' "$RESPONSE" | tail -1)
BODY=$(printf '%s' "$RESPONSE" | sed '$d')

if [ "$STATUS" != "200" ]; then
  echo "ERROR: Mailgun returned HTTP $STATUS" >&2
  echo "$BODY" >&2
  exit 1
fi

echo "$BODY"
echo
echo "Route created. Mail to *@${RDRS_MAIL_DOMAIN} will now be stored for Jaddar to ingest."
echo "Verify in Jaddar: Settings -> RDRS Mail -> Capture now."
