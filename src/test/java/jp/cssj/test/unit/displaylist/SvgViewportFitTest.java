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
 * An SVG with a viewBox in a box of another ratio is laid out anew in the box by its preserveAspectRatio, as Chrome
 * draws it, not stretched (2026-11-26, batch 6 of the real-site tests: BBC Sport's search icon, a 32 x 32 viewBox
 * drawn as a tall ellipse). Each case draws a red circle of a 32 x 32 viewBox in a 20 x 200pt box at the page's top
 * left: kept round, it lies around y = 100pt (xMidYMid) or 10pt (xMinYMin); stretched, it reaches y = 20pt too.
 */
public class SvgViewportFitTest extends TestCase {
	private static final String CIRCLE = "<circle cx='16' cy='16' r='14' fill='#f00'/>";

	private static final String DATA = "data:image/svg+xml;utf8,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22"
			+ " viewBox=%220 0 32 32%22%3E%3Ccircle cx=%2216%22 cy=%2216%22 r=%2214%22 fill=%22%23f00%22/%3E%3C/svg%3E";

	public void testInlineSvgStaysRound() throws Exception {
		final BufferedImage img = render("<svg viewBox='0 0 32 32' style='display:block;width:20pt;height:200pt'>"
				+ CIRCLE + "</svg>");
		assertTrue("中央に円が無い", red(img.getRGB(10, 100)));
		assertFalse("縦に引き伸ばされた", red(img.getRGB(10, 20)));
	}

	public void testImgStaysRound() throws Exception {
		final BufferedImage img = render("<img src='" + DATA + "' style='display:block;width:20pt;height:200pt'>");
		assertTrue("中央に円が無い", red(img.getRGB(10, 100)));
		assertFalse("縦に引き伸ばされた", red(img.getRGB(10, 20)));
	}

	public void testBackgroundStaysRound() throws Exception {
		final BufferedImage img = render("<div style='width:20pt;height:200pt;background:url(\"" + DATA
				+ "\") no-repeat;background-size:20pt 200pt'></div>");
		assertTrue("中央に円が無い", red(img.getRGB(10, 100)));
		assertFalse("縦に引き伸ばされた", red(img.getRGB(10, 20)));
	}

	public void testAlignmentIsKept() throws Exception {
		final BufferedImage img = render("<svg viewBox='0 0 32 32' preserveAspectRatio='xMinYMin meet'"
				+ " style='display:block;width:20pt;height:200pt'>" + CIRCLE + "</svg>");
		assertTrue("上に円が無い", red(img.getRGB(10, 10)));
		assertFalse("中央に円がある", red(img.getRGB(10, 100)));
	}

	public void testNoneStillStretches() throws Exception {
		final BufferedImage img = render("<svg viewBox='0 0 32 32' preserveAspectRatio='none'"
				+ " style='display:block;width:20pt;height:200pt'>" + CIRCLE + "</svg>");
		assertTrue("引き伸ばされていない", red(img.getRGB(10, 20)));
	}

	private static boolean red(final int rgb) {
		return (rgb >> 16 & 0xff) > 200 && (rgb >> 8 & 0xff) < 60 && (rgb & 0xff) < 60;
	}

	private static BufferedImage render(final String body) throws Exception {
		final File dir = new File("local/svg-viewport-fit-in");
		dir.mkdirs();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><style>@page{size:200pt 300pt;margin:0}"
				+ "body{margin:0}</style></head><body>" + body + "</body></html>", StandardCharsets.UTF_8);
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
