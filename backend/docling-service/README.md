# Docling HTTP Service

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
\.venv\Scripts\python.exe -m uvicorn app:app --host 0.0.0.0 --port 8090
```

The Java application calls `POST /v1/parse` with multipart fields `file`, `extension`,
`samplePages`, and `sampleRows`. Configure `DOCLING_ENDPOINT` in the Java process.
