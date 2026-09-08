FROM python:3.11-slim

WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1

COPY requirements.txt .
RUN pip install --no-cache-dir --timeout=30 --retries=2 \
    --index-url https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt
COPY app.py .

EXPOSE 8095
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=12 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8095/health', timeout=3)"

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8095"]
