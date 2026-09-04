import os
import tempfile
import importlib.metadata
from pathlib import Path

import fitz
import numpy as np
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from paddleocr import PaddleOCR
from PIL import Image

MAX_BYTES = int(os.getenv("PADDLEOCR_MAX_BYTES", str(50 * 1024 * 1024)))
OCR_LANG = os.getenv("PADDLEOCR_LANG", "ch")
OCR_MODEL_VERSION = os.getenv("PADDLEOCR_MODEL_VERSION", "PP-OCRv4")
OCR_PACKAGE_VERSION = importlib.metadata.version("paddleocr")
app = FastAPI(title="Aoneng PaddleOCR Service", version="1.0")
ocr = None


def engine():
    global ocr
    if ocr is None:
        ocr = PaddleOCR(lang=OCR_LANG, use_angle_cls=True, show_log=False)
    return ocr


@app.get("/health")
def health():
    return {"status": "UP", "parser": "paddleocr", "engine": "PaddleOCR",
            "engineVersion": OCR_PACKAGE_VERSION, "language": OCR_LANG,
            "packageVersion": OCR_PACKAGE_VERSION,
            "modelVersion": OCR_MODEL_VERSION,
            "modelLoaded": ocr is not None}


async def save_upload(file: UploadFile) -> Path:
    suffix = Path(file.filename or "document").suffix.lower() or ".bin"
    target = Path(tempfile.mkstemp(prefix="paddleocr-", suffix=suffix)[1])
    total = 0
    try:
        with target.open("wb") as output:
            while data := await file.read(1024 * 1024):
                total += len(data)
                if total > MAX_BYTES:
                    raise HTTPException(status_code=413, detail="document too large")
                output.write(data)
        return target
    except Exception:
        target.unlink(missing_ok=True)
        raise


def recognize(image: Image.Image, page_no: int, order_start: int):
    result = engine().ocr(np.asarray(image.convert("RGB")), cls=True)
    lines = result[0] if result and result[0] else []
    blocks = []
    for offset, line in enumerate(lines):
        if not line or len(line) < 2:
            continue
        points, text_info = line[0], line[1]
        text = str(text_info[0]).strip() if text_info else ""
        score = float(text_info[1]) if text_info and len(text_info) > 1 else None
        if not text:
            continue
        xs = [float(point[0]) for point in points]
        ys = [float(point[1]) for point in points]
        blocks.append({"type": "paragraph", "text": text, "pageNo": page_no,
                       "order": order_start + offset,
                       "bbox": {"l": min(xs), "t": min(ys), "r": max(xs), "b": max(ys)},
                       "metadata": {"source": "paddleocr", "confidence": score}})
    return blocks


@app.post("/v1/ocr/pdf")
async def ocr_pdf(file: UploadFile = File(...), dpi: int = Form(150)):
    source = await save_upload(file)
    try:
        suffix = source.suffix.lower()
        pages = []
        if suffix == ".pdf":
            document = fitz.open(source)
            try:
                scale = max(72, min(dpi, 300)) / 72.0
                matrix = fitz.Matrix(scale, scale)
                order = 0
                for index, page in enumerate(document, start=1):
                    pixmap = page.get_pixmap(matrix=matrix, alpha=False)
                    image = Image.frombytes("RGB", [pixmap.width, pixmap.height], pixmap.samples)
                    blocks = recognize(image, index, order)
                    order += len(blocks)
                    pages.extend(blocks)
            finally:
                document.close()
        elif suffix in {".png", ".jpg", ".jpeg", ".tif", ".tiff", ".bmp"}:
            with Image.open(source) as image:
                pages = recognize(image, 1, 0)
        else:
            raise HTTPException(status_code=400, detail="only PDF and image files are supported")
        text = "\n".join(block["text"] for block in pages)
        return {"text": text, "blocks": pages,
                "metadata": {"parser": "paddleocr", "engine": "PaddleOCR",
                             "engineVersion": OCR_PACKAGE_VERSION, "language": OCR_LANG,
                             "packageVersion": OCR_PACKAGE_VERSION,
                             "modelVersion": OCR_MODEL_VERSION,
                             "blockCount": len(pages)}}
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=422, detail="OCR processing failed") from exc
    finally:
        source.unlink(missing_ok=True)
