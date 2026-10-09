package jp.cssj.test.unit.displaylist;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.Results;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * {@code background-clip: text} (2026-10-09). Until then the glyph outlines came from the fonts that have local
 * outlines only, so with the default Latin fonts (core 14) and Japanese fonts that are not embedded the background
 * disappeared together with the transparent text; and an inline element had its whole box painted.
 *
 * <p>
 * The fixture is drawn at 72 dpi (1 px = 1 pt). The expected extents of the colored ink were measured in Chrome
 * 151 on the same fixture (in pt, Arial against Helvetica here):
 * </p>
 * <ul>
 * <li>block: red x 12.8–99.0, blue x 99.8–136.5 (the box ends at 190, its middle is at 100), y 28.5–49.5;
 * nothing above the glyphs or beyond the text</li>
 * <li>inline over two lines: green x 12.0–102.0, y 96.8–141.0, "ab" before it stays black</li>
 * <li>vertical-rl: red y 11.3–108.8, blue y 112.5–123.0 (the middle is at 110), nothing below the text</li>
 * <li>{@code A<span style="color: transparent">HIDDEN</span>B}: nothing between A and B (x 26–97, y 225–256)</li>
 * <li>two columns: red x 10.5–57.0 in the first, blue x 111.8–156.0 in the second, y 261.0–293.2</li>
 * </ul>
 *
 * <p>
 * The fixture also has a {@code -webkit-text-stroke: 1px transparent}, which stopped the whole conversion until the
 * same day (a cast of the transparent keyword to a color).
 * </p>
 *
 * <p>
 * The transparent span is for PDF without transparency (1.3, PDF/A-1b, PDF/X-1a), which drew transparent text opaque
 * black until the same day; it is now written in text rendering mode 3 (invisible, still extractable).
 * </p>
 */
public class BackgroundClipTextTest extends TestCase {
	private static final File DOCUMENT = new File("files/unittest/3080-MODERN-CSS/background-clip-text.html");

	public void testPdfPixels() throws Exception {
		try (PDDocument pdf = Loader.loadPDF(convert(DOCUMENT, Map.of()))) {
			assertPixels(new PDFRenderer(pdf).renderImageWithDPI(0, 72));
		}
	}

	/**
	 * Without transparency (PDF 1.3) the clip works the same, and the transparent text that no clip shows is
	 * written invisible rather than black.
	 */
	public void testPdf13Pixels() throws Exception {
		try (PDDocument pdf = Loader.loadPDF(convert(DOCUMENT, Map.of("output.pdf.version", "1.3")))) {
			assertPixels(new PDFRenderer(pdf).renderImageWithDPI(0, 72));
			final String content = new String(pdf.getPage(0).getContents().readAllBytes(),
					StandardCharsets.ISO_8859_1);
			assertEquals(content, 1, count(content, "\\b3 Tr\\b"));
			final PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSuppressDuplicateOverlappingText(false);
			assertTrue(stripper.getText(pdf).replaceAll("\\s+", "").contains("AHIDDENB"));
		}
	}

	/**
	 * The clip shows the text in text rendering mode 7, one text object per box (the inline has one per line),
	 * and the transparent copy is left out: extraction finds each word once, in order.
	 */
	public void testPdfTextOnce() throws Exception {
		final byte[] bytes = convert(DOCUMENT, Map.of());
		try (PDDocument pdf = Loader.loadPDF(bytes)) {
			final PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSuppressDuplicateOverlappingText(false);
			// (PDFBox puts spaces between the glyphs of the vertical run)
			assertEquals("HHHHHHabMMMMMMMMHHHHHHHHAHIDDENBSAAAAAAAABBBBBBBB", stripper.getText(pdf).replaceAll("\\s+", ""));
			final String content = new String(pdf.getPage(0).getContents().readAllBytes(),
					StandardCharsets.ISO_8859_1);
			assertEquals(content, 5, count(content, "\\bBT\\s+7 Tr\\b"));
			assertEquals(content, 5, count(content, "\\b7 Tr\\b"));
		}
	}

	/** Tagged PDF keeps the text it draws, and the clip text is an artifact. */
	public void testTaggedPdf() throws Exception {
		try (PDDocument pdf = Loader.loadPDF(convert(DOCUMENT, Map.of("output.pdf.tagged", "true")))) {
			final String content = new String(pdf.getPage(0).getContents().readAllBytes(),
					StandardCharsets.ISO_8859_1);
			assertEquals(content, 5, count(content, "/Artifact\\s+BMC\\s+BT\\s+7 Tr\\b"));
			assertPixels(new PDFRenderer(pdf).renderImageWithDPI(0, 72));
		}
	}

	/** Image output clips with the glyph outlines. */
	public void testPngPixels() throws Exception {
		final Capture results = new Capture();
		convert(DOCUMENT, Map.of("output.type", "image/png", "output.image.resolution", "72"), results);
		assertFalse("a PNG page must be emitted", results.order.isEmpty());
		final BufferedImage page = ImageIO.read(new ByteArrayInputStream(results.bytes(results.order.get(0))));
		assertNotNull(page);
		assertPixels(page);
	}

	/** SVG clips with the glyph outlines as paths. */
	public void testPagedSvgClipsWithPaths() throws Exception {
		final Capture results = new Capture();
		convert(DOCUMENT, Map.of("output.type", "application/vnd.copper.paged-svg", "output.paged-svg.compression",
				"none"), results);
		final String svg = new String(results.bytes("pages/0001.svg"), StandardCharsets.UTF_8);
		final Matcher clips = Pattern.compile("<clipPath[^>]*>\\s*<path[^>]* d=\"([^\"]*)\"").matcher(svg);
		int glyphClips = 0;
		while (clips.find()) {
			// A rectangle is a few points; glyphs are many
			if (clips.group(1).length() > 300) {
				++glyphClips;
			}
		}
		assertEquals(svg, 5, glyphClips);
	}

	private static void assertPixels(final BufferedImage img) {
		// Block: red then blue, split at the middle of the box, only inside the glyphs
		final int[] red = extent(img, 10, 10, 190, 70, Ink.RED);
		final int[] blue = extent(img, 10, 10, 190, 70, Ink.BLUE);
		assertNotNull("the block's red glyphs", red);
		assertNotNull("the block's blue glyphs", blue);
		assertEquals("red starts with the first glyph", 13, red[0], 2);
		assertEquals("red ends at the middle of the box", 99, red[2], 2);
		assertEquals("blue starts at the middle of the box", 100, blue[0], 2);
		assertEquals("blue ends with the last glyph", 137, blue[2], 2.5);
		assertEquals("the glyphs' top", 28, red[1], 4);
		assertEquals("the baseline", 49, red[3], 4);
		assertNull("nothing beyond the text", extent(img, 145, 10, 190, 70, Ink.ANY));
		assertNull("nothing above the glyphs", extent(img, 10, 10, 190, 22, Ink.ANY));

		// Inline over two lines
		final int[] green = extent(img, 10, 90, 130, 150, Ink.GREEN);
		assertNotNull("the inline's glyphs", green);
		assertEquals(12, green[0], 2);
		assertEquals(102, green[2], 2);
		assertEquals(97, green[1], 4);
		assertEquals(141, green[3], 4);
		assertNotNull("the second line", extent(img, 10, 122, 130, 150, Ink.GREEN));
		assertNull("ab is not clipped", extent(img, 10, 90, 30, 120, Ink.GREEN));
		assertNotNull("ab stays black", extent(img, 10, 90, 30, 120, Ink.BLACK));

		// Vertical: red on the top half, blue below the middle, nothing below the text
		final int[] vred = extent(img, 250, 10, 280, 210, Ink.RED);
		final int[] vblue = extent(img, 250, 10, 280, 210, Ink.BLUE);
		assertNotNull("the vertical red glyphs", vred);
		assertNotNull("the vertical blue glyphs", vblue);
		assertEquals(11, vred[1], 2);
		assertEquals(109, vred[3], 2);
		assertEquals(113, vblue[1], 2);
		assertEquals(123, vblue[3], 2.5);
		assertNull("nothing below the vertical text", extent(img, 250, 130, 280, 210, Ink.ANY));

		// Transparent text without a clip
		assertNotNull("A", extent(img, 10, 225, 24, 260, Ink.BLACK));
		assertNull("HIDDEN", extent(img, 26, 225, 97, 256, Ink.ANY));
		assertNotNull("B", extent(img, 100, 225, 115, 256, Ink.BLACK));

		// Two columns
		final int[] cred = extent(img, 10, 256, 100, 300, Ink.RED);
		final int[] cblue = extent(img, 100, 256, 200, 300, Ink.BLUE);
		assertNotNull("the first column's glyphs", cred);
		assertNotNull("the second column's glyphs", cblue);
		// (image output draws Helvetica with a substitute whose A is narrower: the ends are looser)
		assertEquals(11, cred[0], 2);
		assertEquals(57, cred[2], 6);
		assertEquals(112, cblue[0], 2);
		assertEquals(156, cblue[2], 6);
		assertEquals(261, cred[1], 4);
		assertEquals(293, cred[3], 4);
	}

	private enum Ink {
		RED, BLUE, GREEN, BLACK, ANY;

		boolean matches(final int rgb) {
			final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
			return switch (this) {
			case RED -> r > 150 && g < 100 && b < 100;
			case BLUE -> b > 150 && r < 100 && g < 100;
			case GREEN -> g > 100 && r < 80 && b < 80;
			case BLACK -> r < 90 && g < 90 && b < 90;
			case ANY -> r < 230 || g < 230 || b < 230;
			};
		}
	}

	/** {minX, minY, maxX, maxY} of the ink in the region, or null. */
	private static int[] extent(final BufferedImage img, final int x0, final int y0, final int x1, final int y1,
			final Ink ink) {
		int[] e = null;
		for (int y = y0; y < y1 && y < img.getHeight(); ++y) {
			for (int x = x0; x < x1 && x < img.getWidth(); ++x) {
				if (ink.matches(img.getRGB(x, y))) {
					if (e == null) {
						e = new int[] { x, y, x, y };
					} else {
						e[0] = Math.min(e[0], x);
						e[1] = Math.min(e[1], y);
						e[2] = Math.max(e[2], x);
						e[3] = Math.max(e[3], y);
					}
				}
			}
		}
		return e;
	}

	private static int count(final String text, final String regex) {
		final Matcher m = Pattern.compile(regex).matcher(text);
		int count = 0;
		while (m.find()) {
			++count;
		}
		return count;
	}

	private static byte[] convert(final File document, final Map<String, String> properties) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		convert(document, properties, new SingleResult(new StreamFragmentedOutput(out)));
		return out.toByteArray();
	}

	private static void convert(final File document, final Map<String, String> properties, final Results results)
			throws Exception {
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(results);
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			for (final Map.Entry<String, String> property : properties.entrySet()) {
				session.property(property.getKey(), property.getValue());
			}
			CTISessionHelper.transcodeFile(session, document, "text/html", "UTF-8");
		} finally {
			session.close();
		}
	}

	private static final class Capture implements Results {
		final Map<String, ByteArrayOutputStream> data = new LinkedHashMap<>();
		final List<String> order = new ArrayList<>();

		@Override
		public boolean hasNext() {
			return true;
		}

		@Override
		public FragmentedOutput nextBuilder(final SourceMetadata metadata) {
			final String uri = metadata.getURI().toString();
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			this.data.put(uri, out);
			this.order.add(uri);
			return new StreamFragmentedOutput(out);
		}

		@Override
		public void end() {
			// Do nothing.
		}

		byte[] bytes(final String uri) {
			final ByteArrayOutputStream out = this.data.get(uri);
			assertNotNull(uri + " must be emitted: " + this.order, out);
			return out.toByteArray();
		}
	}
}
