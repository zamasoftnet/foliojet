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
 * 画像出力で見えていた 2 つの欠陥を固定します(NEXT-SESSION §5-3、2026-09-02)。
 *
 * <ol>
 * <li>{@code @page} の背景が塗り足し(bleed)の帯まで届かず白い縁が出る</li>
 * <li>隣り合う矩形の塗り(表のセル・1×1 タイル)の境目に下地が 1px 覗く</li>
 * </ol>
 */
public class RasterDefectsTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 96dpi: 1mm = 3.78px。 */
	private static final double PX_PER_MM = 96 / 25.4;

	public void testPageBackgroundReachesTheBleed() throws Exception {
		final BufferedImage img = render("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
				+ "@page{size:100mm 60mm;margin:10mm;bleed:5mm;background:#FBE4C8}body{margin:0}"
				+ "</style></head><body><p>bleed</p></body></html>");
		// 用紙は仕上り 100×60mm に塗り足し 5mm を足した大きさ
		assertEquals("paper width with bleed", Math.round(110 * PX_PER_MM), img.getWidth(), 2);
		final int bg = 0xFBE4C8;
		assertEquals("the bleed band (top-left) must carry the page background", bg, rgb(img, 3, 3));
		assertEquals("the bleed band (bottom-right) must carry the page background", bg,
				rgb(img, img.getWidth() - 4, img.getHeight() - 4));
		assertEquals("the page area keeps the background", bg, rgb(img, img.getWidth() / 2, 8));
	}

	public void testAdjacentCellsLeaveNoSeam() throws Exception {
		// セル幅を 20.3pt にして境界を画素の途中に置く
		final BufferedImage img = render("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
				+ "@page{size:60mm 30mm;margin:2mm}body{margin:0}"
				+ "table{border-collapse:collapse;border-spacing:0;margin:0}"
				+ "td{padding:0;width:20.3pt;height:20.3pt;background:#0066CC}"
				+ "</style></head><body><table><tr><td></td><td></td><td></td></tr><tr><td></td><td></td><td></td></tr></table></body></html>");
		// 表の中央付近を横に 1 行、縦に 1 列走査して、セルの色以外が無いこと
		final int x0 = (int) Math.round(2 * PX_PER_MM) + 3, y0 = (int) Math.round(2 * PX_PER_MM) + 3;
		final int span = (int) Math.round(3 * 20.3 * 96 / 72) - 6;
		final StringBuilder bad = new StringBuilder();
		for (int x = x0; x < x0 + span; ++x) {
			final int c = rgb(img, x, y0 + 10);
			if (c != 0x0066CC) {
				bad.append(String.format(" (%d,%d)=%06X", x, y0 + 10, c));
			}
		}
		for (int y = y0; y < y0 + (int) Math.round(2 * 20.3 * 96 / 72) - 6; ++y) {
			final int c = rgb(img, x0 + 10, y);
			if (c != 0x0066CC) {
				bad.append(String.format(" (%d,%d)=%06X", x0 + 10, y, c));
			}
		}
		assertEquals("no lighter pixels along the cell boundaries:" + bad, 0, bad.length());
	}

	private static int rgb(final BufferedImage img, final int x, final int y) {
		return img.getRGB(x, y) & 0xFFFFFF;
	}

	private BufferedImage render(final String html) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "image/png");
			session.property("output.resolution", "96");
			session.property("output.image-antialias", "true");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					new java.io.File("files/unittest/raster-defects.html").getAbsoluteFile().toURI(), "text/html",
					"UTF-8");
		} finally {
			session.close();
		}
		assertFalse("a page image must be emitted: " + results.order, results.order.isEmpty());
		final BufferedImage img = ImageIO.read(new ByteArrayInputStream(results.data.get(results.order.get(0)).toByteArray()));
		assertNotNull("the result must be a readable PNG", img);
		return img;
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
	}
}
