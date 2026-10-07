package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.css.html.HTMLStyle;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Pin down <b>candidate selection for {@code srcset}/{@code <picture>}</b>
 * (2026-08-20).
 */
public class SrcsetPictureTest extends TestCase {
	/** srcset selects the highest-resolution candidate (suitable for printing). */
	public void testPickFromSrcset() {
		assertEquals("b.png", HTMLStyle.pickFromSrcset("a.png 1x, b.png 2x"));
		assertEquals("b.png", HTMLStyle.pickFromSrcset("b.png 2x, a.png 1x"));
		assertEquals("wide.png", HTMLStyle.pickFromSrcset("small.png 320w, wide.png 1280w"));
		assertEquals("only.png", HTMLStyle.pickFromSrcset("only.png"));
		assertEquals("a.png", HTMLStyle.pickFromSrcset("a.png"));
		assertNull(HTMLStyle.pickFromSrcset(null));
		assertNull(HTMLStyle.pickFromSrcset(""));
	}

	/** Type filter: accept only readable formats; skip avif and similar formats. */
	public void testSupportedImageType() {
		assertTrue(HTMLStyle.isSupportedImageType(null));
		assertTrue(HTMLStyle.isSupportedImageType("image/webp"));
		assertTrue(HTMLStyle.isSupportedImageType("image/png"));
		assertFalse(HTMLStyle.isSupportedImageType("image/avif"));
		assertFalse(HTMLStyle.isSupportedImageType("image/jxl"));
	}

	/**
	 * Verify that {@code <source>} (a void element) does not swallow subsequent content
	 * in compatibility mode (no DOCTYPE = legacy.xml). html4.xml was corrected on 2026-07-18,
	 * but compatibility mode still had the defect
	 * (corrected on 2026-08-20; observed while verifying srcset/picture support).
	 */
	public void testPictureDoesNotSwallowFollowingContent() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session, new File("files/unittest/3080-MODERN-CSS/picture-swallow.html"),
					"text/html", null);
		} finally {
			session.close();
		}
		final List<String> texts = new ArrayList<>();
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			texts.add(new PDFTextStripper().getText(doc));
		}
		final String all = String.join(" ", texts);
		assertTrue("sourceの後の内容が失われています: " + all, all.contains("AFTERSOURCE"));
		assertTrue("pictureの後の内容が失われています: " + all, all.contains("AFTERPICTURE"));
	}
}
