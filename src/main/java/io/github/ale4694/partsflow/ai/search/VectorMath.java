package io.github.ale4694.partsflow.ai.search;

import java.util.Locale;

/**
 * The little maths and formatting the search needs.
 * <p>
 * <b>Why vectors are normalised and compared by cosine distance.</b> gemini-embedding-001 produces 3072 numbers,
 * and only that full-size vector comes pre-normalised (length 1). We ask for 768 numbers (a documented, cheaper
 * truncation), and those are NOT normalised. Comparing un-normalised vectors with the inner product would let the
 * vector's length decide the ranking. So two things are done, either of which is enough on its own:
 * <ol>
 *   <li>every vector is scaled to length 1 before it is stored or used ({@link #normalize});</li>
 *   <li>PostgreSQL compares them with the COSINE distance operator {@code <=>}, which looks only at the angle,
 *       and the index uses {@code vector_cosine_ops}.</li>
 * </ol>
 * For vectors of length 1, cosine similarity (1 - distance) is exactly what the inner product would give.
 */
final class VectorMath {

	private VectorMath() {
	}

	/** A copy of the vector scaled to length 1 (a vector of zeros stays zeros). */
	static float[] normalize(float[] vector) {
		double sumOfSquares = 0;
		for (float value : vector) {
			sumOfSquares += (double) value * value;
		}
		float[] result = new float[vector.length];
		if (sumOfSquares == 0) {
			return result;
		}
		double length = Math.sqrt(sumOfSquares);
		for (int i = 0; i < vector.length; i++) {
			result[i] = (float) (vector[i] / length);
		}
		return result;
	}

	/** pgvector's text form of a vector, e.g. {@code [0.1,0.25,-0.3]}; used as {@code CAST(? AS vector)}. */
	static String toPgVector(float[] vector) {
		StringBuilder text = new StringBuilder(vector.length * 10).append('[');
		for (int i = 0; i < vector.length; i++) {
			if (i > 0) {
				text.append(',');
			}
			text.append(String.format(Locale.ROOT, "%.8f", vector[i]));
		}
		return text.append(']').toString();
	}
}
