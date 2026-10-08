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
 * Pin down <b>{@code text-overflow: ellipsis}</b> (css-overflow-3, 2026-08-29).
 *
 * <p>
 * Three blocks containing "MMMM…" (20 pt) in 100 pt wide nowrap blocks: ellipsis (overflow:hidden),
 * default clip (overflow:hidden), and overflow:visible+ellipsis. Inspect pixels:
 * in block 1, the box end (ellipsis area) has no ink at mid x-height (the M stems disappear),
 * but has ink just above the baseline (ellipsis dots). Block 2 has M stems at mid x-height
 * in the same area. Both blocks 1 and 2 are white outside the box; block 3 has text
 * outside the box too (ellipsis does not apply).
 * </p>
 */
public class TextOverflowTest extends TestCase {
	public void testEllipsis() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/0040-overflow/text-overflow-ellipsis.html"), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 144); // 2px/pt
			// For visual inspection (diagnosis on failure).
			new File("build/test-images").mkdirs();
			javax.imageio.ImageIO.write(img, "png", new File("build/test-images/text-overflow-ellipsis.png"));
			// Margin 10 pt. Each block: 24 pt height + 6 pt gap → block 1 y=10..34, block 2 40..64, block 3 70..94.
			// The box spans x=10..110 pt. The ellipsis (1 em = 20 pt) is placed at x=90..110 pt.
			final int box0 = 10 * 2, box1 = 110 * 2;
			// Block 1: white outside the box (x>110 pt).
			assertFalse("ellipsis: 箱の外に文字が描かれています", hasInk(img, box1 + 4, 300 * 2, 10 * 2, 34 * 2));
			// Block 2 (clip): white outside the box.
			assertFalse("clip: 箱の外に文字が描かれています", hasInk(img, box1 + 4, 300 * 2, 40 * 2, 64 * 2));
			// Block 3 (overflow:visible): text also exists outside the box.
			assertTrue("visible: 箱の外に文字が描かれていません", hasInk(img, box1 + 4, 200 * 2, 70 * 2, 94 * 2));
			// Ellipsis area (x=92..108 pt). At mid x-height (about 12 pt from the line top),
			// block 1 has no ink, while block 2 has M stems.
			final int ex0 = 92 * 2, ex1 = 108 * 2;
			assertFalse("ellipsis: 省略記号の位置にMが残っています", hasInk(img, ex0, ex1, 18 * 2, 24 * 2));
			assertTrue("clip: 同じ位置にMがありません", hasInk(img, ex0, ex1, 48 * 2, 54 * 2));
			// In the same area in block 1, dots appear just above the baseline (about 20 pt from the line top).
			assertTrue("ellipsis: 省略記号が描かれていません", hasInk(img, ex0, ex1, 26 * 2, 30 * 2));
			// Body text remains in the first half of block 1's box.
			assertTrue("ellipsis: 本文が消えています", hasInk(img, box0 + 4, 60 * 2, 18 * 2, 24 * 2));
		}
	}

	/**
	 * The ellipsis of a line in an invisible box (visibility: hidden, inherited or its own, or opacity: 0 on an
	 * ancestor) is hidden like the line's text (2026-10-09; Chrome draws only the last block's "SH…").
	 */
	public void testEllipsisHiddenWithText() throws Exception {
		final File dir = new File("local/text-overflow/hidden");
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		try (java.io.OutputStream out = new java.io.FileOutputStream(new File(dir, "out.pdf"));
				AutoCloseable scope = net.zamasoft.foliojet.layout.draw.DisplayListDumper.scopedDir(dir.getPath())) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				CTISessionHelper.transcodeFile(session,
						new File("files/unittest/0040-overflow/text-overflow-hidden.html"), "text/html", null);
			} finally {
				session.close();
			}
		}
		final String page = java.nio.file.Files.readString(new File(dir, "page-0001.txt").toPath(),
				java.nio.charset.StandardCharsets.UTF_8);
		final java.util.List<String> texts = new java.util.ArrayList<>();
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile(" y=([-0-9.]+) Text\\[\"([^\"]*)\"")
				.matcher(page);
		while (m.find()) {
			texts.add(m.group(2) + "@" + m.group(1));
		}
		final String shown = texts.stream().filter(t -> t.startsWith("SHOWN@")).findFirst().orElse(null);
		assertNotNull("SHOWN が描かれていません:\n" + page, shown);
		final java.util.List<String> ellipses = texts.stream().filter(t -> t.startsWith("…@")).toList();
		assertEquals("省略記号は見える行の 1 つだけ:\n" + page,
				java.util.List.of("…" + shown.substring(shown.indexOf('@'))), ellipses);
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
