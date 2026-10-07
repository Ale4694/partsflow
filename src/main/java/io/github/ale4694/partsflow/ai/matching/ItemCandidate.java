package io.github.ale4694.partsflow.ai.matching;

/** A catalog item that looks similar to an invoice line (spring-data projection of the pg_trgm query). */
public interface ItemCandidate {

	Long getId();

	String getCode();

	String getDescription();

	String getUnit();

	/** pg_trgm similarity between 0 (nothing in common) and 1 (identical). */
	Float getScore();
}
