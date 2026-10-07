# Roadmap

Milestones are built in order. Each one ends with a passing `./mvnw verify` and a commit.

## M0 — Foundations
- [x] Project skeleton with package-by-feature layout (`catalog`, `inventory`, `invoiceimport`, `ai`, `common`)
- [x] `compose.yaml` with PostgreSQL and a healthcheck, `.env.example` (placeholders only), `.gitignore` excluding `.env`
- [x] Flyway baseline migration, `spring.jpa.hibernate.ddl-auto=validate`
- [x] Global error handling with `@RestControllerAdvice` returning `ProblemDetail`
- [x] GitHub Actions workflow running `./mvnw -B verify` on push and pull request

## M1 — Catalog
- [x] Suppliers (name, VAT number), items (internal code, description, unit, reorder threshold), supplier item codes
- [x] CRUD REST APIs with record DTOs, validation, pagination
- [x] Unit tests and `@WebMvcTest` tests

## M2 — Inventory
- [x] Stock movements (IN/OUT, quantity, reason, source document reference, timestamp) and current stock per item
- [x] Optimistic locking on stock; OUT movements that would make stock negative return 409
- [x] Endpoint listing items below the reorder threshold
- [x] Integration tests with Testcontainers, including a concurrency test

## M3 — FatturaPA import
- [x] Parse FatturaPA XML (FPR12/FPA12, v1.2.x) with Jackson XML; XML mapping classes separate from the domain model
- [x] BigDecimal for money and quantities; line totals validated against `DatiRiepilogo`
- [x] Document types as a sealed interface; TD04 credit notes reverse quantities
- [x] Idempotent import (same supplier + number + date is rejected)
- [x] Unknown supplier codes become pending review items, not stock
- [x] Upload creates a draft; a confirm endpoint writes the stock movements
- [x] Synthetic fixtures: valid invoice, credit note, malformed file, totals mismatch, unknown codes

## M4 — AI agent (human in the loop)
- [x] PDF documents: PDFBox text extraction, LLM structured output, validation in code, same draft as M3
- [x] Item matching: pg_trgm candidates, LLM picks best or none with justification, user accepts the suggestion
- [x] Inventory assistant chat endpoint with read-only tools
- [x] Step limit, LLM call logging without document contents, graceful 503 when the API key is missing or quota is exceeded
- [x] Model mocked in tests; optional eval test, strictly opt-in (`LLM_API_KEY` set and `LLM_EVAL=true`)

## M5 — Packaging and documentation
- [x] Multi-stage Dockerfile; full stack runs with `docker compose up`
- [x] README with Mermaid architecture diagram, one-command run, API overview, design decisions, CI badge
- [x] Architecture Decision Records in `docs/adr/`

## M6 — Angular web frontend
- [x] Angular 22 app in `frontend/` (standalone components, signals, zoneless, reactive forms, `httpResource`, lazy routes), UI in Italian
- [x] Dashboard, suppliers (with item codes), items with stock, warehouse movements, imports (draft review, resolve/skip/confirm/discard), AI assistant
- [x] One error interceptor turns ProblemDetail responses into messages; confirmation dialog before destructive actions
- [x] Small backend additions for the UI: stock overview endpoint, item search, item code on movements
- [x] nginx image serving the app and proxying `/api/`; `web` service in `compose.yaml` (port 8081)
- [x] Frontend unit tests (Vitest) and a frontend job in CI
- [x] README walkthrough with screenshots, ADR 0010

## M7 — Configurable LLM provider
- [x] `LLM_PROVIDER` selects Gemini (default) or any OpenAI-compatible service (`LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY`), environment variables only
- [x] Provider-specific code isolated in `ai.provider` (error translators); the gateway, retry policy and error codes work the same for both
- [x] Incomplete configuration means "AI not configured" (503), never a startup failure
- [x] Tests with mocked errors and local fake servers for both providers; README "Choosing the LLM provider" and ADR 0011

## Later / out of scope for now
- Signed FatturaPA files (`.p7m`)
- FatturaPA files containing several documents (several `FatturaElettronicaBody`)
- Document-level discounts and non-EUR currencies in FatturaPA import
- Authentication and user roles for the web interface
- End-to-end browser tests (Playwright) for the web interface
