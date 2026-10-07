package io.github.ale4694.partsflow.ai.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class VectorMathTest {

	private static double length(float[] vector) {
		double sum = 0;
		for (float value : vector) {
			sum += value * value;
		}
		return Math.sqrt(sum);
	}

	@Test
	void normalizingScalesTheVectorToLengthOne() {
		float[] normalised = VectorMath.normalize(new float[] { 3, 4 });

		assertThat(normalised).containsExactly(0.6f, 0.8f);
		assertThat(length(normalised)).isCloseTo(1.0, within(1e-6));
	}

	@Test
	void normalizingDoesNotChangeTheDirection() {
		float[] normalised = VectorMath.normalize(new float[] { 30, -40, 0 });

		assertThat(normalised[0] / normalised[1]).isCloseTo(-0.75f, within(1e-6f));
	}

	@Test
	void theOriginalVectorIsNotModifiedAndAZeroVectorStaysZero() {
		float[] original = { 3, 4 };
		VectorMath.normalize(original);

		assertThat(original).containsExactly(3, 4);
		assertThat(VectorMath.normalize(new float[] { 0, 0 })).containsExactly(0, 0);
	}

	@Test
	void formatsAVectorAsPgvectorText() {
		assertThat(VectorMath.toPgVector(new float[] { 0.5f, -0.25f, 1f })).isEqualTo("[0.50000000,-0.25000000,1.00000000]");
	}
}
