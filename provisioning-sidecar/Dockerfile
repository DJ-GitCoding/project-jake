FROM python:3.11-slim

WORKDIR /app

# Install system deps for psycopg2 and certbot
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    nginx \
    certbot \
    python3-certbot-nginx \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Entrypoint decides plain HTTP vs. server-side mTLS based on MTLS_ENABLED.
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

EXPOSE 9500

ENTRYPOINT ["/app/docker-entrypoint.sh"]
