#!/bin/sh
# SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
# SPDX-License-Identifier: AGPL-3.0-only
#
# Author: Derek Jenkins <derek@pure-code.net>
# Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.

#
# Postgres/MariaDB wrapper entrypoint: stage the TLS server cert/key to a
# container path owned by the DB user with the key at 0600, then chain to the
# stock entrypoint. Bind-mounts keep host ownership and Postgres refuses to
# start on a group/world-readable key. Missing certs -> start without TLS
# rather than hard-fail, so a plain local run still works.
#
# Env:
#   DB_SSL_CERT_NAME  basename of the cert/key in /certs (e.g. "postgres")   [required]
#   DB_SSL_OWNER      user:group to own the staged files                     [default root]
#   DB_SSL_SRC        source cert dir                                        [default /certs]
#   DB_SSL_DEST       destination dir for staged certs                       [default /etc/db-certs]
# Args ($@): the database server command (e.g. `postgres -c ssl=on ...`).
set -e

SRC="${DB_SSL_SRC:-/certs}"
DEST="${DB_SSL_DEST:-/etc/db-certs}"
OWNER="${DB_SSL_OWNER:-root}"
NAME="${DB_SSL_CERT_NAME:?DB_SSL_CERT_NAME is required}"

if [ -f "$SRC/$NAME.crt" ] && [ -f "$SRC/$NAME.key" ] && [ -f "$SRC/infra-ca.crt" ]; then
    mkdir -p "$DEST"
    cp "$SRC/$NAME.crt" "$DEST/server.crt"
    cp "$SRC/$NAME.key" "$DEST/server.key"
    cp "$SRC/infra-ca.crt" "$DEST/ca.crt"
    chown "$OWNER:$OWNER" "$DEST/server.crt" "$DEST/server.key" "$DEST/ca.crt" 2>/dev/null || true
    chmod 600 "$DEST/server.key"
    chmod 644 "$DEST/server.crt" "$DEST/ca.crt"
    echo "[db-ssl-entrypoint] TLS certs staged at $DEST (owner=$OWNER); starting with TLS"
    exec docker-entrypoint.sh "$@"
else
    echo "[db-ssl-entrypoint] WARNING: $SRC/$NAME.{crt,key} or infra-ca.crt not found — starting WITHOUT TLS"
    # Drop TLS flags; start with just the server binary.
    exec docker-entrypoint.sh "$1"
fi
