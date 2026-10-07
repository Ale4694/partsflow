# partsflow web interface

Angular single-page application for the partsflow backend (UI labels in Italian, code in English).
See the "Web interface" section of the repository README and `docs/adr/0010-angular-spa-behind-nginx.md`.

Requires Node 24 (`.node-version`).

```bash
npm ci
npm start        # http://localhost:4200, /api is proxied to http://localhost:8080 (proxy.conf.json)
npm test         # unit tests (Vitest), single run: npm test -- --watch=false
npm run lint
npm run build    # production build in dist/partsflow-web/browser
```

Layout: `src/app/core` (DTO types, error interceptor, notifications, layout), then one folder per feature
(`dashboard`, `suppliers`, `items`, `inventory`, `imports`, `ai`). Each feature has an `*-api.ts` service.
