package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** Verify that relative-positioning insets accept calc() mixing percentages and absolute lengths. */
public class RelativeInsetCalcTest extends TestCase {
	public void testMixedCalcForBothInsetDirections() throws Exception {
		final String html = "<!doctype html><html><head><meta charset='UTF-8'><style>"
				+ "div{position:relative}.a{left:calc(50% - 25px);top:calc(50% - 10px)}"
				+ ".b{right:calc(50% - 8px);bottom:calc(50% - 6px)}"
				+ "</style></head><body><div class='a'>CALC-A</div><div class='b'>CALC-B</div></body></html>";
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("urn:test:relative-inset-calc"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		try (PDDocument pdf = Loader.loadPDF(out.toByteArray())) {
			final String text = new PDFTextStripper().getText(pdf);
			assertTrue(text.contains("CALC-A"));
			assertTrue(text.contains("CALC-B"));
		}
	}
}
