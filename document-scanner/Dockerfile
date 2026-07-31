FROM python:3.11-slim

WORKDIR /app

# tesseract-ocr provides the OCR engine (+ English language data). PyMuPDF handles
# PDF parsing/rasterising, so no poppler is needed.
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-eng \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Entrypoint decides plain HTTP vs. server-side mTLS based on MTLS_ENABLED.
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

# Untrusted documents are parsed here — drop privileges.
RUN useradd --create-home --shell /usr/sbin/nologin scanner \
    && chown -R scanner:scanner /app
USER scanner

EXPOSE 9600

ENTRYPOINT ["/app/docker-entrypoint.sh"]
