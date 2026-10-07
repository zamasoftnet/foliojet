package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.ua.impl.svg.MyGVTGlyphVector;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verify that <b>{@code <text>} in SVG documents is painted</b> (2026-09-14).
 *
 * <p>
 * SVG text is painted as glyph paths through Batik's GlyphLayout → {@link MyGVTGlyphVector}.
 * Previously, methods called by GlyphLayout, such as {@code getGlyphTransform}, threw
 * {@code UnsupportedOperationException}, making all SVG document {@code <text>} conversions fail.
 * Even after that fix, {@code getOutline()} applied scaling and pen advances twice.
 * Display-list goldens do not capture SVG internals, so render images and check for glyph ink.
 * </p>
 */
public class SvgTextPathTest extends TestCase {
	/** Glyph ink exists in three horizontal lines (Latin, bold Japanese, synthetic italic) and one vertical line, but not blank areas. */
	public void testTextLinesAreDrawn() throws Exception {
		final java.awt.image.BufferedImage img = render("files/unittest/0480-svg-text/inline-svg-text.html");
		// @page 260x180pt margin 10pt. SVG coordinates use px (= 0.75 pt), so x=10,y=30 becomes (17.5, 32.5) pt.
		assertTrue("横 1 行目(欧文 16px, baseline 32.5pt)に字面がありません", hasInk(img, 16, 20, 76, 16));
		assertTrue("横 2 行目(太字和文 14px, baseline 55pt)に字面がありません", hasInk(img, 16, 43, 86, 15));
		assertTrue("横 3 行目(合成斜体 12px, baseline 77.5pt)に字面がありません", hasInk(img, 16, 66, 44, 14));
		assertTrue("縦 1 行(x=215px → 171pt, 14px)に字面がありません", hasInk(img, 158, 14, 14, 68));
		// The vertical line has 6 characters × 10.5 pt = 63 pt, ending at y≈80 pt. Double transforms doubled advances and overflowed below.
		assertFalse("縦行が下へ伸びすぎています(字送りの二重掛け)", hasInk(img, 158, 92, 14, 60));
		assertFalse("何も無いはずの領域に字面があります", hasInk(img, 100, 100, 50, 60));
	}

	private static java.awt.image.BufferedImage render(final String file) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			// Enable the fixture's PI (output.pdf.fonts.policy=embedded). Test conf ignores PIs by default;
			// without this, built-in CID-keyed fonts (not embedded) are used, and PDFBox rendering depends
			// on environment substitute fonts (WSL lacked a bold Gothic substitute, leaving line 2 blank; 2026-09-15).
			session.property("input.property-pi", "true");
			CTISessionHelper.transcodeFile(session, new File(file), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			return new PDFRenderer(doc).renderImageWithDPI(0, 72);
		}
	}

	private static boolean hasInk(final java.awt.image.BufferedImage img, final int x0, final int y0, final int w,
			final int h) {
		for (int y = y0; y < y0 + h; ++y) {
			for (int x = x0; x < x0 + w; ++x) {
				final int rgb = img.getRGB(x, y);
				if (((rgb >> 16) & 0xFF) < 200 && ((rgb >> 8) & 0xFF) < 200 && (rgb & 0xFF) < 200) {
					return true;
				}
			}
		}
		return false;
	}
}
