package io.github.ale4694.partsflow.ai.assistant;

import io.github.ale4694.partsflow.ai.AiProperties;
import io.github.ale4694.partsflow.ai.LlmGateway;
import io.github.ale4694.partsflow.ai.matching.ItemCandidateRepository;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import io.github.ale4694.partsflow.inventory.InventoryService;
import org.springframework.stereotype.Service;

/** Answers questions about stock by letting the LLM call read-only tools. */
@Service
public class AssistantService {

	private static final String SYSTEM_PROMPT = """
			You are the inventory assistant of a small auto-parts distributor. You answer questions about stock levels,
			items below their reorder threshold and recent stock movements.
			Rules:
			- Base every answer ONLY on data returned by your tools. Never use your own knowledge for quantities,
			  codes or dates, and never guess.
			- If the tools do not give the answer, say that you could not find it.
			- Items are identified by their internal code. If the user names an item by description, use searchItems
			  to find its code first.
			- You can only read data. If asked to change stock, explain that you cannot and that movements are
			  recorded through the inventory API or by confirming an imported document.
			- Answer briefly, in the language of the question.""";

	private final LlmGateway gateway;
	private final InventoryService inventory;
	private final ItemRepository items;
	private final ItemCandidateRepository candidates;
	private final AiProperties properties;

	public AssistantService(LlmGateway gateway, InventoryService inventory, ItemRepository items,
			ItemCandidateRepository candidates, AiProperties properties) {
		this.gateway = gateway;
		this.inventory = inventory;
		this.items = items;
		this.candidates = candidates;
		this.properties = properties;
	}

	public AssistantResponse ask(String question) {
		gateway.requireConfigured();
		// A fresh tool object per question: it carries this question's call budget
		InventoryAssistantTools tools = new InventoryAssistantTools(inventory, items, candidates,
				properties.maxAgentSteps());
		String answer = gateway.converse("inventory-assistant", SYSTEM_PROMPT, question, tools);
		return new AssistantResponse(answer, Math.min(tools.calls(), properties.maxAgentSteps()));
	}
}
