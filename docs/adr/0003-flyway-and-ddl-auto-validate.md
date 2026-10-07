# 0003. Flyway owns the schema; Hibernate only validates it

## Context

JPA/Hibernate can generate the database schema from the entity classes (`spring.jpa.hibernate.ddl-auto=update` or `create`). That is convenient on day one, but the schema then depends on whatever the entities looked like when the app last started, there is no history, and a production database cannot be changed safely this way.

## Decision

- All schema changes are SQL migrations in `src/main/resources/db/migration` (`V1__baseline.sql`, `V2__catalog.sql`, ...), applied by **Flyway** at startup. A migration that has been applied is never edited; a change is a new file.
- Hibernate is set to `spring.jpa.hibernate.ddl-auto=validate`: at startup it checks that every entity matches the real tables and refuses to start if not. So a forgotten migration or a typo in a column name is caught immediately, by the very first test, instead of in production.
- Business rules that the database can enforce are enforced there too, as a second safety net behind the Java code: `UNIQUE (supplier_id, document_number, document_date)` for idempotent imports, `CHECK (quantity > 0)` on movements, `CHECK (quantity >= 0)` on stock, foreign keys everywhere.
- Tests run Flyway against a real PostgreSQL (Testcontainers), the same image as production, so the migrations themselves are tested.

## Alternatives considered

- **`ddl-auto=update`/`create`**: fast to start, but no history, no review of schema changes, and it never drops or renames anything, so the schema and the code slowly diverge.
- **Liquibase**: equally capable; XML/YAML change sets are more abstract. Plain SQL files were chosen because they are easier to read, review and learn from.
- **An in-memory database (H2) for tests**: faster to start, but it behaves differently from PostgreSQL (and cannot run `pg_trgm`), so tests could pass while production fails.

## Consequences

- The schema is versioned, reviewable and reproducible: an empty database reaches the current state by running the migrations in order.
- Every entity change needs a matching migration written by hand. That is extra work, and it is the point.
- A broken migration stops the application from starting, which is the safe failure.
