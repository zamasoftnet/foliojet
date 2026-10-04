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
 * 絶対配置の箱の中の画像の % の高さ(縦書きは幅)を固定します(2026-10-04)。
 *
 * <p>
 * 絶対配置の箱は頁方向の大きさを中身を組んだ後に決めるので、組んでいるあいだの内寸は 0 だった。
 * {@code height: 100%} の画像は 0 になって描かれなかった(出版の表紙のひな形、枠の中の絵)。
 * 大きさが中身に依らず決まる(指定・両端の位置)ときは先に入れ、決まらないときは % を auto として解く。
 * </p>
 */
public class AbsolutePercentImageTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** 100×200 px(75×150 pt)の画像。 */
	private static final String IMAGE = "<img src=\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg'"
			+ " width='100' height='200'%3E%3Crect width='100' height='200' fill='red'/%3E%3C/svg%3E\"/>";

	private static final Pattern FRAME = Pattern.compile("AbsoluteRectFrame\\[w=([-0-9.]+) h=([-0-9.]+)\\]");

	/** 画像の置かれた大きさ {幅, 高さ}。 */
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
