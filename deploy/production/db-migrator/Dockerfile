FROM python:3.11.7-slim-bookworm

RUN apt-get update \
    && apt-get install --yes --no-install-recommends default-mysql-client ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY script/sql/mysql/ script/sql/mysql/

ENTRYPOINT ["python3", "/workspace/script/sql/mysql/tools/zsjos_db.py"]
