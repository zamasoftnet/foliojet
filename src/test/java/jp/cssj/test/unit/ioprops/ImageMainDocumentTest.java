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
 * Tests for streaming an image <b>as the main document</b> (2026-09-02).
 *
 * <p>
 * cti.li report (2026-09-01): a PNG larger than 8 KiB as the CTIP main document produced 0 bytes
 * with {@code [12290] I/O error. Resetting to invalid mark}.
 * The main document arrives as a StreamSource whose {@code getInputStream()} resets to an 8 KiB mark.
 * The cause was that {@code peekOrientation}, which peeks at EXIF orientation, read 256 KiB and then
 * reread the original resource (it returned without wrapping only for orientation 1).
 * Two images, large and small, bracket the boundary where 8,022 B passed but 9,108 B failed.
 * </p>
 */
public class ImageMainDocumentTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** Noisy pixels compress poorly, so the size is largely determined by the side length. */
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

	/** Under 8 KiB (the side that already passed). */
	public void testSmallPngAsMainDocument() throws Exception {
		final byte[] png = png(40);
		assertTrue("the probe must stay under the 8KiB mark window: " + png.length, png.length < 8192);
		assertPdf(convert(png, "application/pdf"), "small PNG");
	}

	/** Over 8 KiB (reproduces the report). Previously produced 0 bytes with 3002. */
	public void testLargePngAsMainDocument() throws Exception {
		final byte[] png = png(400);
		assertTrue("the probe must exceed the 8KiB mark window: " + png.length, png.length > 8192);
		assertPdf(convert(png, "application/pdf"), "large PNG");
	}

	/** An image main document also works with non-PDF output (single SVG). */
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
			// Do nothing.
		}

		String first() {
			assertFalse("a result is expected", this.order.isEmpty());
			return this.data.get(this.order.get(0)).toString(StandardCharsets.UTF_8);
		}
	}
}
