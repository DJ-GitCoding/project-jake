/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { readFileSync } from "node:fs";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);

/*
 * Backing store for the SSR session records (see session.server.js).
 *
 */

const SWEEP_INTERVAL_MS = 5 * 60 * 1000;

function createMemoryStore(app) {
  const key = `__sessionStore_${app}`;
  const store = (globalThis[key] ||= new Map());

  const isExpired = (rec, now) =>
    rec.idleExpiresAt <= now || rec.absoluteExpiresAt <= now;

  return {
    kind: "memory",
    async create(id, data, idleExpiresAt, absoluteExpiresAt) {
      store.set(id, { data, idleExpiresAt, absoluteExpiresAt });
      if (store.size % 200 === 0) {
        const now = Date.now();
        for (const [entryId, rec] of store) {
          if (isExpired(rec, now)) store.delete(entryId);
        }
      }
    },
    async has(id) {
      return store.has(id);
    },
    /** Returns the data and slides the idle window, or null if gone/expired. */
    async readAndTouch(id, idleExpiresAt) {
      const rec = store.get(id);
      if (!rec) return null;
      if (isExpired(rec, Date.now())) {
        store.delete(id);
        return null;
      }
      rec.idleExpiresAt = idleExpiresAt;
      return rec.data;
    },
    async update(id, data, idleExpiresAt, absoluteExpiresAt) {
      const previous = store.get(id);
      store.set(id, {
        data,
        idleExpiresAt,
        // Never let an update push the hard ceiling out.
        absoluteExpiresAt: previous?.absoluteExpiresAt ?? absoluteExpiresAt,
      });
    },
    async destroy(id) {
      store.delete(id);
    },
  };
}

function createPostgresStore({ app, connection, caPath }) {
  // Required lazily so the memory path never needs `pg` resolvable.
  const pg = require("pg");
  const ssl = caPath
    ? {
        ca: readFileSync(caPath, "utf8"),
        rejectUnauthorized: true,
        checkServerIdentity: () => undefined,
      }
    : undefined;

  const pool = new pg.Pool({ ...connection, ssl, max: 10 });

  pool.on("error", (err) => {
    console.error("[session-store] idle client error:", err.message);
  });

  const ready = pool
    .query(
      `CREATE TABLE IF NOT EXISTS sessions (
         id                  TEXT PRIMARY KEY,
         app                 TEXT        NOT NULL,
         data                JSONB       NOT NULL,
         idle_expires_at     TIMESTAMPTZ NOT NULL,
         absolute_expires_at TIMESTAMPTZ NOT NULL
       )`
    )
    .then(() =>
      pool.query(
        `CREATE INDEX IF NOT EXISTS sessions_expiry_idx
           ON sessions (idle_expires_at, absolute_expires_at)`
      )
    );

  const sweep = () =>
    pool
      .query(
        `DELETE FROM sessions
          WHERE app = $1 AND (idle_expires_at <= now() OR absolute_expires_at <= now())`,
        [app]
      )
      .catch((err) => console.error("[session-store] sweep failed:", err.message));

  setInterval(sweep, SWEEP_INTERVAL_MS).unref();

  return {
    kind: "postgres",
    async create(id, data, idleExpiresAt, absoluteExpiresAt) {
      await ready;
      await pool.query(
        `INSERT INTO sessions (id, app, data, idle_expires_at, absolute_expires_at)
         VALUES ($1, $2, $3, $4, $5)`,
        [id, app, data, new Date(idleExpiresAt), new Date(absoluteExpiresAt)]
      );
    },
    async has(id) {
      await ready;
      const { rowCount } = await pool.query(
        `SELECT 1 FROM sessions WHERE id = $1`,
        [id]
      );
      return rowCount > 0;
    },

    async readAndTouch(id, idleExpiresAt) {
      await ready;
      const { rows } = await pool.query(
        `UPDATE sessions
            SET idle_expires_at = $3
          WHERE id = $1
            AND app = $2
            AND idle_expires_at > now()
            AND absolute_expires_at > now()
        RETURNING data`,
        [id, app, new Date(idleExpiresAt)]
      );
      return rows.length ? rows[0].data : null;
    },

    async update(id, data, idleExpiresAt, absoluteExpiresAt) {
      await ready;
      await pool.query(
        `INSERT INTO sessions (id, app, data, idle_expires_at, absolute_expires_at)
         VALUES ($1, $2, $3, $4, $5)
         ON CONFLICT (id) DO UPDATE
            SET data = EXCLUDED.data,
                idle_expires_at = EXCLUDED.idle_expires_at`,
        [id, app, data, new Date(idleExpiresAt), new Date(absoluteExpiresAt)]
      );
    },
    async destroy(id) {
      await ready;
      await pool.query(`DELETE FROM sessions WHERE id = $1 AND app = $2`, [id, app]);
    },
  };
}

function connectionFromEnv() {
  const { SESSION_DATABASE_URL, SESSION_DB_HOST } = process.env;
  if (SESSION_DATABASE_URL) {
    return { connectionString: SESSION_DATABASE_URL };
  }
  if (SESSION_DB_HOST) {
    return {
      host: SESSION_DB_HOST,
      port: Number(process.env.SESSION_DB_PORT) || 5432,
      database: process.env.SESSION_DB_NAME || "sessions_db",
      user: process.env.SESSION_DB_USER,
      password: process.env.SESSION_DB_PASSWORD,
    };
  }
  return null;
}

export function createStore({ app }) {
  const connection = connectionFromEnv();
  if (!connection) {
    return createMemoryStore(app);
  }
  return createPostgresStore({
    app,
    connection,
    caPath: process.env.SESSION_DATABASE_CA,
  });
}
