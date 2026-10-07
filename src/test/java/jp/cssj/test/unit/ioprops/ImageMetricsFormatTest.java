package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import junit.framework.TestCase;
import net.zamasoft.foliojet.ua.ImageMetricsCache;
import net.zamasoft.foliojet.ua.ImageMetricsIO;

/**
 * Tests for the image-dimension table format (JSON output and JSON/XML input)
 * (2026-08-28).
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

	/** Do not use a dimension table with a different resolution (the meaning of the dimensions changes). */
	public void testRejectsDifferentResolution() throws Exception {
		final byte[] json = ImageMetricsIO.write(sample(), 96);
		final ImageMetricsCache read = new ImageMetricsCache();
		assertEquals(0, ImageMetricsIO.read(new ByteArrayInputStream(json), read, 72));
		assertEquals(0, read.size());
	}
}
