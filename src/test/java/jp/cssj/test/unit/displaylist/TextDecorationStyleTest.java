package jp.cssj.test.unit.displaylist;

import java.awt.image.BufferedImage;
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
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Pin down the rendering of <b>{@code text-decoration-style}, {@code text-decoration-thickness},
 * {@code text-underline-offset}, and {@code text-underline-position}</b> with pixels (2026-08-29).
 *
 * <p>
 * Fixture {@code 0160-text-decoration/decoration-styles.html} (20 pt text / 30 pt line height,
 * each block 30 pt). The underline for "MMMMMMMM" is below the baseline, so find the row
 * with the most ink in the band below each block's baseline as the "underline row".
 * Relative to the 2 pt solid line (#thick): wavy also has ink ±2.5 pt above and below
 * (amplitude = thickness); dotted has white gaps in the underline row; offset:9pt
 * (zero = baseline; the auto underline is at the 6.48 pt descent depth, so choose a value
 * clearly below it) lowers the underline below the solid line; under places it below
 * the descent; double produces two lines.
 * </p>
 */
public class TextDecorationStyleTest extends TestCase {
	private static final int SCALE = 2; // 144dpi
	private static final int MARGIN = 10;
	private static final int ROW = 30;

	public void testDecorationStyles() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/0160-text-decoration/decoration-styles.html"), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 72 * SCALE);
			new File("build/test-images").mkdirs();
			javax.imageio.ImageIO.write(img, "png", new File("build/test-images/decoration-styles.png"));

			// Block order: 0 solid, 1 thick, 2 wavy, 3 dotted, 4 dashed, 5 double, 6 offset, 7 under.
			final int thickRow = underlineRow(img, 1);
			// The wavy line's center is on the same row as the solid line in the same layout. The row with most ink lies
			// on the flat parts of the crests, so translate the solid-line row to block 2 as the reference.
			final int wavyCenter = thickRow + ROW * SCALE;
			// The solid line (2 pt) has no ink at ±2.5 pt, while wavy (2 pt amplitude) has ink both above and below.
			final int off = (int) Math.round(2.5 * SCALE);
			assertFalse("実線の上にインクがあります", rowHasInk(img, thickRow - off));
			assertFalse("実線の下にインクがあります", rowHasInk(img, thickRow + off));
			assertTrue("波線の上側の山がありません", rowHasInk(img, wavyCenter - off));
			assertTrue("波線の下側の谷がありません", rowHasInk(img, wavyCenter + off));
			assertTrue("波線の中心の行にインクがありません", rowHasInk(img, wavyCenter));

			// dotted: white gaps occur in the underline row; solid has none.
			final int dottedRow = underlineRow(img, 3);
			assertTrue("点線に隙間がありません", rowHasGap(img, dottedRow));
			assertFalse("実線に隙間があります", rowHasGap(img, thickRow));
			// dashed also has gaps.
			assertTrue("破線に隙間がありません", rowHasGap(img, underlineRow(img, 4)));

			// double: another line lies 2 pt (= thickness) above/below the underline row → ink where solid has none.
			final int doubleRow = underlineRow(img, 5);
			assertTrue("二重線の2本目がありません",
					rowHasInk(img, doubleRow - 2 * SCALE) || rowHasInk(img, doubleRow + 2 * SCALE));

			// offset: 9pt (line top at baseline+9 pt, center at +10 pt) → 3.5 pt below auto
			// (center at baseline+6.48 pt). Compare relative positions within blocks.
			final int offsetRow = underlineRow(img, 6);
			assertTrue("text-underline-offsetで下線が下がっていません",
					relative(offsetRow, 6) > relative(thickRow, 1) + 2 * SCALE);
			// under: the line's top touches the descent bottom (6.48 pt) → half the thickness (1 pt) below
			// the solid line (center = baseline+6.48 pt).
			final int underRow = underlineRow(img, 7);
			assertTrue("text-underline-position: under で下線が下がっていません",
					relative(underRow, 7) >= relative(thickRow, 1) + 1);
		}
	}

	/** Relative y within a block (px). */
	private static int relative(final int row, final int index) {
		return row - (MARGIN + ROW * index) * SCALE;
	}

	/**
	 * Row with the most ink in the band below block index's baseline (block top + 3 + 17.52 pt).
	 */
	private static int underlineRow(final BufferedImage img, final int index) {
		final int top = (MARGIN + ROW * index) * SCALE;
		final int baseline = top + (int) Math.round((3 + 17.52) * SCALE);
		final int bottom = top + ROW * SCALE;
		int best = -1, bestCount = -1;
		for (int y = baseline + 1; y < bottom; ++y) {
			final int count = inkCount(img, y);
			if (count > bestCount) {
				bestCount = count;
				best = y;
			}
		}
		assertTrue("段" + index + "に下線がありません", bestCount > 0);
		return best;
	}

	private static int inkCount(final BufferedImage img, final int y) {
		int count = 0;
		for (int x = MARGIN * SCALE; x < (MARGIN + 120) * SCALE; ++x) {
			if (isInk(img.getRGB(x, y))) {
				++count;
			}
		}
		return count;
	}

	private static boolean rowHasInk(final BufferedImage img, final int y) {
		return inkCount(img, y) > 0;
	}

	/** Whether white pixels occur between the first and last ink pixels in the underline row. */
	private static boolean rowHasGap(final BufferedImage img, final int y) {
		int first = -1, last = -1;
		for (int x = MARGIN * SCALE; x < (MARGIN + 120) * SCALE; ++x) {
			if (isInk(img.getRGB(x, y))) {
				if (first < 0) {
					first = x;
				}
				last = x;
			}
		}
		for (int x = first; x <= last; ++x) {
			if (!isInk(img.getRGB(x, y))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isInk(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return r + g + b < 3 * 160;
	}
}
