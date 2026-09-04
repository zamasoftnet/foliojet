package jp.cssj.test.unit.ioprops;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

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
 * {@code transform-origin} と割合の {@code translate()} の基準箱が border box であることの試験
 * (css-transforms-1 §3。2026-09-03: margin 付きの箱で margin box 基準にずれていた欠陥の回帰)。
 *
 * <p>
 * 頁 100×80mm・余白 10mm、箱は {@code width:20mm;height:10mm;margin:20mm}(border box は頁の
 * 30..50 × 30..40mm)。描画した暗画素の外接矩形を mm で検査する。
 * </p>
 */
public class TransformOriginMarginTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	public void testScaleAboutCenter() throws Exception {
		assertBox("transform:scale(2)", 20, 60, 25, 45);
	}

	public void testScaleAboutTopLeft() throws Exception {
		assertBox("transform:scale(2);transform-origin:0 0", 30, 70, 30, 50);
	}

	public void testScaleAboutBottomRight() throws Exception {
		assertBox("transform:scale(2);transform-origin:100% 100%", 10, 50, 20, 40);
	}

	public void testRotateAboutCenter() throws Exception {
		// 20×10mm を中心 (40,35) で 90 度回す → 10×20mm
		assertBox("transform:rotate(90deg)", 35, 45, 25, 45);
	}

	public void testPercentageTranslateUsesBorderBox() throws Exception {
		// translate(50%, 100%) = (10mm, 10mm)
		assertBox("transform:translate(50%,100%)", 40, 60, 40, 50);
	}

	private static void assertBox(final String transform, final double left, final double right, final double top,
			final double bottom) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
				+ "@page{size:100mm 80mm;margin:10mm}body{margin:0}html{background:#fff}"
				+ ".b{width:20mm;height:10mm;margin:20mm;background:#000;" + transform + "}"
				+ "</style></head><body><div class=\"b\"></div></body></html>";
		final BufferedImage png = render(html);
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
		for (int y = 0; y < png.getHeight(); ++y) {
			for (int x = 0; x < png.getWidth(); ++x) {
				final int rgb = png.getRGB(x, y);
				final int luma = (((rgb >> 16) & 0xff) * 299 + ((rgb >> 8) & 0xff) * 587 + (rgb & 0xff) * 114) / 1000;
				if (luma < 128) {
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
			}
		}
		final double mm = 25.4 / 72;
		final String actual = String.format(java.util.Locale.ROOT, "%.1f..%.1f x %.1f..%.1f", minX * mm, (maxX + 1) * mm,
				minY * mm, (maxY + 1) * mm);
		assertEquals(transform + ": " + actual, Math.round(left), Math.round(minX * mm));
		assertEquals(transform + ": " + actual, Math.round(right), Math.round((maxX + 1) * mm));
		assertEquals(transform + ": " + actual, Math.round(top), Math.round(minY * mm));
		assertEquals(transform + ": " + actual, Math.round(bottom), Math.round((maxY + 1) * mm));
	}

	private static BufferedImage render(final String html) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/pdf");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///transform-origin-margin.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		try (PDDocument pdf = Loader.loadPDF(out.toByteArray())) {
			return new PDFRenderer(pdf).renderImageWithDPI(0, 72);
		}
	}
}
