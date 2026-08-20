package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>auto表のmin-content保証</b>を固定します(2026-08-20)。
 *
 * <p>
 * 列のmin-content合計が利用可能幅を超える表は、列を潰して重ねるのでは
 * なく、列minを保ち表ごと行方向へはみ出す(CSS 2.2 17.5.2.2、Chromeと
 * 同じ)。あわせて、複数ページに分割された表の列幅が全断片で一致する
 * こと(resolveは表ごと1回で断片は確定列幅を共有する)も検査する。
 * 2026-08-18の閾値方式(MIN_OVERFLOW_TOLERANCE)撤回後の本筋。
 * </p>
 */
public class AutoTableMinOverflowTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testMinPreservedAndConsistentAcrossPages() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session, new File("files/unittest/0240-table/auto-min-overflow.html"),
					"text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			assertTrue("複数ページに分割されていません: " + doc.getNumberOfPages(), doc.getNumberOfPages() >= 2);
			// 各ページで第2列の先頭x(接頭辞SECONDCOLUMNの'S')を実測する
			final List<Double> col2Starts = new ArrayList<>();
			for (int p = 1; p <= doc.getNumberOfPages(); ++p) {
				final List<TextPosition> all = new ArrayList<>();
				final PDFTextStripper stripper = new PDFTextStripper() {
					@Override
					protected void processTextPosition(final TextPosition text) {
						all.add(text);
					}
				};
				stripper.setStartPage(p);
				stripper.setEndPage(p);
				stripper.setSuppressDuplicateOverlappingText(false);
				stripper.getText(doc);
				double col2 = Double.NaN;
				TextPosition prev = null;
				for (final TextPosition t : all) {
					final boolean cellStart = prev == null
							|| Math.abs(t.getYDirAdj() - prev.getYDirAdj()) > 0.5
							|| t.getXDirAdj() - (prev.getXDirAdj() + prev.getWidth()) > 4;
					if (cellStart && "S".equals(t.getUnicode())
							&& (Double.isNaN(col2) || t.getXDirAdj() < col2)) {
						col2 = t.getXDirAdj();
					}
					prev = t;
				}
				assertFalse("第2列が見つかりません: page=" + p, Double.isNaN(col2));
				col2Starts.add(col2);
			}
			// 断片間の一貫性: 全ページで第2列の開始xが一致する
			final double first = col2Starts.get(0);
			for (int i = 1; i < col2Starts.size(); ++i) {
				assertEquals("第2列の開始xがページ間で揺れています: " + col2Starts, first, col2Starts.get(i), 0.5);
			}
			// min保証: A4(20mmマージン=版面約470pt)に2列は収まらない内容
			// なので、第2列は版面の中央(潰した場合の位置≈306pt)より右へ
			// 押し出されているはず
			assertTrue("列がmin-content未満へ潰されています: col2.x=" + first, first > 400);
		}
	}

	/**
	 * わずかな超過(min合計が利用可能幅の許容比1.1以内)は従来どおり
	 * 潰して版面に収めることを固定します。同じ内容で版面を広げ
	 * (342mm、min合計/版面≈1.05)、第2列がmin位置(≈511pt)より左へ
	 * 縮められることを実測する——数ptのはみ出しによる紙端の文字切れを
	 * 作らないため(w3c-jlreqの実測に基づく判断)。
	 */
	public void testSlightOverflowIsSqueezed() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/0240-table/auto-min-slight-overflow.html"), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final float paperWidth = doc.getPage(0).getMediaBox().getWidth();
			for (int p = 1; p <= doc.getNumberOfPages(); ++p) {
				final List<TextPosition> all = new ArrayList<>();
				final PDFTextStripper stripper = new PDFTextStripper() {
					@Override
					protected void processTextPosition(final TextPosition text) {
						all.add(text);
					}
				};
				stripper.setStartPage(p);
				stripper.setEndPage(p);
				stripper.setSuppressDuplicateOverlappingText(false);
				stripper.getText(doc);
				assertFalse(all.isEmpty());
				double col2 = Double.NaN, maxRight = 0;
				// 第2列の先頭は語頭の「SEC」の並びで検出する(潰しにより
				// 列間の空隙が消えるため、間隔ベースの検出は使えない)
				for (int i = 0; i + 2 < all.size(); ++i) {
					if ("S".equals(all.get(i).getUnicode()) && "E".equals(all.get(i + 1).getUnicode())
							&& "C".equals(all.get(i + 2).getUnicode())
							&& (Double.isNaN(col2) || all.get(i).getXDirAdj() < col2)) {
						col2 = all.get(i).getXDirAdj();
					}
				}
				for (final TextPosition t : all) {
					maxRight = Math.max(maxRight, t.getXDirAdj() + t.getWidth());
				}
				assertFalse("第2列が見つかりません: page=" + p, Double.isNaN(col2));
				// 潰し: 第2列はmin位置(56.7+454≈511pt)より左に置かれる
				assertTrue("わずかな超過が潰されていません: col2.x=" + col2, col2 < 505);
				// 全テキストが紙面内に収まる(はみ出しなし)
				assertTrue("文字が紙の外にあります: right=" + maxRight + " paper=" + paperWidth,
						maxRight <= paperWidth + 0.5);
			}
		}
	}
}
