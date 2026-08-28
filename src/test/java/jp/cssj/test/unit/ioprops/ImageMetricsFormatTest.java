package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import junit.framework.TestCase;
import net.zamasoft.foliojet.ua.ImageMetricsCache;
import net.zamasoft.foliojet.ua.ImageMetricsIO;

/**
 * 画像寸法表の形式(JSON書き出し・JSON/XML両対応の読み取り)の検査です
 * (2026-08-28)。
 */
public class ImageMetricsFormatTest extends TestCase {

	private static ImageMetricsCache sample() {
		final ImageMetricsCache cache = new ImageMetricsCache();
		cache.putSize("https://example.com/a b.png?q=1&r=\"x\"", 900, 600.5);
		cache.putAsset("https://example.com/a b.png?q=1&r=\"x\"",
				new ImageMetricsCache.Asset("abc123", "image/png", "png", 1200, 800));
		cache.putSize("https://example.com/日本語.jpg", 100, 50);
		return cache;
	}

	public void testWritesJsonAndReadsItBack() throws Exception {
		final byte[] json = ImageMetricsIO.write(sample(), 96);
		final String text = new String(json, StandardCharsets.UTF_8);
		assertTrue("JSONで書くべき: " + text, text.trim().startsWith("{"));
		assertTrue("資源同一性を書くべき: " + text, text.contains("\"sha256\": \"abc123\""));

		final ImageMetricsCache read = new ImageMetricsCache();
		assertEquals(2, ImageMetricsIO.read(new ByteArrayInputStream(json), read, 96));
		final String key = "https://example.com/a b.png?q=1&r=\"x\"";
		assertNotNull("引用符やクエリを含むURIも往復すべき", read.get(key));
		assertEquals(900.0, read.get(key).getWidth(), 1e-9);
		assertEquals(600.5, read.get(key).getHeight(), 1e-9);
		final ImageMetricsCache.Asset asset = read.getAsset(key);
		assertNotNull(asset);
		assertEquals("abc123", asset.sha256());
		assertEquals("image/png", asset.mediaType());
		assertEquals("png", asset.extension());
		assertEquals(1200, asset.pixelWidth());
		assertEquals(800, asset.pixelHeight());
		assertNotNull("非ASCIIのURIも往復すべき", read.get("https://example.com/日本語.jpg"));
	}

	/** 解像度が違う寸法表は使わない(寸法の意味が変わるため)。 */
	public void testRejectsDifferentResolution() throws Exception {
		final byte[] json = ImageMetricsIO.write(sample(), 96);
		final ImageMetricsCache read = new ImageMetricsCache();
		assertEquals(0, ImageMetricsIO.read(new ByteArrayInputStream(json), read, 72));
		assertEquals(0, read.size());
	}

	/** 4.0.0開発中に出していたXMLの寸法表も読めること。 */
	public void testStillReadsLegacyXml() throws Exception {
		final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<image-metrics version=\"1\" resolution=\"96\">\n"
				+ "  <image uri=\"https://example.com/a.png\" width=\"900\" height=\"600\""
				+ " sha256=\"abc123\" media-type=\"image/png\" extension=\"png\""
				+ " pixel-width=\"1200\" pixel-height=\"800\"/>\n"
				+ "</image-metrics>\n";
		final ImageMetricsCache read = new ImageMetricsCache();
		assertEquals(1, ImageMetricsIO.read(
				new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), read, 96));
		assertNotNull(read.get("https://example.com/a.png"));
		assertNotNull("XMLの資源同一性も読むべき", read.getAsset("https://example.com/a.png"));
	}
}
