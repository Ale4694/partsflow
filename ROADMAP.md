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
- [x] Model mocked in tests; optional eval test enabled only when `LLM_API_KEY` is set

## M5 — Packaging and documentation
- [ ] Multi-stage Dockerfile; full stack runs with `docker compose up`
- [ ] README with Mermaid architecture diagram, one-command run, API overview, design decisions, CI badge
- [ ] Architecture Decision Records in `docs/adr/`

## Later / out of scope for now
- Signed FatturaPA files (`.p7m`)
- FatturaPA files containing several documents (several `FatturaElettronicaBody`)
- Document-level discounts and non-EUR currencies in FatturaPA import
