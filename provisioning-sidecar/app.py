# SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
# SPDX-License-Identifier: AGPL-3.0-only
#
# Author: Derek Jenkins <derek@pure-code.net>
# Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.

"""
Provisioning Sidecar — orchestrates creation, management, and teardown
of data holder Docker instances, Nginx configs, and databases.

Runs inside Docker with mounts to:
  - /var/run/docker.sock  (Docker Engine API)
  - /host-nginx-conf      (host /etc/nginx/conf.d)
  - /host-letsencrypt      (host /etc/letsencrypt)

Called by the Jareg (DH Group Admin) backend over the Docker network.
"""

import os
import hmac
import re
import time
import json
import uuid
import logging
import subprocess
from datetime import datetime, timezone

import docker
import psycopg2
from flask import Flask, request, jsonify

app = Flask(__name__)
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("provisioner")

# --- Config from env ---
# Fail-fast: no insecure default. This secret guards Docker-socket-level operations.
SHARED_SECRET = os.getenv("DHG_PROVISIONER_SECRET")
if not SHARED_SECRET or len(SHARED_SECRET) < 16:
    raise RuntimeError("DHG_PROVISIONER_SECRET must be set to a strong (>=16 char) value; refusing to start.")
PARENT_DOMAIN = os.getenv("DHG_PARENT_DOMAIN", "")  # configure per deployment; no domain default
DOCKER_NETWORK = os.getenv("DOCKER_NETWORK", "")  # auto-discovered if blank
POSTGRES_HOST = os.getenv("POSTGRES_HOST", "postgres")
POSTGRES_PORT = int(os.getenv("POSTGRES_PORT", "5432"))
POSTGRES_USER = os.getenv("POSTGRES_USER", "appuser")
POSTGRES_PASSWORD = os.getenv("POSTGRES_PASSWORD", "")
# TLS to Postgres. Default "disable" for a bare local run; compose sets
# DB_SSLMODE=verify-ca + DB_SSLROOTCERT=/certs/infra-ca.crt to verify the CA.
DB_SSLMODE = os.getenv("DB_SSLMODE", "disable")
DB_SSLROOTCERT = os.getenv("DB_SSLROOTCERT", "")
KEYCLOAK_AUTH_SERVER_URL = os.getenv("KEYCLOAK_AUTH_SERVER_URL", "https://keycloak:8443")
KEYCLOAK_INTROSPECT_URL = os.getenv("KEYCLOAK_INTROSPECT_URL", "http://keycloak:8080/realms/master/protocol/openid-connect/token/introspect")
KEYCLOAK_CLIENT_ID = os.getenv("KEYCLOAK_CLIENT_ID", "jaddar-app")
KEYCLOAK_CLIENT_SECRET = os.getenv("KEYCLOAK_CLIENT_SECRET", "")
DH_GROUP_ADMIN_URL = os.getenv("DH_GROUP_ADMIN_URL", "http://dh-group-admin-backend:8083/dh-group-admin")

# Image names (built by the main docker-compose)
# These can be overridden via env, or auto-discovered from existing containers at startup
DH_BACKEND_IMAGE = os.getenv("DH_BACKEND_IMAGE", "")
DH_FRONTEND_IMAGE = os.getenv("DH_FRONTEND_IMAGE", "")

# Port ranges for dynamic allocation
BACKEND_PORT_START = int(os.getenv("BACKEND_PORT_START", "8090"))
FRONTEND_PORT_START = int(os.getenv("FRONTEND_PORT_START", "3010"))

NGINX_CONF_DIR = "/host-nginx-conf"
TEMPLATE_PATH = os.path.join(os.path.dirname(__file__), "nginx_template.conf")

client = docker.from_env()


def _discover_network():
    """Auto-discover the Docker network this container is on."""
    global DOCKER_NETWORK
    if DOCKER_NETWORK:
        log.info(f"Using configured network: {DOCKER_NETWORK}")
        return

    # Find our own container and check what network it's on
    try:
        hostname = os.environ.get("HOSTNAME", "")
        if hostname:
            me = client.containers.get(hostname)
            networks = me.attrs.get("NetworkSettings", {}).get("Networks", {})
            for net_name in networks:
                if "app-network" in net_name or "default" not in net_name:
                    DOCKER_NETWORK = net_name
                    log.info(f"Auto-discovered network: {DOCKER_NETWORK}")
                    return
            # Fallback: use first non-default network
            for net_name in networks:
                if net_name != "bridge":
                    DOCKER_NETWORK = net_name
                    log.info(f"Auto-discovered network (fallback): {DOCKER_NETWORK}")
                    return
    except Exception as e:
        log.warning(f"Could not auto-discover network from container: {e}")

    # Last resort: search for a network with 'app-network' in the name
    try:
        for net in client.networks.list():
            if "app-network" in net.name:
                DOCKER_NETWORK = net.name
                log.info(f"Auto-discovered network (search): {DOCKER_NETWORK}")
                return
    except Exception as e:
        log.warning(f"Network search failed: {e}")

    DOCKER_NETWORK = "bridge"
    log.warning(f"Could not discover network, falling back to: {DOCKER_NETWORK}")


# Discover on startup
_discover_network()


def _discover_images():
    """Auto-discover DH image names from the running dataholder containers."""
    global DH_BACKEND_IMAGE, DH_FRONTEND_IMAGE

    if DH_BACKEND_IMAGE and DH_FRONTEND_IMAGE:
        return

    log.info("Discovering DH images from running containers...")

    try:
        all_containers = client.containers.list(all=True)
        log.info(f"Found {len(all_containers)} containers to scan")

        for c in all_containers:
            name = c.name
            try:
                img = c.image
                tags = img.tags
                image_ref = tags[0] if tags else img.id
            except Exception as e:
                log.debug(f"Could not get image for container {name}: {e}")
                continue

            if not DH_BACKEND_IMAGE and "dataholder" in name and "frontend" not in name and "provisioning" not in name:
                DH_BACKEND_IMAGE = image_ref
                log.info(f"Auto-discovered backend image from '{name}': {DH_BACKEND_IMAGE}")

            if not DH_FRONTEND_IMAGE and "dataholder-frontend" in name:
                DH_FRONTEND_IMAGE = image_ref
                log.info(f"Auto-discovered frontend image from '{name}': {DH_FRONTEND_IMAGE}")
    except Exception as e:
        log.error(f"Image discovery failed: {e}", exc_info=True)

    # If still not found, try searching images directly by name patterns
    if not DH_BACKEND_IMAGE or not DH_FRONTEND_IMAGE:
        try:
            for img in client.images.list():
                for tag in (img.tags or []):
                    if not DH_BACKEND_IMAGE and "dataholder" in tag and "frontend" not in tag:
                        DH_BACKEND_IMAGE = tag
                        log.info(f"Auto-discovered backend image from image list: {DH_BACKEND_IMAGE}")
                    if not DH_FRONTEND_IMAGE and "dataholder-frontend" in tag:
                        DH_FRONTEND_IMAGE = tag
                        log.info(f"Auto-discovered frontend image from image list: {DH_FRONTEND_IMAGE}")
        except Exception as e:
            log.error(f"Image list search failed: {e}")

    if not DH_BACKEND_IMAGE:
        log.warning("Could not discover backend image — will fail at provisioning time")
    if not DH_FRONTEND_IMAGE:
        log.warning("Could not discover frontend image — will fail at provisioning time")

    log.info(f"Image discovery result: backend={DH_BACKEND_IMAGE or 'NOT FOUND'}, frontend={DH_FRONTEND_IMAGE or 'NOT FOUND'}")


def _get_backend_image():
    """Get the backend image, discovering if needed."""
    if not DH_BACKEND_IMAGE:
        _discover_images()
    if not DH_BACKEND_IMAGE:
        raise RuntimeError(
            "Cannot find data holder backend image. "
            "Make sure dataholder container is running or set DH_BACKEND_IMAGE env var. "
            "Run 'docker images | grep dataholder' to find the correct image name."
        )
    return DH_BACKEND_IMAGE


def _get_frontend_image():
    """Get the frontend image, discovering if needed."""
    if not DH_FRONTEND_IMAGE:
        _discover_images()
    if not DH_FRONTEND_IMAGE:
        raise RuntimeError(
            "Cannot find data holder frontend image. "
            "Make sure dataholder-frontend container is running or set DH_FRONTEND_IMAGE env var. "
            "Run 'docker images | grep dataholder' to find the correct image name."
        )
    return DH_FRONTEND_IMAGE


# Try discovery at startup (non-fatal if it fails)
try:
    _discover_images()
except Exception as e:
    log.warning(f"Startup image discovery failed (will retry at provision time): {e}")


@app.errorhandler(Exception)
def handle_exception(e):
    log.error(f"Unhandled exception: {e}", exc_info=True)
    return jsonify({"success": False, "error": "An unexpected error occurred"}), 500


# ─── Auth ───────────────────────────────────────────────────────────
def _check_auth():
    token = request.headers.get("X-Provisioner-Secret", "")
    if not hmac.compare_digest(token, SHARED_SECRET):
        return jsonify({"success": False, "error": "Unauthorized"}), 401
    return None


# ─── Helpers ────────────────────────────────────────────────────────
def _sanitize_subdomain(s):
    """Allow only lowercase alphanumeric and hyphens, 3–30 chars."""
    s = s.strip().lower()
    s = re.sub(r"[^a-z0-9\-]", "", s)
    if not s or len(s) < 3 or len(s) > 30:
        return None
    if s.startswith("-") or s.endswith("-"):
        return None
    return s


def _db_name(subdomain):
    return f"dh_{subdomain.replace('-', '_')}_db"


def _container_name(subdomain, role):
    return f"dh-{subdomain}-{role}"


def _get_pg_conn(database="postgres"):
    kwargs = dict(
        host=POSTGRES_HOST, port=POSTGRES_PORT,
        user=POSTGRES_USER, password=POSTGRES_PASSWORD,
        database=database,
    )
    if DB_SSLMODE and DB_SSLMODE != "disable":
        kwargs["sslmode"] = DB_SSLMODE
        if DB_SSLROOTCERT:
            kwargs["sslrootcert"] = DB_SSLROOTCERT
    return psycopg2.connect(**kwargs)


def _find_used_ports():
    """Scan running/stopped DH containers to find used port mappings."""
    used_backend = set()
    used_frontend = set()
    try:
        for c in client.containers.list(all=True, filters={"name": "dh-"}):
            ports = c.attrs.get("HostConfig", {}).get("PortBindings", {}) or {}
            for container_port, bindings in ports.items():
                if not bindings:
                    continue
                for b in bindings:
                    hp = int(b.get("HostPort", 0))
                    if "8082/tcp" in container_port or "8082" in container_port:
                        used_backend.add(hp)
                    elif "3000/tcp" in container_port or "3000" in container_port:
                        used_frontend.add(hp)
    except Exception as e:
        log.warning(f"Error scanning containers for ports: {e}")
    return used_backend, used_frontend


def _allocate_ports():
    used_be, used_fe = _find_used_ports()
    # Also reserve the static dataholder ports
    used_be.update({8082})
    used_fe.update({3001})
    bp = BACKEND_PORT_START
    while bp in used_be:
        bp += 1
    fp = FRONTEND_PORT_START
    while fp in used_fe:
        fp += 1
    return bp, fp


def _write_nginx_conf(subdomain, backend_port, frontend_port, name):
    with open(TEMPLATE_PATH, "r") as f:
        template = f.read()
    conf = template.replace("{{ subdomain }}", subdomain)
    conf = conf.replace("{{ parent_domain }}", PARENT_DOMAIN)
    conf = conf.replace("{{ backend_port }}", str(backend_port))
    conf = conf.replace("{{ frontend_port }}", str(frontend_port))
    conf = conf.replace("{{ name }}", name)
    conf = conf.replace("{{ created_at }}", datetime.now(timezone.utc).isoformat())
    conf_path = os.path.join(NGINX_CONF_DIR, f"dh-{subdomain}.conf")
    with open(conf_path, "w") as f:
        f.write(conf)
    log.info(f"Wrote nginx config: {conf_path}")
    return conf_path


def _remove_nginx_conf(subdomain):
    conf_path = os.path.join(NGINX_CONF_DIR, f"dh-{subdomain}.conf")
    if os.path.exists(conf_path):
        os.remove(conf_path)
        log.info(f"Removed nginx config: {conf_path}")


def _reload_nginx():
    try:
        result = subprocess.run(
            ["nginx", "-t"],
            capture_output=True, text=True, timeout=10
        )
        if result.returncode != 0:
            log.error(f"Nginx config test failed: {result.stderr}")
            return False, result.stderr
        subprocess.run(["nginx", "-s", "reload"], capture_output=True, text=True, timeout=10)
        log.info("Nginx reloaded")
        return True, None
    except Exception as e:
        log.error(f"Nginx reload failed: {e}")
        return False, str(e)


def _obtain_ssl_cert(subdomain):
    """Attempt to get an SSL cert. Non-fatal if it fails (wildcard cert may cover it)."""
    domain = f"{subdomain}.{PARENT_DOMAIN}"
    try:
        result = subprocess.run(
            ["certbot", "certonly", "--nginx", "-d", domain,
             "--non-interactive", "--agree-tos", "--register-unsafely-without-email",
             "--keep-until-expiring"],
            capture_output=True, text=True, timeout=120
        )
        if result.returncode == 0:
            log.info(f"SSL cert obtained for {domain}")
            return True
        else:
            log.warning(f"Certbot returned {result.returncode}: {result.stderr}")
            return False
    except Exception as e:
        log.warning(f"SSL cert attempt failed (non-fatal): {e}")
        return False


# ─── Create Instance ────────────────────────────────────────────────
@app.route("/api/provision", methods=["POST"])
def provision_instance():
    auth_err = _check_auth()
    if auth_err:
        return auth_err

    data = request.get_json(force=True)
    name = data.get("name", "").strip()
    subdomain = _sanitize_subdomain(data.get("subdomain", ""))
    dh_id = data.get("dataholderId", "")
    dh_group_id = data.get("dataHolderGroupId")
    admin_password = data.get("adminPassword") or f"dh-{uuid.uuid4().hex[:16]}"
    if PARENT_DOMAIN == "localhost":
        cors_origins = data.get("corsOrigins", f"http://localhost:{frontend_port}")
    else:
        cors_origins = data.get("corsOrigins", f"https://{subdomain}.{PARENT_DOMAIN}")

    if not name:
        return jsonify({"success": False, "error": "Name is required"}), 400
    if not subdomain:
        return jsonify({"success": False, "error": "Invalid subdomain (3-30 lowercase alphanumeric/hyphens)"}), 400

    # Check for existing containers
    for role in ["backend", "frontend"]:
        cname = _container_name(subdomain, role)
        try:
            existing = client.containers.get(cname)
            return jsonify({"success": False, "error": f"Container {cname} already exists (status: {existing.status})"}), 409
        except docker.errors.NotFound:
            pass

    # 1. Create database
    db_name = _db_name(subdomain)
    log.info(f"Creating database: {db_name}")
    try:
        conn = _get_pg_conn()
        conn.autocommit = True
        with conn.cursor() as cur:
            cur.execute(f"SELECT 1 FROM pg_database WHERE datname = %s", (db_name,))
            if cur.fetchone():
                return jsonify({"success": False, "error": f"Database {db_name} already exists"}), 409
            cur.execute(f'CREATE DATABASE "{db_name}"')
        conn.close()
        log.info(f"Database {db_name} created")
    except Exception as e:
        log.error(f"Database creation failed for {db_name}: {e}", exc_info=True)
        return jsonify({"success": False, "error": "Failed to create the instance database."}), 500

    # 2. Allocate ports
    backend_port, frontend_port = _allocate_ports()
    log.info(f"Allocated ports — backend: {backend_port}, frontend: {frontend_port}")

    # 3. Create backend container
    be_name = _container_name(subdomain, "backend")
    try:
        backend_image = _get_backend_image()
        log.info(f"Using backend image: {backend_image}")
        be_container = client.containers.run(
            backend_image,
            name=be_name,
            detach=True,
            restart_policy={"Name": "unless-stopped"},
            ports={"8082/tcp": ("127.0.0.1", backend_port)},
            environment={
                "SPRING_DATASOURCE_URL": f"jdbc:postgresql://{POSTGRES_HOST}:{POSTGRES_PORT}/{db_name}",
                "SPRING_DATASOURCE_USERNAME": POSTGRES_USER,
                "SPRING_DATASOURCE_PASSWORD": POSTGRES_PASSWORD,
                "KEYCLOAK_AUTH_SERVER_URL": KEYCLOAK_AUTH_SERVER_URL,
                "KEYCLOAK_INTROSPECT_URL": KEYCLOAK_INTROSPECT_URL,
                "KEYCLOAK_CLIENT_ID": KEYCLOAK_CLIENT_ID,
                "KEYCLOAK_CLIENT_SECRET": KEYCLOAK_CLIENT_SECRET,
                "CORS_ALLOWED_ORIGINS": cors_origins,
                "DATAHOLDER_NAME": name,
                "DATAHOLDER_ID": dh_id or f"DH-{subdomain.upper()}",
                "DEFAULT_ACCESS_LEVEL": "1",
                "REQUIRE_AGREEMENT": "true",
                "MANUAL_VERIFICATION_ENABLED": "false",
                "MANUAL_VERIFICATION_LEVEL": "3",
                "ADMIN_USERNAME": "admin",
                "ADMIN_PASSWORD": admin_password,
                "DH_GROUP_ADMIN_URL": DH_GROUP_ADMIN_URL,
                "DATAHOLDER_NOTIFICATION_ENABLED": "false",
            },
            network=DOCKER_NETWORK,
            labels={
                "managed-by": "provisioning-sidecar",
                "dh-subdomain": subdomain,
                "dh-name": name,
                "dh-role": "backend",
            },
        )
        log.info(f"Backend container created: {be_name} (id: {be_container.short_id})")
    except Exception as e:
        log.error(f"Backend container creation failed: {e}", exc_info=True)
        # Rollback: drop database
        try:
            conn = _get_pg_conn()
            conn.autocommit = True
            with conn.cursor() as cur:
                cur.execute(f'DROP DATABASE IF EXISTS "{db_name}"')
            conn.close()
        except Exception:
            pass
        return jsonify({"success": False, "error": "Failed to create the backend container."}), 500

    # 4. Create frontend container
    fe_name = _container_name(subdomain, "frontend")
    if PARENT_DOMAIN == "localhost":
        frontend_api_url = f"http://localhost:{backend_port}"
        instance_url = f"http://localhost:{frontend_port}"
    else:
        frontend_api_url = f"https://{subdomain}.{PARENT_DOMAIN}/api"
        instance_url = f"https://{subdomain}.{PARENT_DOMAIN}"
    try:
        frontend_image = _get_frontend_image()
        log.info(f"Using frontend image: {frontend_image}")
        fe_container = client.containers.run(
            frontend_image,
            name=fe_name,
            detach=True,
            restart_policy={"Name": "unless-stopped"},
            ports={"3000/tcp": ("127.0.0.1", frontend_port)},
            environment={
                "REACT_APP_API_URL": frontend_api_url,
                "REACT_APP_VERSION": "1.0.0",
            },
            network=DOCKER_NETWORK,
            labels={
                "managed-by": "provisioning-sidecar",
                "dh-subdomain": subdomain,
                "dh-name": name,
                "dh-role": "frontend",
            },
        )
        log.info(f"Frontend container created: {fe_name} (id: {fe_container.short_id})")
    except Exception as e:
        log.error(f"Frontend container creation failed: {e}", exc_info=True)
        # Rollback: remove backend container and database
        try:
            client.containers.get(be_name).remove(force=True)
        except Exception:
            pass
        try:
            conn = _get_pg_conn()
            conn.autocommit = True
            with conn.cursor() as cur:
                cur.execute(f'DROP DATABASE IF EXISTS "{db_name}"')
            conn.close()
        except Exception:
            pass
        return jsonify({"success": False, "error": "Failed to create the frontend container."}), 500

    # 5. Write Nginx config (skip for localhost/local dev)
    if PARENT_DOMAIN != "localhost":
        try:
            _write_nginx_conf(subdomain, backend_port, frontend_port, name)
            ok, err = _reload_nginx()
            if not ok:
                log.warning(f"Nginx reload issue (non-fatal): {err}")
        except Exception as e:
            log.warning(f"Nginx config write failed (non-fatal): {e}")

        # 6. Attempt SSL cert (non-fatal — wildcard cert may cover this)
        _obtain_ssl_cert(subdomain)
    else:
        log.info("Skipping Nginx/SSL config (local dev mode)")

    return jsonify({
        "success": True,
        "instance": {
            "subdomain": subdomain,
            "name": name,
            "url": instance_url,
            "backendPort": backend_port,
            "frontendPort": frontend_port,
            "backendContainer": be_name,
            "frontendContainer": fe_name,
            "database": db_name,
            "adminUsername": "admin",
            "adminPassword": admin_password,
            "status": "running",
        }
    })


# ─── Stop Instance ──────────────────────────────────────────────────
@app.route("/api/instances/<subdomain>/stop", methods=["POST"])
def stop_instance(subdomain):
    auth_err = _check_auth()
    if auth_err:
        return auth_err

    subdomain = _sanitize_subdomain(subdomain)
    if not subdomain:
        return jsonify({"success": False, "error": "Invalid subdomain"}), 400

    stopped = []
    for role in ["backend", "frontend"]:
        cname = _container_name(subdomain, role)
        try:
            c = client.containers.get(cname)
            c.stop(timeout=30)
            stopped.append(cname)
        except docker.errors.NotFound:
            pass
        except Exception as e:
            log.error(f"Failed to stop {cname}: {e}", exc_info=True)
            return jsonify({"success": False, "error": f"Failed to stop the instance {role} component."}), 500

    return jsonify({"success": True, "stopped": stopped})


# ─── Start Instance ─────────────────────────────────────────────────
@app.route("/api/instances/<subdomain>/start", methods=["POST"])
def start_instance(subdomain):
    auth_err = _check_auth()
    if auth_err:
        return auth_err

    subdomain = _sanitize_subdomain(subdomain)
    if not subdomain:
        return jsonify({"success": False, "error": "Invalid subdomain"}), 400

    started = []
    for role in ["backend", "frontend"]:
        cname = _container_name(subdomain, role)
        try:
            c = client.containers.get(cname)
            c.start()
            started.append(cname)
        except docker.errors.NotFound:
            return jsonify({"success": False, "error": f"Container {cname} not found"}), 404
        except Exception as e:
            log.error(f"Failed to start {cname}: {e}", exc_info=True)
            return jsonify({"success": False, "error": f"Failed to start the instance {role} component."}), 500

    return jsonify({"success": True, "started": started})


# ─── Full Teardown ──────────────────────────────────────────────────
@app.route("/api/instances/<subdomain>/teardown", methods=["DELETE"])
def teardown_instance(subdomain):
    auth_err = _check_auth()
    if auth_err:
        return auth_err

    subdomain = _sanitize_subdomain(subdomain)
    if not subdomain:
        return jsonify({"success": False, "error": "Invalid subdomain"}), 400

    errors = []

    # 1. Stop and remove containers
    for role in ["backend", "frontend"]:
        cname = _container_name(subdomain, role)
        try:
            c = client.containers.get(cname)
            c.remove(force=True)
            log.info(f"Removed container: {cname}")
        except docker.errors.NotFound:
            pass
        except Exception as e:
            log.error(f"Failed to remove container {cname}: {e}", exc_info=True)
            errors.append(f"Failed to remove the {role} component.")

    # 2. Drop database
    db_name = _db_name(subdomain)
    try:
        conn = _get_pg_conn()
        conn.autocommit = True
        with conn.cursor() as cur:
            # Terminate active connections
            cur.execute(f"""
                SELECT pg_terminate_backend(pid)
                FROM pg_stat_activity
                WHERE datname = %s AND pid <> pg_backend_pid()
            """, (db_name,))
            cur.execute(f'DROP DATABASE IF EXISTS "{db_name}"')
        conn.close()
        log.info(f"Dropped database: {db_name}")
    except Exception as e:
        log.error(f"Failed to drop database {db_name}: {e}", exc_info=True)
        errors.append("Failed to drop the instance database.")

    # 3. Remove Nginx config
    _remove_nginx_conf(subdomain)
    _reload_nginx()

    if errors:
        return jsonify({"success": False, "error": "Partial teardown", "details": errors}), 500
    return jsonify({"success": True, "message": f"Instance {subdomain} fully torn down"})


# ─── Update Instance (recreate containers with new image) ───────────
@app.route("/api/instances/<subdomain>/update", methods=["POST"])
def update_instance(subdomain):
    auth_err = _check_auth()
    if auth_err:
        return auth_err

    subdomain = _sanitize_subdomain(subdomain)
    if not subdomain:
        return jsonify({"success": False, "error": "Invalid subdomain"}), 400

    data = request.get_json(force=True) or {}
    backend_image = data.get("backendImage") or _get_backend_image()
    frontend_image = data.get("frontendImage") or _get_frontend_image()

    updated = []
    for role, image, container_port in [
        ("backend", backend_image, "8082/tcp"),
        ("frontend", frontend_image, "3000/tcp"),
    ]:
        cname = _container_name(subdomain, role)
        try:
            c = client.containers.get(cname)
            # Capture current env and port bindings
            env = c.attrs.get("Config", {}).get("Env", [])
            port_bindings = c.attrs.get("HostConfig", {}).get("PortBindings", {})
            labels = c.attrs.get("Config", {}).get("Labels", {})
            network_name = DOCKER_NETWORK

            # Stop and remove old container
            c.remove(force=True)
            log.info(f"Removed old container: {cname}")

            # Recreate with new image
            env_dict = {}
            for e in env:
                if "=" in e:
                    k, v = e.split("=", 1)
                    env_dict[k] = v

            ports = {}
            for cp, bindings in port_bindings.items():
                if bindings:
                    ports[cp] = (bindings[0].get("HostIp", "127.0.0.1"), int(bindings[0].get("HostPort", 0)))

            new_c = client.containers.run(
                image,
                name=cname,
                detach=True,
                restart_policy={"Name": "unless-stopped"},
                ports=ports,
                environment=env_dict,
                network=network_name,
                labels=labels,
            )
            updated.append({"container": cname, "image": image, "id": new_c.short_id})
            log.info(f"Recreated container: {cname} with image {image}")
        except docker.errors.NotFound:
            return jsonify({"success": False, "error": f"Container {cname} not found"}), 404
        except Exception as e:
            log.error(f"Failed to update {cname}: {e}", exc_info=True)
            return jsonify({"success": False, "error": f"Failed to update the instance {role} component."}), 500

    return jsonify({"success": True, "updated": updated})


# ─── Instance Status ────────────────────────────────────────────────
@app.route("/api/instances/<subdomain>/status", methods=["GET"])
def instance_status(subdomain):
    auth_err = _check_auth()
    if auth_err:
        return auth_err

    subdomain = _sanitize_subdomain(subdomain)
    if not subdomain:
        return jsonify({"success": False, "error": "Invalid subdomain"}), 400

    status = {}
    for role in ["backend", "frontend"]:
        cname = _container_name(subdomain, role)
        try:
            c = client.containers.get(cname)
            status[role] = {
                "name": cname,
                "status": c.status,
                "id": c.short_id,
                "image": c.image.tags[0] if c.image.tags else str(c.image.id)[:19],
                "created": c.attrs.get("Created", ""),
            }
        except docker.errors.NotFound:
            status[role] = {"name": cname, "status": "not_found"}

    return jsonify({"success": True, "subdomain": subdomain, "containers": status})


# ─── List All Managed Instances ─────────────────────────────────────
@app.route("/api/instances", methods=["GET"])
def list_instances():
    auth_err = _check_auth()
    if auth_err:
        return auth_err

    instances = {}
    try:
        containers = client.containers.list(
            all=True,
            filters={"label": "managed-by=provisioning-sidecar"}
        )
        for c in containers:
            labels = c.labels or {}
            subdomain = labels.get("dh-subdomain", "unknown")
            role = labels.get("dh-role", "unknown")
            if subdomain not in instances:
                instances[subdomain] = {
                    "subdomain": subdomain,
                    "name": labels.get("dh-name", ""),
                    "containers": {},
                }
            instances[subdomain]["containers"][role] = {
                "name": c.name,
                "status": c.status,
                "id": c.short_id,
                "image": c.image.tags[0] if c.image.tags else str(c.image.id)[:19],
            }
    except Exception as e:
        log.error(f"Failed to list instances: {e}", exc_info=True)
        return jsonify({"success": False, "error": "Failed to list instances."}), 500

    return jsonify({"success": True, "instances": list(instances.values())})


# ─── Health ─────────────────────────────────────────────────────────
@app.route("/health", methods=["GET"])
def health():
    try:
        client.ping()
        docker_ok = True
    except Exception:
        docker_ok = False

    try:
        conn = _get_pg_conn()
        conn.close()
        pg_ok = True
    except Exception:
        pg_ok = False

    return jsonify({
        "status": "ok" if (docker_ok and pg_ok) else "degraded",
        "docker": docker_ok,
        "postgres": pg_ok,
    })


if __name__ == "__main__":
    # Debug mode exposes the Werkzeug interactive debugger (an RCE surface) and full
    # tracebacks — never enable it by default. Opt in explicitly via env for local dev only.
    debug_mode = os.getenv("PROVISIONER_DEBUG", "false").lower() == "true"

    # Dev-only fallback; gunicorn (docker-entrypoint.sh) is the real path. Mirror its
    # mTLS behaviour when MTLS_ENABLED=true; otherwise serve plain HTTP.
    ssl_context = None
    if os.getenv("MTLS_ENABLED", "false").lower() == "true":
        import ssl
        cert_file = os.getenv("MTLS_CERT_FILE", "/certs/provisioning-sidecar.crt")
        key_file = os.getenv("MTLS_KEY_FILE", "/certs/provisioning-sidecar.key")
        ca_file = os.getenv("MTLS_CA_FILE", "/certs/provisioning-sidecar-truststore.crt")
        ctx = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
        ctx.load_cert_chain(certfile=cert_file, keyfile=key_file)
        ctx.load_verify_locations(cafile=ca_file)
        ctx.verify_mode = ssl.CERT_REQUIRED  # mandatory, CA-signed client cert
        ssl_context = ctx
        log.info(f"mTLS ENABLED (dev server) — requiring client certs (ca={ca_file})")

    app.run(host="0.0.0.0", port=9500, debug=debug_mode, ssl_context=ssl_context)
