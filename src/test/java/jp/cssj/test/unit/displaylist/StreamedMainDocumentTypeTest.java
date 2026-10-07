package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;
import net.zamasoft.zstream.resolver.util.SimpleSourceMetadata;

/**
 * Verify that {@code CTISession.transcode(SourceMetadata)} (the path that writes the body to an output stream)
 * reads the body using the supplied type (2026-10-04, overall review). Previously it passed the output type
 * as the body type and read Markdown as HTML too.
 *
 * <p>
 * This path lays out content on another thread, so inspect text in the output PDF rather than a display-list dump
 * (configured per thread).
 * </p>
 */
public class StreamedMainDocumentTypeTest extends TestCase {
	private static String text(final String mimeType, final String body) throws Exception {
		final ByteArrayOutputStream pdf = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(pdf)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			try (OutputStream out = session
					.transcode(new SimpleSourceMetadata(URI.create("file:///a"), mimeType, "UTF-8", -1L))) {
				out.write(body.getBytes(StandardCharsets.UTF_8));
			}
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(pdf.toByteArray())) {
			return new PDFTextStripper().getText(doc);
		}
	}

	public void testMarkdownIsReadAsMarkdown() throws Exception {
		final String text = text("text/markdown", "plain **bold** text\n");
		assertTrue(text, text.contains("plain bold text"));
		assertFalse(text, text.contains("**"));
	}

	public void testUntypedBodyIsReadAsHtml() throws Exception {
		final String text = text(null, "<p>plain <b>bold</b> text</p>");
		assertTrue(text, text.contains("plain bold text"));
		assertFalse(text, text.contains("<b>"));
	}
}
