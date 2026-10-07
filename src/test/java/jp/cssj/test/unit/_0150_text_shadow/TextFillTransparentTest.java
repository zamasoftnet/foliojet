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
 * Verifies that <b>text with {@code -webkit-text-fill-color: transparent} is not drawn in black</b>
 * (2026-08-18).
 *
 * <p>
 * {@code TextFillColor.get} returned null for transparent, but "do not set a color for null" on the
 * rendering side ({@code AbstractTextBox}, etc.) meant drawing in the default black.
 * Text intended to be transparent therefore appeared black. This caused visibly doubled code in
 * prism-editor (a transparent textarea over a highlighted pre) in the real chartjs-docs corpus.
 * After the fix, rendering uses a color object with alpha 0. This test measures the PDF's non-stroking
 * color alpha with pdfbox.
 * </p>
 */
public class TextFillTransparentTest extends TestCase {
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
					// Check using 'D', which appears only in HIDDEN (SHOWN contains S/H/O/W/N).
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
