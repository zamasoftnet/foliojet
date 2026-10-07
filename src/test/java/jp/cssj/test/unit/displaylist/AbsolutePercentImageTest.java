package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verifies the contract for percentage height of images inside absolutely positioned boxes
 * (width in vertical writing) (2026-10-04).
 *
 * <p>
 * An absolutely positioned box determines its page-axis size after laying out its contents,
 * so its inner size was 0 during layout. An image with {@code height: 100%} became 0 and was not drawn
 * (an illustration inside a frame in a publishing cover template).
 * Set the size beforehand when it is independent of content (explicit size or positions at both ends);
 * otherwise, resolve percentages as auto.
 * </p>
 */
public class AbsolutePercentImageTest extends TestCase {
	/** A 100×200 px (75×150 pt) image. */
	private static final String IMAGE = "<img src=\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg'"
			+ " width='100' height='200'%3E%3Crect width='100' height='200' fill='red'/%3E%3C/svg%3E\"/>";

	private static final Pattern FRAME = Pattern.compile("AbsoluteRectFrame\\[w=([-0-9.]+) h=([-0-9.]+)\\]");

	/** The image's placed dimensions {width, height}. */
	private static double[] imageSize(final String style) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:400pt 200pt;margin:0} body{margin:0;position:relative;height:200pt}"
				+ ".box{position:absolute;left:5pt;width:90pt;" + style + "}</style></head><body>"
				+ "<div class='box'>" + IMAGE + "</div></body></html>";
		final Path dir = Files.createTempDirectory("abs-percent");
		try (AutoCloseable d = DisplayListDumper.scopedDir(dir.toString());
				AutoCloseable g = DisplayListDumper.scopedDetailedGeometry(true)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				CTISessionHelper.transcodeStream(session,
						new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
						URI.create("file:///abs-percent.html"), "text/html", null);
			} finally {
				session.close();
			}
		}
		final String dump = Files.readString(dir.resolve("page-0001.txt"));
		final Matcher m = FRAME.matcher(dump);
		assertTrue(dump, m.find());
		return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
	}

	public void testSpecifiedHeight() throws Exception {
		final double[] size = imageSize("top:10pt;height:92pt;text-align:center} .box img{height:100%;width:auto");
		assertEquals(92, size[1], 0.01);
		assertEquals(46, size[0], 0.01);
	}

	public void testHeightFromBothInsets() throws Exception {
		final double[] size = imageSize("top:10pt;bottom:98pt} .box img{height:100%;width:auto");
		assertEquals(92, size[1], 0.01);
	}

	public void testAutoHeightResolvesPercentAsAuto() throws Exception {
		final double[] size = imageSize("top:10pt} .box img{height:100%;width:auto");
		assertEquals("intrinsic height", 150, size[1], 0.01);
		assertEquals(75, size[0], 0.01);
	}

	public void testVerticalSpecifiedWidth() throws Exception {
		final double[] size = imageSize(
				"top:10pt;writing-mode:vertical-rl;height:150pt} .box img{width:100%;height:auto");
		assertEquals(90, size[0], 0.01);
		assertEquals(180, size[1], 0.01);
	}
}
