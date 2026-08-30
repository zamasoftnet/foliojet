package jp.cssj.test.unit.ioprops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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
 * <b>写真のEXIFの向き</b>({@code image-orientation})の検査です(2026-08-30新設)。
 *
 * <p>
 * 試験画像は40×80で{@code orientation=6}(時計回り90度)。向きを尊重すれば
 * <b>80×40</b>に、{@code image-orientation: none}なら素の<b>40×80</b>になる。
 *
 * <p>
 * <b>PDF出力はEXIFの向きを見ていなかった。</b>{@code PDFUserAgent}が
 * {@code PDFWriter}へ直に読ませており、EXIFを読む{@code RasterImageLoader}を
 * 通らないためで、<b>携帯で撮った横向きの写真が寝たまま出ていた</b>
 * (画像出力・SVG出力では正しく回っていた)。資源の先頭だけ覗いて向きを読み、
 * 同じストリームを頭出しした状態でPDFWriterへ渡すようにして直した——
 * HTTPの資源を二度取りに行かないためである。
 *
 * <p>
 * 判定は<b>実際に描いた絵</b>で行う。PDFの{@code cm}を読む方法だと、回転は
 * 外側の{@code cm}に入り{@code Do}直前の{@code cm}は素の画素寸法のままなので、
 * 一番内側だけを見ると「効いていない」と誤読する(実際に一度誤読した)。
 */
public class ImageOrientationTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final File DOCUMENT = new File("files/unittest/ioprops/image-orientation.html");

	/**
	 * 40×80の画像(上4分の1が赤)が、EXIFを尊重すると横長に、
	 * {@code image-orientation: none}では縦長に置かれること。
	 */
	public void testExifOrientationIsHonouredInPdf() throws Exception {
		final java.awt.image.BufferedImage page = this.render();
		final java.awt.Rectangle[] marks = redMarks(page);
		assertEquals("赤い帯が2つ写ること", 2, marks.length);
		final java.awt.Rectangle rotated = marks[0];
		final java.awt.Rectangle asIs = marks[1];
		assertTrue("EXIFを尊重すると赤い帯は縦長になる: " + rotated, rotated.height > rotated.width);
		assertTrue("image-orientation:none では赤い帯は横長のまま: " + asIs, asIs.width > asIs.height);
	}

	/**
	 * 赤い画素のかたまりを上から順に返します。2つの画像は縦に並ぶので、
	 * 行方向に切れ目を見つけるだけで分けられる。
	 */
	private static java.awt.Rectangle[] redMarks(final java.awt.image.BufferedImage page) {
		final List<java.awt.Rectangle> marks = new ArrayList<>();
		java.awt.Rectangle current = null;
		for (int y = 0; y < page.getHeight(); ++y) {
			int minX = Integer.MAX_VALUE, maxX = -1;
			for (int x = 0; x < page.getWidth(); ++x) {
				final int rgb = page.getRGB(x, y);
				final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
				if (r > 150 && g < 100 && b < 100) {
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
				}
			}
			if (maxX < 0) {
				current = null;
				continue;
			}
			if (current == null) {
				current = new java.awt.Rectangle(minX, y, maxX - minX + 1, 1);
				marks.add(current);
			} else {
				current.add(new java.awt.Rectangle(minX, y, maxX - minX + 1, 1));
			}
		}
		return marks.toArray(new java.awt.Rectangle[marks.size()]);
	}

	private java.awt.image.BufferedImage render() throws Exception {
		final File out = new File("local/unittest/pdf/" + this.getClass().getName() + ".pdf");
		out.getParentFile().mkdirs();
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				CTISessionHelper.transcodeFile(session, DOCUMENT, "text/html", null);
			} finally {
				session.close();
			}
		}
		try (PDDocument pdf = Loader.loadPDF(out)) {
			return new PDFRenderer(pdf).renderImageWithDPI(0, 144);
		}
	}
}
