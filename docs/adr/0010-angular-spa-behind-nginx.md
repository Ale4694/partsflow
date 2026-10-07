# 0010. Angular single-page app served by nginx, which proxies the API

## Context

The backend can be explored with Swagger UI, but that is not something a non-technical person can use, and the main feature (reviewing an imported invoice before it changes the stock) is a visual task: look at the lines, decide on the unclear ones, confirm. The project needs a web interface that can be demonstrated in a few minutes, and the code must stay readable for a junior developer who is learning the framework and will rewrite it by hand.

## Decision

- A **single-page application in Angular** (`frontend/`), written with the current idioms: standalone components, signals for state, the `@if`/`@for` control flow, `input()`/`output()`, `inject()`, zoneless change detection, reactive forms, lazy-loaded routes. Reads use `httpResource`, writes use `HttpClient`. Plain CSS with a few CSS variables; no component library.
- **One API service per feature** (`SuppliersApi`, `ImportsApi`...) with TypeScript interfaces that mirror the backend DTOs, and **one HTTP interceptor** that turns every RFC 9457 ProblemDetail into a visible message using its `detail` field. Screens never build error texts by hand.
- **The browser never calculates.** Money and quantities are only formatted (`Intl.NumberFormat('it-IT')`); the draft totals, the stock and the credit-note sign all come from the backend, so there is one place where arithmetic can be wrong.
- **Production**: a multi-stage Docker image builds the app with Node and serves the static files with **nginx**. nginx falls back to `index.html` for client-side routes and **reverse-proxies `/api/` to the Spring Boot container**. The browser talks to one origin only, so no CORS configuration is needed in the backend. In development, `ng serve` does the same job with `proxy.conf.json`.
- The backend stays unchanged except for three small additive endpoints/fields the screens needed (a stock overview list, a `q` search on items, `itemCode` on movements).

## Alternatives considered

- **React or SvelteKit.** Both are good choices and lighter to start with. Angular was picked because it is what the owner is learning, and because it ships routing, forms, an HTTP client and testing in one consistent toolkit, which means fewer library choices to explain. SvelteKit would give the smallest code; React the largest job market and ecosystem.
- **Thymeleaf (server-rendered pages inside Spring Boot).** One deployable and no CORS or proxy at all. But the review screen is interactive (searchable item pickers, per-line actions, live enabled/disabled confirm button), and that pushes server-rendered pages towards a lot of JavaScript anyway, mixed into the backend. It would also hide the clean REST API boundary that the rest of the project is built around.
- **Server-side rendering (Angular SSR or similar).** Helps with SEO and first paint on public sites. This is an internal tool behind a login that does not exist yet, so SSR would add a Node server to run and debug for no benefit.
- **Serving the static files from Spring Boot.** Avoids the extra container, but ties the frontend release to the backend and mixes two build tools in one image. Keeping nginx separate is the usual shape of a real deployment.
- **Calling the API directly from the browser (CORS).** Needs CORS configuration in the backend and the API address baked into the frontend build. The reverse proxy avoids both.

## Consequences

- Two more things to build and run (a Node build stage and an nginx container), and the CI workflow has a second job.
- The API contract is copied into TypeScript by hand: a backend change must be mirrored in `core/models.ts`. Generating the types from the OpenAPI document would remove that, at the price of a build step; it was left out to keep the code easy to read.
- No authentication yet: whoever can reach port 8081 can use everything. That has to come before any real use.
- Screens are unit-tested (Vitest) at the level of services, the interceptor, formatting and the draft review component; there are no end-to-end browser tests yet.
