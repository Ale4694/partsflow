package io.github.ale4694.partsflow.ai.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import io.github.ale4694.partsflow.ai.AiProperties;
import io.github.ale4694.partsflow.ai.AiUnavailableException;
import io.github.ale4694.partsflow.ai.ProviderCallPolicy;
import io.github.ale4694.partsflow.ai.provider.GeminiEmbeddingOptionsFactory;
import io.github.ale4694.partsflow.ai.provider.GeminiErrorTranslator;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;

/** The gateway with a fake embedding model: batching, normalising, size checks and the shared error policy. */
class EmbeddingGatewayTest {

	private static final int DIMENSIONS = 16;

	private FakeEmbeddingModel model;

	@BeforeEach
	void setUp() {
		model = new FakeEmbeddingModel(DIMENSIONS);
	}

	@SuppressWarnings("unchecked")
	private EmbeddingGateway gateway(String apiKey, String embeddingModel, int batchSize, EmbeddingModel actual) {
		AiProperties ai = new AiProperties(5, 5, 0.1, 10, 30000,
				new AiProperties.Retry(3, Duration.ofMillis(1), 2.0, Duration.ofSeconds(5)),
				AiProperties.Provider.GEMINI, apiKey, "", "");
		ObjectProvider<EmbeddingModel> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(actual);
		when(provider.getObject()).thenReturn(actual);
		return new EmbeddingGateway(provider, ai, TestEmbeddingProperties.of(embeddingModel, DIMENSIONS, batchSize),
				new GeminiEmbeddingOptionsFactory(), new ProviderCallPolicy(ai, new GeminiErrorTranslator()));
	}

	@Test
	void textsAreSentInBatchesAndComeBackInOrder() {
		EmbeddingGateway gateway = gateway("key", "", 100, model);
		List<String> texts = IntStream.range(0, 250).mapToObj(i -> "filtro olio " + i).toList();

		List<float[]> vectors = gateway.embedDocuments(texts);

		assertThat(vectors).hasSize(250);
		assertThat(model.batchSizes()).containsExactly(100, 100, 50); // three requests for 250 texts
		assertThat(vectors.get(7)).containsExactly(VectorMath.normalize(model.embed("filtro olio 7")));
	}

	@Test
	void severalQueriesShareOneRequest() {
		EmbeddingGateway gateway = gateway("key", "", 100, model);

		gateway.embedQueries(List.of("filtro olio", "pastiglie freni", "fiat panda"));

		assertThat(model.batchSizes()).containsExactly(3);
	}

	@Test
	void vectorsAreNormalisedToLengthOne() {
		EmbeddingGateway gateway = gateway("key", "", 100, model);

		float[] vector = gateway.embedQueries(List.of("filtro olio fiat panda")).getFirst();

		double sum = 0;
		for (float value : vector) {
			sum += value * value;
		}
		assertThat(Math.sqrt(sum)).isCloseTo(1.0, within(1e-5));
	}

	@Test
	void geminiHasADefaultModelAndOthersNeedOne() {
		assertThat(gateway("key", "", 100, model).modelName()).isEqualTo("gemini-embedding-001");
		assertThat(gateway("key", "my-model", 100, model).modelName()).isEqualTo("my-model");
	}

	@Test
	void withoutAKeyNothingIsSent() {
		EmbeddingGateway gateway = gateway("", "", 100, model);

		assertThat(gateway.isConfigured()).isFalse();
		assertThatThrownBy(() -> gateway.embedQueries(List.of("x")))
				.isInstanceOfSatisfying(AiUnavailableException.class,
						e -> assertThat(e.reason()).isEqualTo(AiUnavailableException.Reason.KEY_MISSING));
		assertThat(model.batchSizes()).isEmpty();
	}

	@Test
	void withoutAnEmbeddingModelBeanItIsNotConfigured() {
		assertThat(gateway("key", "", 100, null).isConfigured()).isFalse();
	}

	@Test
	void aVectorOfTheWrongSizeIsRejected() {
		EmbeddingGateway gateway = gateway("key", "", 100, new FakeEmbeddingModel(DIMENSIONS + 1));

		assertThatThrownBy(() -> gateway.embedDocuments(List.of("filtro")))
				.isInstanceOfSatisfying(AiUnavailableException.class, e -> {
					assertThat(e.reason()).isEqualTo(AiUnavailableException.Reason.REJECTED);
					assertThat(e.getMessage()).contains("17").contains("16");
				});
	}

	@Test
	void anOverloadedProviderIsRetriedOnceAndThenAnswers() {
		int[] calls = { 0 };
		EmbeddingModel flaky = new FakeEmbeddingModel(DIMENSIONS) {
			@Override
			public org.springframework.ai.embedding.EmbeddingResponse call(
					org.springframework.ai.embedding.EmbeddingRequest request) {
				if (calls[0]++ == 0) {
					throw new ServerException(503, "UNAVAILABLE", "high demand");
				}
				return super.call(request);
			}
		};

		List<float[]> vectors = gateway("key", "", 100, flaky).embedQueries(List.of("filtro"));

		assertThat(vectors).hasSize(1);
		assertThat(calls[0]).isEqualTo(2);
	}

	@Test
	void aDailyQuotaFailsAtOnceWithoutRetrying() {
		model.failWith(() -> new ClientException(429, "RESOURCE_EXHAUSTED",
				"Quota exceeded for metric: embed_content_free_tier_requests. Please retry in 9h3m1.2s."));

		assertThatThrownBy(() -> gateway("key", "", 100, model).embedDocuments(List.of("filtro")))
				.isInstanceOfSatisfying(AiUnavailableException.class, e -> {
					assertThat(e.reason()).isEqualTo(AiUnavailableException.Reason.DAILY_QUOTA_EXHAUSTED);
					assertThat(e.retryAfter()).isNotNull();
				});
		assertThat(Collections.frequency(model.batchSizes(), 1)).isZero(); // the failing model never answered
	}
}
