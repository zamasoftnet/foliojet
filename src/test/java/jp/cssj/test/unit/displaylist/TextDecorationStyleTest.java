package jp.cssj.test.unit.displaylist;

import java.awt.image.BufferedImage;
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
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>{@code text-decoration-style}・{@code text-decoration-thickness}・
 * {@code text-underline-offset}・{@code text-underline-position}</b>の描画を
 * 画素で固定します(2026-08-29)。
 *
 * <p>
 * fixture {@code 0160-text-decoration/decoration-styles.html}(20pt/行高
 * 30pt、各段30pt)。"MMMMMMMM"の下線はベースラインより下なので、段の
 * ベースラインより下の帯で最もインクの多い行を「下線の行」として探し、
 * 太さ2ptの実線(#thick)を基準に: wavyは上下±2.5ptにもインクがある
 * (振幅=太さ)、dottedは下線の行に白い隙間がある、offset:9pt(零位置=
 * ベースライン。autoの下線はディセント6.48ptの深さにあるので、それより
 * 明確に下がる値にした)は実線より下線が下がる、underはディセントの下に
 * 来る、doubleは2本になる。
 * </p>
 */
public class TextDecorationStyleTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final int SCALE = 2; // 144dpi
	private static final int MARGIN = 10;
	private static final int ROW = 30;

	public void testDecorationStyles() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/0160-text-decoration/decoration-styles.html"), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 72 * SCALE);
			new File("build/test-images").mkdirs();
			javax.imageio.ImageIO.write(img, "png", new File("build/test-images/decoration-styles.png"));

			// 段の並び: 0 solid, 1 thick, 2 wavy, 3 dotted, 4 dashed, 5 double, 6 offset, 7 under
			final int thickRow = underlineRow(img, 1);
			// 波線の中心は同じレイアウトの実線と同じ行(最もインクの多い行は山の
			// 平らな部分になるので、実線の行を段2へ平行移動して基準にする)
			final int wavyCenter = thickRow + ROW * SCALE;
			// 実線(2pt)は±2.5ptにインクが無く、wavy(振幅2pt)は上下ともにある
			final int off = (int) Math.round(2.5 * SCALE);
			assertFalse("実線の上にインクがあります", rowHasInk(img, thickRow - off));
			assertFalse("実線の下にインクがあります", rowHasInk(img, thickRow + off));
			assertTrue("波線の上側の山がありません", rowHasInk(img, wavyCenter - off));
			assertTrue("波線の下側の谷がありません", rowHasInk(img, wavyCenter + off));
			assertTrue("波線の中心の行にインクがありません", rowHasInk(img, wavyCenter));

			// dotted: 下線の行に白い隙間がある。solidには無い
			final int dottedRow = underlineRow(img, 3);
			assertTrue("点線に隙間がありません", rowHasGap(img, dottedRow));
			assertFalse("実線に隙間があります", rowHasGap(img, thickRow));
			// dashed も隙間がある
			assertTrue("破線に隙間がありません", rowHasGap(img, underlineRow(img, 4)));

			// double: 下線の行の上下2pt(=太さ)にもう1本ある → 実線には無い位置にインク
			final int doubleRow = underlineRow(img, 5);
			assertTrue("二重線の2本目がありません",
					rowHasInk(img, doubleRow - 2 * SCALE) || rowHasInk(img, doubleRow + 2 * SCALE));

			// offset: 9pt(線の上辺がベースライン+9pt、中心+10pt) → auto(中心
			// ベースライン+6.48pt)より3.5pt下(段内の相対位置で比較)
			final int offsetRow = underlineRow(img, 6);
			assertTrue("text-underline-offsetで下線が下がっていません",
					relative(offsetRow, 6) > relative(thickRow, 1) + 2 * SCALE);
			// under: ディセント(6.48pt)の下端に線の上辺が付く → 実線(中心=ベースライン+6.48pt)より
			// 太さの半分(1pt)下がる
			final int underRow = underlineRow(img, 7);
			assertTrue("text-underline-position: under で下線が下がっていません",
					relative(underRow, 7) >= relative(thickRow, 1) + 1);
		}
	}

	/** 段内の相対y(px)。 */
	private static int relative(final int row, final int index) {
		return row - (MARGIN + ROW * index) * SCALE;
	}

	/**
	 * 段 index のベースライン(段上端+3+17.52pt)より下の帯で、最もインクの多い行。
	 */
	private static int underlineRow(final BufferedImage img, final int index) {
		final int top = (MARGIN + ROW * index) * SCALE;
		final int baseline = top + (int) Math.round((3 + 17.52) * SCALE);
		final int bottom = top + ROW * SCALE;
		int best = -1, bestCount = -1;
		for (int y = baseline + 1; y < bottom; ++y) {
			final int count = inkCount(img, y);
			if (count > bestCount) {
				bestCount = count;
				best = y;
			}
		}
		assertTrue("段" + index + "に下線がありません", bestCount > 0);
		return best;
	}

	private static int inkCount(final BufferedImage img, final int y) {
		int count = 0;
		for (int x = MARGIN * SCALE; x < (MARGIN + 120) * SCALE; ++x) {
			if (isInk(img.getRGB(x, y))) {
				++count;
			}
		}
		return count;
	}

	private static boolean rowHasInk(final BufferedImage img, final int y) {
		return inkCount(img, y) > 0;
	}

	/** 下線の行で、最初のインクから最後のインクの間に白い画素があるか。 */
	private static boolean rowHasGap(final BufferedImage img, final int y) {
		int first = -1, last = -1;
		for (int x = MARGIN * SCALE; x < (MARGIN + 120) * SCALE; ++x) {
			if (isInk(img.getRGB(x, y))) {
				if (first < 0) {
					first = x;
				}
				last = x;
			}
		}
		for (int x = first; x <= last; ++x) {
			if (!isInk(img.getRGB(x, y))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isInk(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return r + g + b < 3 * 160;
	}
}
