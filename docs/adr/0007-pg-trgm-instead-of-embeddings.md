# 0007. pg_trgm instead of embeddings to find candidate items

## Context

When an invoice line has an unknown supplier code, a person must say which of our items it is. To help, the system looks for catalog items whose description resembles the invoice line ("Pastiglie freno anteriori" vs "Front brake pad set"), then asks the LLM to pick the right one or none (ADR 0008).

The first step needs a way to find a short list of *similar texts*. The fashionable answer is embeddings and a vector database; PostgreSQL also has a classic one, `pg_trgm`.

## Decision

Use PostgreSQL's **`pg_trgm`** extension. It splits texts into overlapping three-letter groups ("trigrams") and measures how many two texts share (`similarity()` from 0 to 1). The query is one SQL statement (`ItemCandidateRepository`): the items whose description is at least 0.1 similar, best first, at most 5.

- The extension is enabled by the baseline migration; the `pgvector/pgvector` Docker image includes it.
- It only produces the **shortlist**. The LLM never sees the whole catalog, and it may only choose among the candidates it was shown: any other item id it returns is discarded by our code. If the shortlist is empty, the LLM is not called at all, which also saves free-tier quota.
- It is plain SQL, deterministic, and testable (the integration tests run it against real PostgreSQL).

## Alternatives considered

- **Embeddings + pgvector.** Understands meaning across languages and synonyms ("pastiglie" ~ "brake pads"). But it needs an embedding model call for every item and every line (more LLM quota, more latency, more cost), a vector index, and re-embedding when descriptions change. That is a lot of machinery for a catalog of a small distributor, and results are harder to explain.
- **`LIKE '%word%'` or PostgreSQL full-text search.** Cheap, but needs exact words or stemming for one language; it handles typos and word-order changes badly.
- **Send the whole catalog to the LLM.** Simple but does not scale, wastes tokens, and gives the model more room to invent matches.

## Consequences

- Fast, cheap, no extra infrastructure, and easy to explain: "texts that share many trigrams".
- It is lexical: a French or German description of the same part may share no trigrams with ours and not be found. Cross-language matching is where embeddings would win; the design leaves room to add them as a second candidate source later.
- The query does a sequential scan. That is fine for thousands of items; a GIN index with `gin_trgm_ops` (and the `%` operator) is the next step if the catalog grows.
