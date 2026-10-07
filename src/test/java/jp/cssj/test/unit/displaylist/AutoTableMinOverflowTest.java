package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
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
 * Verifies the <b>min-content guarantee for auto tables</b> (2026-08-20).
 *
 * <p>
 * When the sum of column min-content widths exceeds the available width, preserve column minima
 * and let the whole table overflow along the line axis, rather than collapsing columns into overlap
 * (CSS 2.2 17.5.2.2, as in Chrome). Also checks that column widths match in every fragment of a table
 * split across multiple pages (resolve runs once per table, and fragments share finalized column widths).
 * This is the main approach after withdrawing the threshold method (MIN_OVERFLOW_TOLERANCE)
 * of 2026-08-18.
 * </p>
 */
public class AutoTableMinOverflowTest extends TestCase {
	public void testMinPreservedAndConsistentAcrossPages() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session, new File("files/unittest/0240-table/auto-min-overflow.html"),
					"text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			assertTrue("複数ページに分割されていません: " + doc.getNumberOfPages(), doc.getNumberOfPages() >= 2);
			// Measure the starting x of column 2 on each page (the 'S' of the SECONDCOLUMN prefix).
			final List<Double> col2Starts = new ArrayList<>();
			for (int p = 1; p <= doc.getNumberOfPages(); ++p) {
				final List<TextPosition> all = new ArrayList<>();
				final PDFTextStripper stripper = new PDFTextStripper() {
					@Override
					protected void processTextPosition(final TextPosition text) {
						all.add(text);
					}
				};
				stripper.setStartPage(p);
				stripper.setEndPage(p);
				stripper.setSuppressDuplicateOverlappingText(false);
				stripper.getText(doc);
				double col2 = Double.NaN;
				TextPosition prev = null;
				for (final TextPosition t : all) {
					final boolean cellStart = prev == null
							|| Math.abs(t.getYDirAdj() - prev.getYDirAdj()) > 0.5
							|| t.getXDirAdj() - (prev.getXDirAdj() + prev.getWidth()) > 4;
					if (cellStart && "S".equals(t.getUnicode())
							&& (Double.isNaN(col2) || t.getXDirAdj() < col2)) {
						col2 = t.getXDirAdj();
					}
					prev = t;
				}
				assertFalse("第2列が見つかりません: page=" + p, Double.isNaN(col2));
				col2Starts.add(col2);
			}
			// Consistency across fragments: column 2 starts at the same x on every page.
			final double first = col2Starts.get(0);
			for (int i = 1; i < col2Starts.size(); ++i) {
				assertEquals("第2列の開始xがページ間で揺れています: " + col2Starts, first, col2Starts.get(i), 0.5);
			}
			// Minimum guarantee: the two columns' content cannot fit on A4 (20 mm margins, type area about 470 pt),
			// so column 2 should be pushed rightward beyond the type area's center
			// (the collapsed position, ≈306 pt).
			assertTrue("列がmin-content未満へ潰されています: col2.x=" + first, first > 400);
		}
	}

	/**
	 * Verifies that slight overflow (sum of minima within the allowed 1.1 ratio to available width)
	 * still compresses to fit the type area. With the same content and a wider type area
	 * (342 mm, sum of minima/type area≈1.05), measures that column 2 is compressed leftward from
	 * its minimum position (≈511 pt). This prevents a few points of overflow from clipping text
	 * at the paper edge (a decision based on measurements of w3c-jlreq).
	 */
	public void testSlightOverflowIsSqueezed() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/0240-table/auto-min-slight-overflow.html"), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final float paperWidth = doc.getPage(0).getMediaBox().getWidth();
			for (int p = 1; p <= doc.getNumberOfPages(); ++p) {
				final List<TextPosition> all = new ArrayList<>();
				final PDFTextStripper stripper = new PDFTextStripper() {
					@Override
					protected void processTextPosition(final TextPosition text) {
						all.add(text);
					}
				};
				stripper.setStartPage(p);
				stripper.setEndPage(p);
				stripper.setSuppressDuplicateOverlappingText(false);
				stripper.getText(doc);
				assertFalse(all.isEmpty());
				double col2 = Double.NaN, maxRight = 0;
				// Detect the start of column 2 by the initial "SEC" sequence (compression removes
				// the gap between columns, so spacing-based detection cannot be used).
				for (int i = 0; i + 2 < all.size(); ++i) {
					if ("S".equals(all.get(i).getUnicode()) && "E".equals(all.get(i + 1).getUnicode())
							&& "C".equals(all.get(i + 2).getUnicode())
							&& (Double.isNaN(col2) || all.get(i).getXDirAdj() < col2)) {
						col2 = all.get(i).getXDirAdj();
					}
				}
				for (final TextPosition t : all) {
					maxRight = Math.max(maxRight, t.getXDirAdj() + t.getWidth());
				}
				assertFalse("第2列が見つかりません: page=" + p, Double.isNaN(col2));
				// Compression: column 2 is placed left of its minimum position (56.7+454≈511 pt).
				assertTrue("わずかな超過が潰されていません: col2.x=" + col2, col2 < 505);
				// All text fits on the page (no overflow).
				assertTrue("文字が紙の外にあります: right=" + maxRight + " paper=" + paperWidth,
						maxRight <= paperWidth + 0.5);
			}
		}
	}
}
