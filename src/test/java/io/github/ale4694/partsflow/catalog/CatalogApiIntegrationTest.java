package io.github.ale4694.partsflow.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Runs the whole catalog API against a real PostgreSQL, so the migration and the entities are checked together. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogApiIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Test
	void supplierItemAndMappingLifecycle() throws Exception {
		long supplierId = createAndReturnId("/api/suppliers", """
				{"name": "Autoricambi Bianchi Spa", "vatNumber": "10000000001"}""");
		long itemId = createAndReturnId("/api/items", """
				{"code": "IT-OIL-5W30", "description": "Engine oil 5W-30, 5 litre can", "unit": "PZ", "reorderThreshold": 10}""");

		String mappingUrl = "/api/suppliers/" + supplierId + "/item-codes";
		long mappingId = createAndReturnId(mappingUrl, """
				{"supplierCode": "AB-OIL-530", "itemId": %d}""".formatted(itemId));

		// same supplier code twice -> 409
		mvc.perform(post(mappingUrl).contentType(MediaType.APPLICATION_JSON).content("""
				{"supplierCode": "AB-OIL-530", "itemId": %d}""".formatted(itemId)))
				.andExpect(status().isConflict());

		mvc.perform(get(mappingUrl))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].itemCode").value("IT-OIL-5W30"));

		// supplier and item are still referenced -> 409
		mvc.perform(delete("/api/suppliers/" + supplierId)).andExpect(status().isConflict());
		mvc.perform(delete("/api/items/" + itemId)).andExpect(status().isConflict());

		mvc.perform(delete(mappingUrl + "/" + mappingId)).andExpect(status().isNoContent());
		mvc.perform(delete("/api/suppliers/" + supplierId)).andExpect(status().isNoContent());
		mvc.perform(delete("/api/items/" + itemId)).andExpect(status().isNoContent());
		mvc.perform(get("/api/suppliers/" + supplierId)).andExpect(status().isNotFound());
	}

	@Test
	void duplicateVatNumberAndItemCodeAreRejected() throws Exception {
		String supplier = """
				{"name": "Ricambi Verdi Snc", "vatNumber": "10000000002"}""";
		String item = """
				{"code": "IT-FLT-001", "description": "Oil filter", "unit": "PZ", "reorderThreshold": 3.5}""";
		createAndReturnId("/api/suppliers", supplier);
		createAndReturnId("/api/items", item);

		mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON).content(supplier))
				.andExpect(status().isConflict());
		mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content(item))
				.andExpect(status().isConflict());
	}

	@Test
	void pagination() throws Exception {
		for (int i = 0; i < 3; i++) {
			createAndReturnId("/api/items", """
					{"code": "PAGE-%d", "description": "Pagination item", "unit": "PZ", "reorderThreshold": 0}""".formatted(i));
		}

		mvc.perform(get("/api/items?size=2&page=0&sort=code"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.size").value(2));
	}

	@Test
	void itemsCanBeSearchedByCodeOrDescription() throws Exception {
		createAndReturnId("/api/items", """
				{"code": "SRCH-ALPHA", "description": "Timing belt kit", "unit": "PZ", "reorderThreshold": 0}""");
		createAndReturnId("/api/items", """
				{"code": "SRCH-BETA", "description": "Wiper blade 600mm", "unit": "PZ", "reorderThreshold": 0}""");

		mvc.perform(get("/api/items?q=alpha"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].code").value("SRCH-ALPHA"));
		mvc.perform(get("/api/items?q=WIPER"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].code").value("SRCH-BETA"));
		mvc.perform(get("/api/items?q=nothing-like-this"))
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	private long createAndReturnId(String url, String body) throws Exception {
		MvcResult result = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn();
		String location = result.getResponse().getHeader("Location");
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}
}
