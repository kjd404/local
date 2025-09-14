FROM python:3.12-slim

WORKDIR /app

# Install pinned deps
COPY requirements.lock /app/requirements.lock
RUN pip install --no-cache-dir -r /app/requirements.lock

# Copy source
COPY src /app/src
ENV PYTHONPATH=/app/src

ENTRYPOINT ["python", "-m", "athena_bot.main"]
