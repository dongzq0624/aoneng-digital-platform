import json
import os
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from docling.document_converter import DocumentConverter

app = FastAPI(title="Aoneng Docling Parser", version="1.0")
converter = DocumentConverter()


@app.get("/health")
def health():
    return {"status": "UP", "parser": "docling"}


@app.post("/v1/parse")
async def parse(file: UploadFile = File(...), extension: str = Form(""),
               samplePages: int = Form(8), sampleRows: int = Form(200)):
    suffix = Path(file.filename or "document").suffix or ("." + extension.lower().lstrip("."))
    temp_name = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as target:
            temp_name = target.name
            total = 0
            while chunk := await file.read(1024 * 1024):
                total += len(chunk)
                if total > int(os.getenv("DOCLING_MAX_BYTES", 52428800)):
                    raise HTTPException(status_code=413, detail="document too large")
                target.write(chunk)
        result = converter.convert(temp_name)
        document = result.document
        blocks = []
        for item, _level in document.iterate_items():
            provenance = getattr(item, "prov", None) or []
            page_no = getattr(provenance[0], "page_no", None) if provenance else None
            if page_no is not None and page_no > samplePages:
                continue
            label = str(getattr(getattr(item, "label", None), "value", "paragraph")).lower()
            kind = "heading" if "section" in label or "title" in label else "table" if "table" in label else "paragraph"
            text = item.export_to_markdown() if kind == "table" else getattr(item, "text", "")
            text = (text or "").strip()
            if kind == "table" and len(text.splitlines()) > sampleRows + 2:
                text = "\n".join(text.splitlines()[:sampleRows + 2]) + "\n| ... | (sampled) |"
            if text:
                blocks.append({"type": kind, "text": text, "pageNo": page_no,
                               "metadata": {"label": label, "sampled": True}})
        return {"text": document.export_to_markdown(), "blocks": blocks,
                "metadata": {"parser": "docling", "extension": extension,
                             "samplePages": samplePages, "sampleRows": sampleRows,
                             "blockCount": len(blocks)}}
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=422, detail="document parsing failed") from exc
    finally:
        if temp_name:
            Path(temp_name).unlink(missing_ok=True)
