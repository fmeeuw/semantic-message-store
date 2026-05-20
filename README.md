# semantic-message-store

A Ktor-based service that stores messages in postgreSQL, and enables semantic search using Ollama embeddings and pgvector.

# Architecture

Ingestion REST API
├── POST /api/messages            Store message
↓
Ollama (nomic-embed-text) → vector embedding
↓
PostgreSQL + pgvector (message + embedding)
↓
Search REST API
├── GET  /api/messages/{id}     single message
├── POST /api/search            semantic search via vector similarity

## Stack
- **Kotlin** + **Ktor** — idiomatic async HTTP server
- **PostgreSQL** + **pgvector** — storage + vector similarity search
- **Ollama** (`nomic-embed-text`) — local embeddings, no API costs
- **Exposed** — Kotlin SQL framework
- **Testcontainers** — integration tests


## TODO

# High priority
* Database migrations (Flyway) — replace manual exec(CREATE TABLE...) in init() with proper Flyway migrations. Handles schema versioning, rollbacks, and production safety.
* PostgreSQL UUID type — change id column from varchar(36) to native uuid type. Better performance, storage, and type safety.
* Use separate dto and domain layer

# Medium priority
* Test isolation via transaction rollback — currently uses TRUNCATE which is fragile. Proper fix requires making dbQuery use an injectable transaction so tests can wrap in a rollback.
* Test with ivfflat index — requires inserting ≥100 vectors before creating the index, then running search. Needed to verify production search behavior.

Low priority / nice to have
* Move UUIDSerializer out of Models.kt — already noted in code, minor cleanup.
* Ollama service URL as config only — currently model is hardcoded as "nomic-embed-text", should be in application.conf.
* Input validation — POST /api/messages accepts empty content, no max length check.
* Ollama model name in config — "nomic-embed-text" hardcoded in OllamaService, should come from application.conf
* Input validation on requests
* HikariCP pool size in config — maximumPoolSize = 10 hardcoded in Plugins.kt
