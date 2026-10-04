package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * {@code float: bottom}の箱が錨より前の頁に置かれないことを固定します(2026-10-04、
 * TECH-20261003-004 の⑱)。錨に来た時点で本文がもう置き場の帯(頁の下端から図の高さ
 * ぶん)へ届いていると、頁の残りを一次元で縮めて、帯より下の本文(錨の段落も)を次頁へ
 * 押し出していた。図だけがその頁の下端に残り、錨より前の頁に出た(時限暗号の本の
 * 第1章で、挿絵が節の見出しの前の頁に出た)。今はその頁には置かず、次頁の下端へ回す。
 */
public class PageFloatBottomAnchorTest extends TestCase {
	public void testBottomFloatIsNotBeforeItsAnchor() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			CTISessionHelper.transcodeFile(session, new File("files/unittest/0230-page-float/bottom-past-band.html"),
					"text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final int anchor = pageOf(doc, "ANCHOR");
			final int floating = pageOf(doc, "FLOAT");
			assertTrue("anchor found", anchor > 0);
			assertTrue("float found", floating > 0);
			assertTrue("the bottom float must not precede its anchor: float=" + floating + " anchor=" + anchor,
					floating >= anchor);
			// 錨の頁は図に縮められていないので、錨の段落は図の頁より前の頁に収まる
			assertEquals("the anchor stays on the page where it fits", floating - 1, anchor);
		}
	}

	private static int pageOf(final PDDocument doc, final String word) throws Exception {
		for (int page = 1; page <= doc.getNumberOfPages(); ++page) {
			final PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(page);
			stripper.setEndPage(page);
			if (stripper.getText(doc).contains(word)) {
				return page;
			}
		}
		return -1;
	}
}
