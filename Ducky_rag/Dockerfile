FROM python:3.12-slim
ENV TESSDATA_PREFIX=/opt/ducky-tessdata
COPY install_ocr_data.py /tmp/install_ocr_data.py
RUN python /tmp/install_ocr_data.py "$TESSDATA_PREFIX" && rm /tmp/install_ocr_data.py
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY ducky_rag ./ducky_rag
RUN useradd --system worker
USER worker
EXPOSE 8091
CMD ["python", "-m", "ducky_rag.server", "--host", "0.0.0.0"]
