package io.github.ale4694.partsflow.ai.eval;

import java.util.List;
import java.util.Set;

/**
 * The queries of the retrieval eval, written the way a person would type them, each with the item(s) of the demo
 * catalog (src/main/resources/demo/catalog.json) that a good search should return. Several queries use words that
 * do not appear in the catalog (synonyms: "ferodi", "accumulatore", "gomme da neve"), a few are plain spelling
 * matches (to check that the hybrid search does not lose what text search finds).
 */
final class RetrievalEvalQueries {

	/** @param acceptable any of these item codes counts as a hit */
	record Query(String text, Set<String> acceptable) {
	}

	private RetrievalEvalQueries() {
	}

	static List<Query> all() {
		return List.of(
				new Query("filtro olio Fiat Panda", Set.of("FLT-001")),
				new Query("cinghia distribuzione 1.6 diesel con pompa", Set.of("MOT-001")),
				new Query("batteria auto con start&stop", Set.of("ELE-002")),
				new Query("pneumatici da neve 195/65 R15", Set.of("CAR-007")),
				new Query("liquido per radiatore", Set.of("LUB-007", "LUB-008")),
				new Query("pastiglie freni Panda", Set.of("FRE-001")),
				new Query("ferodi dietro", Set.of("FRE-002", "FRE-006")),
				new Query("spazzole tergi 60 cm", Set.of("ELE-011")),
				new Query("lampada faro H7", Set.of("ELE-003", "ELE-006")),
				new Query("olio cambio automatico", Set.of("LUB-005")),
				new Query("accumulatore 12 volt", Set.of("ELE-001", "ELE-002")),
				new Query("marmitta", Set.of("SCA-001", "SCA-006")),
				new Query("gas aria condizionata", Set.of("CLI-003")),
				new Query("kit frizione", Set.of("TRA-001")),
				new Query("candele accensione", Set.of("MOT-003")),
				new Query("ammortizzatori anteriori", Set.of("SOS-001")),
				new Query("cuscinetto ruota", Set.of("SOS-008")),
				new Query("filtro abitacolo carboni attivi", Set.of("FLT-005")),
				new Query("gomme estive 205 55 16", Set.of("CAR-008")),
				new Query("sensore lambda", Set.of("MOT-013")));
	}
}
