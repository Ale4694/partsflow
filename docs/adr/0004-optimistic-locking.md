# 0004. Optimistic locking (with retry) for stock

## Context

Two requests can change the stock of the same item at the same moment (two people confirming invoices, a sale while an import is confirmed). A naive "read the quantity, add, write it back" loses updates: both read 10, both write 11, and the stock is 11 instead of 12. For OUT movements it can sell stock that does not exist.

There are two classic answers:

- **Pessimistic locking**: lock the row while working on it. Others wait.
- **Optimistic locking**: assume conflicts are rare. Do the work without a lock, and when saving, check that nobody else changed the row in the meantime.

## Decision

Optimistic locking:

- `Stock` has a `@Version` column. Hibernate turns each update into `UPDATE stock SET quantity=?, version=? WHERE item_id=? AND version=?`. If another transaction got there first, no row matches and the update fails instead of overwriting.
- The conflict is only detected when the transaction commits, so the retry has to wrap the **whole transaction**. `RetryingTransaction` does that: it runs the work in a transaction and repeats it (up to 10 times) when it loses a race. With N concurrent writers each round at least one wins, so a writer loses at most N-1 times.
- The same helper covers the race where two requests create the stock row of a new item at once (the second hits the primary key and retries).
- When retries run out, the client gets `409 Conflict`. An OUT movement that would make the stock negative is a `409` as well (`InsufficientStockException`), and the database also has `CHECK (quantity >= 0)` as a last line of defence.
- Stock movements are an append-only history; current stock is a separate row so reading it is one lookup.
- The draft of an import has its own `@Version` too (see ADR 0005), so two simultaneous "confirm" requests cannot both apply the stock.

Concurrency tests (`StockConcurrencyTest`, `ImportConcurrencyTest`) start several threads at the same instant against a real PostgreSQL: 8 concurrent IN movements all apply; 8 concurrent OUT movements on a stock of 5 succeed exactly 5 times; 6 concurrent confirms of one draft apply the stock once.

## Alternatives considered

- **Pessimistic locking (`SELECT ... FOR UPDATE`)**: simple and never needs a retry, but holds database locks while other work happens and can deadlock when a transaction locks several items in different orders. For a system where conflicts on the same item are rare, optimistic is cheaper.
- **`UPDATE stock SET quantity = quantity + ?` in SQL**: atomic and very fast, but the "not negative" rule and the movement history then live in SQL tricks instead of readable Java, and it gives up the `@Version` teaching value. A fine choice for a high-throughput system.
- **Serializable transactions**: correct but pushes the retry problem onto every query in the application.

## Consequences

- No lock is held while the application works; throughput is good when conflicts are rare.
- Code that changes stock must run inside `RetryingTransaction` (or be called from something that does). Calling it from inside another transaction would move the retry too late; `applyAll` is marked `Propagation.MANDATORY` so the intent is explicit.
- Under very heavy contention on one item, writers retry and some may end in a 409. That is acceptable and visible, never silent data loss.
