package jp.cssj.test.unit.displaylist;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>{@code line-clamp} / {@code -webkit-line-clamp}</b>を固定します
 * (css-overflow-4、2026-08-29)。
 *
 * <p>
 * fixture {@code 0040-overflow/line-clamp.html}(12pt/行高16pt、幅200pt):
 * 3行clamp(-webkit-box イディオム)→灰色の後続ブロック→1行clamp→
 * span+インライン画像入りの2行clamp→3行clampだが1行しかない段落→
 * 2つのpを含む2行clamp(入れ子のブロックの行も数え、2つ目のpは丸ごと消える)。
 * 表示リストで省略記号「…」の位置を、画素で3行clampの高さ(後続ブロックの
 * 位置=4行目以降の抑止)を検査する。
 * </p>
 */
public class LineClampTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testLineClamp() throws Exception {
		final File dumpDir = new File("local/unittest/line-clamp-test");
		dumpDir.mkdirs();
		for (final File f : dumpDir.listFiles()) {
			f.delete();
		}
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		System.setProperty(DisplayListDumper.DIR_PROPERTY, dumpDir.getPath());
		try {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				CTISessionHelper.transcodeFile(session, new File("files/unittest/0040-overflow/line-clamp.html"),
						"text/html", null);
			} finally {
				session.close();
			}
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}

		// 表示リスト: 省略記号の行位置(margin-box原点、行の上端)。
		// #three の3行目 y=32、#one の1行目 y=74、#nested の2行目 y=114。
		// #short(y=138)には付かない。#blocks の1つ目のpの2行目 y=178
		final String dump = Files.readString(new File(dumpDir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		final List<Double> ellipsisY = new ArrayList<>();
		final Matcher m = Pattern.compile("y=([0-9.]+) Text\\[\"…\"").matcher(dump);
		while (m.find()) {
			ellipsisY.add(Double.parseDouble(m.group(1)));
		}
		assertEquals("省略記号の数が違います: " + ellipsisY + "\n" + dump, 4, ellipsisY.size());
		assertEquals("3行clampの3行目に省略記号がありません", 32.0, ellipsisY.get(0), 0.01);
		assertEquals("1行clampの1行目に省略記号がありません", 74.0, ellipsisY.get(1), 0.01);
		assertEquals("入れ子span入り2行clampの2行目に省略記号がありません", 114.0, ellipsisY.get(2), 0.01);
		assertEquals("入れ子ブロックの2行目に省略記号がありません", 178.0, ellipsisY.get(3), 0.01);
		assertFalse("2つ目のpが描かれています", dump.contains("Second"));
		// 3行clampの1・2行目には省略記号が無い(行の内容は全て通常のTextとして残る)
		assertFalse("4行目以降が描かれています", dump.contains("y=48.00 Text"));

		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 144); // 2px/pt
			new File("build/test-images").mkdirs();
			javax.imageio.ImageIO.write(img, "png", new File("build/test-images/line-clamp.png"));
			// #three は y=10..58pt(3行×16pt)。後続の灰色ブロックは margin 8pt を
			// 挟んで y=66..76pt に来る(=使用高さがちょうど3行で、後続が上へ詰まる)
			assertTrue("後続ブロックが3行の直後に来ていません", isGray(img, 100 * 2, 71 * 2));
			assertFalse("3行clampの高さが3行を超えています", isGray(img, 100 * 2, 62 * 2));
			// 4行目の領域(y=58..66pt)に文字が無い
			assertFalse("4行目が描かれています", hasInk(img, 10 * 2, 210 * 2, 59 * 2, 65 * 2));
			// 3行目(y=42..58pt)には文字がある
			assertTrue("3行目が消えています", hasInk(img, 10 * 2, 100 * 2, 44 * 2, 56 * 2));
			// #short(y=148..164pt)は1行だけで高さ16pt: 直後のmargin(y=164..172pt)は空で、
			// #blocks の1行目が y=172pt から始まる
			assertFalse("N行未満の段落の下に何か描かれています", hasInk(img, 10 * 2, 210 * 2, 165 * 2, 171 * 2));
			assertTrue("入れ子ブロックの1行目がありません", hasInk(img, 10 * 2, 100 * 2, 174 * 2, 186 * 2));
			// #blocks(y=172..204pt)の下、2つ目のpの領域(y=204..220pt)は空
			assertFalse("2つ目のpが描かれています", hasInk(img, 10 * 2, 210 * 2, 205 * 2, 219 * 2));
		}
	}

	private static boolean isGray(final BufferedImage img, final int x, final int y) {
		final int rgb = img.getRGB(x, y);
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return Math.abs(r - 0xCC) <= 8 && Math.abs(g - 0xCC) <= 8 && Math.abs(b - 0xCC) <= 8;
	}

	private static boolean hasInk(final BufferedImage img, final int x0, final int x1, final int y0, final int y1) {
		for (int y = y0; y < y1; ++y) {
			for (int x = x0; x < x1; ++x) {
				final int rgb = img.getRGB(x, y);
				final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
				if (r + g + b < 3 * 128) {
					return true;
				}
			}
		}
		return false;
	}
}
