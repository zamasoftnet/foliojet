package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Fix the requirement to write resolution in image output and round pixel counts to the nearest integer
 * (2026-10-04, report from publishing).
 *
 * <p>
 * Without resolution, the print shop cannot determine dimensions (Seihon Chokuso covers are submitted as
 * 300–350 dpi images). Pixel counts were truncated, turning 50 mm×350 dpi=688.98 into 688 pixels.
 * </p>
 */
public class ImageResolutionTest extends TestCase {
	private static byte[] convert(final String type) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>@page{size:50mm 30mm;margin:0}"
				+ "</style></head><body><p>x</p></body></html>";
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", type);
			session.property("output.image.resolution", "350");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///dpi.html"), "text/html", null);
		} finally {
			session.close();
		}
		return out.toByteArray();
	}

	/** {width, height, native image metadata tree}. */
	private static Object[] read(final byte[] bytes) throws Exception {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			final ImageReader reader = readers.next();
			try {
				reader.setInput(in);
				final IIOMetadata metadata = reader.getImageMetadata(0);
				return new Object[] { reader.getWidth(0), reader.getHeight(0),
						metadata.getAsTree(metadata.getNativeMetadataFormatName()) };
			} finally {
				reader.dispose();
			}
		}
	}

	private static Element find(final Node node, final String name) {
		if (node instanceof Element e && name.equals(e.getNodeName())) {
			return e;
		}
		for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
			final Element found = find(child, name);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	public void testPngHasPhysAndRoundedSize() throws Exception {
		final Object[] png = read(convert("image/png"));
		assertEquals("50mm at 350dpi = 688.98", 689, png[0]);
		assertEquals("30mm at 350dpi = 413.39", 413, png[1]);
		final Element phys = find((Node) png[2], "pHYs");
		assertNotNull("pHYs", phys);
		assertEquals("meter", phys.getAttribute("unitSpecifier"));
		assertEquals(Long.toString(Math.round(350 / 0.0254)), phys.getAttribute("pixelsPerUnitXAxis"));
	}

	public void testJpegHasJfifDensity() throws Exception {
		final Object[] jpeg = read(convert("image/jpeg"));
		assertEquals(689, jpeg[0]);
		final Element jfif = find((Node) jpeg[2], "app0JFIF");
		assertNotNull("app0JFIF", jfif);
		assertEquals("1", jfif.getAttribute("resUnits"));
		assertEquals("350", jfif.getAttribute("Xdensity"));
		assertEquals("350", jfif.getAttribute("Ydensity"));
	}
}
