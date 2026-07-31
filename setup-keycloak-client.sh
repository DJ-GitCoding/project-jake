#!/bin/bash
# SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
# SPDX-License-Identifier: AGPL-3.0-only
#
# Author: Derek Jenkins <derek@pure-code.net>
# Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.


echo "Waiting for Keycloak to be ready..."
until curl -f http://localhost:8080/realms/master > /dev/null 2>&1; do
    echo "Waiting for Keycloak..."
    sleep 5
done

echo "Keycloak is ready! Getting admin token..."

# Get admin token
TOKEN=$(curl -s -X POST \
  "http://localhost:8080/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin" \
  -d "password=admin" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo "Failed to get admin token. Please check if Keycloak is running."
    exit 1
fi

echo "Creating client..."

# Create the client
curl -X POST \
  "http://localhost:8080/admin/realms/master/clients" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "jaddar-app",
    "name": "React Python App",
    "enabled": true,
    "clientAuthenticatorType": "client-secret",
    "secret": "secret123",
    "redirectUris": ["http://localhost:3000/*"],
    "webOrigins": ["http://localhost:3000"],
    "standardFlowEnabled": true,
    "directAccessGrantsEnabled": true,
    "publicClient": false,
    "protocol": "openid-connect"
  }'

echo ""
echo "Client created successfully!"
echo "Client ID: jaddar-app"
echo "Client Secret: secret123"
