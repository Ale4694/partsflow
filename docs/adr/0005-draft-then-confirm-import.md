# 0005. Imports create a draft; only confirmation changes stock

## Context

Importing a supplier invoice changes the inventory, and mistakes are expensive: the same invoice sent twice would double the stock, a product code the system does not know cannot be put on a shelf automatically, and a credit note must *remove* goods. Also, a person (or later an LLM) may need to look at the document before it counts.

## Decision

An import is two steps:

1. **Upload creates a draft** (`ImportDraft` with lines). Nothing touches the stock. Each line is `MATCHED` (the supplier's code maps to one of our items), `PENDING_REVIEW` (unknown or missing code) or `SKIPPED` (not goods, e.g. transport). For every goods line the draft already shows the `stockDelta`, positive for an invoice and negative for a credit note (decided by an exhaustive `switch` over the sealed `Document` type), so the person sees exactly what confirming will do.
2. **Confirm writes the stock movements.** It is refused (`409`) while any line is still `PENDING_REVIEW`: the person must pick an item for it (which also remembers the supplier code for next time) or skip it. Confirmation marks the draft `CONFIRMED` and writes all movements in **one transaction**, so it is all-or-nothing.

Safety rules around it:

- **Which code identifies a line**: FatturaPA allows several `CodiceArticolo` per line, each with a free-text `CodiceTipo` (the sample invoice prints an `EAN` and then the supplier's own `FORNITORE` code). The *supplier code* of a line is the first code whose type is **not** a barcode (`EAN`, `GTIN`, `UPC`, `BARCODE`, compared ignoring case); if the line only has barcodes, the first one is used. That code is what a pending line shows and what is remembered when a person resolves it. Matching against known mappings tries every code, supplier codes before barcodes, so a mapping saved under any of them is found. The rule lives in `DocumentLine.supplierCode()`.
- **Idempotency**: `UNIQUE (supplier_id, document_number, document_date)`. The same document cannot be imported twice; the second upload is a `409`. A draft that was not confirmed can be deleted to upload a corrected file.
- **Double confirm**: the draft has a `@Version` and is loaded with `OPTIMISTIC_FORCE_INCREMENT`, so two simultaneous confirms conflict; the loser retries, sees `CONFIRMED`, and answers `409`. Stock is applied once.
- **Validation before the draft exists**: totals must match `DatiRiepilogo` per VAT rate, the document type must be supported, the supplier must exist in the catalog. Failures are `422` with a message that says what is wrong.
- A failed confirm (for example a credit note that would make stock negative) rolls back completely and leaves the draft in `DRAFT`.

## Alternatives considered

- **Write to stock immediately on upload.** Simpler, but unknown codes, duplicates and errors would corrupt the inventory, and there is nothing to review.
- **Confirm partially (only matched lines) and leave pending lines behind.** Faster for the user, but pending lines would silently never reach the stock. Forcing "resolve or skip" makes every line an explicit decision.
- **Draft stored only in memory or as a file.** Would not survive a restart and cannot be reviewed by more than one person.

## Consequences

- The inventory only changes through a deliberate, auditable action; the movement carries the source document (`TD01 FT-0001/2026 of 2026-03-10 from Ricambi Rossi Srl`).
- One extra API call (confirm) per import and some extra tables.
- The same draft mechanism serves the AI features (ADR 0008): a PDF read by the LLM becomes a draft like any other.
