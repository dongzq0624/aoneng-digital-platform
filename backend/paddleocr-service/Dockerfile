FROM python:3.11-slim

WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 \
    PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True

RUN apt-get update \
    && apt-get install -y --no-install-recommends libglib2.0-0 libgl1 libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip pip install --no-cache-dir -r requirements.txt
COPY app.py .

EXPOSE 8091
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=30 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8091/health', timeout=3)"

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8091"]
