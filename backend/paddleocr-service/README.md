# PaddleOCR service

Endpoints:

- `GET /health`
- `POST /v1/ocr/pdf` (`multipart/form-data`, field `file`)

The service renders PDF pages with PyMuPDF and returns OCR blocks containing page number, reading order, bounding box, and confidence. PaddleOCR model files are downloaded on first OCR request; production deployments should prewarm and persist the model cache.
