package io.github.ale4694.partsflow.ai.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * A deterministic stand-in for a real embedding model, for tests (no network, no key).
 * Each word is mapped to a "concept" (words with the same meaning share one, via the synonym table) and each concept
 * switches on a few fixed positions of the vector ("feature hashing"). Two texts about the same concepts therefore
 * get similar vectors, which is all the search needs to be tested. NOT normalised, like the real 768-number
 * Gemini vectors, so the code under test has to normalise.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

	/** Words that mean the same thing for the tests (Italian synonyms of the demo catalog and of the brief). */
	public static final Map<String, String> SYNONYMS = Map.ofEntries(
			Map.entry("filtro", "filtro"), Map.entry("cartuccia", "filtro"), Map.entry("filtrante", "filtro"),
			Map.entry("olio", "olio"), Map.entry("lubrificante", "olio"),
			Map.entry("panda", "panda"), Map.entry("fire", "panda"),
			Map.entry("fiat", "fiat"),
			Map.entry("pastiglie", "freno"), Map.entry("freni", "freno"), Map.entry("freno", "freno"),
			Map.entry("brake", "freno"), Map.entry("pads", "freno"));

	private final int dimensions;
	private final Map<String, String> synonyms;
	private final List<Integer> batchSizes = new ArrayList<>();
	private Supplier<RuntimeException> failure;

	public FakeEmbeddingModel(int dimensions) {
		this(dimensions, SYNONYMS);
	}

	public FakeEmbeddingModel(int dimensions, Map<String, String> synonyms) {
		this.dimensions = dimensions;
		this.synonyms = synonyms;
	}

	/** From now on every call throws this (null = work again). */
	public void failWith(Supplier<RuntimeException> failure) {
		this.failure = failure;
	}

	/** How many texts each call carried, in order: the number of provider requests is its size. */
	public List<Integer> batchSizes() {
		return batchSizes;
	}

	public void reset() {
		batchSizes.clear();
		failure = null;
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		if (failure != null) {
			throw failure.get();
		}
		batchSizes.add(request.getInstructions().size());
		List<Embedding> embeddings = new ArrayList<>();
		for (int i = 0; i < request.getInstructions().size(); i++) {
			embeddings.add(new Embedding(embed(request.getInstructions().get(i)), i));
		}
		return new EmbeddingResponse(embeddings);
	}

	@Override
	public float[] embed(Document document) {
		return embed(document.getText());
	}

	@Override
	public int dimensions() {
		return dimensions;
	}

	/** Public so tests can compute the vector of a text. */
	public float[] embed(String text) {
		float[] vector = new float[dimensions];
		for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
			if (word.isEmpty()) {
				continue;
			}
			String concept = synonyms.getOrDefault(word, word);
			// three fixed positions per concept, from a hash that does not change between runs
			int hash = concept.hashCode();
			for (int k = 0; k < 3; k++) {
				int h = hash * (31 + 2 * k) + 17 * k;
				int position = Math.floorMod(h, dimensions);
				vector[position] += (h & 1) == 0 ? 3.0f : 2.0f; // not 1.0: the vector is deliberately not unit length
			}
		}
		return vector;
	}
}
