package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import junit.framework.TestCase;
import net.zamasoft.foliojet.ua.ImageMetricsCache;
import net.zamasoft.foliojet.ua.ImageMetricsXML;

/** 画像寸法表(input.image-metrics)の読み書きの単体試験です。 */
public class ImageMetricsXMLTest extends TestCase {

	public void testRoundTrip() throws Exception {
		final ImageMetricsCache written = new ImageMetricsCache();
		written.putSize("file:/a/one.png", 1200, 800);
		written.putSize("https://example.com/two.jpg", 640, 480);

		final byte[] xml = ImageMetricsXML.write(written);
		final ImageMetricsCache read = new ImageMetricsCache();
		assertEquals(2, ImageMetricsXML.read(new ByteArrayInputStream(xml), read));

		assertEquals(2, read.size());
		assertEquals(1200.0, read.get("file:/a/one.png").getWidth(), 0.0);
		assertEquals(800.0, read.get("file:/a/one.png").getHeight(), 0.0);
		assertEquals(640.0, read.get("https://example.com/two.jpg").getWidth(), 0.0);
		assertEquals(480.0, read.get("https://example.com/two.jpg").getHeight(), 0.0);
	}

	/** URI順に並べるので、同じ内容からは必ず同じバイト列になる。 */
	public void testWriteIsDeterministic() throws Exception {
		final ImageMetricsCache a = new ImageMetricsCache();
		a.putSize("file:/z.png", 10, 20);
		a.putSize("file:/a.png", 30, 40);
		final ImageMetricsCache b = new ImageMetricsCache();
		b.putSize("file:/a.png", 30, 40);
		b.putSize("file:/z.png", 10, 20);
		assertEquals(new String(ImageMetricsXML.write(a), StandardCharsets.UTF_8),
				new String(ImageMetricsXML.write(b), StandardCharsets.UTF_8));
	}

	public void testUriIsEscaped() throws Exception {
		final ImageMetricsCache cache = new ImageMetricsCache();
		cache.putSize("https://example.com/a?x=1&y=2", 5, 6);
		final byte[] xml = ImageMetricsXML.write(cache);
		assertTrue(new String(xml, StandardCharsets.UTF_8).contains("&amp;"));

		final ImageMetricsCache read = new ImageMetricsCache();
		ImageMetricsXML.read(new ByteArrayInputStream(xml), read);
		assertNotNull(read.get("https://example.com/a?x=1&y=2"));
	}

	/** 実測済みの値のほうが確かなので、読み込みは上書きしない。 */
	public void testReadDoesNotOverwriteMeasuredValues() throws Exception {
		final ImageMetricsCache cache = new ImageMetricsCache();
		cache.putSize("file:/a.png", 100, 200);
		final String xml = "<image-metrics version=\"1\"><image uri=\"file:/a.png\" width=\"1\" height=\"2\"/>"
				+ "</image-metrics>";
		assertEquals(0, ImageMetricsXML.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), cache));
		assertEquals(100.0, cache.get("file:/a.png").getWidth(), 0.0);
	}

	/** 欠けた属性・0以下・数値でない値は黙って捨てる。組版を止める理由にはならない。 */
	public void testMalformedEntriesAreSkipped() throws Exception {
		final String xml = "<image-metrics version=\"1\">"
				+ "<image uri=\"file:/no-size.png\"/>"
				+ "<image uri=\"file:/zero.png\" width=\"0\" height=\"10\"/>"
				+ "<image uri=\"file:/negative.png\" width=\"-5\" height=\"10\"/>"
				+ "<image uri=\"file:/nan.png\" width=\"abc\" height=\"10\"/>"
				+ "<image width=\"10\" height=\"10\"/>"
				+ "<image uri=\"file:/good.png\" width=\"10\" height=\"10\"/>"
				+ "</image-metrics>";
		final ImageMetricsCache cache = new ImageMetricsCache();
		assertEquals(1, ImageMetricsXML.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), cache));
		assertEquals(1, cache.size());
		assertNotNull(cache.get("file:/good.png"));
	}

	/** 外部実体を読まないこと。寸法表は信用できない場所から来うる。 */
	public void testDoctypeIsRejected() throws Exception {
		final String xml = "<!DOCTYPE image-metrics [<!ENTITY x \"y\">]><image-metrics/>";
		try {
			ImageMetricsXML.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
					new ImageMetricsCache());
			fail("a DOCTYPE declaration must be rejected");
		} catch (final org.xml.sax.SAXException expected) {
			// 期待どおり
		}
	}

	public void testEmptyCacheWritesValidXml() throws Exception {
		final byte[] xml = ImageMetricsXML.write(new ImageMetricsCache());
		final ImageMetricsCache read = new ImageMetricsCache();
		assertEquals(0, ImageMetricsXML.read(new ByteArrayInputStream(xml), read));
		assertEquals(0, read.size());
	}
}
