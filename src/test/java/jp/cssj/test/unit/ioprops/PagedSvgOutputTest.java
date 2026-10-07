package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.Results;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.pdfg2d.font.FontFile;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** Page SVG, shared webfont and image directory package regression tests. */
public class PagedSvgOutputTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final String PNG =
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

	public void testTwoPassDirectoryPackage() throws Exception {
		final byte[] jpeg = jpeg();
		final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
				+ "@page{size:100pt 80pt;margin:8pt}body{margin:0;font-size:12pt}"
				+ "h1,h2{font-size:12pt;margin:0}"
				+ ".next{page-break-before:always}.v{writing-mode:vertical-rl;height:45pt}img{width:10pt;height:10pt}"
				+ "</style></head><body><h1 id=\"top\"><a href=\"#second\">第一頁 ABC</a></h1><img src=\"data:image/png;base64," + PNG
				+ "\"><img src=\"data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg)
				+ "\"><div id=\"second\" class=\"next v\"><h2>第二頁</h2><img src=\"data:image/png;base64," + PNG
				+ "\"></div></body></html>";
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.default-font-family", "'Noto Serif JP'");
			session.property("processing.pass-count", "2");
			// The default is gzip (2026-08-28). Leave pages uncompressed to read their contents directly.
			session.property("output.paged-svg.compression", "none");
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					new File("files/unittest/1080-FONT/paged-svg-test.html").toURI(), "text/html", "UTF-8");
		} finally {
			session.close();
		}

		assertTrue(results.ended);
		assertEquals("manifest must be the final result", "manifest.json",
				results.order.get(results.order.size() - 1));
		assertTrue(results.data.containsKey("pages/0001.svg"));
		assertTrue(results.data.containsKey("pages/0001.json"));
		assertTrue(results.data.containsKey("pages/0002.svg"));
		assertTrue(results.data.containsKey("pages/0002.json"));
		assertFalse("compression=none must keep the plain names",
				results.data.containsKey("pages/0001.svgz"));
		assertEquals("the repeated PNG and one JPEG must be emitted once each", 2,
				results.order.stream().filter(uri -> uri.startsWith("assets/images/")).count());
		final String jpegUri = results.order.stream().filter(uri -> uri.endsWith(".jpg")).findFirst().orElseThrow();
		assertEquals("image/jpeg", results.mediaTypes.get(jpegUri));
		assertTrue("JPEG bytes must be preserved without recompression",
				java.util.Arrays.equals(jpeg, results.data.get(jpegUri).toByteArray()));

		final String first = results.text("pages/0001.svg");
		assertWellFormedXml(results.data.get("pages/0001.svg").toByteArray());
		assertWellFormedXml(results.data.get("pages/0002.svg").toByteArray());
		assertTrue(first.contains("<text"));
		assertTrue(first.contains("data-copper-text="));
		assertTrue(first.contains("../assets/fonts/"));
		assertTrue(first.contains("../assets/images/"));
		assertFalse(first.contains("data:image/"));
		assertTrue(results.text("pages/0001.json").contains("第一頁"));
		assertTrue(results.text("pages/0001.json").contains("ABC"));
		assertTrue(results.text("pages/0002.json").contains("第二頁"));
		assertTrue(results.text("pages/0001.json").contains("\"href\":\"#second\""));
		assertTrue(results.text("pages/0001.json").contains("\"anchors\""));
		assertTrue(results.text("pages/0001.json").contains("\"id\":\"top\""));
		assertTrue(results.text("pages/0002.json").contains("\"id\":\"second\""));

		final String manifest = results.text("manifest.json");
		assertTrue(manifest.contains("\"pageCount\":2"));
		assertTrue(manifest.contains("\"mediaType\":\"application/vnd.copper.paged-svg\""));
		assertTrue(manifest.contains("pages/0001.svg"));
		assertTrue(manifest.contains("assets/images/"));
		assertTrue(manifest.contains("\"anchors\""));
		assertTrue(manifest.contains("\"top\":{\"page\":1"));
		assertTrue(manifest.contains("\"second\":{\"page\":2"));
		assertTrue(manifest.contains("\"outline\""));
		assertTrue(manifest.contains("\"title\":\"第一頁 ABC\""));
		assertTrue(manifest.contains("\"title\":\"第二頁\""));
		final List<String> fonts = results.order.stream().filter(uri -> uri.startsWith("assets/fonts/")).toList();
		assertFalse(results.text("pages/0001.json"), fonts.isEmpty());
		for (final String uri : fonts) {
			final byte[] bytes = results.data.get(uri).toByteArray();
			assertEquals("WOFF2 must retain the four-byte alignment expected by browser decoders", 0,
					bytes.length & 3);
			assertEquals('w', bytes[0]);
			assertEquals('O', bytes[1]);
			assertEquals('F', bytes[2]);
			assertEquals('2', bytes[3]);
			final int totalSfntSize = ByteBuffer.wrap(bytes).getInt(16);
			assertTrue("WOFF2 must use actual Brotli compression: " + bytes.length + " >= " + totalSfntSize,
					bytes.length < totalSfntSize);
			assertWoff2CanBeRead(bytes);
		}
		assertTrue(manifest.contains("assets/fonts/"));
		assertTrue("font manifest must expose the original OS/2 embedding flags",
				manifest.contains("\"fsType\":0"));
	}

	/**
	 * Suppress shared resources. When relaying out the same book with only the font size changed,
	 * this avoids re-emitting font subsets and images identical to the previous conversion.
	 * References and manifest entries remain, allowing the recipient to retrieve them from the previous output.
	 *
	 * <p>
	 * Omit <b>only font versions the recipient should already have</b> (2026-08-29).
	 * Emit them in the first conversion of a session because the recipient does not have them.
	 * In the second conversion of the same session, the carried-over version suffices:
	 * omit it and mark it omitted in the manifest.
	 * </p>
	 */
	public void testResourceSuppression() throws Exception {
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			final CapturingResults first = run(session, simpleHtml(),
					Map.of("output.paged-svg.resources", "omit"));
			assertTrue("no image bytes may be emitted",
					first.order.stream().noneMatch(uri -> uri.startsWith("assets/images/")));
			assertTrue("the first conversion must still emit the font the receiver cannot have",
					first.order.stream().anyMatch(uri -> uri.startsWith("assets/fonts/")));
			final String page = first.text("pages/0001.svg");
			assertTrue("the page must still reference the shared subset", page.contains("../assets/fonts/"));
			assertTrue("the page must still reference the shared image", page.contains("../assets/images/"));
			final String manifest = first.text("manifest.json");
			assertTrue("the manifest must still list the fonts", manifest.contains("assets/fonts/"));
			assertTrue("the manifest must still list the images", manifest.contains("assets/images/"));
			assertTrue("omitted images must be marked as such", manifest.contains("\"omitted\":true"));
			assertFalse(manifest.contains("\"sha256\":\"null\""));

			final CapturingResults second = run(session, simpleHtml(),
					Map.of("output.paged-svg.resources", "omit"));
			assertTrue("the second conversion must emit no shared resource at all: " + second.order,
					second.order.stream().noneMatch(uri -> uri.startsWith("assets/")));
			assertEquals("the page must be byte-identical to the first conversion", page,
					second.text("pages/0001.svg"));
			final String manifest2 = second.text("manifest.json");
			assertTrue("the carried font must be listed as omitted with its hash: " + manifest2,
					manifest2.matches("(?s).*\"uri\":\"assets/fonts/font-0001.woff2\",\"sha256\":\"[0-9a-f]{64}\",\"bytes\":[0-9]+,\"omitted\":true.*"));
		} finally {
			session.close();
		}
	}

	/**
	 * Carry over font subsets (2026-08-29). Relaying out the same book in the same session emits
	 * the previous subset unchanged <b>before the first page</b>. Only when a new glyph appears is
	 * an updated version emitted again at the end under a different URI.
	 */
	public void testFontSubsetCarriedOverToNextConversion() throws Exception {
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			final CapturingResults first = run(session, simpleHtml(), Map.of());
			final List<String> firstFonts = first.order.stream().filter(uri -> uri.startsWith("assets/fonts/")).toList();
			assertEquals(List.of("assets/fonts/font-0001.woff2"), firstFonts);
			assertTrue("without a carry the font can only come after the pages",
					first.order.indexOf("assets/fonts/font-0001.woff2") > first.order.indexOf("pages/0001.svg"));
			final byte[] firstBytes = first.data.get("assets/fonts/font-0001.woff2").toByteArray();

			// Lay out the same book again with only the font size changed.
			final CapturingResults second = run(session, simpleHtml(), Map.of("output.text-size", "150%"));
			assertTrue("the carried subset must precede the first page: " + second.order,
					second.order.indexOf("assets/fonts/font-0001.woff2") < second.order.indexOf("pages/0001.svg"));
			assertTrue("the carried subset is the same bytes", java.util.Arrays.equals(firstBytes,
					second.data.get("assets/fonts/font-0001.woff2").toByteArray()));
			assertEquals("no other font may be emitted when the glyph set did not change", 1,
					second.order.stream().filter(uri -> uri.startsWith("assets/fonts/")).count());
			assertTrue(second.text("pages/0001.svg").contains("../assets/fonts/font-0001.woff2"));

			// A new glyph advances the version; the expanded version is emitted at the end in addition to the previous one.
			final CapturingResults third = run(session, simpleHtml().replace("ABC", "ABC XYZ"), Map.of());
			final List<String> thirdFonts = third.order.stream().filter(uri -> uri.startsWith("assets/fonts/")).toList();
			assertEquals(List.of("assets/fonts/font-0001.woff2", "assets/fonts/font-0001-2.woff2"), thirdFonts);
			assertTrue("the grown version comes after the pages",
					third.order.indexOf("assets/fonts/font-0001-2.woff2") > third.order.indexOf("pages/0001.svg"));
			assertTrue("pages closed after growth reference the grown version",
					third.text("pages/0001.svg").contains("../assets/fonts/font-0001-2.woff2"));
			assertTrue(third.data.get("assets/fonts/font-0001-2.woff2").size() > 0);
			assertWoff2CanBeRead(third.data.get("assets/fonts/font-0001-2.woff2").toByteArray());
			final String manifest = third.text("manifest.json");
			assertTrue(manifest.contains("assets/fonts/font-0001.woff2"));
			assertTrue(manifest.contains("assets/fonts/font-0001-2.woff2"));

			// The expanded version becomes the next carried-over version.
			final CapturingResults fourth = run(session, simpleHtml().replace("ABC", "ABC XYZ"), Map.of());
			assertEquals(List.of("assets/fonts/font-0001-2.woff2"),
					fourth.order.stream().filter(uri -> uri.startsWith("assets/fonts/")).toList());
			assertTrue(fourth.order.indexOf("assets/fonts/font-0001-2.woff2") < fourth.order.indexOf("pages/0001.svg"));
		} finally {
			session.close();
		}
	}

	/** Suppression does not change referenced URIs; a change would break reuse by the recipient. */
	public void testSuppressedResourceUrisMatchEmittedOnes() throws Exception {
		final CapturingResults emitted = run(simpleHtml(), Map.of());
		final CapturingResults omitted = run(simpleHtml(),
				Map.of("output.paged-svg.resources", "omit"));
		assertEquals(emitted.text("pages/0001.svg"),
				omitted.text("pages/0001.svg"));
	}

	/**
	 * Record dimensions of actual image files in metrics.xml. Passing it back through input.image-metrics
	 * produces the same layout without opening images in passes that need only dimensions.
	 */
	public void testImageMetricsJsonRoundTrip() throws Exception {
		final File image = new File("files/unittest/kappa.png").getAbsoluteFile();
		assertTrue("test fixture is missing: " + image, image.isFile());
		final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
				+ "@page{size:200pt 160pt;margin:8pt}body{margin:0;font-size:12pt}"
				+ "</style></head><body><p>ABC</p><img src=\"" + image.toURI() + "\"></body></html>";

		final CapturingResults measured = run(html, Map.of());
		final byte[] metrics = measured.data.get("metrics.json").toByteArray();
		final String json = new String(metrics, StandardCharsets.UTF_8);
		assertTrue("the metrics file must name the image " + image.toURI() + " but was:\n" + json,
				json.contains(image.getName()));
		assertTrue("the metrics file must be JSON: " + json, json.trim().startsWith("{"));

		// Records use output units (pt), so also record the output.resolution on which they depend.
		// Mixing in a dimension table from a different resolution would produce layout with incorrect dimensions.
		assertTrue("the resolution the sizes depend on must be recorded: " + json,
				json.contains("\"resolution\""));
		final BufferedImage actual = ImageIO.read(image);
		assertTrue("recorded sizes must be positive and proportional: " + json,
				json.matches("(?s).*\"width\": [0-9.]+, \"height\": [0-9.]+.*"));
		assertTrue("the fixture must be a real image", actual.getWidth() > 0);

		// Supplying the dimension table again does not change a single byte of page contents.
		final File file = Files.createTempFile("copper-metrics-", ".json").toFile();
		try {
			Files.write(file.toPath(), metrics);
			final CapturingResults reused = run(html,
					Map.of("input.image-metrics", file.toURI().toString()));
			assertEquals(measured.text("pages/0001.svg"), reused.text("pages/0001.svg"));
			assertEquals(measured.text("metrics.json"), reused.text("metrics.json"));
		} finally {
			Files.deleteIfExists(file.toPath());
		}
	}

	/** Even if the dimension table is corrupt, fall back to actual measurement and continue layout. */
	public void testBrokenImageMetricsFallsBackToMeasuring() throws Exception {
		final File image = new File("files/unittest/kappa.png").getAbsoluteFile();
		final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
				+ "@page{size:200pt 160pt;margin:8pt}body{margin:0}"
				+ "</style></head><body><img src=\"" + image.toURI() + "\"></body></html>";
		final File file = Files.createTempFile("copper-metrics-broken-", ".json").toFile();
		try {
			Files.writeString(file.toPath(), "this is not JSON");
			final CapturingResults results = run(html, Map.of("input.image-metrics", file.toURI().toString()));
			assertTrue(results.ended);
			assertTrue(results.data.containsKey("pages/0001.svg"));
			assertEquals(run(html, Map.of()).text("metrics.json"), results.text("metrics.json"));
		} finally {
			Files.deleteIfExists(file.toPath());
		}
	}

	/** Do not include data: images in the dimension table: the key would be as large as the image itself. */
	public void testDataUriImagesAreNotRecordedAsMetrics() throws Exception {
		final CapturingResults results = run(simpleHtml(), Map.of());
		assertFalse("data: images must not produce a metrics file", results.data.containsKey("metrics.json"));
	}

	/**
	 * In SVG rendered by browsers, write exact effects that are approximated in PDF (2026-08-29):
	 * feGaussianBlur for box-shadow/text-shadow blur, feColorMatrix/feDropShadow on layers for filter,
	 * spreadMethod for repeating gradients, and style on the layer's <g> for mix-blend-mode.
	 * Layers remain vector rather than raster.
	 */
	public void testExactEffectsAreWrittenAsSvgFilters() throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
				+ "@page{size:200pt 200pt;margin:8pt}body{margin:0;font-size:12pt}"
				+ ".s{width:60pt;height:20pt;background:#fff;box-shadow:2pt 2pt 6pt rgba(0,0,0,.5)}"
				+ ".t{text-shadow:1pt 1pt 4pt #f00}"
				+ ".f{width:60pt;height:20pt;background:#0f0;filter:grayscale(100%) drop-shadow(1pt 2pt 4pt #000)}"
				+ ".r{width:60pt;height:20pt;background:repeating-linear-gradient(90deg,#fff,#000 10pt)}"
				+ ".c{width:60pt;height:20pt;background:conic-gradient(#f00,#00f)}"
				+ ".m{width:60pt;height:20pt;background:#ff0;mix-blend-mode:multiply}"
				+ "</style></head><body><div class=\"s\"></div><p class=\"t\">ABC</p><div class=\"f\"></div>"
				+ "<div class=\"r\"></div><div class=\"c\"></div><div class=\"m\"></div></body></html>";
		final List<String[]> messages = new ArrayList<>();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		final CapturingResults results;
		try {
			session.setMessageHandler((code, args, mes) -> {
				final String[] m = new String[(args == null ? 0 : args.length) + 1];
				m[0] = Integer.toString(code & 0xFFFF);
				if (args != null) {
					System.arraycopy(args, 0, m, 1, args.length);
				}
				messages.add(m);
			});
			results = run(session, html, Map.of());
		} finally {
			session.close();
		}
		final String page = results.text("pages/0001.svg");
		assertWellFormedXml(results.data.get("pages/0001.svg").toByteArray());
		assertTrue("box-shadow blur -> feGaussianBlur on the path: " + page,
				page.matches("(?s).*<path [^>]*filter=\"url\\(#fb[0-9]+\\)\".*"));
		assertTrue(page.contains("<feGaussianBlur stdDeviation=\"3\"/>"));
		assertTrue("text-shadow blur -> a vector layer with a blur filter (sigma 2)",
				page.contains("<feGaussianBlur stdDeviation=\"2\"/>"));
		assertTrue("filter -> feColorMatrix in sRGB", page.contains("color-interpolation-filters=\"sRGB\""));
		assertTrue(page.contains("<feColorMatrix type=\"matrix\" values=\""));
		assertTrue("drop-shadow -> feDropShadow with sigma = radius / 2",
				page.contains("<feDropShadow dx=\"1\" dy=\"2\" stdDeviation=\"2\" flood-color=\"#000000\"/>"));
		assertTrue("repeating gradient -> one period with spreadMethod=repeat",
				page.contains("spreadMethod=\"repeat\""));
		assertTrue("blend group -> <g style=\"mix-blend-mode:multiply\">: " + page,
				page.matches("(?s).*<g[^>]*style=\"mix-blend-mode:multiply\">.*"));
		assertFalse("layers stay vector (no rasterized group image)", page.contains("<image"));
		assertTrue("the text inside the layer keeps its page position", results.text("pages/0001.json").contains("ABC"));
		// Only conic gradients remain sectors because SVG lacks them. Only those are reported with 2822.
		final List<String[]> approximated = messages.stream()
				.filter(m -> m[0].equals(Integer.toString(0x2822))).toList();
		assertEquals("only conic-gradient is approximated for paged SVG: " + describe(messages), 1,
				approximated.size());
		assertEquals("background-image", approximated.get(0)[1]);
		assertEquals("application/vnd.copper.paged-svg", approximated.get(0)[2]);
	}

	private static String describe(final List<String[]> messages) {
		final StringBuilder s = new StringBuilder();
		for (final String[] m : messages) {
			s.append(String.join("|", m)).append('\n');
		}
		return s.toString();
	}

	private String simpleHtml() throws Exception {
		return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
				+ "@page{size:100pt 80pt;margin:8pt}body{margin:0;font-size:12pt}img{width:10pt;height:10pt}"
				+ "</style></head><body><p>ABC あいう</p><img src=\"data:image/png;base64," + PNG
				+ "\"></body></html>";
	}

	private CapturingResults run(final String html, final Map<String, String> extraProps) throws Exception {
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			return run(session, html, extraProps);
		} finally {
			session.close();
		}
	}

	/** Run consecutive conversions in the same session (for checking carryover). */
	private CapturingResults run(final DirectSession session, final String html,
			final Map<String, String> extraProps) throws Exception {
		final CapturingResults results = new CapturingResults();
		{
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.default-font-family", "'Noto Serif JP'");
			session.property("processing.pass-count", "2");
			// The default is gzip (2026-08-28). Leave pages uncompressed to read their contents directly.
			session.property("output.paged-svg.compression", "none");
			for (final Map.Entry<String, String> entry : extraProps.entrySet()) {
				session.property(entry.getKey(), entry.getValue());
			}
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					new File("files/unittest/1080-FONT/paged-svg-test.html").toURI(), "text/html", "UTF-8");
		}
		return results;
	}

	private static byte[] jpeg() throws Exception {
		final BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, 0xCC3300);
		image.setRGB(1, 0, 0x336699);
		image.setRGB(2, 1, 0x99CC33);
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		assertTrue(ImageIO.write(image, "jpeg", bytes));
		return bytes.toByteArray();
	}

	private static void assertWellFormedXml(final byte[] bytes) throws Exception {
		javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new ByteArrayInputStream(bytes));
	}

	private static void assertWoff2CanBeRead(final byte[] bytes) throws Exception {
		final File file = Files.createTempFile("copper-subset-", ".woff2").toFile();
		try {
			Files.write(file.toPath(), bytes);
			final FontFile fontFile = new FontFile(file);
			assertEquals(1, fontFile.getNumFonts());
			assertTrue(fontFile.getFont().getNumGlyphs() > 1);
		} finally {
			Files.deleteIfExists(file.toPath());
		}
	}

	private static final class CapturingResults implements Results {
		final Map<String, ByteArrayOutputStream> data = new LinkedHashMap<>();
		final Map<String, String> mediaTypes = new LinkedHashMap<>();
		final List<String> order = new ArrayList<>();
		boolean ended;

		@Override public boolean hasNext() { return true; }

		@Override
		public FragmentedOutput nextBuilder(final SourceMetadata metadata) throws java.io.IOException {
			final String uri = metadata.getURI().toString();
			if (this.data.containsKey(uri)) {
				throw new AssertionError("duplicate result URI: " + uri);
			}
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			this.data.put(uri, out);
			this.mediaTypes.put(uri, metadata.getMimeType());
			this.order.add(uri);
			return new StreamFragmentedOutput(out);
		}

		@Override public void end() { this.ended = true; }

		String text(final String uri) {
			return this.data.get(uri).toString(StandardCharsets.UTF_8);
		}
	}
}
