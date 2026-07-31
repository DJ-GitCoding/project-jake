#!/bin/sh
# SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
# SPDX-License-Identifier: AGPL-3.0-only
#
# Author: Derek Jenkins <derek@pure-code.net>
# Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.

set -e

cat <<EOF > /usr/share/nginx/html/env.js
window.__ENV__ = {
  REACT_APP_API_URL: "${REACT_APP_API_URL:-http://localhost:8082}",
  REACT_APP_REQUESTOR_API_URL: "${REACT_APP_REQUESTOR_API_URL:-http://localhost:8000}",
  REACT_APP_VERSION: "${REACT_APP_VERSION:-dev}"
};
EOF

find /usr/share/nginx/html -name '*.js' -exec sed -i \
  -e "s|__REACT_APP_API_URL__|${REACT_APP_API_URL:-http://localhost:8082}|g" \
  -e "s|__REACT_APP_REQUESTOR_API_URL__|${REACT_APP_REQUESTOR_API_URL:-http://localhost:8000}|g" \
  -e "s|__REACT_APP_VERSION__|${REACT_APP_VERSION:-dev}|g" \
  {} \;

echo "[entrypoint] Environment injected, starting nginx..."
exec "$@"