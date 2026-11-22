package jp.cssj.test.unit.ioprops;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * A foreground watermark ({@code output.pdf.watermark.mode=front}) in PDF/X.
 *
 * <p>
 * The foreground watermark is an annotation the size of the page, and PDF/X allows annotations only outside the
 * bleed area: the conversion stopped with an IllegalStateException when the page was closed. In PDF/X the
 * watermark is painted as the last of the page's content instead.
 * </p>
 */
public class PdfxWatermarkTest extends TestCase {
	private static final String HTML = "<!doctype html><title>Watermark</title>"
			+ "<style>@page{size:200pt 200pt;margin:0}</style><p>KEEP ME</p>";

	/** An opaque red 8×8 PNG. */
	private static final String RED = "data:image/png;base64,"
			+ "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAEUlEQVR42mP4z8CAFTEMLQkAKP8/wc53yE8AAAAASUVORK5CYII=";

	/** PDF/X-4: the conversion finishes, the page has no annotation and the watermark covers the page. */
	public void testFrontWatermarkInPdfXIsPaintedOnThePage() throws Exception {
		final byte[] pdf = convert(props("output.pdf.version", "1.6X-4", "output.pdf.watermark.mode", "front",
				"output.pdf.watermark.uri", RED));
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertEquals(1, doc.getNumberOfPages());
			assertEquals("PDF/X allows no annotation over the page", 0, doc.getPage(0).getAnnotations().size());
			assertWatermarked(doc);
		}
	}

	/** PDF/X-1a (PDF 1.4, no transparency) takes the same path. */
	public void testFrontWatermarkInPdfX1a() throws Exception {
		final byte[] pdf = convert(props("output.pdf.version", "1.4X-1", "output.pdf.watermark.mode", "front",
				"output.pdf.watermark.uri", RED));
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertEquals(0, doc.getPage(0).getAnnotations().size());
			assertWatermarked(doc);
		}
	}

	/** Outside PDF/X the foreground watermark stays an annotation, which can be hidden on screen or in print. */
	public void testFrontWatermarkInPlainPdfIsAnAnnotation() throws Exception {
		final byte[] pdf = convert(props("output.pdf.watermark.mode", "front", "output.pdf.watermark.uri", RED));
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertEquals(1, doc.getPage(0).getAnnotations().size());
		}
	}

	/** The red watermark is tiled over the whole page, on top of the white paper. */
	private static void assertWatermarked(final PDDocument doc) throws Exception {
		final BufferedImage image = new PDFRenderer(doc).renderImageWithDPI(0, 72);
		for (final int[] point : new int[][] { { 20, 150 }, { 100, 100 }, { 180, 180 } }) {
			final int rgb = image.getRGB(point[0], point[1]);
			final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
			assertTrue("the watermark is not painted at " + point[0] + "," + point[1] + ": #"
					+ Integer.toHexString(rgb & 0xFFFFFF), r > 150 && g < 110 && b < 110);
		}
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private static byte[] convert(final Map<String, String> properties) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/pdf");
			for (final Map.Entry<String, String> e : properties.entrySet()) {
				session.property(e.getKey(), e.getValue());
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///watermark.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return out.toByteArray();
	}
}
