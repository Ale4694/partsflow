# Architecture Decision Records

An ADR is a short note that captures **one important decision**: the situation that forced it, what was decided, which alternatives were rejected and why, and what the decision costs. They are written so that a junior developer can learn from them and explain each choice in an interview.

Each record has the same shape: **Context**, **Decision**, **Alternatives considered**, **Consequences**.

| # | Decision |
| --- | --- |
| [0001](0001-package-by-feature.md) | Package by feature, not by layer |
| [0002](0002-bigdecimal-for-money.md) | BigDecimal, created from strings, for money and quantities |
| [0003](0003-flyway-and-ddl-auto-validate.md) | Flyway owns the schema; Hibernate only validates it |
| [0004](0004-optimistic-locking.md) | Optimistic locking (with retry) for stock |
| [0005](0005-draft-then-confirm-import.md) | Imports create a draft; only confirmation changes stock |
| [0006](0006-jackson-xml-instead-of-jaxb.md) | Jackson XML instead of JAXB, with separate mapping classes |
| [0007](0007-pg-trgm-instead-of-embeddings.md) | pg_trgm instead of embeddings to find candidate items |
| [0008](0008-human-in-the-loop-ai.md) | Human-in-the-loop AI: the LLM proposes, a person confirms |
| [0009](0009-gemini-free-tier-and-synthetic-data.md) | Gemini free tier as LLM provider, and only synthetic data |
| [0010](0010-angular-spa-behind-nginx.md) | Angular single-page app served by nginx, which proxies the API |
| [0011](0011-configurable-llm-provider.md) | A configurable LLM provider: Gemini or any OpenAI-compatible service |
| [0012](0012-semantic-search-with-pgvector.md) | Semantic catalog search: pgvector in the same database, hybrid ranking, text fallback |
