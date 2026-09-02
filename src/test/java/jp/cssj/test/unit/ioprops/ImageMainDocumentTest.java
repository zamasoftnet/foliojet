package jp.cssj.test.unit.ioprops;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
 * 画像を<b>主文書として</b>ストリームで流したときの試験です(2026-09-02)。
 *
 * <p>
 * cti.liの報告(2026-09-01): 8KiBを超えるPNGをCTIPの主文書にすると
 * {@code [12290] I/O error. Resetting to invalid mark}で0バイトになる。
 * 主文書はStreamSourceで届き、その{@code getInputStream()}は8KiBのmarkへ
 * resetする契約。EXIFの向きを覗く{@code peekOrientation}が256KiBを読んだ後に
 * 元の資源を読み直していたのが原因(向き1のときだけ包まずに返していた)。
 * 8,022Bは通り9,108Bで落ちる境目を、ここでは大小2つの画像で挟む。
 * </p>
 */
public class ImageMainDocumentTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 雑音の画素なので圧縮が効かず、大きさは辺の長さでほぼ決まる。 */
	private static byte[] png(final int size) throws Exception {
		final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		final Random random = new Random(size);
		for (int y = 0; y < size; ++y) {
			for (int x = 0; x < size; ++x) {
				image.setRGB(x, y, random.nextInt(0x1000000));
			}
		}
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	/** 8KiB未満(以前から通っていた側)。 */
	public void testSmallPngAsMainDocument() throws Exception {
		final byte[] png = png(40);
		assertTrue("the probe must stay under the 8KiB mark window: " + png.length, png.length < 8192);
		assertPdf(convert(png, "application/pdf"), "small PNG");
	}

	/** 8KiBを超える(報告の再現側)。以前は3002で0バイトだった。 */
	public void testLargePngAsMainDocument() throws Exception {
		final byte[] png = png(400);
		assertTrue("the probe must exceed the 8KiB mark window: " + png.length, png.length > 8192);
		assertPdf(convert(png, "application/pdf"), "large PNG");
	}

	/** 主文書の画像は、PDF以外の出力(単一SVG)でも通ること。 */
	public void testLargePngToSvg() throws Exception {
		final byte[] png = png(400);
		final CapturingResults r = convert(png, "image/svg+xml");
		final String svg = r.first();
		assertTrue("an SVG must be produced", svg.contains("<svg"));
	}

	private static void assertPdf(final CapturingResults r, final String what) {
		assertEquals(what + " must produce one result: " + r.order, 1, r.order.size());
		final byte[] bytes = r.data.get(r.order.get(0)).toByteArray();
		assertTrue(what + " must produce a PDF, got " + bytes.length + " bytes",
				bytes.length > 4 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("%PDF"));
	}

	private CapturingResults convert(final byte[] png, final String outputType) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", outputType);
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(png),
					URI.create("file:///photo.png"), "image/png", null);
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

		String first() {
			assertFalse("a result is expected", this.order.isEmpty());
			return this.data.get(this.order.get(0)).toString(StandardCharsets.UTF_8);
		}
	}
}
