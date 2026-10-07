package io.github.ale4694.partsflow.ai.provider;

/** Small helper shared by the translators. */
final class ProviderErrors {

	private ProviderErrors() {
	}

	/** The first exception of the given type in the cause chain (the provider's exception is often wrapped). */
	static <E extends Throwable> E find(Throwable error, Class<E> type) {
		for (Throwable current = error; current != null; current = current.getCause()) {
			if (type.isInstance(current)) {
				return type.cast(current);
			}
		}
		return null;
	}
}
