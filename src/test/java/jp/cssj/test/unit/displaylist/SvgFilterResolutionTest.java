package jp.cssj.test.unit.displaylist;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * An SVG filter is rasterized at the PDF's filter resolution, output.pdf.filter-resolution (300 dpi), at the size the
 * SVG is drawn (2026-11-26, pdfg2d SVGImage.deviceScale). It was one pixel per SVG unit: the vertical newspaper pages
 * of a local film project, 1.86pt a unit, had their filtered text at 39 dpi (copperpdf4
 * docs/history/2026-11-25-realweb-test.md section 11).
 */
public class SvgFilterResolutionTest extends TestCase {
	private static byte[] pdf(final String html) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.property-pi", "true");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///svg-filter-resolution.html"), "text/html", null);
		} finally {
			session.close();
		}
		return out.toByteArray();
	}

	/** The pixels of all the images the page draws (Batik draws a filter's result tile by tile). */
	private static long imagePixels(final String html) throws Exception {
		try (PDDocument doc = Loader.loadPDF(pdf(html))) {
			return pixels(doc.getPage(0).getResources());
		}
	}

	private static long pixels(final PDResources r) throws Exception {
		long pixels = 0;
		for (final COSName n : r.getXObjectNames()) {
			final PDXObject xo = r.getXObject(n);
			if (xo instanceof PDImageXObject image) {
				pixels += (long) image.getWidth() * image.getHeight();
			} else if (xo instanceof PDFormXObject form && form.getResources() != null) {
				pixels += pixels(form.getResources());
			}
		}
		return pixels;
	}

	/**
	 * A blurred square 80 units wide in an SVG of 100 units drawn at 300pt (3pt a unit): the filter region (the square
	 * and 10% on each side, 96 units, 288pt) comes out about 1200 x 1200 pixels at 300 dpi.
	 */
	public void testBlurAtFilterResolution() throws Exception {
		final long pixels = imagePixels("""
				<!DOCTYPE html><html><head><meta charset="UTF-8"/>
				<style>@page{size:320pt 320pt;margin:0} body{margin:0}</style></head><body>
				<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400" viewBox="0 0 100 100">
				<filter id="f"><feGaussianBlur stdDeviation="2"/></filter>
				<rect x="10" y="10" width="80" height="80" fill="blue" filter="url(#f)"/></svg>
				</body></html>
				""");
		assertTrue("image pixels " + pixels, pixels > 900L * 900 && pixels < 1500L * 1500);
	}

	/**
	 * A filter region larger than an SVG with overflow: visible (userSpaceOnUse) is rasterized beyond the image's box,
	 * as Chrome draws it, at the filter resolution: 400 x 400 units of a 75pt SVG, 3.125 pixels a unit, about 1.56M
	 * pixels.
	 */
	public void testFilterRegionLargerThanTheImage() throws Exception {
		final long pixels = imagePixels("""
				<!DOCTYPE html><html><head><meta charset="UTF-8"/>
				<style>@page{size:320pt 320pt;margin:0} body{margin:0}</style></head><body>
				<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100" overflow="visible"
				 style="margin:150px"><filter id="f" filterUnits="userSpaceOnUse" x="-150" y="-150" width="400" height="400">
				<feFlood flood-color="red"/></filter>
				<rect x="10" y="10" width="80" height="80" fill="blue" filter="url(#f)"/></svg>
				</body></html>
				""");
		assertTrue("image pixels " + pixels, pixels > 1_400_000L && pixels < 1_700_000L);
	}

	/**
	 * A filter region far larger than the image is rasterized no further than the pixel cap (16M) allows around it
	 * (codex 2026-11-26: a 2000-unit region of a 100-unit SVG at 3pt a unit came to 625M pixels).
	 */
	public void testFilterRegionWithinThePixelCap() throws Exception {
		final long pixels = imagePixels("""
				<!DOCTYPE html><html><head><meta charset="UTF-8"/>
				<style>@page{size:320pt 320pt;margin:0} body{margin:0}</style></head><body>
				<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400" viewBox="0 0 100 100" overflow="visible">
				<filter id="f" filterUnits="userSpaceOnUse" x="-950" y="-950" width="2000" height="2000">
				<feFlood flood-color="red"/></filter>
				<rect x="10" y="10" width="80" height="80" fill="blue" filter="url(#f)"/></svg>
				</body></html>
				""");
		assertTrue("image pixels " + pixels, pixels > 1_000_000L && pixels <= 16L * 1024 * 1024 + 100_000);
	}

	/**
	 * Vector content wholly outside an SVG with overflow: visible is drawn, as Chrome draws it (codex 2026-11-26: with
	 * the image's box as the clip Graphics2D reports, Batik left out every node whose bounds missed it).
	 */
	public void testVectorContentOutsideAnOverflowVisibleSvg() throws Exception {
		final BufferedImage image;
		try (PDDocument doc = Loader.loadPDF(pdf("""
				<!DOCTYPE html><html><head><meta charset="UTF-8"/>
				<style>@page{size:300pt 100pt;margin:0} body{margin:0}</style></head><body>
				<svg xmlns="http://www.w3.org/2000/svg" width="100pt" height="100pt" viewBox="0 0 100 100"
				 style="display:block;overflow:visible"><rect x="150" y="40" width="40" height="20" fill="red"/></svg>
				</body></html>
				"""))) {
			image = new PDFRenderer(doc).renderImageWithDPI(0, 72);
		}
		final int rgb = image.getRGB(170, 50);
		assertEquals("the red rectangle at x 150-190", 0xFF0000, rgb & 0xFFFFFF);
	}
}
