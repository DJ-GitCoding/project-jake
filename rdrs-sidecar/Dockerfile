# SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
# SPDX-License-Identifier: AGPL-3.0-only
#
# Author: Derek Jenkins <derek@pure-code.net>
# Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.

# Microsoft's Playwright image ships Chromium plus every shared library it needs,
# already matched to the pinned Playwright version. It must stay in lockstep with
# the playwright pin in requirements.txt.
#
# Note this is glibc-based, unlike the Alpine images the Java services use —
# Playwright publishes no musl browser builds, which is precisely why the browser
# lives in this sidecar instead of inside jaddar-backend.
FROM mcr.microsoft.com/playwright/python:v1.47.0-jammy

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Entrypoint decides plain HTTP vs. server-side mTLS based on MTLS_ENABLED.
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

EXPOSE 9700

ENTRYPOINT ["/app/docker-entrypoint.sh"]
