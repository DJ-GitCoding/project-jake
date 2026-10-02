# SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
# SPDX-License-Identifier: AGPL-3.0-only
#
# Author: Derek Jenkins <derek@pure-code.net>
# Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.

"""
Document Scanner Sidecar — validates uploaded request-type attachments against
admin-authored format criteria.

The data holder backend forwards each attachment here at upload time together with
the criteria defined on the request type's file parameter (authored in the DH Group
Admin UI). This service extracts what it can from the document and returns a
PASS/FAIL verdict plus a per-criterion failure list.

Pipeline by type:
  PDF   -> PyMuPDF: page count, encryption, text layer, embedded images.
           Only when the text layer is missing/thin do we rasterize and OCR
           (Tesseract) — OCR on a digital PDF is slower and strictly less accurate.
  DOCX  -> python-docx: paragraph/table text + inline images. Never OCR'd.
  Image -> Pillow for dimensions/DPI, Tesseract for any embedded text.

Called by dataholder-backend over the Docker network with a shared-secret header.
"""

import io
import os
import re
import hmac
import json
import logging

import fitz  # PyMuPDF
import pytesseract
from PIL import Image
from flask import Flask, request, jsonify

try:
    import docx  # python-docx
except ImportError:  # pragma: no cover - dependency is declared in requirements
    docx = None

app = Flask(__name__)
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("document-scanner")

# --- Config ---
# Fail-fast: no insecure default. This endpoint accepts untrusted user documents.
SHARED_SECRET = os.getenv("DOCUMENT_SCANNER_SECRET")
if not SHARED_SECRET or len(SHARED_SECRET) < 16:
    raise RuntimeError("DOCUMENT_SCANNER_SECRET must be set to a strong (>=16 char) value; refusing to start.")

MAX_FILE_MB = int(os.getenv("SCANNER_MAX_FILE_MB", "25"))
DEFAULT_MAX_OCR_PAGES = int(os.getenv("SCANNER_MAX_OCR_PAGES", "10"))
OCR_DPI = int(os.getenv("SCANNER_OCR_DPI", "300"))
TEXT_SAMPLE_CHARS = 2000
# A page with fewer than this many extracted characters is treated as having no
# usable text layer (i.e. it is a scan), which is what triggers the OCR fallback.
TEXT_LAYER_MIN_CHARS = 20

app.config["MAX_CONTENT_LENGTH"] = (MAX_FILE_MB + 5) * 1024 * 1024

PDF_EXTS = {"pdf"}
DOCX_EXTS = {"docx"}
IMAGE_EXTS = {"jpg", "jpeg", "png"}


@app.errorhandler(Exception)
def handle_exception(e):
    log.error(f"Unhandled exception: {e}", exc_info=True)
    return jsonify({"verdict": "ERROR", "error": "An unexpected error occurred"}), 500


def _check_auth():
    token = request.headers.get("X-Scanner-Secret", "")
    if not hmac.compare_digest(token, SHARED_SECRET):
        return jsonify({"verdict": "ERROR", "error": "Unauthorized"}), 401
    return None


# ─── Analysis ───────────────────────────────────────────────────────

def _image_dims(blob):
    try:
        with Image.open(io.BytesIO(blob)) as im:
            return {"width": im.width, "height": im.height}
    except Exception:
        return None


def analyze_pdf(data, criteria):
    out = {
        "fileType": "pdf", "pageCount": 0, "encrypted": False,
        "hasTextLayer": False, "ocrUsed": False, "text": "", "images": [],
    }
    doc = fitz.open(stream=data, filetype="pdf")
    try:
        out["encrypted"] = bool(doc.needs_pass)
        if out["encrypted"]:
            # Nothing further is readable without the password.
            return out

        out["pageCount"] = doc.page_count
        parts = []
        for page in doc:
            parts.append(page.get_text() or "")
        embedded_text = "".join(parts)
        out["hasTextLayer"] = len(embedded_text.strip()) >= TEXT_LAYER_MIN_CHARS

        if out["hasTextLayer"]:
            out["text"] = embedded_text
        else:
            # Scanned/image-only PDF — fall back to OCR, capped for performance.
            max_pages = criteria.get("maxOcrPages") or DEFAULT_MAX_OCR_PAGES
            ocr_parts = []
            for page in doc[: max(0, int(max_pages))]:
                pix = page.get_pixmap(dpi=OCR_DPI)
                with Image.open(io.BytesIO(pix.tobytes("png"))) as im:
                    ocr_parts.append(pytesseract.image_to_string(im))
            out["text"] = "".join(ocr_parts)
            out["ocrUsed"] = True

        # Inventory embedded raster images (for "must contain a photo" style rules).
        seen = set()
        for page in doc:
            for info in page.get_images(full=True):
                xref = info[0]
                if xref in seen:
                    continue
                seen.add(xref)
                try:
                    img = doc.extract_image(xref)
                    out["images"].append({"width": img.get("width"), "height": img.get("height")})
                except Exception:
                    continue
    finally:
        doc.close()
    return out


def analyze_docx(data, criteria):
    out = {
        "fileType": "docx", "pageCount": None, "encrypted": False,
        "hasTextLayer": True, "ocrUsed": False, "text": "", "images": [],
    }
    if docx is None:
        raise RuntimeError("python-docx is not available")
    d = docx.Document(io.BytesIO(data))
    parts = [p.text for p in d.paragraphs]
    for table in d.tables:
        for row in table.rows:
            parts.extend(cell.text for cell in row.cells)
    out["text"] = "\n".join(parts)

    for rel in d.part.related_parts.values():
        content_type = getattr(rel, "content_type", "") or ""
        if content_type.startswith("image/"):
            dims = _image_dims(rel.blob)
            if dims:
                out["images"].append(dims)
    return out


def analyze_image(data, criteria):
    out = {
        "fileType": "image", "pageCount": 1, "encrypted": False,
        "hasTextLayer": False, "ocrUsed": True, "text": "", "images": [],
    }
    with Image.open(io.BytesIO(data)) as im:
        out["images"].append({"width": im.width, "height": im.height})
        dpi = im.info.get("dpi")
        if dpi and dpi[0]:
            out["dpi"] = int(dpi[0])
        out["text"] = pytesseract.image_to_string(im)
    return out


# ─── Criteria evaluation ────────────────────────────────────────────

def _fail(failures, criterion, message):
    failures.append({"criterion": criterion, "message": message})


def evaluate(analysis, criteria):
    """Compare an analysis against the authored criteria. Absent/null criteria are not enforced."""
    failures = []
    text = analysis.get("text") or ""
    words = len(text.split())
    pages = analysis.get("pageCount")
    images = analysis.get("images") or []

    if criteria.get("rejectEncrypted", True) and analysis.get("encrypted"):
        _fail(failures, "rejectEncrypted", "The document is password-protected and cannot be read.")
        return failures  # nothing else is knowable

    if criteria.get("minPages") is not None and pages is not None and pages < criteria["minPages"]:
        _fail(failures, "minPages", f"Document has {pages} page(s); at least {criteria['minPages']} required.")
    if criteria.get("maxPages") is not None and pages is not None and pages > criteria["maxPages"]:
        _fail(failures, "maxPages", f"Document has {pages} page(s); at most {criteria['maxPages']} allowed.")

    if criteria.get("requireTextLayer") and not analysis.get("hasTextLayer"):
        _fail(failures, "requireTextLayer",
              "The document has no selectable text (it appears to be a scan). A text-based document is required.")
    if criteria.get("allowScanned") is False and analysis.get("ocrUsed"):
        _fail(failures, "allowScanned", "Scanned/image-only documents are not accepted for this parameter.")

    if criteria.get("minWordCount") is not None and words < criteria["minWordCount"]:
        _fail(failures, "minWordCount",
              f"Only {words} words of text were found; at least {criteria['minWordCount']} required.")

    for pattern in criteria.get("requiredTextPatterns") or []:
        try:
            if not re.search(pattern, text, re.IGNORECASE | re.MULTILINE):
                _fail(failures, "requiredTextPatterns", f"Required content not found (pattern: {pattern}).")
        except re.error:
            _fail(failures, "requiredTextPatterns", f"Invalid pattern configured: {pattern}")
    for pattern in criteria.get("forbiddenTextPatterns") or []:
        try:
            if re.search(pattern, text, re.IGNORECASE | re.MULTILINE):
                _fail(failures, "forbiddenTextPatterns", f"Document contains disallowed content (pattern: {pattern}).")
        except re.error:
            _fail(failures, "forbiddenTextPatterns", f"Invalid pattern configured: {pattern}")

    # Image / photo rules
    min_w = criteria.get("minEmbeddedImageWidth")
    min_h = criteria.get("minEmbeddedImageHeight")
    if criteria.get("requireEmbeddedImage"):
        qualifying = [
            i for i in images
            if (min_w is None or (i.get("width") or 0) >= min_w)
            and (min_h is None or (i.get("height") or 0) >= min_h)
        ]
        if not qualifying:
            if min_w or min_h:
                _fail(failures, "requireEmbeddedImage",
                      f"No image of at least {min_w or 0}x{min_h or 0}px was found in the document.")
            else:
                _fail(failures, "requireEmbeddedImage", "The document must contain at least one image.")

    # Dimension / DPI rules apply to a directly uploaded photo
    if analysis.get("fileType") == "image" and images:
        w, h = images[0].get("width") or 0, images[0].get("height") or 0
        if criteria.get("minWidth") is not None and w < criteria["minWidth"]:
            _fail(failures, "minWidth", f"Image is {w}px wide; at least {criteria['minWidth']}px required.")
        if criteria.get("minHeight") is not None and h < criteria["minHeight"]:
            _fail(failures, "minHeight", f"Image is {h}px tall; at least {criteria['minHeight']}px required.")
        if criteria.get("minDpi") is not None and analysis.get("dpi") is not None \
                and analysis["dpi"] < criteria["minDpi"]:
            _fail(failures, "minDpi", f"Image is {analysis['dpi']} DPI; at least {criteria['minDpi']} required.")

    return failures


# ─── Routes ─────────────────────────────────────────────────────────

@app.route("/scan", methods=["POST"])
def scan():
    unauthorized = _check_auth()
    if unauthorized:
        return unauthorized

    upload = request.files.get("file")
    if upload is None:
        return jsonify({"verdict": "ERROR", "error": "No file provided"}), 400

    raw_criteria = request.form.get("criteria") or "{}"
    try:
        criteria = json.loads(raw_criteria) or {}
    except json.JSONDecodeError:
        return jsonify({"verdict": "ERROR", "error": "criteria must be valid JSON"}), 400

    data = upload.read()
    if len(data) > MAX_FILE_MB * 1024 * 1024:
        return jsonify({"verdict": "ERROR", "error": f"File exceeds {MAX_FILE_MB} MB"}), 400

    ext = (upload.filename or "").rsplit(".", 1)[-1].lower() if "." in (upload.filename or "") else ""
    try:
        if ext in PDF_EXTS:
            analysis = analyze_pdf(data, criteria)
        elif ext in DOCX_EXTS:
            analysis = analyze_docx(data, criteria)
        elif ext in IMAGE_EXTS:
            analysis = analyze_image(data, criteria)
        else:
            return jsonify({"verdict": "ERROR", "error": f"Unsupported file type: .{ext}"}), 400
    except Exception as e:
        # A document we cannot parse is a failure of the format requirement, not a
        # server error — report it as such so the reviewer sees an actionable reason.
        log.warning(f"Failed to analyze {upload.filename}: {e}")
        return jsonify({
            "verdict": "FAIL", "fileType": ext,
            "failures": [{"criterion": "readable", "message": "The document could not be read; it may be corrupt."}],
        })

    failures = evaluate(analysis, criteria)
    text = analysis.pop("text", "") or ""
    analysis["wordCount"] = len(text.split())
    analysis["textSample"] = text[:TEXT_SAMPLE_CHARS]
    analysis["verdict"] = "PASS" if not failures else "FAIL"
    analysis["failures"] = failures
    return jsonify(analysis)


@app.route("/health", methods=["GET"])
def health():
    try:
        version = str(pytesseract.get_tesseract_version())
        tesseract_ok = True
    except Exception:
        version, tesseract_ok = None, False
    return jsonify({
        "status": "ok" if tesseract_ok else "degraded",
        "tesseract": tesseract_ok,
        "tesseractVersion": version,
    })
