# 0001. Package by feature, not by layer

## Context

A Spring application can be organised in two main ways:

- **By layer**: `controller/`, `service/`, `repository/`, `model/`. All controllers sit together, all services together, and so on.
- **By feature**: `catalog/`, `inventory/`, `invoiceimport/`, `ai/`. Everything about one business area sits together.

partsflow has a few clearly separate business areas (master data, stock, invoice import, AI helpers), each with its own tables, rules and endpoints.

## Decision

Package by feature, under `io.github.ale4694.partsflow`:

- `catalog`: suppliers, items, supplier item codes
- `inventory`: stock movements and current stock
- `invoiceimport`: FatturaPA parsing, drafts, confirmation
- `ai`: PDF import, item matching, inventory assistant
- `common`: only what is truly shared (error handling, retry helper, page response, clock)

Inside a feature, classes that are only used there are package-private (`SupplierService.find` is package-private, for example), so the package boundary is a real boundary and not just a folder.

Dependencies go one way: `ai` -> `invoiceimport` -> `inventory`/`catalog`. Nothing depends on `ai`. Each feature that needs special error mapping owns its own `@RestControllerAdvice` (`ImportExceptionHandler`, `AiExceptionHandler`), so `common` never has to know about features.

## Alternatives considered

- **Package by layer.** Familiar and what many tutorials show. But a change to one feature touches four packages, everything must be `public` so layers can see each other, and the folder structure says nothing about what the system does.
- **Separate Maven modules per feature.** Enforces boundaries at compile time, but adds build complexity that a project this size does not need.

## Consequences

- Opening the project shows what the system is about (`catalog`, `inventory`...) rather than which Spring stereotypes it uses.
- A feature can be understood, changed, or even extracted into its own service with little spill-over.
- Nothing stops a developer from adding a "wrong" dependency (for example `inventory` -> `ai`). The rule is kept by convention and code review, not by the compiler. If that becomes a problem, a tool such as ArchUnit or Maven modules can enforce it.
