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
