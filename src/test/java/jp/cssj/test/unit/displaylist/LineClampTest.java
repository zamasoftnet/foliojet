package jp.cssj.test.unit.displaylist;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verify <b>{@code line-clamp} / {@code -webkit-line-clamp}</b>
 * (css-overflow-4, 2026-08-29).
 *
 * <p>
 * Fixture {@code 0040-overflow/line-clamp.html} (12 pt text, 16 pt line height, 200 pt width):
 * three-line clamp (-webkit-box idiom)→following gray block→one-line clamp→
 * two-line clamp with span and inline image→three-line clamp on a one-line paragraph→
 * two-line clamp containing two p elements (count nested block lines too; the second p disappears entirely).
 * Check ellipsis ("…") positions in the display list and the three-line clamp's height through pixels
 * (the following block's position confirms suppression from line 4 onward).
 * </p>
 */
public class LineClampTest extends TestCase {
	public void testLineClamp() throws Exception {
		final File dumpDir = new File("local/unittest/line-clamp-test");
		dumpDir.mkdirs();
		for (final File f : dumpDir.listFiles()) {
			f.delete();
		}
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		System.setProperty(DisplayListDumper.DIR_PROPERTY, dumpDir.getPath());
		try {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				CTISessionHelper.transcodeFile(session, new File("files/unittest/0040-overflow/line-clamp.html"),
						"text/html", null);
			} finally {
				session.close();
			}
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}

		// Display list: ellipsis line positions (margin-box origin, line top).
		// #three line 3 y=32, #one line 1 y=74, #nested line 2 y=114.
		// None on #short (y=138). Line 2 of #blocks' first p is y=178.
		final String dump = Files.readString(new File(dumpDir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		final List<Double> ellipsisY = new ArrayList<>();
		final Matcher m = Pattern.compile("y=([0-9.]+) Text\\[\"…\"").matcher(dump);
		while (m.find()) {
			ellipsisY.add(Double.parseDouble(m.group(1)));
		}
		assertEquals("省略記号の数が違います: " + ellipsisY + "\n" + dump, 4, ellipsisY.size());
		assertEquals("3行clampの3行目に省略記号がありません", 32.0, ellipsisY.get(0), 0.01);
		assertEquals("1行clampの1行目に省略記号がありません", 74.0, ellipsisY.get(1), 0.01);
		assertEquals("入れ子span入り2行clampの2行目に省略記号がありません", 114.0, ellipsisY.get(2), 0.01);
		assertEquals("入れ子ブロックの2行目に省略記号がありません", 178.0, ellipsisY.get(3), 0.01);
		assertFalse("2つ目のpが描かれています", dump.contains("Second"));
		// No ellipses on lines 1/2 of the three-line clamp (all line contents remain ordinary Text).
		assertFalse("4行目以降が描かれています", dump.contains("y=48.00 Text"));

		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 144); // 2px/pt
			new File("build/test-images").mkdirs();
			javax.imageio.ImageIO.write(img, "png", new File("build/test-images/line-clamp.png"));
			// #three occupies y=10..58 pt (3 lines×16 pt). After an 8 pt margin, the following gray block
			// occupies y=66..76 pt (used height is exactly three lines, so later content moves up).
			assertTrue("後続ブロックが3行の直後に来ていません", isGray(img, 100 * 2, 71 * 2));
			assertFalse("3行clampの高さが3行を超えています", isGray(img, 100 * 2, 62 * 2));
			// No text in the fourth-line area (y=58..66 pt).
			assertFalse("4行目が描かれています", hasInk(img, 10 * 2, 210 * 2, 59 * 2, 65 * 2));
			// Text exists in the third line (y=42..58 pt).
			assertTrue("3行目が消えています", hasInk(img, 10 * 2, 100 * 2, 44 * 2, 56 * 2));
			// #short (y=148..164 pt) has one line, height 16 pt: the following margin (y=164..172 pt) is empty,
			// and #blocks' first line starts at y=172 pt.
			assertFalse("N行未満の段落の下に何か描かれています", hasInk(img, 10 * 2, 210 * 2, 165 * 2, 171 * 2));
			assertTrue("入れ子ブロックの1行目がありません", hasInk(img, 10 * 2, 100 * 2, 174 * 2, 186 * 2));
			// Below #blocks (y=172..204 pt), the second p's area (y=204..220 pt) is empty.
			assertFalse("2つ目のpが描かれています", hasInk(img, 10 * 2, 210 * 2, 205 * 2, 219 * 2));
		}
	}

	private static boolean isGray(final BufferedImage img, final int x, final int y) {
		final int rgb = img.getRGB(x, y);
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return Math.abs(r - 0xCC) <= 8 && Math.abs(g - 0xCC) <= 8 && Math.abs(b - 0xCC) <= 8;
	}

	private static boolean hasInk(final BufferedImage img, final int x0, final int x1, final int y0, final int y1) {
		for (int y = y0; y < y1; ++y) {
			for (int x = x0; x < x1; ++x) {
				final int rgb = img.getRGB(x, y);
				final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
				if (r + g + b < 3 * 128) {
					return true;
				}
			}
		}
		return false;
	}
}
