# partsflow

![CI](https://github.com/ale4694/partsflow/actions/workflows/ci.yml/badge.svg)

A management system ("gestionale") for a fictional small Italian auto-parts distributor: a Spring Boot backend and an Angular web interface.

It imports Italian electronic invoices (**FatturaPA XML**) from suppliers, keeps the **inventory** up to date, and uses an **LLM-based agent** for the messy parts (PDF documents, matching unknown product codes, answering stock questions). Everything the LLM produces is a *proposal* that a person confirms: the LLM never writes to the inventory.

This is a portfolio project. The code is written to be read: simple, explicit, and commented where a decision is not obvious. All sample data (companies, VAT numbers, products) is invented.

## What it does

| Area | What you can do |
| --- | --- |
| **Catalog** | Manage suppliers, items, and the mapping from a supplier's article code to our internal item. |
| **Inventory** | Record stock movements (IN/OUT), see current stock, list items below their reorder threshold. Stock never goes negative, even with concurrent updates. |
| **FatturaPA import** | Upload an XML invoice or credit note. It becomes a **draft**; lines with unknown supplier codes wait for review. Only **confirming** the draft changes the stock. Importing the same document twice is rejected. |
| **Web interface** | An Italian-language Angular app on top of the API: dashboard, suppliers, items, warehouse, the import review screen and the AI assistant. |
| **AI agent** | Turn a supplier **PDF** into a draft, get **match suggestions** for unknown codes, and ask an **inventory assistant** questions in plain language. |

## Architecture

```mermaid
flowchart LR
    browser([Browser])
    web[web - Angular SPA<br/>served by nginx]
    client([API client / Swagger UI])

    subgraph app[partsflow - Spring Boot]
        direction TB
        catalog[catalog<br/>suppliers, items, supplier codes]
        inventory[inventory<br/>movements, stock, optimistic locking]
        imports[invoiceimport<br/>FatturaPA parser, drafts, confirm]
        ai[ai<br/>PDF import, item matching, assistant]
        common[common<br/>errors, retry, config]
    end

    db[(PostgreSQL<br/>+ pg_trgm)]
    llm[[Gemini API<br/>free tier]]

    browser --> web
    web -- "/api reverse proxy" --> app
    client --> catalog
    client --> inventory
    client --> imports
    client --> ai
    imports --> catalog
    imports -- "confirm: one transaction" --> inventory
    ai --> imports
    ai --> inventory
    catalog --> db
    inventory --> db
    imports --> db
    ai --> db
    ai -- "only through LlmGateway" --> llm
```

Packages are organised **by feature**, not by layer (see [ADR 0001](docs/adr/0001-package-by-feature.md)). Dependencies only point one way: `ai` may use `invoiceimport`, `invoiceimport` may use `inventory` and `catalog`, and nothing depends on `ai`.

### How an import flows

```mermaid
sequenceDiagram
    actor User
    participant API as partsflow
    participant DB as PostgreSQL

    User->>API: POST /api/imports/fatturapa (XML)
    API->>API: parse, validate totals, reject duplicates
    API->>DB: save DRAFT (lines: MATCHED / PENDING_REVIEW / SKIPPED)
    API-->>User: 201 draft (stock untouched)
    opt unknown supplier codes
        User->>API: resolve line (pick item) or skip line
        Note over API,DB: resolving also remembers the supplier code
    end
    User->>API: POST /api/imports/{id}/confirm
    API->>DB: ONE transaction: draft = CONFIRMED + stock movements
    API-->>User: 200 confirmed draft
```

## Run it

You need Docker (with Compose). One command starts PostgreSQL, the application and the web interface:

```bash
docker compose up --build
```

The web interface starts only after the application reports itself healthy (`/actuator/health`), so the first page load never gets a "502 Bad Gateway" while the backend is still starting. Then open **http://localhost:8081** for the web interface (change the port with `WEB_PORT`), or **http://localhost:8080/swagger-ui.html** to explore the API directly.

The AI features are optional. Without an API key every `/api/ai/**` endpoint answers `503` and the rest of the application works normally. To enable them, get a free key from Google AI Studio and export it **in your shell** before starting (it is passed to the container, never stored in a file):

```bash
export LLM_API_KEY=...   # your own key
docker compose up --build
```

Settings live in environment variables; `.env.example` lists them with placeholder values. Copy it to `.env` if you want to change the database credentials (`.env` is git-ignored).

### Try the whole flow

The repository contains invented sample invoices in `src/test/resources/fatturapa/`.

```bash
# 1. A supplier (the VAT number matches the sample invoice) and two items
curl -s -X POST localhost:8080/api/suppliers -H 'Content-Type: application/json' \
  -d '{"name":"Ricambi Rossi Srl","vatNumber":"20000000001"}'
curl -s -X POST localhost:8080/api/items -H 'Content-Type: application/json' \
  -d '{"code":"BRK-001","description":"Front brake pad set","unit":"PZ","reorderThreshold":5}'
curl -s -X POST localhost:8080/api/items -H 'Content-Type: application/json' \
  -d '{"code":"OIL-530","description":"Engine oil 5W-30, 5 litre can","unit":"PZ","reorderThreshold":10}'

# 2. Tell the system which supplier code is which of our items
curl -s -X POST localhost:8080/api/suppliers/1/item-codes -H 'Content-Type: application/json' \
  -d '{"supplierCode":"RR-BRK-001","itemId":1}'
curl -s -X POST localhost:8080/api/suppliers/1/item-codes -H 'Content-Type: application/json' \
  -d '{"supplierCode":"RR-OIL-530","itemId":2}'

# 3. Upload the invoice: you get a DRAFT, the stock is still 0
curl -s -F file=@src/test/resources/fatturapa/invoice-valid.xml localhost:8080/api/imports/fatturapa
curl -s localhost:8080/api/inventory/stock/1

# 4. Confirm the draft: now the stock moves (+10 brake pads, +6 oil)
curl -s -X POST localhost:8080/api/imports/1/confirm
curl -s localhost:8080/api/inventory/stock/1

# 5. Upload the same file again: rejected (409), a document is imported only once
curl -s -F file=@src/test/resources/fatturapa/invoice-valid.xml localhost:8080/api/imports/fatturapa
```

(The ids above assume a fresh database.)

The sample invoice prints two codes on its first line: an EAN barcode and the supplier's own code `RR-BRK-001`. The supplier's code is the one used to identify a line (barcodes are only a fallback), see [ADR 0005](docs/adr/0005-draft-then-confirm-import.md). If you upload the invoice **before** creating the supplier item codes, the lines stay `PENDING_REVIEW`: resolve them (or create the codes and upload again) and the codes are remembered.

## Web interface

Open **http://localhost:8081** after `docker compose up --build`. The sidebar has six screens (labels are in Italian):

| Screen | What it is for |
| --- | --- |
| **Dashboard** | Items below their reorder threshold, the latest stock movements, drafts waiting for confirmation, whether the AI is available |
| **Fornitori** | Suppliers (create, edit, delete) and, for each supplier, the codes it prints on invoices mapped to your items |
| **Articoli** | Items with their current stock (create, edit, delete) |
| **Magazzino** | Movement history (filter by item) and a form to record IN/OUT movements; an OUT that would make stock negative is refused with a clear message |
| **Importazioni** | Upload a FatturaPA XML (or, with the AI, a supplier PDF), review the draft, resolve or skip pending lines, confirm or discard |
| **Assistente** | Ask questions about the inventory in plain language (shown as unavailable without an API key) |

![Dashboard](docs/screenshots/dashboard.png)

### Walkthrough: import an invoice

1. In **Fornitori** create *Ricambi Rossi Srl* with VAT number `20000000001`; in **Articoli** create the items `BRK-001`, `OIL-530` and `TB-100`.
2. Open the supplier and add the code `RR-OIL-530` mapped to `OIL-530`.
3. In **Importazioni** click *Carica fattura XML* and choose `src/test/resources/fatturapa/unknown-codes.xml`. You get a **draft**: the stock has not changed yet.
4. The draft shows each line with a colour: green *Abbinata* (the supplier code is known), amber *Da verificare* (unknown or missing code), grey *Ignorata*. *Conferma* stays disabled while a line is amber.
5. For the first line click *Scegli articolo*, search "belt" and pick `TB-100`: the supplier code is remembered for the next invoice. Click *Ignora* on the wiper blades line.
6. Click *Conferma* and accept the dialog. The stock moves (+3 timing belts, +1 oil); see it in **Magazzino** and **Articoli**. Uploading the same file again is refused.

![Items with their current stock](docs/screenshots/items.png)

![A draft with lines waiting for review](docs/screenshots/import-draft-pending.png)

With an API key the *Importazioni* screen also offers *Carica PDF (AI)* and, on a draft, *Suggerisci abbinamenti (AI)* with accept/reject buttons. Money and quantities are only formatted in the browser (Italian number format); every calculation stays in the backend.

For development with hot reload, run the backend (see below) and then `cd frontend && npm ci && npm start`: the app is on http://localhost:4200 and `/api` is proxied to port 8080. It needs Node 24 (see `frontend/.node-version`). More in [`frontend/README.md`](frontend/README.md) and [ADR 0010](docs/adr/0010-angular-spa-behind-nginx.md).

## Develop

Requirements: Java 21 and Docker (the integration tests start PostgreSQL with Testcontainers).

```bash
./mvnw verify          # compile, run all tests
./mvnw -q verify       # same, short output
```

To run the application from your IDE against the Compose database, start only the database (`docker compose up db`) and run `PartsflowApplication`; the defaults in `application.yml` match `compose.yaml`.

### The optional LLM accuracy eval

`ExtractionEvalTest` sends a few **synthetic** PDFs to the real Gemini API and prints how many fields were extracted correctly (report in `target/llm-eval-report.txt`). It is **strictly opt-in**: it runs only when **both** `LLM_API_KEY` is set **and** `LLM_EVAL=true`. A plain `./mvnw verify` never calls the LLM, even if `LLM_API_KEY` is exported in your shell, and CI never does either. All other tests mock the model.

```bash
export LLM_API_KEY=...        # your own key
LLM_EVAL=true ./mvnw -Dtest=ExtractionEvalTest test
```

It pauses between calls to respect the free-tier rate limit (`EVAL_PAUSE_SECONDS`, default 15) and takes a couple of minutes. It reports accuracy instead of failing on a low score, so you can compare prompt changes.

## API overview

Interactive documentation: Swagger UI at `/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`. Lists are paginated (`page`, `size`, `sort`) and return `content`, `page`, `size`, `totalElements`, `totalPages`.

| Method and path | Purpose |
| --- | --- |
| `GET/POST /api/suppliers`, `GET/PUT/DELETE /api/suppliers/{id}` | Suppliers (name, VAT number) |
| `GET/POST /api/items`, `GET/PUT/DELETE /api/items/{id}` | Items (code, description, unit, reorder threshold); `GET` accepts `q` to search code and description |
| `GET/POST /api/suppliers/{id}/item-codes`, `GET/PUT/DELETE .../{codeId}` | A supplier's article codes mapped to our items |
| `POST /api/inventory/movements` | Record an IN or OUT movement (`409` if stock would go negative) |
| `GET /api/inventory/movements?itemId=` | Movement history, newest first |
| `GET /api/inventory/stock` | Every item with its current stock (ordered by code) |
| `GET /api/inventory/stock/{itemId}` | Current stock of an item |
| `GET /api/inventory/low-stock` | Items below their reorder threshold |
| `POST /api/imports/fatturapa` | Upload a FatturaPA XML file: creates a draft |
| `GET /api/imports`, `GET /api/imports/{id}` | List drafts / see a draft with its lines |
| `POST /api/imports/{id}/lines/{lineId}/resolve` | Pick the item for a pending line (remembers the supplier code) |
| `POST /api/imports/{id}/lines/{lineId}/skip` | Do not load a pending line into stock |
| `POST /api/imports/{id}/confirm` | Confirm the draft: writes the stock movements |
| `DELETE /api/imports/{id}` | Discard a draft that was not confirmed |
| `GET /actuator/health` | Health check used by Docker Compose (the only Actuator endpoint exposed) |
| `GET /api/ai/status` | Whether the AI features are available |
| `POST /api/ai/imports/pdf` | Upload a supplier PDF (invoice, credit note, delivery note): creates a draft |
| `POST /api/ai/imports/{id}/suggest-matches` | Ask the LLM to propose items for pending lines |
| `GET /api/ai/imports/{id}/suggestions` | See the stored suggestions |
| `POST /api/ai/suggestions/{id}/accept` / `reject` | Decide on a suggestion |
| `POST /api/ai/assistant` | Ask a question about stock in plain language |

Errors are always [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json`:

| Status | Meaning |
| --- | --- |
| `400` | The request or file cannot be read (invalid JSON, broken XML, not a PDF) |
| `404` | The resource does not exist |
| `409` | Conflict with current data: duplicate, insufficient stock, document already imported or confirmed, lines still pending review |
| `422` | Readable, but breaks a business rule: totals do not add up, unsupported document type, unknown supplier VAT number |
| `502` | The LLM answered with something unusable |
| `503` | The AI features are unavailable: no API key, or the free-tier quota is exhausted |

## Design decisions

The reasoning behind each choice, with the alternatives that were considered, is written down in [`docs/adr/`](docs/adr/README.md):

1. [Package by feature](docs/adr/0001-package-by-feature.md)
2. [BigDecimal for money and quantities](docs/adr/0002-bigdecimal-for-money.md)
3. [Flyway migrations with `ddl-auto=validate`](docs/adr/0003-flyway-and-ddl-auto-validate.md)
4. [Optimistic locking for stock](docs/adr/0004-optimistic-locking.md)
5. [Draft-then-confirm import](docs/adr/0005-draft-then-confirm-import.md)
6. [Jackson XML instead of JAXB](docs/adr/0006-jackson-xml-instead-of-jaxb.md)
7. [pg_trgm instead of embeddings for matching](docs/adr/0007-pg-trgm-instead-of-embeddings.md)
8. [Human-in-the-loop AI](docs/adr/0008-human-in-the-loop-ai.md)
9. [Gemini free tier, and why only synthetic data](docs/adr/0009-gemini-free-tier-and-synthetic-data.md)
10. [Angular single-page app behind an nginx reverse proxy](docs/adr/0010-angular-spa-behind-nginx.md)

In short: deterministic code for everything that follows fixed rules (XML parsing, totals, stock arithmetic); the LLM only where judgment is needed (reading a messy PDF, deciding whether two product descriptions are the same part); and a person always in the loop before the inventory changes.

## Limitations

- Signed FatturaPA files (`.p7m`) and files with several documents are not supported yet.
- Document-level discounts and currencies other than EUR are not handled in the FatturaPA import.
- PDFs must contain text; scanned images (OCR) are not supported.
- There is no authentication: this is a portfolio project, not a production system.

## Roadmap

Progress and what is planned next: [ROADMAP.md](ROADMAP.md).
