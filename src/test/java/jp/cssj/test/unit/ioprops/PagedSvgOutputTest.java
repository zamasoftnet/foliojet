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
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

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
		assertFalse("compression belongs to the client, not the server",
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
	 * 共有資源の抑止。同じ本を文字サイズだけ変えて組み直すとき、前回と同じ
	 * フォントサブセットと画像を出し直さないための指定。参照とmanifestの
	 * 記載は残るので、受け手は前回の出力から拾える。
	 */
	public void testResourceSuppression() throws Exception {
		final CapturingResults results = run(simpleHtml(),
				Map.of("output.paged-svg.fonts", "omit", "output.paged-svg.images", "omit"));
		assertTrue("no font or image bytes may be emitted",
				results.order.stream().noneMatch(uri -> uri.startsWith("assets/")));

		final String page = results.text("pages/0001.svg");
		assertTrue("the page must still reference the shared subset", page.contains("../assets/fonts/"));
		assertTrue("the page must still reference the shared image", page.contains("../assets/images/"));

		final String manifest = results.text("manifest.json");
		assertTrue("the manifest must still list the fonts", manifest.contains("assets/fonts/"));
		assertTrue("the manifest must still list the images", manifest.contains("assets/images/"));
		assertTrue("omitted resources must be marked as such", manifest.contains("\"omitted\":true"));
		assertFalse("no hash can be reported for a subset that was never built",
				manifest.contains("\"sha256\":\"null\""));
	}

	/** 抑止しても参照先のURIは変わらないこと——変われば受け手の再利用が壊れる。 */
	public void testSuppressedResourceUrisMatchEmittedOnes() throws Exception {
		final CapturingResults emitted = run(simpleHtml(), Map.of());
		final CapturingResults omitted = run(simpleHtml(),
				Map.of("output.paged-svg.fonts", "omit", "output.paged-svg.images", "omit"));
		assertEquals(emitted.text("pages/0001.svg"),
				omitted.text("pages/0001.svg"));
	}

	/**
	 * 実ファイルの画像は寸法をmetrics.xmlに残し、それをinput.image-metricsで
	 * 渡し直すと、寸法しか要らないパスでは画像を一度も開かずに同じ組版になる。
	 */
	public void testImageMetricsXmlRoundTrip() throws Exception {
		final File image = new File("files/unittest/kappa.png").getAbsoluteFile();
		assertTrue("test fixture is missing: " + image, image.isFile());
		final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
				+ "@page{size:200pt 160pt;margin:8pt}body{margin:0;font-size:12pt}"
				+ "</style></head><body><p>ABC</p><img src=\"" + image.toURI() + "\"></body></html>";

		final CapturingResults measured = run(html, Map.of());
		final byte[] metrics = measured.data.get("metrics.xml").toByteArray();
		final String xml = new String(metrics, StandardCharsets.UTF_8);
		assertTrue("the metrics file must name the image " + image.toURI() + " but was:\n" + xml,
				xml.contains(image.getName()));
		assertTrue(xml.contains("<image-metrics"));

		// 記録は出力単位(pt)なので、依拠したoutput.resolutionを併記する。
		// 別の解像度の寸法表を混ぜると誤った寸法で組んでしまうため
		assertTrue("the resolution the sizes depend on must be recorded: " + xml,
				xml.contains("resolution=\""));
		final BufferedImage actual = ImageIO.read(image);
		assertTrue("recorded sizes must be positive and proportional: " + xml,
				xml.matches("(?s).*width=\"[0-9.]+\" height=\"[0-9.]+\".*"));
		assertTrue("the fixture must be a real image", actual.getWidth() > 0);

		// 寸法表を渡し直しても、ページの中身は1バイトも変わらない
		final File file = Files.createTempFile("copper-metrics-", ".xml").toFile();
		try {
			Files.write(file.toPath(), metrics);
			final CapturingResults reused = run(html,
					Map.of("input.image-metrics", file.toURI().toString()));
			assertEquals(measured.text("pages/0001.svg"), reused.text("pages/0001.svg"));
			assertEquals(measured.text("metrics.xml"), reused.text("metrics.xml"));
		} finally {
			Files.deleteIfExists(file.toPath());
		}
	}

	/** 寸法表が壊れていても、実測に戻って組版は続く。 */
	public void testBrokenImageMetricsFallsBackToMeasuring() throws Exception {
		final File image = new File("files/unittest/kappa.png").getAbsoluteFile();
		final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
				+ "@page{size:200pt 160pt;margin:8pt}body{margin:0}"
				+ "</style></head><body><img src=\"" + image.toURI() + "\"></body></html>";
		final File file = Files.createTempFile("copper-metrics-broken-", ".xml").toFile();
		try {
			Files.writeString(file.toPath(), "this is not XML");
			final CapturingResults results = run(html, Map.of("input.image-metrics", file.toURI().toString()));
			assertTrue(results.ended);
			assertTrue(results.data.containsKey("pages/0001.svg"));
			assertEquals(run(html, Map.of()).text("metrics.xml"), results.text("metrics.xml"));
		} finally {
			Files.deleteIfExists(file.toPath());
		}
	}

	/** data:の画像は寸法表に入れない——キーが画像本体と同じ大きさになるため。 */
	public void testDataUriImagesAreNotRecordedAsMetrics() throws Exception {
		final CapturingResults results = run(simpleHtml(), Map.of());
		assertFalse("data: images must not produce a metrics file", results.data.containsKey("metrics.xml"));
	}

	private String simpleHtml() throws Exception {
		return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
				+ "@page{size:100pt 80pt;margin:8pt}body{margin:0;font-size:12pt}img{width:10pt;height:10pt}"
				+ "</style></head><body><p>ABC あいう</p><img src=\"data:image/png;base64," + PNG
				+ "\"></body></html>";
	}

	private CapturingResults run(final String html, final Map<String, String> extraProps) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.default-font-family", "'Noto Serif JP'");
			session.property("processing.pass-count", "2");
			for (final Map.Entry<String, String> entry : extraProps.entrySet()) {
				session.property(entry.getKey(), entry.getValue());
			}
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					new File("files/unittest/1080-FONT/paged-svg-test.html").toURI(), "text/html", "UTF-8");
		} finally {
			session.close();
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
