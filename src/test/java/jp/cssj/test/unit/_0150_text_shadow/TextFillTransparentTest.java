package jp.cssj.test.unit._0150_text_shadow;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;

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
 * <b>{@code -webkit-text-fill-color: transparent}の文字が黒く描かれない</b>
 * ことを固定します(2026-08-18)。
 *
 * <p>
 * {@code TextFillColor.get}はtransparentでnullを返していたが、描画側
 * ({@code AbstractTextBox}等)の「nullなら色を設定しない」は既定の黒の
 * まま描くという意味で、透明のつもりの文字が黒く見えていた。
 * prism-editor(透明textareaとハイライト済みpreの重ね)でコードが
 * 二重に見える実欠陥(実コーパスchartjs-docs)。修正後はalpha 0の
 * 色実体で描かれる——このテストはPDFの非ストローク色のalphaを
 * pdfboxで実測する。
 * </p>
 */
public class TextFillTransparentTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testTransparentTextFillIsNotPaintedBlack() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/3080-MODERN-CSS/text-fill-transparent.html"), "text/html", null);
		} finally {
			session.close();
		}

		final StringBuilder report = new StringBuilder();
		final boolean[] sawHidden = new boolean[1];
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final PDFTextStripper stripper = new PDFTextStripper() {
				{
					addOperator(new org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace(this));
					addOperator(new org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor(this));
					addOperator(
							new org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor(this));
					addOperator(new org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColor(this));
					addOperator(new org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorN(this));
				}

				protected void processTextPosition(TextPosition p) {
					// HIDDEN側にだけ現れる文字'D'で判定(SHOWNはS/H/O/W/N)
					if ("D".equals(p.getUnicode())) {
						sawHidden[0] = true;
						final double alpha = getGraphicsState().getNonStrokeAlphaConstant();
						report.append("D alpha=").append(alpha).append('\n');
						assertEquals("transparentのtext-fillが不透明で描かれています: " + report, 0.0, alpha,
								0.001);
					}
					super.processTextPosition(p);
				}
			};
			stripper.setSuppressDuplicateOverlappingText(false);
			stripper.getText(doc);
		}
		assertTrue("HIDDENのグリフがPDFにありません(透明はスキップでなくalpha0で描く想定)", sawHidden[0]);
	}
}
