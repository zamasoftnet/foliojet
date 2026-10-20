package jp.cssj.test.unit.ioprops;

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
import java.util.regex.Pattern;

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
 * {@code output.color=gray} in page-split SVG (2026-10-20, a publishing request: until then only PDF honored it).
 * Text, backgrounds, gradient stops and effect filters are written as gray, and images are re-encoded as gray.
 */
public class PagedSvgGrayTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final Pattern HEX = Pattern.compile("#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})\\b");

	/** An opaque 60×40 PNG of red, green and blue stripes. */
	private static URI colorPng() throws Exception {
		final File file = new File("build/tmp/paged-svg-gray.png").getAbsoluteFile();
		file.getParentFile().mkdirs();
		final BufferedImage image = new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 40; ++y) {
			for (int x = 0; x < 60; ++x) {
				image.setRGB(x, y, x < 20 ? 0xFF0000 : x < 40 ? 0x00FF00 : 0x0000FF);
			}
		}
		ImageIO.write(image, "png", file);
		return file.toURI();
	}

	/** An opaque 40×40 gray PNG of sample 128 (decoded as TYPE_BYTE_GRAY). */
	private static URI grayPng() throws Exception {
		final File file = new File("build/tmp/paged-svg-gray-128.png").getAbsoluteFile();
		file.getParentFile().mkdirs();
		final BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_BYTE_GRAY);
		for (int y = 0; y < 40; ++y) {
			for (int x = 0; x < 40; ++x) {
				image.getRaster().setSample(x, y, 0, 128);
			}
		}
		ImageIO.write(image, "png", file);
		return file.toURI();
	}

	private static String html(final URI image) {
		return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
				+ "@page{size:200pt 200pt;margin:8pt}body{margin:0}img{width:60pt}"
				+ "</style></head><body>"
				+ "<p style=\"color:#c03020;background:#2050d0\">Red on blue</p>"
				+ "<div style=\"height:20pt;background:linear-gradient(90deg,#ff0000,#0000ff)\"></div>"
				+ "<div style=\"filter:sepia(1)\"><p>Sepia</p></div>"
				+ "<img src=\"" + image + "\"></body></html>";
	}

	public void testColorModeKeepsColorsByDefault() throws Exception {
		final CapturingResults r = convert(html(colorPng()), Map.of());
		assertTrue("色の指定が無ければ色のまま", hasChromaticHex(r.pages()));
	}

	public void testGrayWritesOnlyGrays() throws Exception {
		final CapturingResults r = convert(html(colorPng()), Map.of("output.color", "gray"));
		final String svg = r.pages();
		assertFalse("グレーでは #rrggbb がすべて無彩色: " + svg, hasChromaticHex(svg));
		// #c03020: 0.3*192 + 0.59*48 + 0.11*32 = 89.3
		assertTrue("字の色は PDF のグレーと同じ式: " + svg, svg.contains("#595959"));
		final var matrix = Pattern.compile("<feColorMatrix type=\"matrix\" values=\"([^\"]+)\"").matcher(svg);
		assertTrue("sepia の色行列が出ること: " + svg, matrix.find());
		final String[] v = matrix.group(1).split(" ");
		for (int c = 0; c < 5; ++c) {
			assertEquals("色行列の R・G・B の行が同じ(後ろにグレー): " + matrix.group(1), v[c], v[5 + c]);
			assertEquals("色行列の R・G・B の行が同じ(後ろにグレー): " + matrix.group(1), v[c], v[10 + c]);
		}
		final String asset = r.order.stream().filter(u -> u.startsWith("assets/images/")).findFirst().orElse(null);
		assertNotNull(r.order.toString(), asset);
		final BufferedImage image = ImageIO.read(new ByteArrayInputStream(r.data.get(asset).toByteArray()));
		assertNotNull(asset, image);
		for (int y = 0; y < image.getHeight(); y += 7) {
			for (int x = 0; x < image.getWidth(); x += 7) {
				final int rgb = image.getRGB(x, y);
				assertTrue("画像の画素が灰色: " + Integer.toHexString(rgb) + " at " + x + "," + y,
						((rgb >> 16) & 0xFF) == ((rgb >> 8) & 0xFF) && ((rgb >> 8) & 0xFF) == (rgb & 0xFF));
			}
		}
		final String manifest = r.text("manifest.json");
		assertFalse("元の色の画像を参照しない: " + manifest, manifest.contains("paged-svg-gray.png"));
	}

	/**
	 * An image that is already gray keeps its samples: through getRGB() the CS_GRAY gamma turned 128 into 188
	 * (codex review 2026-10-20).
	 */
	public void testGrayImageKeepsItsSamples() throws Exception {
		final String html = "<!DOCTYPE html><html><head><style>@page{size:100pt 100pt;margin:0}body{margin:0}"
				+ "img{width:40pt}</style></head><body><img src=\"" + grayPng() + "\"></body></html>";
		final CapturingResults r = convert(html, Map.of("output.color", "gray"));
		final String asset = r.order.stream().filter(u -> u.startsWith("assets/images/")).findFirst().orElse(null);
		assertNotNull(r.order.toString(), asset);
		final BufferedImage image = ImageIO.read(new ByteArrayInputStream(r.data.get(asset).toByteArray()));
		assertEquals("灰色の画像は 1 成分のまま", 1, image.getColorModel().getNumComponents());
		assertEquals("標本の値はそのまま", 128, image.getRaster().getSample(20, 20, 0));
	}

	private static boolean hasChromaticHex(final String svg) {
		final var m = HEX.matcher(svg);
		while (m.find()) {
			if (!m.group(1).equals(m.group(2)) || !m.group(2).equals(m.group(3))) {
				return true;
			}
		}
		return false;
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
					new File("files/unittest/paged-svg-gray.html").getAbsoluteFile().toURI(), "text/html", "UTF-8");
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
			final ByteArrayOutputStream out = this.data.get(uri);
			assertNotNull(uri + " must be emitted: " + this.order, out);
			return out.toString(StandardCharsets.UTF_8);
		}

		/** All page SVGs concatenated. */
		String pages() {
			final StringBuilder all = new StringBuilder();
			for (final String uri : this.order) {
				if (uri.endsWith(".svg")) {
					all.append(this.text(uri));
				}
			}
			assertTrue("ページの SVG が出ること: " + this.order, all.length() > 0);
			return all.toString();
		}
	}
}
