package io.github.ale4694.partsflow.ai;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a 429 answer tells us about WHEN to try again, read from the provider's error text, for example
 * "Quota exceeded for metric ...free_tier_requests, limit: 20 ... Please retry in 9h3m1.2s." or a details entry
 * with {@code "retryDelay": "33s"}.
 *
 * @param daily true for a daily quota (it cannot recover within a request, so waiting and retrying is pointless)
 * @param suggestedDelay how long the provider asks us to wait, or null when it did not say
 */
record QuotaAdvice(boolean daily, Duration suggestedDelay) {

	/** A delay this long can only be a daily (or otherwise long-lived) quota, never a per-minute limit. */
	private static final Duration LONG_DELAY = Duration.ofHours(1);

	private static final Pattern RETRY_DELAY_FIELD = Pattern.compile("retryDelay\"?\\s*[:=]\\s*\"?(\\d+(?:\\.\\d+)?)s",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern RETRY_IN = Pattern.compile("retry in ((?:\\d+(?:\\.\\d+)?(?:ms|h|m|s)\\s*)+)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern DURATION_PART = Pattern.compile("(\\d+(?:\\.\\d+)?)(ms|h|m|s)");

	static QuotaAdvice from(String providerMessage) {
		String text = providerMessage == null ? "" : providerMessage;
		Duration delay = suggestedDelay(text);
		String lower = text.toLowerCase(Locale.ROOT);
		boolean daily = lower.contains("perday") || lower.contains("per day") || lower.contains("daily")
				|| (delay != null && delay.compareTo(LONG_DELAY) >= 0);
		return new QuotaAdvice(daily, delay);
	}

	private static Duration suggestedDelay(String text) {
		Matcher field = RETRY_DELAY_FIELD.matcher(text);
		if (field.find()) {
			return seconds(Double.parseDouble(field.group(1)));
		}
		Matcher retryIn = RETRY_IN.matcher(text);
		if (!retryIn.find()) {
			return null;
		}
		double seconds = 0;
		Matcher part = DURATION_PART.matcher(retryIn.group(1).toLowerCase(Locale.ROOT));
		while (part.find()) {
			double amount = Double.parseDouble(part.group(1));
			seconds += switch (part.group(2)) {
				case "h" -> amount * 3600;
				case "m" -> amount * 60;
				case "s" -> amount;
				default -> amount / 1000; // ms
			};
		}
		return seconds(seconds);
	}

	private static Duration seconds(double seconds) {
		return Duration.ofMillis(Math.round(seconds * 1000));
	}

	/** "about 9 hours", "about 12 minutes", "about 33 seconds": for messages meant for people. */
	static String describe(Duration delay) {
		long seconds = Math.max(1, Math.round(delay.toMillis() / 1000.0));
		if (seconds < 90) {
			return seconds + (seconds == 1 ? " second" : " seconds");
		}
		long minutes = Math.round(seconds / 60.0);
		if (minutes < 90) {
			return minutes + " minutes";
		}
		long hours = Math.round(minutes / 60.0);
		return hours + (hours == 1 ? " hour" : " hours");
	}
}
