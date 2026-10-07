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
 * Verify <b>drop caps with {@code initial-letter}</b>
 * (css-inline-3, 2026-08-20).
 *
 * <p>
 * Measure the PDF to check that the initial character grows to about three parent lines
 * (cap approximation 0.7), and float wrapping indents the body text.
 * </p>
 */
public class InitialLetterTest extends TestCase {
	public void testDropCapSizeAndIndent() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session, new File("files/unittest/3080-MODERN-CSS/initial-letter.html"),
					"text/html", null);
		} finally {
			session.close();
		}

		final List<TextPosition> all = new ArrayList<>();
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final PDFTextStripper stripper = new PDFTextStripper() {
				@Override
				protected void processTextPosition(final TextPosition text) {
					all.add(text);
					super.processTextPosition(text);
				}
			};
			stripper.setSuppressDuplicateOverlappingText(false);
			stripper.getText(doc);
		}
		assertFalse("テキストが出力されていません", all.isEmpty());

		TextPosition cap = null;
		final List<TextPosition> body = new ArrayList<>();
		for (final TextPosition t : all) {
			if (cap == null && "X".equals(t.getUnicode())) {
				cap = t;
			} else {
				body.add(t);
			}
		}
		assertNotNull("ドロップキャップの文字が見つかりません", cap);
		assertFalse(body.isEmpty());
		final float bodySize = body.get(0).getFontSizeInPt();

		// Three-line drop cap: cap = ((N-1)*1.4 + 0.7) / 0.7 = 5×.
		final float ratio = cap.getFontSizeInPt() / bodySize;
		assertTrue("ドロップキャップが拡大されていません: ratio=" + ratio, ratio > 4.0f && ratio < 6.0f);

		// Wrapping: body text in the same line band as the cap starts to its right.
		final float capRight = cap.getXDirAdj() + cap.getWidth();
		int wrapped = 0;
		for (final TextPosition t : body) {
			if (t.getYDirAdj() >= cap.getYDirAdj() - cap.getHeight() && t.getYDirAdj() <= cap.getYDirAdj() + 2) {
				assertTrue("回り込みの本文がドロップキャップに重なっています: x=" + t.getXDirAdj(),
						t.getXDirAdj() >= capRight - 1);
				++wrapped;
			}
		}
		assertTrue("回り込みの本文が検出できません", wrapped > 0);
	}
}
