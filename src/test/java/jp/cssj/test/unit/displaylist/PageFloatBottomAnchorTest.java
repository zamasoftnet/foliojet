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
 * Verify that a {@code float: bottom} box is not placed on a page before its anchor
 * (2026-10-04, TECH-20261003-004 item ⑱). If body text already reached the placement band
 * (the image's height above the page bottom) when the anchor arrived, remaining page space
 * shrank in one dimension, pushing text below the band, including the anchor paragraph, to the next page.
 * Only the image stayed at that page's bottom, preceding its anchor (an illustration appeared before
 * its section heading's page in chapter 1 of the Jigen Ango book). Now defer it to the next page's bottom.
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
			// The image does not shrink the anchor's page, so the anchor paragraph fits on a page before the image's page.
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
