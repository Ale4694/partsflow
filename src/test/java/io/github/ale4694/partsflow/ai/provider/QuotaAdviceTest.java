package io.github.ale4694.partsflow.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class QuotaAdviceTest {

	private static final String DAILY_FREE_TIER = "Quota exceeded for metric: "
			+ "generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 20, model: gemini-3.5-flash\n"
			+ "Please retry in 9h3m1.2s.";

	@Test
	void aWaitOfHoursIsADailyQuotaWithTheSuggestedDelay() {
		QuotaAdvice advice = QuotaAdvice.from(DAILY_FREE_TIER);

		assertThat(advice.daily()).isTrue();
		assertThat(advice.suggestedDelay()).isEqualTo(Duration.ofHours(9).plusMinutes(3).plusMillis(1200));
	}

	@Test
	void aQuotaIdForADayIsDailyEvenWithoutADelay() {
		QuotaAdvice advice = QuotaAdvice.from("Quota exceeded {\"quotaId\":\"GenerateRequestsPerDayPerProjectPerModel-FreeTier\"}");

		assertThat(advice.daily()).isTrue();
		assertThat(advice.suggestedDelay()).isNull();
	}

	@Test
	void aShortRetryDelayFieldIsAPerMinuteLimit() {
		QuotaAdvice advice = QuotaAdvice.from("Quota exceeded. Details: {\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\","
				+ "\"retryDelay\":\"33s\"}");

		assertThat(advice.daily()).isFalse();
		assertThat(advice.suggestedDelay()).isEqualTo(Duration.ofSeconds(33));
	}

	@Test
	void readsMinutesSecondsAndMilliseconds() {
		assertThat(QuotaAdvice.from("Please retry in 1m30s.").suggestedDelay()).isEqualTo(Duration.ofSeconds(90));
		assertThat(QuotaAdvice.from("Please retry in 850ms").suggestedDelay()).isEqualTo(Duration.ofMillis(850));
		assertThat(QuotaAdvice.from("Please retry in 12.5s.").suggestedDelay()).isEqualTo(Duration.ofMillis(12500));
	}

	@Test
	void noInformationMeansNeitherDailyNorADelay() {
		for (String message : new String[] { "You exceeded your current quota", "", null }) {
			QuotaAdvice advice = QuotaAdvice.from(message);

			assertThat(advice.daily()).isFalse();
			assertThat(advice.suggestedDelay()).isNull();
		}
	}

	@Test
	void describesADelayForPeople() {
		assertThat(QuotaAdvice.describe(Duration.ofSeconds(1))).isEqualTo("1 second");
		assertThat(QuotaAdvice.describe(Duration.ofSeconds(33))).isEqualTo("33 seconds");
		assertThat(QuotaAdvice.describe(Duration.ofMinutes(12))).isEqualTo("12 minutes");
		assertThat(QuotaAdvice.describe(Duration.ofHours(9).plusMinutes(3))).isEqualTo("9 hours");
		assertThat(QuotaAdvice.describe(Duration.ofHours(1))).isEqualTo("60 minutes");
	}
}
