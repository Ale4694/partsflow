package io.github.ale4694.partsflow.invoiceimport;

import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.ItemChanged;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import io.github.ale4694.partsflow.catalog.Supplier;
import io.github.ale4694.partsflow.catalog.SupplierItemCode;
import io.github.ale4694.partsflow.catalog.SupplierItemCodeRepository;
import io.github.ale4694.partsflow.catalog.SupplierRepository;
import io.github.ale4694.partsflow.common.ConflictException;
import io.github.ale4694.partsflow.common.PageResponse;
import io.github.ale4694.partsflow.common.ResourceNotFoundException;
import io.github.ale4694.partsflow.common.RetryingTransaction;
import io.github.ale4694.partsflow.inventory.InventoryService;
import io.github.ale4694.partsflow.inventory.MovementType;
import io.github.ale4694.partsflow.inventory.StockMovementRequest;
import io.github.ale4694.partsflow.invoiceimport.domain.ArticleCode;
import io.github.ale4694.partsflow.invoiceimport.domain.Document;
import io.github.ale4694.partsflow.invoiceimport.domain.DocumentBody;
import io.github.ale4694.partsflow.invoiceimport.domain.DocumentLine;
import io.github.ale4694.partsflow.invoiceimport.domain.InvalidDocumentException;
import io.github.ale4694.partsflow.invoiceimport.domain.StockEffects;
import io.github.ale4694.partsflow.invoiceimport.draft.DraftDdtReference;
import io.github.ale4694.partsflow.invoiceimport.draft.DraftSource;
import io.github.ale4694.partsflow.invoiceimport.draft.DraftStatus;
import io.github.ale4694.partsflow.invoiceimport.draft.ImportDraft;
import io.github.ale4694.partsflow.invoiceimport.draft.ImportDraftLine;
import io.github.ale4694.partsflow.invoiceimport.draft.ImportDraftRepository;
import io.github.ale4694.partsflow.invoiceimport.draft.LineStatus;
import io.github.ale4694.partsflow.invoiceimport.xml.FatturaPaParser;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The import flow: upload creates a draft, a person reviews it, confirmation writes the stock movements.
 * Methods carry their own @Transactional because {@link #confirm} must NOT run inside one (it manages its own).
 */
@Service
public class ImportService {

	/** The reason and the source document columns are 200 characters long. */
	private static final int MAX_SOURCE_DOCUMENT_LENGTH = 200;
	private static final DateTimeFormatter DOCUMENT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final FatturaPaParser parser;
	private final SupplierRepository suppliers;
	private final SupplierItemCodeRepository mappings;
	private final ItemRepository items;
	private final ImportDraftRepository drafts;
	private final InventoryService inventory;
	private final RetryingTransaction retrying;
	private final Clock clock;
	private final ApplicationEventPublisher events;

	public ImportService(FatturaPaParser parser, SupplierRepository suppliers, SupplierItemCodeRepository mappings,
			ItemRepository items, ImportDraftRepository drafts, InventoryService inventory,
			RetryingTransaction retrying, Clock clock, ApplicationEventPublisher events) {
		this.parser = parser;
		this.suppliers = suppliers;
		this.mappings = mappings;
		this.items = items;
		this.drafts = drafts;
		this.inventory = inventory;
		this.retrying = retrying;
		this.clock = clock;
		this.events = events;
	}

	@Transactional
	public DraftResponse importFatturaPa(InputStream xml) {
		return createDraft(parser.parse(xml), DraftSource.XML);
	}

	/** Builds a draft from an already validated document. Every import path (XML, PDF) ends up here. */
	@Transactional
	public DraftResponse createDraft(Document document, DraftSource source) {
		DocumentBody body = document.body();
		Supplier supplier = suppliers.findByVatNumber(body.supplier().vatNumber())
				.orElseThrow(() -> new InvalidDocumentException("Fornitore sconosciuto con partita IVA "
						+ body.supplier().vatNumber() + " (" + body.supplier().name()
						+ "): crea prima il fornitore nel catalogo"));
		if (drafts.existsBySupplierIdAndDocumentNumberAndDocumentDate(supplier.getId(), body.number(), body.date())) {
			throw new ConflictException("Il documento " + body.number() + " del " + body.date() + " di "
					+ supplier.getName() + " è già stato importato");
		}

		ImportDraft draft = new ImportDraft(supplier, source, body.tipoDocumento(), body.number(), body.date(), body.total(),
				clock.instant());
		body.ddtReferences().forEach(ddt -> draft.addDdtReference(new DraftDdtReference(ddt.number(), ddt.date())));

		Map<String, SupplierItemCode> knownCodes = knownCodes(supplier, body.lines());
		for (DocumentLine line : body.lines()) {
			draft.addLine(toDraftLine(document, line, knownCodes));
		}
		return DraftResponse.from(drafts.save(draft));
	}

	private Map<String, SupplierItemCode> knownCodes(Supplier supplier, List<DocumentLine> lines) {
		List<String> codes = lines.stream().flatMap(l -> l.articleCodes().stream()).map(ArticleCode::value).distinct()
				.toList();
		Map<String, SupplierItemCode> bySupplierCode = new HashMap<>();
		if (!codes.isEmpty()) {
			mappings.findBySupplierIdAndSupplierCodeIn(supplier.getId(), codes)
					.forEach(m -> bySupplierCode.put(m.getSupplierCode(), m));
		}
		return bySupplierCode;
	}

	private ImportDraftLine toDraftLine(Document document, DocumentLine line,
			Map<String, SupplierItemCode> knownCodes) {
		String supplierCode = line.supplierCode();
		if (line.quantity() == null) {
			// Not goods (transport, fees...): nothing to put in stock
			return new ImportDraftLine(line.lineNumber(), supplierCode, line.description(), null, line.unit(),
					line.unitPrice(), line.totalPrice(), line.vatRate(), null, LineStatus.SKIPPED, null);
		}
		BigDecimal stockDelta = StockEffects.stockDelta(document, line);
		// A line can print several codes (EAN, supplier SKU...): the first one we know wins, supplier codes before barcodes
		for (ArticleCode code : line.codesByPreference()) {
			SupplierItemCode mapping = knownCodes.get(code.value());
			if (mapping != null) {
				return new ImportDraftLine(line.lineNumber(), code.value(), line.description(), line.quantity(),
						line.unit(), line.unitPrice(), line.totalPrice(), line.vatRate(), stockDelta,
						LineStatus.MATCHED, mapping.getItem());
			}
		}
		return new ImportDraftLine(line.lineNumber(), supplierCode, line.description(), line.quantity(), line.unit(),
				line.unitPrice(), line.totalPrice(), line.vatRate(), stockDelta, LineStatus.PENDING_REVIEW, null);
	}

	@Transactional(readOnly = true)
	public DraftResponse get(Long id) {
		return DraftResponse.from(drafts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Draft", id)));
	}

	@Transactional(readOnly = true)
	public PageResponse<DraftSummaryResponse> list(DraftStatus status, Pageable pageable) {
		Page<ImportDraft> page = status == null ? drafts.findAllBy(pageable) : drafts.findByStatus(status, pageable);
		return PageResponse.from(page.map(DraftSummaryResponse::from));
	}

	/**
	 * Assigns an item to a pending line. The supplier's code is remembered, so the next invoice that prints
	 * the same code is matched automatically.
	 */
	@Transactional
	public DraftResponse resolveLine(Long draftId, Long lineId, Long itemId) {
		ImportDraft draft = openDraft(draftId);
		ImportDraftLine line = pendingLine(draft, lineId);
		Item item = items.findById(itemId).orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
		line.resolve(item);
		saveMappingIfNew(draft.getSupplier(), line.getSupplierCode(), item);
		return DraftResponse.from(draft);
	}

	/** Marks a pending line as "do not load into stock". */
	@Transactional
	public DraftResponse skipLine(Long draftId, Long lineId) {
		ImportDraft draft = openDraft(draftId);
		pendingLine(draft, lineId).skip();
		return DraftResponse.from(draft);
	}

	/**
	 * Confirms a draft: marks it confirmed and writes the stock movements in ONE transaction.
	 * Two people confirming the same draft at once cannot both win: the draft's version column makes the
	 * second transaction fail, and its retry then sees the draft already confirmed (409).
	 * Not @Transactional: {@link RetryingTransaction} opens the transaction so it can retry the whole thing.
	 */
	public DraftResponse confirm(Long draftId) {
		return retrying.execute(() -> {
			ImportDraft draft = drafts.findForUpdate(draftId)
					.orElseThrow(() -> new ResourceNotFoundException("Draft", draftId));
			draft.confirm(clock.instant());
			inventory.applyAll(draft.getLines().stream()
					.filter(line -> line.getStatus() == LineStatus.MATCHED && line.getStockDelta().signum() != 0)
					.map(line -> toMovement(draft, line))
					.toList());
			return DraftResponse.from(draft);
		});
	}

	@Transactional
	public void delete(Long draftId) {
		ImportDraft draft = drafts.findForUpdate(draftId)
				.orElseThrow(() -> new ResourceNotFoundException("Draft", draftId));
		if (draft.getStatus() == DraftStatus.CONFIRMED) {
			throw new ConflictException("La bozza " + draftId + " è confermata: i suoi movimenti di magazzino sono già stati scritti");
		}
		drafts.delete(draft);
	}

	/**
	 * The movement texts are shown to the (Italian) user in the warehouse history, so they are in Italian:
	 * reason "Carico da fattura FT-0001/2026 di Ricambi Rossi Srl", source "TD01 FT-0001/2026 del 10/03/2026".
	 * Only new movements get these texts; existing rows are not touched.
	 */
	private StockMovementRequest toMovement(ImportDraft draft, ImportDraftLine line) {
		BigDecimal delta = line.getStockDelta();
		MovementType type = delta.signum() > 0 ? MovementType.IN : MovementType.OUT;
		String reason = (type == MovementType.IN ? "Carico da " : "Scarico per ") + documentKind(draft.getTipoDocumento())
				+ " " + draft.getDocumentNumber() + " di " + draft.getSupplier().getName();
		String source = draft.getTipoDocumento() + " " + draft.getDocumentNumber() + " del "
				+ DOCUMENT_DATE.format(draft.getDocumentDate());
		return new StockMovementRequest(line.getItem().getId(), type, delta.abs(), truncate(reason),
				truncate(source));
	}

	/** Italian name of the kind of document (the movement reason is built from it). */
	private static String documentKind(String tipoDocumento) {
		return switch (tipoDocumento) {
			case "TD01", "TD24" -> "fattura";
			case "TD04" -> "nota di credito";
			case "DDT" -> "DDT";
			default -> "documento";
		};
	}

	private static String truncate(String text) {
		return text.length() <= MAX_SOURCE_DOCUMENT_LENGTH ? text : text.substring(0, MAX_SOURCE_DOCUMENT_LENGTH);
	}

	private ImportDraft openDraft(Long draftId) {
		ImportDraft draft = drafts.findForUpdate(draftId)
				.orElseThrow(() -> new ResourceNotFoundException("Draft", draftId));
		draft.requireOpen();
		return draft;
	}

	private ImportDraftLine pendingLine(ImportDraft draft, Long lineId) {
		ImportDraftLine line = draft.getLine(lineId);
		if (line.getStatus() != LineStatus.PENDING_REVIEW) {
			throw new ConflictException("La riga " + lineId + " non è da verificare (stato " + line.getStatus() + ")");
		}
		return line;
	}

	private void saveMappingIfNew(Supplier supplier, String supplierCode, Item item) {
		if (supplierCode != null && !mappings.existsBySupplierIdAndSupplierCode(supplier.getId(), supplierCode)) {
			mappings.save(new SupplierItemCode(supplier, item, supplierCode));
			// the supplier codes are part of the text the semantic search reads about an item
			events.publishEvent(ItemChanged.of(item.getId()));
		}
	}
}
