package io.github.ale4694.partsflow.ai;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import io.github.ale4694.partsflow.catalog.Supplier;
import io.github.ale4694.partsflow.catalog.SupplierItemCode;
import io.github.ale4694.partsflow.catalog.SupplierItemCodeRepository;
import io.github.ale4694.partsflow.catalog.SupplierRepository;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared setup for AI integration tests: real database, MOCKED LLM gateway.
 * The API key is set explicitly to a fake value so the tests never depend on (or use) the key in the environment.
 * Keeping every annotation here lets all subclasses reuse one Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
		"spring.ai.google.genai.api-key=test-key-not-real",
		"partsflow.ai.api-key=test-key-not-real",
		"partsflow.ai.max-lines-per-match-request=1"
})
public abstract class AiIntegrationTestBase {

	private static final AtomicInteger COUNTER = new AtomicInteger();

	@MockitoBean
	protected LlmGateway gateway;

	@Autowired
	protected MockMvc mvc;
	@Autowired
	protected SupplierRepository suppliers;
	@Autowired
	protected ItemRepository items;
	@Autowired
	protected SupplierItemCodeRepository mappings;

	protected static int next() {
		return COUNTER.incrementAndGet();
	}

	protected Supplier newSupplier(int n) {
		return suppliers.save(new Supplier("Ricambi Rossi Srl", "4%010d".formatted(n)));
	}

	protected Item newItem(String code, String description) {
		return items.save(new Item(code, description, "PZ", BigDecimal.ZERO));
	}

	protected void map(Supplier supplier, Item item, String supplierCode) {
		mappings.save(new SupplierItemCode(supplier, item, supplierCode));
	}
}
