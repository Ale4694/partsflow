package io.github.ale4694.partsflow.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * The only place that talks to the chat model, whichever provider is active. It adds a clear 503 when the AI is not
 * configured (the model is not called at all) and runs every call through the {@link ProviderCallPolicy}, which
 * owns the retry rules, the error mapping and the logging.
 */
@Service
public class LlmGateway {

	private final ChatClient chatClient;
	private final AiProperties properties;
	private final ProviderCallPolicy policy;

	public LlmGateway(ChatClient chatClient, AiProperties properties, ProviderCallPolicy policy) {
		this.chatClient = chatClient;
		this.properties = properties;
		this.policy = policy;
	}

	public boolean isConfigured() {
		return properties.configured();
	}

	/** Fails fast with a 503 when the AI is not configured, so callers can skip work that would be wasted. */
	public void requireConfigured() {
		if (!isConfigured()) {
			throw new AiUnavailableException(AiUnavailableException.Reason.KEY_MISSING,
					"AI features are disabled: LLM_API_KEY is not set"
							+ (properties.provider() == AiProperties.Provider.OPENAI_COMPATIBLE
									? " or LLM_BASE_URL / LLM_MODEL are missing (provider openai-compatible)" : ""));
		}
	}

	/** Asks the LLM for an answer shaped like {@code type} (JSON mapped to a record). The caller must validate it. */
	public <T> T structured(String operation, String systemPrompt, String userPrompt, Class<T> type) {
		requireConfigured();
		return policy.execute(operation, "promptChars=" + (systemPrompt.length() + userPrompt.length()),
				() -> chatClient.prompt()
						.system(systemPrompt)
						.user(userPrompt)
						.call()
						.entity(type));
	}

	/** Asks the LLM a question it may answer by calling the given {@code @Tool} methods. */
	public String converse(String operation, String systemPrompt, String userPrompt, Object tools) {
		requireConfigured();
		return policy.execute(operation, "promptChars=" + (systemPrompt.length() + userPrompt.length()),
				() -> chatClient.prompt()
						.system(systemPrompt)
						.user(userPrompt)
						.tools(tools)
						.call()
						.content());
	}
}
