# Python 3.11 slim — smaller than full Python, has everything we need
FROM python:3.11-slim

WORKDIR /app

# Install deps first (cached unless requirements.txt changes)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .

EXPOSE 8000

# Workers=1 because the models are large and share memory.
# If you need concurrency, scale the container count instead.
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
