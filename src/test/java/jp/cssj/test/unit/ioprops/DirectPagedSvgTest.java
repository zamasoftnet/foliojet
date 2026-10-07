package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.Results;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Tests for Paged SVG output (the Batik version was removed on 2026-08-28, leaving only this writer).
 *
 * <p>
 * Check that the output references the same resources, preserves the same text, has the expected
 * page count, and is valid XML. Details such as coordinate rounding may change, so do not compare bytes.
 * </p>
 */
public class DirectPagedSvgTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final String PNG =
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

	private static String html() {
		return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
				+ "@page{size:120pt 90pt;margin:8pt}body{margin:0;font-size:11pt}"
				+ "h1{font-size:11pt;margin:0}"
				+ ".box{width:40pt;height:12pt;background:#3366cc;border:1pt solid #000}"
				+ ".next{page-break-before:always}"
				+ "</style></head><body>"
				+ "<h1 id=\"top\"><a href=\"#second\">第一頁 ABC</a></h1>"
				+ "<div class=\"box\"></div>"
				// Specify a size. Drawing at 1 pixel would hide dimension mix-ups within the error tolerance.
				+ "<img src=\"data:image/png;base64," + PNG + "\" style=\"width:40pt;height:30pt\">"
				+ "<div id=\"second\" class=\"next\"><p>第二頁 XYZ</p></div>"
				+ "</body></html>";
	}

	/** A constant to avoid confusing which writer is being selected. The default is direct. */
	/** Pages, resources, and the table of contents are emitted in the expected form. */
	public void testPagesAreWellFormed() throws Exception {
		final CapturingResults direct = run(Map.of());
		assertTrue("at least two pages are expected", pageCount(direct) >= 2);
		for (int i = 1; i <= pageCount(direct); ++i) {
			final String name = String.format("pages/%04d.svg", i);
			final byte[] svg = direct.data.get(name).toByteArray();
			assertWellFormedXml(name, svg);
			final String text = new String(svg, StandardCharsets.UTF_8);
			assertTrue(name + " must declare the SVG namespace",
					text.contains("http://www.w3.org/2000/svg"));
			assertTrue(name + " must carry a viewBox", text.contains("viewBox="));
		}
	}

	/** Characters remain as text and reference shared WOFF2. */
	public void testTextIsPreserved() throws Exception {
		final CapturingResults direct = run(Map.of());
		final String first = direct.text("pages/0001.svg");
		assertTrue("text must stay as <text>", first.contains("<text"));
		assertTrue("the original characters must be recoverable",
				first.contains("data-copper-text="));
		assertTrue("the shared subset must be referenced", first.contains("../assets/fonts/"));
		assertTrue("the font-face rule must be emitted", first.contains("@font-face"));
		assertTrue("page data must keep the source text",
				direct.text("pages/0001.json").contains("第一頁"));
	}

	/** Shapes are emitted as paths with fill and stroke. */
	public void testShapesBecomePaths() throws Exception {
		final CapturingResults direct = run(Map.of());
		final String first = direct.text("pages/0001.svg");
		assertTrue("shapes must be emitted as <path>", first.contains("<path"));
		assertTrue("a fill must be present", first.contains("fill=\"#"));
		assertTrue("the box border must be stroked", first.contains("stroke=\"#"));
	}

	/** Images are emitted externally as shared resources, without data: embedded in the body. */
	public void testImagesAreExternalised() throws Exception {
		final CapturingResults direct = run(Map.of());
		final String first = direct.text("pages/0001.svg");
		assertTrue("the image must reference a shared asset", first.contains("../assets/images/"));
		assertFalse("no data: URI may be inlined", first.contains("data:image/"));
		assertTrue("the asset itself must be emitted",
				direct.order.stream().anyMatch(u -> u.startsWith("assets/images/")));
	}

	/**
	 * With embedding, images are included in the page SVG and not emitted as separate files.
	 * This option is for delivery methods that cannot write to a directory.
	 */
	public void testEmbeddedResources() throws Exception {
		final CapturingResults r = run(Map.of("output.paged-svg.resources", "embed"));
		final String first = r.text("pages/0001.svg");
		assertTrue("the image must be inlined", first.contains("data:image/"));
		assertFalse("no shared image file may be emitted",
				r.order.stream().anyMatch(u -> u.startsWith("assets/images/")));
		assertWellFormedXml("pages/0001.svg", r.data.get("pages/0001.svg").toByteArray());
	}

	/**
	 * {@code omit} suppresses both fonts and images together.
	 *
	 * <p>
	 * Previously, {@code output.paged-svg.fonts} and {@code output.paged-svg.images} were separate.
	 * Both concern how resources reach the recipient, so they were merged into
	 * {@code output.paged-svg.resources}. Referencing, embedding, and omitting resources are mutually
	 * exclusive.
	 * </p>
	 */
	public void testOmitStopsImagesButKeepsFonts() throws Exception {
		final CapturingResults r = run(Map.of("output.paged-svg.resources", "omit"));

		assertFalse("no image asset may be emitted",
				r.order.stream().anyMatch(u -> u.startsWith("assets/images/")));
		// **Fonts are emitted** (2026-08-28). Names referenced by page SVGs are numbered per conversion;
		// omitting them and referencing resources from the previous conversion would substitute different glyphs.
		assertTrue("the font subset must still be emitted",
				r.order.stream().anyMatch(u -> u.startsWith("assets/fonts/")));

		// References and manifest entries remain so the recipient can reuse previous resources.
		final String first = r.text("pages/0001.svg");
		assertTrue("the page must still reference the shared subset", first.contains("../assets/fonts/"));
		assertTrue("the page must still reference the shared image", first.contains("../assets/images/"));
		final String manifest = r.text("manifest.json");
		assertTrue("the manifest must mark the images as omitted", manifest.contains("\"omitted\":true"));
		assertTrue("the manifest must still list the pages", manifest.contains("pages/0001.svg"));
	}

	/** The default is referencing. Only one physical copy of the same image is needed. */
	public void testReferencedResourcesAreDefault() throws Exception {
		final CapturingResults r = run(Map.of());
		final String first = r.text("pages/0001.svg");
		assertTrue("the default must reference a shared file", first.contains("../assets/images/"));
		assertFalse("nothing may be inlined by default", first.contains("data:image/"));
	}

	/**
	 * <b>Images are drawn at the same size by both methods.</b>
	 *
	 * <p>
	 * The image contract is to draw into a rectangle of its own logical dimensions, not a unit rectangle
	 * or its pixel dimensions. Confusing these still leaves references correct and XML well-formed,
	 * so all other checks pass. Only checking the actual drawn dimensions catches the error.
	 * </p>
	 */
	public void testImageIsDrawnAtTheGivenSize() throws Exception {
		final double[] drawn = imageBox(run(Map.of()).data.get("pages/0001.svg").toByteArray());
		assertNotNull("the writer must draw the image", drawn);
		// CSS specifies 40pt×30pt. Any departure indicates a dimension mix-up.
		assertEquals("the CSS width must be honoured", 40.0, drawn[0], 1.0);
		assertEquals("the CSS height must be honoured", 30.0, drawn[1], 1.0);
	}

	/** Return the actual width and height occupied by the first {@code <image>}, including ancestor transforms. */
	private static double[] imageBox(final byte[] svg) throws Exception {
		final org.w3c.dom.Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new ByteArrayInputStream(svg));
		final org.w3c.dom.NodeList images = doc.getElementsByTagName("image");
		if (images.getLength() == 0) {
			return null;
		}
		final org.w3c.dom.Element image = (org.w3c.dom.Element) images.item(0);
		double w = Double.parseDouble(image.getAttribute("width"));
		double h = Double.parseDouble(image.getAttribute("height"));
		for (org.w3c.dom.Node n = image; n instanceof org.w3c.dom.Element e; n = n.getParentNode()) {
			final double[] s = scaleOf(e.getAttribute("transform"));
			w *= s[0];
			h *= s[1];
		}
		return new double[] { Math.abs(w), Math.abs(h) };
	}

	/** Extract only the scale factors from {@code matrix(a b c d e f)} or {@code scale(...)}. */
	private static double[] scaleOf(final String transform) {
		if (transform == null || transform.isEmpty()) {
			return new double[] { 1, 1 };
		}
		final java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("(matrix|scale)\\s*\\(([^)]*)\\)").matcher(transform);
		double sx = 1;
		double sy = 1;
		while (m.find()) {
			final String[] parts = m.group(2).trim().split("[\\s,]+");
			if ("matrix".equals(m.group(1)) && parts.length >= 4) {
				sx *= Double.parseDouble(parts[0]);
				sy *= Double.parseDouble(parts[3]);
			} else if ("scale".equals(m.group(1)) && parts.length >= 1) {
				sx *= Double.parseDouble(parts[0]);
				sy *= Double.parseDouble(parts.length >= 2 ? parts[1] : parts[0]);
			}
		}
		return new double[] { sx, sy };
	}

	/**
	 * With gzip delivery, only page SVGs and page JSON are compressed and renamed.
	 *
	 * <p>
	 * Shared WOFF2 and PNG are already compressed, so leave them untouched.
	 * {@code manifest.json} is the entry point, so leave it unchanged. Compute SHA-256 over
	 * <b>the bytes actually delivered</b>, i.e., the compressed bytes.
	 * </p>
	 */
	public void testGzipCompressesOnlyTheTextResults() throws Exception {
		final CapturingResults plain = run(Map.of());
		final CapturingResults gzip = run(Map.of("output.paged-svg.compression", "gzip"));

		assertTrue("the page SVG must be named .svgz", gzip.data.containsKey("pages/0001.svgz"));
		assertTrue("the page data must be named .json.gz", gzip.data.containsKey("pages/0001.json.gz"));
		assertFalse("the uncompressed names must be gone", gzip.data.containsKey("pages/0001.svg"));
		assertTrue("the manifest must stay readable", gzip.data.containsKey("manifest.json"));
		assertTrue("the manifest must point at the compressed page",
				gzip.text("manifest.json").contains("pages/0001.svgz"));

		final byte[] compressed = gzip.data.get("pages/0001.svgz").toByteArray();
		assertTrue("the page must actually shrink",
				compressed.length < plain.data.get("pages/0001.svg").toByteArray().length);
		// Decompression yields the same contents as uncompressed output.
		try (var in = new java.util.zip.GZIPInputStream(new ByteArrayInputStream(compressed))) {
			assertEquals("gzip must be lossless", plain.text("pages/0001.svg"),
					new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}

		// Shared resources remain unchanged.
		for (final String uri : gzip.data.keySet()) {
			assertFalse("shared resources must not be wrapped: " + uri,
					uri.endsWith(".woff2.gz") || uri.endsWith(".png.gz"));
		}

		// The manifest SHA-256 covers the compressed bytes.
		assertTrue("the manifest must record the stored bytes",
				gzip.text("manifest.json").contains(sha256(compressed)));
	}

	/** The manifest SHA-256 matches the actual data. Check this especially because it is computed while streaming. */
	public void testManifestHashesMatchTheBytes() throws Exception {
		final CapturingResults direct = run(Map.of());
		final String manifest = direct.text("manifest.json");
		for (int i = 1; i <= pageCount(direct); ++i) {
			final String name = String.format("pages/%04d.svg", i);
			final String sha = sha256(direct.data.get(name).toByteArray());
			assertTrue("the manifest must record the actual bytes of " + name + " (" + sha + ")",
					manifest.contains(sha));
		}
	}

	private static String sha256(final byte[] bytes) throws Exception {
		return java.util.HexFormat.of()
				.formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static int pageCount(final CapturingResults r) {
		return (int) r.order.stream().filter(u -> u.startsWith("pages/") && u.endsWith(".svg")).count();
	}

	private static void assertWellFormedXml(final String name, final byte[] bytes) throws Exception {
		try {
			javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
					.parse(new ByteArrayInputStream(bytes));
		} catch (final Exception e) {
			throw new AssertionError(name + " is not well-formed XML: " + e + "\n"
					+ new String(bytes, StandardCharsets.UTF_8), e);
		}
	}

	private CapturingResults run(final Map<String, String> extraProps) throws Exception {
		return this.run(extraProps, html());
	}

	/**
	 * Text bounds in page JSON end at the glyph advance (2026-09-06, user report: "tate-chu-yoko link bounds").
	 * Previously, the last glyph position + font-size (1em) determined the right edge (bottom edge in
	 * vertical writing), so bounds for half-width digits extended by 0.5em, and the reader's text
	 * and link layers exceeded the line width.
	 * For a run of N identical glyphs, the correct width is (last−first)×N/(N−1).
	 */
	public void testTextRunBoundsFollowGlyphAdvances() throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
				+ "@page{size:200pt 200pt;margin:10pt}body{margin:0;font-size:10pt}"
				+ ".v{writing-mode:vertical-rl;height:150pt}.tcy{text-combine-upright:all}"
				+ "</style></head><body>"
				+ "<p>0000000000</p>"
				+ "<div class=\"v\"><p>縦<span class=\"tcy\">00</span>縦</p><p>横倒し0000000000</p></div>"
				+ "</body></html>";
		final CapturingResults r = this.run(Map.of(), html);
		// The vertical-writing div may move to page 2, so concatenate all pages for searching.
		final StringBuilder jsonAll = new StringBuilder();
		final StringBuilder svgAll = new StringBuilder();
		for (int i = 1; i <= pageCount(r); ++i) {
			jsonAll.append(r.text(String.format("pages/%04d.json", i)));
			svgAll.append(r.text(String.format("pages/%04d.svg", i)));
		}
		final String json = jsonAll.toString();
		final String svg = svgAll.toString();
		// 10 digits in horizontal writing
		assertRunBoundsFollowAdvances(json, svg, "0000000000", false);
		// 2 tate-chu-yoko digits in vertical writing (arranged horizontally)
		assertRunBoundsFollowAdvances(json, svg, "00", false);
		// Sideways digits in vertical writing (arranged vertically)
		assertRunBoundsFollowAdvances(json, svg, "0000000000", true);
	}

	private static void assertRunBoundsFollowAdvances(final String json, final String svg, final String value,
			final boolean vertical) {
		final java.util.regex.Matcher tm = java.util.regex.Pattern
				.compile("<text x=\"([^\"]*)\" y=\"([^\"]*)\"[^>]*data-copper-text=\"" + value + "\"")
				.matcher(svg);
		assertTrue("SVG must contain the run " + value, tm.find());
		// Sideways digits form a run advancing along x under rotation, so select the axis along which coordinates vary.
		final String[] xsRaw = tm.group(1).trim().split(" ");
		final String[] ysRaw = tm.group(2).trim().split(" ");
		final int n = xsRaw.length;
		assertEquals(value.length(), n);
		final double xSpread = Math.abs(Double.parseDouble(xsRaw[n - 1]) - Double.parseDouble(xsRaw[0]));
		final double ySpread = Math.abs(Double.parseDouble(ysRaw[n - 1]) - Double.parseDouble(ysRaw[0]));
		final boolean alongY = ySpread > xSpread;
		final double expected = Math.max(xSpread, ySpread) * n / (n - 1);
		final java.util.regex.Matcher jm = java.util.regex.Pattern
				.compile("\\{\"value\":\"" + value + "\",\"font\":\"[^\"]*\",\"size\":([0-9.]+),\"transform\":\\[[^\\]]*\\],\"bounds\":\\[([^\\]]*)\\]")
				.matcher(json);
		assertTrue("JSON must contain the run " + value, jm.find());
		final String[] b = jm.group(2).split(",");
		final double extent = alongY ? Double.parseDouble(b[3]) - Double.parseDouble(b[1])
				: Double.parseDouble(b[2]) - Double.parseDouble(b[0]);
		assertEquals("bounds of " + value + (vertical ? " (vertical)" : "") + " must close on the glyph advances",
				expected, extent, 0.05);
	}

	private CapturingResults run(final Map<String, String> extraProps, final String html) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.default-font-family", "'Noto Serif JP'");
			session.property("processing.pass-count", "2");
			// The default is gzip (2026-08-28). These tests read page SVG contents directly,
			// so leave them uncompressed unless explicitly specified.
			session.property("output.paged-svg.compression", "none");
			for (final Map.Entry<String, String> e : extraProps.entrySet()) {
				session.property(e.getKey(), e.getValue());
			}
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					new File("files/unittest/1080-FONT/direct-svg-test.html").toURI(), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return results;
	}

	private static final class CapturingResults implements Results {
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

		String text(final String uri) {
			return this.data.get(uri).toString(StandardCharsets.UTF_8);
		}
	}
}
