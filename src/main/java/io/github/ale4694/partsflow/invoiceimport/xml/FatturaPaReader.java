package io.github.ale4694.partsflow.invoiceimport.xml;

import io.github.ale4694.partsflow.invoiceimport.domain.MalformedDocumentException;
import java.io.InputStream;
import javax.xml.stream.XMLInputFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.xml.XmlFactory;
import tools.jackson.dataformat.xml.XmlMapper;

/** Turns FatturaPA XML bytes into {@link FatturaPaXml}. Knows nothing about our domain. */
public class FatturaPaReader {

	private final XmlMapper mapper;

	public FatturaPaReader() {
		// Uploaded files are untrusted: no DTDs and no external entities (XXE protection)
		XMLInputFactory stax = XMLInputFactory.newFactory();
		stax.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		stax.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

		this.mapper = XmlMapper.builder(XmlFactory.builder().xmlInputFactory(stax).build())
				// repeated elements (DettaglioLinee, DatiRiepilogo...) are plain siblings, not wrapped in a parent
				.defaultUseWrapper(false)
				// a FatturaPA file has many elements we do not need
				.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.build();
	}

	public FatturaPaXml read(InputStream xml) {
		try {
			return mapper.readValue(xml, FatturaPaXml.class);
		}
		catch (JacksonException ex) {
			throw new MalformedDocumentException("Il file non è una fattura elettronica FatturaPA leggibile: " + ex.getOriginalMessage(), ex);
		}
	}
}
