package jp.cssj.test.unit.ioprops;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.Results;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** {@code border-image}のグラデーション源を9分割して描く試験です。 */
public class BorderImageGradientTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final File DOCUMENT = new File("files/unittest/0310-border/border-image-gradient.html");
	private static final Pattern MATRIX = Pattern.compile(
			"matrix\\(([-+0-9.eE]+)[ ,]+([-+0-9.eE]+)[ ,]+([-+0-9.eE]+)[ ,]+([-+0-9.eE]+)[ ,]+([-+0-9.eE]+)[ ,]+([-+0-9.eE]+)\\)");

	public void testPdfUsesVirtualSourceAcrossNineSlices() throws Exception {
		try (PDDocument pdf = Loader.loadPDF(this.convertSingle("application/pdf"))) {
			assertTrue("the fixture must produce two pages", pdf.getNumberOfPages() >= 2);
			final BufferedImage page = new PDFRenderer(pdf).renderImageWithDPI(0, 72);
			assertGradientPixels(page);
		}
	}

	public void testPaintAutoWidthUsesBorderWidth() throws Exception {
		try (PDDocument pdf = Loader.loadPDF(this.convertSingle("application/pdf"))) {
			final BufferedImage page = new PDFRenderer(pdf).renderImageWithDPI(1, 72);
			assertPainted("9mm into the 10mm left border must be painted", page.getRGB(mm(19), mm(40)));
			assertWhite("1mm inside the content box must not be painted", page.getRGB(mm(21), mm(40)));
		}
	}

	public void testPagedSvgCarriesTheTileCtmInTheGradient() throws Exception {
		final CapturingResults results = this.convertMultiple("application/vnd.copper.paged-svg");
		final byte[] bytes = results.bytes("pages/0001.svg");
		final org.w3c.dom.Document svg = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new ByteArrayInputStream(bytes));
		final NodeList gradients = svg.getElementsByTagName("linearGradient");
		assertTrue("the eight non-centre slices must emit gradients", gradients.getLength() >= 8);

		final Element first = (Element) gradients.item(0);
		final double width = pt(80), height = pt(60);
		final double angle = Math.toRadians(135);
		final double length = Math.abs(width * Math.sin(angle)) + Math.abs(height * Math.cos(angle));
		final double dx = Math.sin(angle) * length / 2;
		final double dy = -Math.cos(angle) * length / 2;
		assertEquals(width / 2 - dx, Double.parseDouble(first.getAttribute("x1")), .02);
		assertEquals(height / 2 - dy, Double.parseDouble(first.getAttribute("y1")), .02);
		assertEquals(width / 2 + dx, Double.parseDouble(first.getAttribute("x2")), .02);
		assertEquals(height / 2 + dy, Double.parseDouble(first.getAttribute("y2")), .02);

		// 先頭の左上隅では、30 CSS px (=22.5pt)の源を10mmへ拡大し、
		// 頁余白10mmの位置へ移す。順序には依存せず、この行列を持つ定義を探す。
		final double expectedScale = pt(10) / 22.5;
		final double expectedTranslate = pt(10);
		boolean found = false;
		for (int i = 0; i < gradients.getLength(); ++i) {
			final double[] m = matrix(((Element) gradients.item(i)).getAttribute("gradientTransform"));
			if (m != null && near(m[0], expectedScale, .02) && near(m[1], 0, .02) && near(m[2], 0, .02)
					&& near(m[3], expectedScale, .02) && near(m[4], expectedTranslate, .02)
					&& near(m[5], expectedTranslate, .02)) {
				found = true;
				break;
			}
		}
		assertTrue("a gradient must carry the top-left tile CTM", found);
	}

	public void testPngMatchesThePdfGradientPixels() throws Exception {
		final CapturingResults results = this.convertMultiple("image/png");
		assertFalse("a PNG page must be emitted", results.order.isEmpty());
		final BufferedImage page = ImageIO.read(new ByteArrayInputStream(results.bytes(results.order.get(0))));
		assertNotNull("the first raster result must be a PNG", page);
		assertGradientPixels(page);
	}

	private static void assertGradientPixels(final BufferedImage page) {
		final int topLeft = page.getRGB(mm(15), mm(15));
		final int bottomRight = page.getRGB(mm(85), mm(65));
		assertTrue("the top-left corner must be reddish: " + rgb(topLeft), red(topLeft) > blue(topLeft) + 140);
		assertTrue("the bottom-right corner must be bluish: " + rgb(bottomRight),
				blue(bottomRight) > red(bottomRight) + 140);

		// 上辺中央の目的座標(50mm,15mm)は仮想源では(40mm, slice/2)。
		// 135degの勾配線へ射影して、期待する赤青の割合を定義から求める。
		final double slice = 30 * 25.4 / 96;
		final double[] expected = linearGradientAt(80, 60, 135, 40, slice / 2);
		assertColorNear("the top edge centre must use its virtual-source colour",
				page.getRGB(mm(50), mm(15)), expected, 40);
	}

	private static double[] linearGradientAt(final double width, final double height, final double degrees,
			final double x, final double y) {
		final double angle = Math.toRadians(degrees);
		final double sin = Math.sin(angle), cos = Math.cos(angle);
		final double length = Math.abs(width * sin) + Math.abs(height * cos);
		final double vx = sin * length, vy = -cos * length;
		final double x1 = width / 2 - vx / 2, y1 = height / 2 - vy / 2;
		final double t = Math.max(0, Math.min(1, ((x - x1) * vx + (y - y1) * vy) / (vx * vx + vy * vy)));
		return new double[] { 255 * (1 - t), 0, 255 * t };
	}

	private static void assertColorNear(final String message, final int actual, final double[] expected,
			final int tolerance) {
		assertTrue(message + ": expected=" + rgb(expected) + ", actual=" + rgb(actual),
				Math.abs(red(actual) - expected[0]) <= tolerance && Math.abs(green(actual) - expected[1]) <= tolerance
						&& Math.abs(blue(actual) - expected[2]) <= tolerance);
	}

	private static void assertPainted(final String message, final int actual) {
		assertTrue(message + ": " + rgb(actual), green(actual) < 80 && (red(actual) < 230 || blue(actual) < 230));
	}

	private static void assertWhite(final String message, final int actual) {
		assertTrue(message + ": " + rgb(actual), red(actual) >= 240 && green(actual) >= 240 && blue(actual) >= 240);
	}

	private byte[] convertSingle(final String outputType) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			this.configure(session, outputType);
			CTISessionHelper.transcodeFile(session, DOCUMENT, "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return out.toByteArray();
	}

	private CapturingResults convertMultiple(final String outputType) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			this.configure(session, outputType);
			CTISessionHelper.transcodeFile(session, DOCUMENT, "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return results;
	}

	private void configure(final DirectSession session, final String outputType) throws Exception {
		session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
		session.property("input.include", "**");
		session.property("output.type", outputType);
		if ("application/vnd.copper.paged-svg".equals(outputType)) {
			session.property("output.paged-svg.compression", "none");
		} else if ("image/png".equals(outputType)) {
			session.property("output.image.resolution", "72");
		}
	}

	private static double[] matrix(final String value) {
		final Matcher matcher = MATRIX.matcher(value);
		if (!matcher.matches()) {
			return null;
		}
		final double[] matrix = new double[6];
		for (int i = 0; i < matrix.length; ++i) {
			matrix[i] = Double.parseDouble(matcher.group(i + 1));
		}
		return matrix;
	}

	private static boolean near(final double a, final double b, final double tolerance) {
		return Math.abs(a - b) <= tolerance;
	}

	private static int mm(final double mm) {
		return (int) Math.round(pt(mm));
	}

	private static double pt(final double mm) {
		return mm / 25.4 * 72;
	}

	private static int red(final int rgb) {
		return (rgb >> 16) & 0xff;
	}

	private static int green(final int rgb) {
		return (rgb >> 8) & 0xff;
	}

	private static int blue(final int rgb) {
		return rgb & 0xff;
	}

	private static String rgb(final int rgb) {
		return red(rgb) + "," + green(rgb) + "," + blue(rgb);
	}

	private static String rgb(final double[] rgb) {
		return Math.round(rgb[0]) + "," + Math.round(rgb[1]) + "," + Math.round(rgb[2]);
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

		byte[] bytes(final String uri) {
			final ByteArrayOutputStream out = this.data.get(uri);
			assertNotNull(uri + " must be emitted: " + this.order, out);
			return out.toByteArray();
		}
	}
}
