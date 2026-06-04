# Chroma Setup

This project uses Chroma as the MVP vector database for contract clauses, legal review rules, and risk knowledge.

## Local Runtime

Chroma is installed in the project virtual environment:

```powershell
.venv-chroma\Scripts\chroma.exe --version
```

Start Chroma:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-chroma.ps1
```

Stop Chroma:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-chroma.ps1
```

Service URL:

```text
http://localhost:8000
```

Heartbeat check:

```powershell
Invoke-RestMethod http://localhost:8000/api/v2/heartbeat
```

Persistent data is stored in:

```text
chroma-data/
```

## Spring Boot Config

Local profile:

```yaml
contract:
  vector-store: chroma
  chroma:
    url: http://localhost:8000
```

Docker profile:

```yaml
contract:
  chroma:
    url: ${CHROMA_URL:http://chroma:8000}
```

## Docker

Docker is not currently available on this machine's PATH, but `docker-compose.yml` includes a `chroma` service for later use:

```powershell
docker compose up -d chroma
```

## Data Mapping

Use MySQL for metadata and Chroma for vectors:

```text
knowledge_document.id        -> knowledge_chunk.document_id
knowledge_chunk.vector_id    -> Chroma document/vector id
knowledge_chunk.content      -> original chunk text
knowledge_chunk.metadata     -> Chroma metadata mirror
```
