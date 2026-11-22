package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * The names under which {@code output.pdf.attachments.N} are attached.
 *
 * <p>
 * An attachment without a name is named by the last segment of its URI's path; a {@code data:} URI has no path,
 * which stopped the conversion with a NullPointerException. And the attachments were keyed by their description
 * in the PDF, so of two attachments with the same description only the last could be reached.
 * </p>
 */
public class PdfAttachmentNamesTest extends TestCase {
	private static final String HTML = "<!doctype html><p>KEEP ME</p>";

	/** A {@code data:} URI without a name: the document is finished and the file is attached under a made-up name. */
	public void testOpaqueUriWithoutNameGetsAFallbackName() throws Exception {
		final byte[] pdf = convert(props("output.pdf.attachments.0.uri", "data:text/plain,hello"));
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertTrue(new PDFTextStripper().getText(doc).contains("KEEP ME"));
			final Map<String, PDComplexFileSpecification> files = attachments(doc);
			assertEquals(files.keySet().toString(), 1, files.size());
			final PDComplexFileSpecification file = files.values().iterator().next();
			assertEquals("attachment-1", file.getFile());
			assertEquals("hello", content(file));
		}
	}

	/** The name given is used as it is, also for a {@code data:} URI. */
	public void testOpaqueUriWithName() throws Exception {
		final byte[] pdf = convert(props("output.pdf.attachments.0.uri", "data:text/plain,hello",
				"output.pdf.attachments.0.name", "hello.txt"));
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			final Map<String, PDComplexFileSpecification> files = attachments(doc);
			assertEquals(files.keySet().toString(), 1, files.size());
			assertEquals("hello.txt", files.values().iterator().next().getFile());
		}
	}

	/** Two attachments with the same description are two files. */
	public void testSameDescriptionKeepsBothFiles() throws Exception {
		final byte[] pdf = convert(props("output.pdf.attachments.0.uri", "data:text/plain,FIRST",
				"output.pdf.attachments.0.name", "a.txt", "output.pdf.attachments.0.description", "Supporting",
				"output.pdf.attachments.1.uri", "data:text/plain,SECOND", "output.pdf.attachments.1.name", "b.txt",
				"output.pdf.attachments.1.description", "Supporting"));
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			final Map<String, PDComplexFileSpecification> files = attachments(doc);
			assertEquals(files.keySet().toString(), 2, files.size());
			final Map<String, String> contents = new LinkedHashMap<>();
			for (final PDComplexFileSpecification file : files.values()) {
				contents.put(file.getFile(), content(file));
				assertEquals("Supporting", file.getFileDescription());
			}
			assertEquals("FIRST", contents.get("a.txt"));
			assertEquals("SECOND", contents.get("b.txt"));
		}
	}

	/** Two attachments without names and with the same description: both are kept, each under its own name. */
	public void testUnnamedAttachmentsWithSameDescription() throws Exception {
		final byte[] pdf = convert(props("output.pdf.attachments.0.uri", "data:text/plain,FIRST",
				"output.pdf.attachments.0.description", "Supporting", "output.pdf.attachments.1.uri",
				"data:text/plain,SECOND", "output.pdf.attachments.1.description", "Supporting"));
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			final Map<String, PDComplexFileSpecification> files = attachments(doc);
			assertEquals(files.keySet().toString(), 2, files.size());
			final Map<String, String> contents = new LinkedHashMap<>();
			for (final PDComplexFileSpecification file : files.values()) {
				contents.put(file.getFile(), content(file));
			}
			assertEquals("FIRST", contents.get("attachment-1"));
			assertEquals("SECOND", contents.get("attachment-2"));
		}
	}

	private static Map<String, PDComplexFileSpecification> attachments(final PDDocument doc) throws Exception {
		assertNotNull("the PDF has no name dictionary", doc.getDocumentCatalog().getNames());
		assertNotNull("the PDF has no attachments", doc.getDocumentCatalog().getNames().getEmbeddedFiles());
		final Map<String, PDComplexFileSpecification> files = doc.getDocumentCatalog().getNames().getEmbeddedFiles()
				.getNames();
		assertNotNull("the attachment name tree is empty", files);
		return files;
	}

	private static String content(final PDComplexFileSpecification file) throws Exception {
		assertNotNull("the attachment has no data", file.getEmbeddedFile());
		return new String(file.getEmbeddedFile().toByteArray(), StandardCharsets.UTF_8);
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private static byte[] convert(final Map<String, String> properties) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/pdf");
			for (final Map.Entry<String, String> e : properties.entrySet()) {
				session.property(e.getKey(), e.getValue());
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///attachments.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return out.toByteArray();
	}
}
