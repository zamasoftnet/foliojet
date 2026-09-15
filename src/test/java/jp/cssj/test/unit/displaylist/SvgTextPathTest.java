package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.ua.impl.svg.MyGVTGlyphVector;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>SVG 文書の {@code <text>} が描かれる</b>ことを固定します(2026-09-14)。
 *
 * <p>
 * SVG の文字は Batik の GlyphLayout → {@link MyGVTGlyphVector} で字形の path として
 * 描かれる。以前は {@code getGlyphTransform} など GlyphLayout が呼ぶメソッドが
 * {@code UnsupportedOperationException} を投げて SVG 文書の {@code <text>} は
 * 全部変換に失敗し、直した後も {@code getOutline()} が拡縮とペン送りを二重に掛けていた。
 * 表示リストの golden には SVG の内側が写らないので、画像にして字面の有無で押さえる。
 * </p>
 */
public class SvgTextPathTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** 横 3 行(欧文・太字和文・合成斜体)と縦 1 行に字面があり、空白の領域には無い。 */
	public void testTextLinesAreDrawn() throws Exception {
		final java.awt.image.BufferedImage img = render("files/unittest/0480-svg-text/inline-svg-text.html");
		// @page 260x180pt margin 10pt。svg の座標は px(= 0.75pt)なので x=10,y=30 は (17.5, 32.5)pt
		assertTrue("横 1 行目(欧文 16px, baseline 32.5pt)に字面がありません", hasInk(img, 16, 20, 76, 16));
		assertTrue("横 2 行目(太字和文 14px, baseline 55pt)に字面がありません", hasInk(img, 16, 43, 86, 15));
		assertTrue("横 3 行目(合成斜体 12px, baseline 77.5pt)に字面がありません", hasInk(img, 16, 66, 44, 14));
		assertTrue("縦 1 行(x=215px → 171pt, 14px)に字面がありません", hasInk(img, 158, 14, 14, 68));
		// 縦行は 6 字 × 10.5pt = 63pt で y≈80pt まで。二重変換のときは字送りが倍になって下へはみ出ていた
		assertFalse("縦行が下へ伸びすぎています(字送りの二重掛け)", hasInk(img, 158, 92, 14, 60));
		assertFalse("何も無いはずの領域に字面があります", hasInk(img, 100, 100, 50, 60));
	}

	private static java.awt.image.BufferedImage render(final String file) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			// fixture の PI(output.pdf.fonts.policy=embedded)を効かせる。試験 conf は PI を既定で
			// 無視するので、これが無いと内蔵 CID フォント(非埋め込み)になり、PDFBox の描画が
			// 環境の代用書体に依存する(WSL では太字ゴシックの代用が無く 2 行目が空になった、2026-09-15)
			session.property("input.property-pi", "true");
			CTISessionHelper.transcodeFile(session, new File(file), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			return new PDFRenderer(doc).renderImageWithDPI(0, 72);
		}
	}

	private static boolean hasInk(final java.awt.image.BufferedImage img, final int x0, final int y0, final int w,
			final int h) {
		for (int y = y0; y < y0 + h; ++y) {
			for (int x = x0; x < x0 + w; ++x) {
				final int rgb = img.getRGB(x, y);
				if (((rgb >> 16) & 0xFF) < 200 && ((rgb >> 8) & 0xFF) < 200 && (rgb & 0xFF) < 200) {
					return true;
				}
			}
		}
		return false;
	}
}
