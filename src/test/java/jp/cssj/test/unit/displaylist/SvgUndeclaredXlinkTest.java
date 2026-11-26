package jp.cssj.test.unit.displaylist;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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
 * An inline svg's {@code xlink:href} needs no declaration of the prefix in HTML (2026-11-26, batch 6 of the real-site
 * tests: the Tokyo Metropolitan Government's site writes {@code <use xlink:href="...#icon">} in svg elements without
 * {@code xmlns:xlink}). The attribute was left in no namespace, and Batik drew nothing for the use element.
 */
public class SvgUndeclaredXlinkTest extends TestCase {
	public void testUseWithUndeclaredXlinkHrefIsDrawn() throws Exception {
		final BufferedImage img = render("<svg width='80' height='80'><defs><rect id='r' width='80' height='80'"
				+ " fill='#f00'/></defs><use xlink:href='#r'></use></svg>");
		// 80px = 60pt from the top left corner of the page area (margin 0)
		assertTrue(Integer.toHexString(img.getRGB(30, 30)), red(img.getRGB(30, 30)));
	}

	public void testDeclaredXlinkHrefStillDrawn() throws Exception {
		final BufferedImage img = render("<svg xmlns:xlink='http://www.w3.org/1999/xlink' width='80' height='80'><defs>"
				+ "<rect id='r' width='80' height='80' fill='#f00'/></defs><use xlink:href='#r'/></svg>");
		assertTrue(Integer.toHexString(img.getRGB(30, 30)), red(img.getRGB(30, 30)));
	}

	private static boolean red(final int rgb) {
		return (rgb >> 16 & 0xff) > 200 && (rgb >> 8 & 0xff) < 60 && (rgb & 0xff) < 60;
	}

	private static BufferedImage render(final String svg) throws Exception {
		final File dir = new File("local/svg-undeclared-xlink-in");
		dir.mkdirs();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><style>@page{size:200pt 200pt;margin:0}"
				+ "body{margin:0}</style></head><body>" + svg + "</body></html>", StandardCharsets.UTF_8);
		final ByteArrayOutputStream pdf = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(pdf)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(pdf.toByteArray())) {
			return new PDFRenderer(doc).renderImage(0);
		}
	}
}
