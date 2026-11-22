package jp.cssj.test.unit.security;

import java.io.ByteArrayOutputStream;
import java.net.URI;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.progress.ProgressListener;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * A redirect to a target that may not be fetched is the failure of that one resource.
 *
 * <p>
 * The redirect is only seen when the resource is first read, long after it was resolved. The refusal used to be an
 * unchecked exception thrown from there: it passed every caller that handles a resource it cannot read and ended
 * the whole conversion (a style sheet: "unexpected error" and no output; the main document: an exception out of
 * the session, which servers reported as an authentication failure or by closing the connection).
 * </p>
 */
public class RedirectRefusalTest extends TestCase {
	private static String text(final byte[] pdf) throws Exception {
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(doc).replaceAll("\\s+", " ").trim();
		}
	}

	/** The document is laid out without the style sheet, with the warning for a style sheet that is missing. */
	public void testRefusedStyleSheetRedirectLeavesTheDocumentUnstyled() throws Exception {
		try (ProbeServer front = new ProbeServer(); ProbeServer other = new ProbeServer()) {
			other.put("/b.css", "text/css", ".styled { display: none }");
			front.redirect("/a.css", other.url("/b.css"));
			final ConversionProbe.Result r = new ConversionProbe().include(front.url("/a.css"))
					.convertHtml("<!DOCTYPE html><html><head><link rel='stylesheet' href='" + front.url("/a.css")
							+ "'/></head><body><p>KEEP</p><p class='styled'>UNSTYLED</p></body></html>");
			assertTrue("the permitted style sheet was not requested: " + front.hitPaths(),
					front.hits("/a.css") >= 1);
			assertEquals("the redirect target was fetched: " + other.hitPaths(), 0, other.totalHits());
			final String text = text(r.pdf());
			assertTrue(text, text.contains("KEEP"));
			assertTrue("the refused style sheet was applied: " + text, text.contains("UNSTYLED"));
			assertTrue("no warning for the style sheet: " + r.describe(), r.hasMessage("2803"));
		}
	}

	/** The same style sheet is applied when its redirect target is permitted (the path is live). */
	public void testPermittedStyleSheetRedirectIsFollowed() throws Exception {
		try (ProbeServer front = new ProbeServer(); ProbeServer other = new ProbeServer()) {
			other.put("/b.css", "text/css", ".styled { display: none }");
			front.redirect("/a.css", other.url("/b.css"));
			final ConversionProbe.Result r = new ConversionProbe().include(front.url("/a.css"))
					.include(other.url("/b.css"))
					.convertHtml("<!DOCTYPE html><html><head><link rel='stylesheet' href='" + front.url("/a.css")
							+ "'/></head><body><p>KEEP</p><p class='styled'>UNSTYLED</p></body></html>");
			final String text = text(r.pdf());
			assertTrue(text, text.contains("KEEP"));
			assertFalse("the style sheet was not applied: " + text + " " + r.describe(), text.contains("UNSTYLED"));
		}
	}

	/**
	 * The main document, opened as a server opens it (with a progress listener): the conversion ends with the
	 * message for a document that cannot be fetched, and the message names the refused target.
	 */
	public void testRefusedMainDocumentRedirectIsReportedAsMessage() throws Exception {
		try (ProbeServer front = new ProbeServer()) {
			front.put("/moved.html", "text/html", "<!DOCTYPE html><html><body><p>MOVED</p></body></html>");
			front.redirect("/doc.html", front.url("/moved.html"));
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				// A list that does not name the redirect target. The main document itself needs no entry.
				session.property("input.include.0", front.url("/css/**"));
				session.setProgressListener(new ProgressListener() {
					public void sourceLength(final long length) {
					}

					public void progress(final long read) {
					}
				});
				session.transcode(URI.create(front.url("/doc.html")));
				fail("the conversion was not aborted");
			} catch (final TranscoderException e) {
				assertEquals(MessageCodes.ERROR_UNREACHABLE_SERVERSIDE_DOCUMENT, e.getCode());
				assertEquals(2, e.getArgs().length);
				assertTrue(e.getArgs()[1], e.getArgs()[1].contains("/moved.html"));
			} finally {
				session.close();
			}
			assertTrue("the document was not requested: " + front.hitPaths(), front.hits("/doc.html") >= 1);
			assertEquals("the redirect target was fetched: " + front.hitPaths(), 0, front.hits("/moved.html"));
		}
	}
}
