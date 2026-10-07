package io.github.ale4694.partsflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InventoryIntegrationTest {

	private static final AtomicInteger CODE_COUNTER = new AtomicInteger();

	@Autowired
	MockMvc mvc;
	@Autowired
	ItemRepository items;
	@Autowired
	StockMovementRepository movements;

	private Item newItem(String threshold) {
		String code = "INV-TEST-" + CODE_COUNTER.incrementAndGet();
		return items.save(new Item(code, "Inventory test item", "PZ", new BigDecimal(threshold)));
	}

	private void move(Item item, String type, String quantity, int expectedStatus) throws Exception {
		mvc.perform(post("/api/inventory/movements").contentType(MediaType.APPLICATION_JSON).content("""
				{"itemId": %d, "type": "%s", "quantity": %s, "reason": "integration test", "sourceDocument": "test-doc"}"""
				.formatted(item.getId(), type, quantity)))
				.andExpect(status().is(expectedStatus));
	}

	@Test
	void stockFollowsMovementsAndNeverGoesNegative() throws Exception {
		Item item = newItem("5");

		mvc.perform(get("/api/inventory/stock/" + item.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quantity").value(0));

		move(item, "IN", "10", 201);
		move(item, "OUT", "3.5", 201);
		move(item, "OUT", "6.6", 409); // only 6.5 left

		mvc.perform(get("/api/inventory/stock/" + item.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quantity").value(6.5))
				.andExpect(jsonPath("$.itemCode").value(item.getCode()));

		// the rejected OUT left no trace; history is newest first
		mvc.perform(get("/api/inventory/movements?itemId=" + item.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].type").value("OUT"))
				.andExpect(jsonPath("$.content[0].sourceDocument").value("test-doc"))
				.andExpect(jsonPath("$.content[0].itemCode").value(item.getCode()));
	}

	@Test
	void unknownItemReturns404() throws Exception {
		mvc.perform(post("/api/inventory/movements").contentType(MediaType.APPLICATION_JSON).content("""
				{"itemId": 999999, "type": "IN", "quantity": 1, "reason": "x"}"""))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/inventory/stock/999999")).andExpect(status().isNotFound());
	}

	@Test
	void lowStockListsItemsBelowTheirThreshold() throws Exception {
		Item low = newItem("5");
		Item enough = newItem("5");
		Item noThreshold = newItem("0");
		move(low, "IN", "4", 201);
		move(enough, "IN", "5", 201); // exactly at the threshold is not "below"
		move(noThreshold, "IN", "1", 201);

		String body = mvc.perform(get("/api/inventory/low-stock?size=100"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(body).contains(low.getCode()).doesNotContain(enough.getCode()).doesNotContain(noThreshold.getCode());
	}

	@Test
	void itemWithoutAnyMovementCountsAsZeroStock() throws Exception {
		Item untouched = newItem("2");

		String body = mvc.perform(get("/api/inventory/low-stock?size=100"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(body).contains(untouched.getCode());
	}

	@Test
	void stockOverviewListsEveryItemWithItsCurrentStock() throws Exception {
		Item withStock = newItem("5");
		Item untouched = newItem("2");
		move(withStock, "IN", "7.5", 201);

		String body = mvc.perform(get("/api/inventory/stock?size=100"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(body).contains("\"itemCode\":\"" + withStock.getCode() + "\"")
				.contains("\"itemCode\":\"" + untouched.getCode() + "\"");
		mvc.perform(get("/api/inventory/stock?size=100"))
				.andExpect(jsonPath("$.content[?(@.itemCode == '" + withStock.getCode() + "')].quantity")
						.value(Matchers.contains(7.5)))
				.andExpect(jsonPath("$.content[?(@.itemCode == '" + untouched.getCode() + "')].quantity")
						.value(Matchers.contains(0)));
	}

	@Test
	void invalidSortPropertyReturns400() throws Exception {
		mvc.perform(get("/api/inventory/movements?sort=nope")).andExpect(status().isBadRequest());
	}
}
