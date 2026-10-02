# Startup Guide

How to bring the stack up from a clean checkout: prerequisites, the one manual script you
must run, and every environment variable the compose files read.

The stack is five applications (Jaddar, Requestor Manager, Data Holder, DH Group Admin,
Data Holder Registry — each a backend + an SSR frontend) plus Keycloak, Postgres, a MariaDB
RDAP test database, a provisioning sidecar, and a document-scanner sidecar.

---

## 1. Prerequisites

| Tool | Why |
|---|---|
| Docker + Docker Compose v2 | Runs the entire stack. |
| `openssl` | Used by the mTLS certificate script. |
| `keytool` (any JDK/JRE) | Builds the Java PKCS12 truststores. |

Nothing else needs to be installed on the host — all builds happen inside containers.

---

## 2. Manual steps (required, in this order)

### 2.1 Generate the mTLS certificates — **required, run once**

```bash
scripts/generate-mtls-certs.sh          # add --force to regenerate everything
```

This is the only script you must run by hand. `certs/` is git-ignored, so a fresh clone has
no certificates, and **the stack will not start without them** — Postgres, MariaDB and
Keycloak are all configured for TLS and mount `./certs` read-only, and the backends talk to
each other over mutual TLS.

The script creates, in `./certs`:

- One CA per application (`jaddar-ca`, `rm-ca`, `dhg-ca`, `dh-ca`) plus a shared `infra-ca`
  for Postgres / MariaDB / Keycloak.
- A leaf keystore (`<service>.p12`) for each backend and infra server.
- A truststore per application (full mesh — every app trusts the other three plus infra).
- `provisioning-sidecar-truststore.crt`, a PEM bundle for the Python sidecar.
- A copy of `infra-ca.crt` into `dataholder-backend/`, which the Dockerfile bakes into the
  image's JVM truststore so **dynamically spawned** data-holder instances (which get no
  mounted certs) still trust Keycloak.

It honours `MTLS_KEYSTORE_PASSWORD` / `MTLS_TRUSTSTORE_PASSWORD` (default `changeit`) — set
these in the environment **before** running the script if you are not using the defaults, and
use the same values in your `.env`. Existing CAs and keystores are reused unless `--force`.

> The `*-ca.key` files are private keys. `certs/` is git-ignored — never commit it.

### 2.2 Create your env file

```bash
cp .env.example .env      # then fill in the values from section 3
```

Note that `.env.example` is currently incomplete — it predates several services. Use the
tables in section 3 as the authoritative list; the ones missing from the example file are
marked ⚠️.

### 2.3 Everything else is automatic

These run as compose services on `up`; **do not run them manually**:

- `postgres-setup` — creates `agreement_db`, `keycloak_db`, `dataholder_db`, `dhgroupadmin_db`
  if absent, then Keycloak waits on it.
- `keycloak-setup` — creates the OIDC client, event config, group mapper and Jaddar roles.
- Schema migrations — Flyway/Hibernate run at backend startup.
- The RDAP dump is baked into the `rdap-mariadb-init` image and loads on first boot.

Two scripts in the repo are **legacy and referenced by nothing** — ignore them:
`init-multiple-dbs.sh` (superseded by `postgres-setup`) and `setup-keycloak-client.sh`
(superseded by `keycloak-setup`, and it hardcodes dev credentials).

---

## 3. Environment variables

Legend: **Req** = must be set, no default and startup fails or misbehaves without it ·
**Def** = has a compose default you can usually leave alone · ⚠️ = missing from `.env.example`.

### Keycloak / identity

| Variable | | What it does |
|---|---|---|
| `KEYCLOAK_ADMIN` | Req | Keycloak bootstrap admin username, also used by backends for admin-API calls. |
| `KEYCLOAK_ADMIN_PASSWORD` | Req | Password for that admin account. |
| `KC_HOSTNAME_URL` | Req | Public base URL Keycloak advertises in issuer/redirect metadata (e.g. `https://auth.example.org`). |
| `JADDAR_CLIENT_ID` | Req | OIDC client ID shared by all four backends and created by `keycloak-setup`. |
| `JADDAR_CLIENT_SECRET` | Req | Client secret for that OIDC client. |
| `JADDAR_REDIRECT_URI` | Req | OAuth callback URL registered for the client (e.g. `https://app.example.org/callback`). |
| `JADDAR_WEB_ORIGIN` | Req | Browser origin allowed as a CORS/web origin on the OIDC client. |
| `JADDAR_KEYCLOAK_AUTH_URL` | Def | Internal realm URL the backend uses for token calls — defaults to `https://keycloak:8443/realms/master`. |
| `JADDAR_JWKS_URL` | Def | Internal realm URL used to fetch signing keys — defaults to `http://keycloak:8080/realms/master`. |
| `JADDAR_PUBLIC_AUTH_URL` | Req | Browser-facing realm URL; must be reachable from the user's browser, not the docker hostname. |

### Database

| Variable | | What it does |
|---|---|---|
| `POSTGRES_USER` | Req | Postgres superuser owning every application database. |
| `POSTGRES_PASSWORD` | Req | Password for that user; also used by Keycloak's datastore. |
| `POSTGRES_DB` | Req | Name of the initial database created at cluster init (Jaddar's own DB). |
| `RDAP_MARIADB_ROOT_PASSWORD` | Req ⚠️ | Root password for the RDAP test MariaDB. |
| `RDAP_MARIADB_USER` | Def ⚠️ | Application user for the RDAP database — defaults to `rdap`. |
| `RDAP_MARIADB_PASSWORD` | Req ⚠️ | Password for that user; also what you enter in the Data Mapping UI. |
| `RDAP_MARIADB_PORT` | Def ⚠️ | Host port the RDAP MariaDB is published on — defaults to `3307`. |

### Mutual TLS

| Variable | | What it does |
|---|---|---|
| `MTLS_ENABLED` | Def | Turns on the backends' mTLS listeners and peer verification — keep `true`. |
| `MTLS_KEYSTORE_PASSWORD` | Def | Password for the per-service `.p12` keystores; must match what the cert script used. |
| `MTLS_TRUSTSTORE_PASSWORD` | Def | Password for the per-app truststores; must match what the cert script used. |
| `SCANNER_MTLS_ENABLED` | Def ⚠️ | Separate mTLS toggle for the document scanner so it can boot before its certs exist — defaults to `false`. |

> Leaving `MTLS_ENABLED=false` also breaks Keycloak token introspection: the backends' trust
> of Keycloak's HTTPS listener rides on the same truststore.

### Jaddar (main application)

| Variable | | What it does |
|---|---|---|
| `JADDAR_REQUESTOR_AGENT_ID` | Req | Identifier this deployment presents as the requesting agent in RDAP requests. |
| `JADDAR_FRONTEND_URL` | Req | Public base URL of the Jaddar UI, used to build password-reset links. |
| `REACT_APP_API_URL` | Req | Browser-facing URL of the Jaddar backend API. |
| `REACT_APP_AUTH_URL` | Req | Browser-facing Keycloak URL used by the Jaddar UI. |
| `REACT_APP_KEYCLOAK_ADMIN_URL` | Req ⚠️ | Base URL the UI links to for the Keycloak admin console. |
| `REACT_APP_VERSION` | Def ⚠️ | Version string displayed in the UI footer. |
| `ADMIN_EMAIL` | Def ⚠️ | Email treated as the bootstrap admin account — defaults to `admin@admin.com`. |
| `RATE_LIMIT_RPM` | Def ⚠️ | Per-client request-per-minute cap on the Jaddar backend — defaults to `240`. |

### Requestor Manager

| Variable | | What it does |
|---|---|---|
| `REQUESTOR_MANAGER_JWT_SECRET` | Req | Signing secret for Requestor Manager's own JWTs (use ≥256 bits of random). |
| `REQUESTOR_MANAGER_MASTER_EMAIL` | Req | Email of the master account seeded on first run. |
| `REQUESTOR_MANAGER_MASTER_PASSWORD` | Req | Password for that master account. |
| `REQUESTOR_MANAGER_FRONTEND_URL` | Req | Public base URL of the RM UI, used for password-reset links. |
| `REQUESTOR_MANAGER_FRONTEND_API_URL` | Req ⚠️ | Browser-facing URL the RM UI calls for its API. |
| `REGISTRY_DIRECTORY_CACHE_SECONDS` | Def ⚠️ | How long a fetched data holder group feed is reused before re-fetching — defaults to `300`. |
| `REGISTRY_DIRECTORY_TIMEOUT_SECONDS` | Def ⚠️ | Connect/read timeout when fetching that feed — defaults to `10`. |

### Data Holder

| Variable | | What it does |
|---|---|---|
| `DATAHOLDER_JWT_SECRET` | Req | Signing secret for Data Holder's JWTs — startup **fails** if unset. |
| `DATAHOLDER_ADMIN_USERNAME` | Def | Local admin username seeded on first run — defaults to `admin`. |
| `DATAHOLDER_ADMIN_PASSWORD` | Req | Password for that local admin. |
| `DATAHOLDER_CORS_ORIGINS` | Req ⚠️ | Comma-separated browser origins allowed to call the Data Holder API. |
| `DATAHOLDER_FRONTEND_URL` | Req | Public base URL of the DH UI, used for password-reset links. |
| `DATAHOLDER_FRONTEND_API_URL` | Req ⚠️ | Browser-facing URL the DH UI calls for its API. |
| `DATAHOLDER_URL` | Def ⚠️ | Internal container URL other services use to reach the Data Holder — defaults to `http://dataholder-backend:8082`. |
| `DATAHOLDER_NAME` | Def ⚠️ | Display name of this data holder — defaults to `Test Data Holder`. |
| `DATAHOLDER_ID` | Def ⚠️ | Identifier this data holder reports to the group admin — defaults to `TDH-001`. |
| `DATAHOLDER_ACCESS_LEVEL` | Def ⚠️ | Default RDAP access level granted to new requestors — defaults to `1`. |
| `DATAHOLDER_REQUIRE_AGREEMENT` | Def ⚠️ | Whether an approved agreement is required before serving data — defaults to `true`. |
| `DATAHOLDER_MANUAL_VERIFICATION` | Def ⚠️ | Whether requests need a human approval step — defaults to `false`. |
| `DATAHOLDER_MANUAL_VERIFICATION_LEVEL` | Def ⚠️ | Access level at or above which manual verification kicks in — defaults to `3`. |
| `DATAHOLDER_NOTIFICATION_ENABLED` | Def | Master switch for outbound request notifications — defaults to `true`. |
| `DATAHOLDER_NOTIFICATION_MAIL_ENDPOINT` | Req | HTTP endpoint the backend posts notification emails to. |
| `DATAHOLDER_NOTIFICATION_FROM_EMAIL` | Def | From-address on those notifications. |
| `DATAHOLDER_NOTIFICATION_FROM_NAME` | Def | From-name on those notifications. |

### DH Group Admin (Jareg) and provisioning

| Variable | | What it does |
|---|---|---|
| `DHG_JWT_SECRET` | Req | Signing secret for DH Group Admin's JWTs — startup **fails** if unset. |
| `DHG_ADMIN_DEFAULT_EMAIL` | Req | Email of the admin seeded on first run, only when the database is empty. |
| `DHG_ADMIN_DEFAULT_PASSWORD` | Req | Password for that seeded admin. |
| `DHG_ADMIN_FRONTEND_URL` | Req | Public base URL of the group-admin UI, used for password-reset links. |
| `DH_GROUP_ADMIN_FRONTEND_API_URL` | Req ⚠️ | Browser-facing URL the group-admin UI calls for its API. |
| `DH_GROUP_ADMIN_CORS_ORIGINS` | Req ⚠️ | Comma-separated origins allowed to call the group-admin API. |
| `DHG_PROVISIONER_SECRET` | Req | Shared secret authenticating the group admin to the provisioning sidecar (≥16 chars). |
| `DHG_PARENT_DOMAIN` | Req | Parent domain under which provisioned data-holder instances get their hostnames. |
| `PROVISIONER_URL` | Def ⚠️ | Internal URL of the provisioning sidecar — defaults to `https://provisioning-sidecar:9500`. |
| `DHG_ENCRYPTION_KEY` | Def ⚠️ | Key encrypting the common-repository announce token at rest; falls back to `DHG_JWT_SECRET`. Set it, or rotating the JWT secret leaves the token undecryptable. |
| `REGISTRY_PUBLICATION_INTERVAL_MS` | Def ⚠️ | How often the group admin re-announces itself to the common repository — defaults to `900000` (15 min). |
| `REGISTRY_PUBLICATION_INITIAL_DELAY_MS` | Def ⚠️ | Delay before the first announcement after startup — defaults to `120000`. |
| `REGISTRY_PUBLICATION_TIMEOUT_SECONDS` | Def ⚠️ | Connect/read timeout on announcements — defaults to `10`. |
| `COMPOSE_PROJECT_NAME` | Def ⚠️ | Compose project name; the sidecar derives the docker network name from it — defaults to `icann`. |

> The provisioning sidecar mounts the host docker socket so it can spawn data-holder
> containers. If you override `COMPOSE_PROJECT_NAME` (or use `-p`), it must match, or the
> sidecar will attach new containers to a network that does not exist.

### Data Holder Registry

Publishes a list of RDAP data holders and the coverage each one answers for, in the spirit
of IANA's RDAP bootstrap registries. The feeds under `/dataholder-registry/registry` are
public and unauthenticated; the admin API behind them has its own accounts (master / admin /
user), separate from Keycloak.

Jaddar consults a registry only when an operator turns it on under **Admin → Data Holders**;
locally configured data holders always take precedence, and IANA remains the final fallback.

The registry doubles as the **common repository** for data holder groups: a DH Group Admin
announces *itself* — the whole deployment, as one data holder group registry — under
**Settings → Common Repository**, carrying its data holder groups and their permitted
templates as a list. The announcement lands as a pending entry under **Group Registries** in
the registry UI, and once an administrator there approves it the registry appears in
`/registry/group-registries.json`. A Requestor Manager points at that feed under **Data
Holder Groups → Common repository** and registers what it finds. See section 6.

| Variable | | What it does |
|---|---|---|
| `REGISTRY_JWT_SECRET` | Req | Signing secret for the registry's JWTs (≥32 bytes) — startup **fails** if unset. |
| `REGISTRY_ADMIN_DEFAULT_EMAIL` | Req | Email of the master account seeded on first run, only when no accounts exist. |
| `REGISTRY_ADMIN_DEFAULT_PASSWORD` | Def | Password for that account; when blank one is generated and logged once. |
| `REGISTRY_FRONTEND_URL` | Req | Public base URL of the registry UI, used for password-reset links. |
| `REGISTRY_PUBLIC_URL` | Req | Publicly reachable base URL of the feed directory — shown on the dashboard and what Jaddar is pointed at. |
| `DATAHOLDER_REGISTRY_CORS_ORIGINS` | Req ⚠️ | Production only: comma-separated origins allowed to call the registry admin API. |
| `REGISTRY_FEED_DESCRIPTION` | Def ⚠️ | Description published in every feed header — defaults to `Data Holder Registry`. |
| `REGISTRY_FEED_CACHE_SECONDS` | Def ⚠️ | How long a consumer may reuse a fetched feed — defaults to `300`. |
| `REGISTRY_CACHE_SECONDS` | Def ⚠️ | Jaddar-side: how long a fetched feed is reused before re-fetching — defaults to `300`. |
| `DATAHOLDER_REGISTRY_PORT` | Def ⚠️ | Dev only: host port for the registry backend — defaults to `8084`. |

Published feeds, all `GET` and unauthenticated:

| Path | Shape |
|---|---|
| `/registry/dataholders.json` | Expanded — full records including name, description, `requiresAuth`. |
| `/registry/group-registries.json` | Data holder group registries that announced themselves and were approved here, each carrying its data holder groups and the templates each group set to Globally published. |
| `/registry/dns.json` | IANA bootstrap shape (RFC 9224), TLD coverage. |
| `/registry/ipv4.json`, `/registry/ipv6.json` | IANA bootstrap shape, IP coverage. |
| `/registry/asn.json` | IANA bootstrap shape, ASN coverage. |
| `/registry` | Index listing the above. |

### Document scanner sidecar

| Variable | | What it does |
|---|---|---|
| `DOCUMENT_SCANNER_SECRET` | Req | Shared secret between the Data Holder backend and the scanner (≥16 chars); **both** refuse to start without it. |
| `SCANNER_MAX_FILE_MB` | Def ⚠️ | Largest upload the scanner will accept, in MB — defaults to `25`. |
| `SCANNER_MAX_OCR_PAGES` | Def ⚠️ | Page cap for OCR on a single document — defaults to `10`. |
| `SCANNER_OCR_DPI` | Def ⚠️ | Rasterisation DPI used before OCR — defaults to `300`. |
| `SCANNER_WORKERS` | Def ⚠️ | Number of scanner worker processes — defaults to `2`. |

### Frontend sessions (all five UIs)

| Variable | | What it does |
|---|---|---|
| `SESSION_SECRET` | Req ⚠️ | Key that signs the httpOnly session cookie and encrypts the JWTs inside it (AES-256-GCM). |

Use ≥32 random characters. In production compose this is `:?`-guarded, so an unset value
aborts the whole `up`; the dev compose falls back to an insecure placeholder. It accepts a
comma-separated list for rotation — the first entry is the active key, the rest still verify.
Note that sessions live in an in-process `Map`, so they are lost on restart and a shared store
would be needed before running multiple replicas of a frontend.

### Licensing (all five UIs)

| Variable | | What it does |
|---|---|---|
| `SOURCE_CODE_URL` | Def | Public URL of the Corresponding Source, linked from every footer. |

AGPL-3.0 §13 requires that users interacting with this software over a network be offered
the source of the **running** version. When this is unset the footer link is omitted rather
than rendered broken, so the stack still starts — but a public deployment left unset is not
§13-compliant. It must point at source that is reachable at no charge, so a private
repository URL does not satisfy the requirement. **If you deploy a modified version, point
this at the source of your version, not the upstream project.**

### Email (SendGrid — shared by all backends)

| Variable | | What it does |
|---|---|---|
| `SENDGRID_API_KEY` | Def | SendGrid API key; leave blank in dev and reset emails are logged instead of sent. |
| `SENDGRID_FROM_EMAIL` | Req | From-address on password-reset emails. |
| `SENDGRID_FROM_NAME` | Def | From-name on those emails; each service has its own default. |
| `PASSWORD_RESET_EXPIRY_MINUTES` | Def | Lifetime of a password-reset link in minutes — defaults to `60`. |

### Development-only (host port overrides, `docker-compose.yml`)

| Variable | Default | What it does |
|---|---|---|
| `KEYCLOAK_PORT` | `8080` | Host port for Keycloak. |
| `JADDAR_BACKEND_PORT` | `8000` | Host port for the Jaddar backend. |
| `JADDAR_FRONTEND_PORT` | `3000` | Host port for the Jaddar UI. |
| `REQUESTOR_MANAGER_SERVER_PORT` | `8081` | Host port for the Requestor Manager backend. |
| `DH_GROUP_ADMIN_PORT` | `8083` | Host port for the DH Group Admin backend. |

Fixed dev ports (not configurable): DH UI `3001`, RM UI `3002`, group-admin UI `3003`,
Data Holder backend `8082`, mTLS listeners `8481`/`8482`/`8483`, JVM debug `5005`–`5007`,
provisioning sidecar `9500`, document scanner `9600`.

---

## 4. Starting the stack

### Development

```bash
scripts/generate-mtls-certs.sh     # once
cp .env.example .env               # then fill it in
docker compose up -d --build
```

`docker-compose.yml` builds from source and mounts source directories for hot reload.

| UI | URL |
|---|---|
| Jaddar | http://localhost:3000 |
| Data Holder | http://localhost:3001 |
| Requestor Manager | http://localhost:3002 |
| DH Group Admin | http://localhost:3003 |
| Data Holder Registry | http://localhost:3004 |
| Keycloak | http://localhost:8080 |

### Production

Three compose files, by intent:

- `docker-compose.prod-build.yml` — builds all images from source on the target host.
- `docker-compose.prod-deploy.yml` — what CI deploys; runs pre-built `:latest` images with
  `--no-build`, and publishes every port on `127.0.0.1` only, behind the host's nginx
  (`nginx-jaddar.conf` handles TLS termination and routing).
- `docker-compose.prod-image.yml` — minimal registry-image variant.

```bash
# On a host with no images yet — build first, then run
docker compose -f docker-compose.prod-build.yml --env-file .env.production build
docker compose -p jaddar-deployment -f docker-compose.prod-deploy.yml \
  --env-file .env.production up -d --no-build
```

CI (`.github/workflows/deploy.yml`) pushes images to GHCR, pulls only the ones whose source
changed, retags them `:latest`, and recreates just those services. It does **not** create
`.env.production` or run the certificate script — both must already exist on the server at
`/opt/jaddar`.

---

## 5. Verifying

```bash
docker compose ps                        # every service Up; the *-setup ones Exited (0)
docker compose logs postgres-setup       # "===== ALL DATABASES READY ====="
docker compose logs keycloak-setup       # client/roles created
docker compose logs -f dataholder-backend
```

Common startup failures:

| Symptom | Cause |
|---|---|
| Compose aborts naming a variable | A `:?`-guarded value is unset: `SESSION_SECRET`, `DOCUMENT_SCANNER_SECRET`, `DHG_JWT_SECRET`, `DATAHOLDER_JWT_SECRET`. |
| Postgres or MariaDB will not start | `certs/` is missing — run `scripts/generate-mtls-certs.sh`. |
| Backend PKIX / SSL handshake errors | Keystore password mismatch between `.env` and the certs, or certs regenerated without restarting the backends. |
| Login redirects to the wrong host | `JADDAR_PUBLIC_AUTH_URL` points at the internal docker hostname instead of a browser-reachable URL. |
| Provisioned data holders fail to appear | `COMPOSE_PROJECT_NAME` does not match the running project, so the sidecar targets a nonexistent network. |

---

## 6. Group discovery and template visibility

Requestor Managers used to learn about a data holder group only by having an operator type
its code and base URL in. A group can now announce itself to a **common repository** — the
Data Holder Registry, wearing its second hat — and a Requestor Manager reads that
repository's feed instead.

### The two controls

Templates carry two independent settings.

**Active / Inactive** (formerly "Published") is whether the template is offered for *new*
subscriptions. Turning a template inactive hides it from every list and refuses new
subscriptions, and **does not touch subscriptions already in place** — those run on the
snapshot pinned to them when they were agreed.

**Visibility** is how far the template is advertised:

| Level | Where it appears |
|---|---|
| Private | Nowhere. Reachable only by someone who knows the template id, or who is already subscribed. |
| Public | In this group admin's own template list, when someone looks the group up. Never forwarded to the common repository. |
| Globally published | In this group admin's list **and** in the common repository, where any Requestor Manager can discover it. |

A data holder group has the same three levels, and the group's level is a **ceiling** on its
templates'. A template set to Globally published inside a group that is only Public stays
public; nothing under a Private group is listed at all. That is what lets a group's master
and admins decide what may reach the global repository, whoever wrote the template. The
templates screen shows the effective level and says which setting capped it.

Template ids generated from now on carry a random suffix, because a Private template's id is
what stands between it and the world. Existing ids are unchanged.

### What gets announced

A DH Group Admin deployment is multi-tenant, so the unit announced is the **deployment as a
whole** — one data holder group registry, with a code and name of its own. Under that name
goes a list of its data holder groups, each carrying the templates that group permits over
there:

```
group registry  EXAMPLE-REG  "Example Group Registry"   https://groups.example.org/dh-group-admin
  ├── data holder group  "Registrars"        (GLOBAL)
  │     ├── template  "Law Enforcement Access"    (GLOBAL)
  │     └── template  "Brand Protection"          (GLOBAL)
  └── data holder group  "Registries"        (GLOBAL)
        └── template  "Security Research"         (GLOBAL)
```

Only GLOBAL groups are listed, and within them only GLOBAL templates. A group with no
globally published templates is still listed on its own — it is a real answer to "who is
behind this registry?". A template marked GLOBAL under a group that is not itself GLOBAL has
nowhere to be listed and never appears, which is the ceiling rule doing its job.

Each published template carries enough for a requestor to judge it before going near it: its
**request types** with the access level each grants, whether confidential and exigent
requests are supported, whether manual approval applies, and the custom parameters that
request type would make them supply; the query limits; the values required at subscription;
and the titles of the terms they would have to accept. Legal text itself is not published —
the terms are read and accepted at subscription time against the copy pinned to that
subscription, so a copy in a public directory would carry no authority.

An announcement is untrusted input on an unauthenticated endpoint, so the registry does not
store it verbatim: every level is rebuilt from a field allow-list and bounded (200 groups,
500 templates per group, 50 request types per template, 50 parameters per request type).
Anything a future group admin invents and sends is dropped until that allow-list learns
about it.

### Announcing a registry

1. **DH Group Admin → Settings → Common Repository** (master only): turn publication on,
   give it the repository URL, the registry code to claim, the registry's name, and the
   public API base URL a Requestor Manager should call. The preview panel lists exactly what
   would be sent, group by group.
2. The first announcement claims the code and the repository returns a one-time announce
   token, stored encrypted here and presented on every later announcement, so nobody else
   can overwrite the entry. A scheduler re-announces every 15 minutes.
3. **Registry → Group Registries**: the entry arrives as *Awaiting review*; opening it shows
   the groups and templates it announced. Approving it puts it in
   `/registry/group-registries.json`; an approved entry can also be withheld without being
   rejected. Master accounts can clear an entry's announce token — the recovery path when a
   deployment loses it — or delete it.
4. **Requestor Manager → Data Holder Groups → Common repository**: point it at the feed and
   turn it on. Each discovered registry is one row, expandable to the groups behind it, with
   a Register button; registering creates the same local record that adding one by hand
   does. Registries already registered locally keep precedence and are never overwritten —
   where the local base URL differs from the feed's, the row says so and the local value is
   what is used.

Nothing leaves a host until an operator turns publication on, and nothing enters a feed until
a registry administrator approves it.

---

## 7. License

Copyright © 2025–2026 Edgemoor Research Institute and Derek Jenkins.

This program is free software: you can redistribute it and/or modify it under the terms of
the **GNU Affero General Public License, version 3**, as published by the Free Software
Foundation. The full text is in [`License`](License).

It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the license
for details.

The AGPL applies to network use: if you run a modified version of this software and make it
available to users over a network, you must offer those users the corresponding source of
your modified version.

**Additional terms under Section 7.** This work carries two additional requirements permitted
by AGPL-3.0 §7 — preservation of author attributions (§7(b)) and a prohibition on
misrepresenting the origin of the work (§7(c)). They are stated in full in [`NOTICE`](NOTICE)
and summarized in [`AUTHORS.md`](AUTHORS.md). Both must be carried forward into derivative
works.
