# 0009. Gemini free tier as LLM provider, and only synthetic data

## Context

The AI features need an LLM. This is a public portfolio project that should run for anyone without a credit card or a paid account, and it should not tie the code to one vendor more than necessary.

## Decision

- **Provider**: Google Gemini through the **Gemini Developer API with an API key** (not Vertex AI, which needs a Google Cloud project), used via **Spring AI** (`spring-ai-starter-model-google-genai`). The model name is a configuration property (`spring.ai.google.genai.chat.model`, set from `LLM_MODEL`) that defaults to a Flash-class model listed as free of charge, `gemini-3.5-flash`. Check Google's pricing page: free-tier models change over time.
- **The key** is read only from the `LLM_API_KEY` environment variable (`spring.ai.google.genai.api-key=${LLM_API_KEY:}`). It is never in a file, never logged, and `.env.example` contains no real value.
- **Low rate limits are expected.** The gateway retries a few times with exponential backoff on HTTP 429 and temporary 5xx errors, then returns `503` with a clear message instead of failing. Spring AI's own retry (which can wait for minutes) is switched off so there is one predictable policy. The eval test pauses between calls.
- **No key is a normal state.** The Spring AI starter refuses to start with an empty key, which would take the whole application down. A placeholder client bean lets the application start; the gateway checks the real key first and never calls the model without it, so AI endpoints answer `503` and everything else works. A test pins this behaviour even when a key exists in the environment.
- **Only synthetic data is ever used** for fixtures, PDFs, examples and the eval. On the free tier, Google may use submitted content to improve its products, so real supplier invoices (company names, VAT numbers, prices) must not be sent. Every name and VAT number in this repository is invented.
- The application code depends on Spring AI's `ChatClient` and on our own `LlmGateway`, not on Gemini classes (except to recognise HTTP errors), so another provider is a matter of replacing the starter and configuration.

## Alternatives considered

- **A paid model/API.** Better limits and data terms, but a barrier for anyone cloning the project. For a real distributor with real invoices this is what should be used (paid tier terms keep prompts out of training), or a self-hosted model.
- **Vertex AI.** Better enterprise controls, but requires a cloud project and credentials.
- **A local model (for example through Ollama).** Keeps data on the machine, but quality for structured extraction from messy PDFs is lower, and it needs hardware.
- **Calling the provider SDK directly.** Fewer moving parts, but loses Spring AI's structured-output and tool-calling support that the features use.

## Consequences

- Anyone can run all features with a free key, and nothing breaks without one.
- Free-tier quotas can make AI endpoints answer `503` under load; clients must handle that.
- The project is not suitable for real business documents as it stands. Moving to production means a paid or self-hosted model, authentication, and a data-protection review.
