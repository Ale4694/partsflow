# 0011. A configurable LLM provider: Gemini or any OpenAI-compatible service

## Context

The project started on the Gemini free tier ([ADR 0009](0009-gemini-free-tier-and-synthetic-data.md)). In live use it proved unreliable for demos: about 20 requests per day per model (an assistant question uses at least two), frequent "high demand" errors, and older models removed for new users. Whoever runs the project must be able to switch to another service, with a key of their own, without changing code, and without the AI features caring which service answers.

## Decision

- **The provider is chosen with environment variables only**: `LLM_PROVIDER` (`gemini`, the default, or `openai-compatible`), `LLM_API_KEY`, `LLM_MODEL` and, for `openai-compatible`, `LLM_BASE_URL`. They are mapped onto `partsflow.ai.*` in `application.yml`. Compose passes them through; `.env.example` lists them with placeholders.
- **Both Spring AI starters are on the classpath** and exactly one chat model starts: a nested placeholder in `application.yml` maps the provider onto Spring AI's `spring.ai.model.chat` (`gemini` to `google-genai`, `openai-compatible` to `openai`). An unknown value starts no model, and the binding of `partsflow.ai.provider` rejects it with a message that lists the valid values.
- **OpenAI-compatible** means Spring AI's OpenAI starter (the official OpenAI Java SDK) with a configurable base URL. OpenAI, Mistral, Groq, DeepSeek, OpenRouter and local servers speak that protocol.
- **Provider code is isolated in the `ai.provider` package.** Each provider has one configuration class (the Gemini client bean with its settings; the OpenAI translator bean) and one `LlmErrorTranslator`. The translator turns the provider's exceptions into a neutral `LlmFailure` (rejected, daily quota, rate limited, overloaded, network) using the HTTP status, the provider's error code, its message and the `Retry-After` header. `LlmGateway`, the services and the controllers see only `ChatClient`, `AiProperties` and the translator interface.
- **One policy for both providers**, in `LlmGateway` and decided from the neutral kind only: overload and short rate limits are retried with backoff (3 attempts); a daily quota, no credit (HTTP 402 or `insufficient_quota`) or a wait longer than 20 s fails at once; rejected requests (invalid key, model not found, bad request) are not retried. The user sees the same error codes (`AI_REJECTED`, `AI_DAILY_QUOTA_EXHAUSTED`, `AI_RATE_LIMITED`, `AI_TEMPORARILY_UNAVAILABLE`, `AI_KEY_MISSING`) and the same Italian messages. An out-of-credit error is reported as `AI_DAILY_QUOTA_EXHAUSTED`, and its message says "riprova più tardi o controlla il credito".
- **Only our gateway retries.** The Gemini SDK retries by default (5 attempts) and so does the OpenAI SDK (`max-retries` 3); both are switched off (`attempts(1)` and `spring.ai.openai.max-retries: 0`), and tests with local fake servers prove one request per call.
- **Incomplete configuration is not an error at startup.** The AI counts as "not configured" without a key, or, for `openai-compatible`, without a base URL or a model. The endpoints answer `503` (`AI_KEY_MISSING`) and everything else works.
- **The "LLM never writes to inventory" rule is untouched**: the assistant's tools are read-only and every other AI result is a proposal a person confirms ([ADR 0008](0008-human-in-the-loop-ai.md)).

## Alternatives considered

- **Only Gemini.** Simplest, but ties demos to a free tier that is unreliable, and to one vendor.
- **Only an OpenAI-compatible API.** Gemini also offers an OpenAI-compatible endpoint, which would make one code path enough. But the native client is what the project was built and tested on, and its error format (quota texts, `retryDelay`) differs; keeping it is the safer default.
- **One Spring AI starter per Maven profile or build.** Switching would need a rebuild; with both starters present a restart with other variables is enough.
- **Wiring the chat models by hand.** Full control, but it duplicates what Spring AI's auto-configuration does (tool calling, observation) and has to be kept in step with it.
- **A provider abstraction of our own over the SDKs.** More code to learn for little gain: Spring AI's `ChatClient` already is that abstraction. Only the error handling is provider specific, and that is what the translators cover.

## Consequences

- Switching provider is a matter of environment variables and a restart; the AI code does not change.
- "OpenAI-compatible" is a family, not a guarantee: services differ in tool-calling support, JSON output and the wording of quota errors. The daily-quota detection uses several hints (provider code, status 402, "per day" and similar wording, a `Retry-After` of an hour or more) and may need a new hint for a new service.
- The OpenAI-compatible path is tested with mocked errors and a local fake server, not against live services.
- Both SDKs are on the classpath (a larger jar); the OpenAI SDK's dependency on the old `swagger-annotations` artifact is excluded in the `pom.xml` because it clashes with the Jakarta variant springdoc uses.
- Data privacy depends on the chosen service: the rule of using only synthetic data applies to every free tier (ADR 0009).
