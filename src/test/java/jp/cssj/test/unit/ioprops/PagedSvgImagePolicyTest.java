package jp.cssj.test.unit.ioprops;

import java.awt.Color;
import java.awt.Graphics2D;
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

import javax.imageio.ImageIO;

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
 * ページ分割SVGの画像の方針({@code output.paged-svg.image.*})と manifest の
 * 頁チェックサム({@code output.paged-svg.page-checksums})の試験です(2026-09-03、
 * cti.li の要望)。
 */
public class PagedSvgImagePolicyTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 透明部分の無い 600×400 の PNG(版面では 40pt 幅にしか描かれない)。 */
	private static URI opaquePng() throws Exception {
		final File file = new File("build/tmp/paged-svg-image-policy.png").getAbsoluteFile();
		file.getParentFile().mkdirs();
		final BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
		final Graphics2D g = image.createGraphics();
		try {
			for (int y = 0; y < 400; y += 20) {
				for (int x = 0; x < 600; x += 20) {
					g.setColor(new Color((x * 255 / 600), (y * 255 / 400), 128));
					g.fillRect(x, y, 20, 20);
				}
			}
		} finally {
			g.dispose();
		}
		ImageIO.write(image, "png", file);
		return file.toURI();
	}

	private static String html(final URI image) {
		return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
				+ "@page{size:120pt 90pt;margin:8pt}body{margin:0}img{width:40pt}"
				+ "</style></head><body><img src=\"" + image + "\"></body></html>";
	}

	public void testDefaultKeepsTheOriginalPng() throws Exception {
		final String manifest = convert(html(opaquePng()), Map.of()).text("manifest.json");
		assertTrue(manifest, manifest.contains("\"mediaType\":\"image/png\",\"width\":600,\"height\":400"));
		assertTrue("page checksums are written by default: " + manifest, manifest.contains("\"svgSha256\""));
	}

	public void testJpegRecompressesOpaqueRasters() throws Exception {
		final CapturingResults r = convert(html(opaquePng()), Map.of("output.paged-svg.image.compression", "jpeg"));
		final String manifest = r.text("manifest.json");
		assertTrue(manifest, manifest.contains("\"mediaType\":\"image/jpeg\",\"width\":600,\"height\":400"));
		final String asset = r.order.stream().filter(u -> u.startsWith("assets/images/")).findFirst().orElse(null);
		assertNotNull(r.order.toString(), asset);
		assertTrue("the shared image must be a .jpg: " + asset, asset.endsWith(".jpg"));
		final byte[] bytes = r.data.get(asset).toByteArray();
		assertTrue("JPEG magic", (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8);
	}

	public void testMaxWidthDownscales() throws Exception {
		final String manifest = convert(html(opaquePng()), Map.of("output.paged-svg.image.max-width", "150"))
				.text("manifest.json");
		assertTrue(manifest, manifest.contains("\"mediaType\":\"image/png\",\"width\":150,\"height\":100"));
	}

	public void testPageChecksumsCanBeOmitted() throws Exception {
		final String manifest = convert(html(opaquePng()), Map.of("output.paged-svg.page-checksums", "false"))
				.text("manifest.json");
		assertFalse(manifest, manifest.contains("svgSha256") || manifest.contains("dataSha256"));
		// 共有資源の sha256 は残る(URI と同一性の鍵)
		assertTrue(manifest, manifest.contains("\"sha256\":\""));
	}

	private CapturingResults convert(final String html, final Map<String, String> props) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.paged-svg.compression", "none");
			for (final var e : props.entrySet()) {
				session.property(e.getKey(), e.getValue());
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					new File("files/unittest/paged-svg-image-policy.html").getAbsoluteFile().toURI(), "text/html",
					"UTF-8");
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
			// 何もしない
		}

		String text(final String uri) {
			final ByteArrayOutputStream out = this.data.get(uri);
			assertNotNull(uri + " must be emitted: " + this.order, out);
			return out.toString(StandardCharsets.UTF_8);
		}
	}
}
