package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Pin down {@code tspan} inside inline SVG {@code text}
 * (2026-10-04, item ③ of TECH-20261003-004; found in a figure in the Jigen Ango book).
 *
 * <ul>
 * <li>The first and last tspan's fonts (italic, bold, size) were swapped: the painter
 * ({@code MyTextPainter}) read run attributes without resetting the ACI to its start.</li>
 * <li>A tspan's dy took effect one tspan late, and baseline-shift had no effect: the painter
 * painted from the position before applying dx, dy, and baseline-shift
 * ({@code TextSpanLayout.getOffset()}).</li>
 * </ul>
 *
 * <p>
 * SVG width 400 with viewBox 0 0 400 300 is painted at 300 pt (0.75 pt per unit).
 * Inspect positions using text positions in the PDF.
 * </p>
 */
public class SvgTextTspanTest extends TestCase {
	private static String document(final String text) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<?jp.cssj.property name="output.page-width" value="400pt"?>
				<?jp.cssj.property name="output.page-height" value="300pt"?>
				<style>@page{margin:0} body{margin:0}</style></head><body>
				<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300" viewBox="0 0 400 300"
				 font-family="serif" font-size="20">%s</svg>
				</body></html>
				""".formatted(text);
	}

	/** Each of the three tspan sizes matches its declaration (the first does not take the last one's size). */
	public void testFirstTspanKeepsItsOwnFont() throws Exception {
		final List<TextPosition> chars = convert(document("<text x=\"10\" y=\"60\">"
				+ "<tspan font-size=\"10\">A</tspan><tspan font-size=\"20\">B</tspan><tspan font-size=\"40\">C</tspan>"
				+ "</text>"));
		// PDFBox sizes exclude the SVG scale factor (0.75), so compare ratios.
		final double a = find(chars, "A").getFontSizeInPt(), b = find(chars, "B").getFontSizeInPt(),
				c = find(chars, "C").getFontSizeInPt();
		assertEquals("B は A の 2 倍(A=" + a + ", B=" + b + ")", 2.0, b / a, 0.01);
		assertEquals("C は A の 4 倍(A=" + a + ", C=" + c + ")", 4.0, c / a, 0.01);
	}

	/** dy takes effect from that tspan (does not shift to the next tspan). */
	public void testDyAppliesToItsOwnTspan() throws Exception {
		final List<TextPosition> chars = convert(document(
				"<text x=\"10\" y=\"100\">A<tspan dy=\"-10\">B</tspan><tspan dy=\"20\">C</tspan>D</text>"));
		final double a = find(chars, "A").getYDirAdj();
		assertEquals("B は A より 10 単位(7.5pt)上", a - 7.5, find(chars, "B").getYDirAdj(), 0.2);
		assertEquals("C は A より 10 単位(7.5pt)下", a + 7.5, find(chars, "C").getYDirAdj(), 0.2);
		assertEquals("D は C と同じ高さ", find(chars, "C").getYDirAdj(), find(chars, "D").getYDirAdj(), 0.2);
	}

	/** baseline-shift super moves up; sub moves down. */
	public void testBaselineShift() throws Exception {
		final List<TextPosition> chars = convert(document("<text x=\"10\" y=\"100\">A"
				+ "<tspan baseline-shift=\"super\">B</tspan>C<tspan baseline-shift=\"sub\">D</tspan>E</text>"));
		final double a = find(chars, "A").getYDirAdj();
		assertTrue("super が上へずれていない", find(chars, "B").getYDirAdj() < a - 2);
		assertEquals("C は元の基準線", a, find(chars, "C").getYDirAdj(), 0.2);
		assertTrue("sub が下へずれていない", find(chars, "D").getYDirAdj() > a + 2);
		assertEquals("E は元の基準線", a, find(chars, "E").getYDirAdj(), 0.2);
	}

	private static TextPosition find(final List<TextPosition> chars, final String c) {
		for (final TextPosition t : chars) {
			if (c.equals(t.getUnicode())) {
				return t;
			}
		}
		throw new AssertionError(c + " が PDF に無い");
	}

	private static List<TextPosition> convert(final String html) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("input.property-pi", "true");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///svg-text-tspan.html"), "text/html", null);
		} finally {
			session.close();
		}
		final List<TextPosition> all = new ArrayList<>();
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final PDFTextStripper stripper = new PDFTextStripper() {
				@Override
				protected void processTextPosition(final TextPosition text) {
					all.add(text);
					super.processTextPosition(text);
				}
			};
			stripper.setSuppressDuplicateOverlappingText(false);
			stripper.getText(doc);
		}
		assertFalse("テキストが出力されていません", all.isEmpty());
		return all;
	}
}
