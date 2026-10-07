# 0012. Semantic catalog search: pgvector in the same database, hybrid ranking, text fallback

## Context

Finding the right catalog item by typing words works only when the words match. A person (or an invoice) says "filtro olio Fiat Panda"; the catalog says "Cartuccia lubrificante motore 1.2 FIRE". Spelling-based search (pg_trgm, ADR 0007) cannot connect them, because the two texts share no words. The same gap hurts the match suggestions for pending invoice lines (the LLM can only choose among candidates it is shown) and the assistant's search tool.

An *embedding* is a list of numbers that a model computes from a text so that texts with similar meaning get similar lists. Searching by meaning means: embed the query, find the items whose embeddings are nearest. This is the retrieval step of RAG (retrieval-augmented generation): retrieve the relevant items first, then let the LLM work with only those.

## Decision

**Where the vectors live: pgvector, in the same PostgreSQL.** The table `item_embedding` (migration V6, written by hand) has one row per item: the vector, the model that produced it, its size, the SHA-256 of the embedded text, a timestamp. The image `pgvector/pgvector:pg17` already used for pg_trgm contains the extension. SQL stays plain and explicit (`ItemEmbeddingRepository`, `ItemSearchRepository`): a vector travels as text and is converted with `CAST(... AS vector)`, so no extra library and no JPA magic hides which query runs.

**Size and index.** The column is `vector(768)`, an HNSW index with `vector_cosine_ops`. 768 is `gemini-embedding-001` reduced from its native 3072 numbers with its `dimensions` option (Google documents this reduction and reports almost no quality loss); pgvector can index at most 2000 numbers in a plain `vector`, so 3072 would need a different type and double the storage. The size comes from `LLM_EMBEDDING_DIMENSIONS` and is substituted into the migration by Flyway; a startup guard compares it with the real column and, on a mismatch, switches semantic search off (text fallback, a WARN in the log, `GET /api/ai/embeddings/status` shows both numbers) instead of failing the application.

**Cosine distance, vectors normalised.** Only the full 3072-number Gemini vector comes pre-normalised (length 1); a reduced 768-number vector does not. Comparing un-normalised vectors by inner product would let the length of a vector influence the ranking. So every vector is scaled to length 1 before it is stored or used (`VectorMath.normalize`), and PostgreSQL compares them with the cosine operator `<=>` (similarity = 1 - distance), with an index built for exactly that operator. Either measure alone would be correct; doing both makes the comparison safe whatever a provider returns.

**What is embedded.** The item's code, description and the codes its suppliers use for it ("Codice: ... Descrizione: ... Codici dei fornitori: ..."). The SHA-256 of that text is stored. An item is *stale* when it has no embedding, when its text hash changed, or when the stored model or size differs from the configured ones. Gemini embeds documents and queries with different task types (`RETRIEVAL_DOCUMENT`, `RETRIEVAL_QUERY`); that detail lives in a provider-specific options factory, like the error translators of ADR 0011.

**When it is computed.** Never while saving. Saving an item (or its supplier codes) publishes an event; after the transaction has committed, a background worker embeds the stale ones, in batches of up to 100 texts per provider request. A scheduled job repeats this every few minutes, which is also the retry for items whose embedding failed, and how existing items get their first embedding. After a quota error the indexer pauses (for what the provider asked, or 30 minutes) instead of asking again. An embedding that fails never blocks or undoes saving an item: the item is simply stale until the next run. The demo catalog is written in one transaction followed by one event, so about 125 items cost two requests.

**Hybrid ranking: Reciprocal Rank Fusion.** Two rankings are computed in one SQL statement: the 20 nearest items by cosine similarity, and the 20 best by pg_trgm similarity (to the description or the code). Each item gets `1 / (60 + rank)` from each ranking it appears in (rank 1 is the best), and the two numbers are added. The API returns this fused score divided by the best possible one (first in both rankings), so it is between 0 and 1, together with both raw similarities. RRF uses only the order of each ranking, so it needs no tuning of weights: cosine similarities and pg_trgm similarities are on different scales. 60 is the constant of the original paper.

**Fallback and the answer.** If embeddings are not configured, not indexed yet, of the wrong size, switched off, or the provider fails (no quota, overloaded, rejected), the search runs on spelling alone. `GET /api/items/search` always says which `mode` answered (`HYBRID` or `TEXT`) and, for a fallback, the `fallbackReason`. The UI shows "ricerca intelligente" or "ricerca testuale".

**Free-tier cost.** One embedding request per search (several queries, such as all pending lines of an invoice, share one request), an in-memory cache of query embeddings (10 minutes, 200 entries, never logged), batch indexing, and the same retry, quota and logging rules as chat (`ProviderCallPolicy`): a daily quota fails at once with a clear message; nothing but the number of texts and the outcome is logged, never a key or a text. The retrieval eval embeds all its queries in one request.

**Used in four places:** the new endpoint `GET /api/items/search` (the old `GET /api/items?q=` is unchanged), the item picker and Articoli search of the web UI, the candidates shown to the LLM for pending invoice lines, and the assistant's `searchItems` tool.

**Changing the model or the size.**
- *Same size, other model* (`LLM_EMBEDDING_MODEL`): nothing to do. Stored model names differ from the configured one, every item becomes stale, and the indexer re-embeds the catalog (two requests for 125 items). Until it finishes, the vector ranking only sees rows of the current model, and the text ranking carries the search.
- *Other size* (`LLM_EMBEDDING_DIMENSIONS`): the column must be recreated. With the application stopped: `DROP TABLE item_embedding; DELETE FROM flyway_schema_history WHERE version = '6';`, then start it with the new value: Flyway applies V6 again and the indexer re-embeds everything. A test runs exactly these steps. (In Docker, `docker compose down -v` also does it, and removes all data.)

**Data privacy.** The text of every item (code, description, supplier codes) and every search query are sent to the embedding provider. On a free tier the provider may use them to improve its models, so use only synthetic data there (ADR 0009); real catalogs need a paid or self-hosted model. A self-hosted OpenAI-compatible server with embeddings keeps everything on your machine.

## Alternatives considered

- **A separate vector database** (Qdrant, Weaviate, Pinecone, Chroma). Built for hundreds of millions of vectors and extra features. A parts catalog has thousands. It would be a second service to run, secure, back up and keep in sync with PostgreSQL (an item deleted in one place must disappear in the other), and no query could join vectors with stock or suppliers. pgvector gives joins, transactions and one backup.
- **Elasticsearch/OpenSearch for hybrid search.** Strong, with a built-in fusion, but again a second system with its own operations, for a problem a single SQL statement solves.
- **Embeddings only.** Simplest ranking, but a query that is a code, a part number or an exact phrase is found better by spelling, and a provider outage would leave no search at all.
- **A weighted sum of the two similarities.** More intuitive, but it needs weights tuned per model, and they must be re-tuned whenever the model changes. RRF has one constant that does not depend on the model.
- **Vectors in a JPA entity** (hibernate-vector). Less SQL to write, but it hides the operator, the cast and the index, which are the things worth explaining.
- **An untyped `vector` column** (any size per row, no index). Would let several models coexist, but there is no ANN index, and a mismatch of sizes fails at query time. A fixed size with a guard is explicit.
- **The full 3072 numbers** (`halfvec`). Slightly better quality, double the storage, and a different type; 768 loses almost nothing for a catalog this size.
- **Exact search without an index.** Perfectly fine for a few thousand items. HNSW is there because it is the standard answer to "what if the catalog grows" and the cost of keeping it is one line of SQL; the trade-off is that HNSW is approximate.

## Consequences

- Searching "filtro olio Fiat Panda" can find "Cartuccia lubrificante motore 1.2 FIRE", and invoice lines described in a supplier's words get better candidates for the LLM. The real quality depends on the embedding model; `RetrievalEvalTest` measures text-only against hybrid on the demo catalog (3 provider requests) and is opt-in like the extraction eval.
- **RRF favours items found by both rankings.** An item that matches a word ("Fiat Panda", "filtro") and is also close in meaning outranks the item that is closest in meaning but shares no word. In tests with a fake model the semantically right item was found and had the best vector similarity, but was not always first. Candidate lists (5 items for the LLM) are not hurt by this; if first place matters more, the fusion can be weighted.
- The HNSW index is approximate, and it filters by model after the nearest-neighbour step, so while many rows belong to an old model the vector ranking can return fewer than 20 candidates until the indexer has caught up.
- The vector size is fixed when the database is created, and changing it takes the documented steps above.
- The scheduler and the background worker are two small pieces of concurrency (one worker thread, one indexer run at a time); tests run everything in the calling thread.
- Embeddings cost provider requests: about 1000 embedding requests per day on the Gemini free tier is plenty for this project, but real use should be on a paid plan.
