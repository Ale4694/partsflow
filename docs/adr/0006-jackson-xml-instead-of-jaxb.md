# 0006. Jackson XML instead of JAXB, with separate mapping classes

## Context

FatturaPA is a large XML standard (schema v1.2.x, hundreds of elements). We only need a small part of it: supplier VAT number and name, document type, number, date, total, the lines (`DettaglioLinee` with `CodiceArticolo`, description, quantity, prices, VAT rate), `DatiRiepilogo` and `DatiDDT`.

Two common ways to read XML in Java:

- **JAXB**: generate Java classes from the official XSD, then unmarshal.
- **Jackson XML** (`jackson-dataformat-xml`): write only the classes you need and let Jackson map them, the same way it maps JSON.

## Decision

Jackson XML, with the XML shape kept **separate from the domain model**:

- `FatturaPaXml` is a small set of records that mirrors only the elements we use, with the official FatturaPA element names. Every value is a `String`. Unknown elements are ignored, so the large rest of the standard does not matter. Repeated elements (`DettaglioLinee`, `DatiRiepilogo`) are read as plain lists (`defaultUseWrapper(false)`).
- `FatturaPaMapper` converts that into the domain model (`Invoice`, `CreditNote`: a sealed `Document` interface around a `DocumentBody`), parsing numbers and dates and applying business rules. Structural problems are `MalformedDocumentException` (400); rule violations are `InvalidDocumentException` (422).
- The domain model knows nothing about XML, so the PDF import (ADR 0008) produces the very same objects and reuses all validation and the draft code.
- Namespaces are not an issue in practice: files with a `p:` prefix and files with a default namespace both parse (both are covered by fixtures).
- The StAX parser is configured without DTD support and without external entities, because uploaded files are untrusted (XXE protection). A test feeds it an XXE payload.
- Jackson 3 is what Spring Boot 4 ships. One side effect worth knowing: with `jackson-dataformat-xml` on the classpath, Spring MVC can also answer in XML if a client asks for it.

## Alternatives considered

- **JAXB with generated classes.** Complete and typed, but the generated model is huge for the 10% we use, it couples the whole application to the XML structure, and the tooling is heavier. JAXB also left the JDK, so it is an extra dependency anyway.
- **Parse by hand with DOM/XPath.** Works, but is easy to get subtly wrong and produces a lot of repetitive code.
- **Validate against the official XSD.** Would give stricter format checking; not done to keep the importer small and because the business checks (totals, supported types) matter more here. It could be added in front of the parser.

## Consequences

- The parser is short, readable and easy to extend: a new field is a new record component and a line in the mapper.
- The code accepts some files that are not strictly schema-valid. That is intentional leniency; the checks we care about are done explicitly.
- Anything not mapped (discounts, shipping data, payment terms) is ignored, not lost track of: it is listed in the README limitations.
