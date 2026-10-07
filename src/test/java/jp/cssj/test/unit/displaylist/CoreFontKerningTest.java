package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verifies that core-font kerning (AFM Times-Roman) uses the same pairs in the same direction
 * for layout and rendering (2026-10-04).
 *
 * <p>
 * Previously, rendering looked up the current/previous character pair (reversed),
 * so "To" (KPX T o -80) did not tighten, while "oT" did.
 * Layout returned the negative KPX value (tightening) unchanged to callers expecting positive
 * values for tightening, widening "To".
 * </p>
 */
public class CoreFontKerningTest extends TestCase {
	/** One BT–ET: Td's x and the TJ array expressed as a sequence of characters and numbers. */
	private record Run(float x, String tj) {
	}

	private static List<Run> runs(final String body) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:300pt 100pt;margin:10pt} body{margin:0;font-family:Times-Roman;font-size:20pt}"
				+ "</style></head><body>" + body + "</body></html>";
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///a.html"), "text/html", null);
		} finally {
			session.close();
		}
		final List<Run> runs = new ArrayList<>();
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final PDFStreamParser parser = new PDFStreamParser(doc.getPage(0));
			final List<Object> operands = new ArrayList<>();
			float x = 0;
			Object token;
			while ((token = parser.parseNextToken()) != null) {
				if (!(token instanceof Operator op)) {
					operands.add(token);
					continue;
				}
				if ("Td".equals(op.getName())) {
					x = ((COSNumber) operands.get(0)).floatValue();
				} else if ("TJ".equals(op.getName())) {
					final StringBuilder tj = new StringBuilder();
					for (final COSBase item : (COSArray) operands.get(0)) {
						if (item instanceof COSString s) {
							tj.append('(').append(s.getString()).append(')');
						} else if (item instanceof COSNumber n) {
							tj.append(' ').append(n.intValue()).append(' ');
						}
					}
					runs.add(new Run(x, tj.toString()));
				}
				operands.clear();
			}
		}
		return runs;
	}

	public void testPairsAreKernedInOrderAndLaidOutWithTheSameWidth() throws Exception {
		final List<Run> runs = runs("<p>To oT</p>");
		assertEquals(runs.toString(), 2, runs.size());
		assertEquals("To は KPX T o -80 で詰める", "(T) 80 (o)", runs.get(0).tj());
		assertEquals("oT に組は無い", "(oT)", runs.get(1).tj());
		// T 611 + o 500 - 80 = 1031 → 20.62 pt; space 250 → 5 pt.
		assertEquals(20.62 + 5, runs.get(1).x() - runs.get(0).x(), 0.01);
	}

	public void testKernOffTurnsOffBothLayoutAndDrawing() throws Exception {
		final List<Run> runs = runs("<p style=\"font-feature-settings:'kern' 0\">To oT</p>");
		assertEquals(runs.toString(), 2, runs.size());
		assertEquals("(To)", runs.get(0).tj());
		assertEquals(22.22 + 5, runs.get(1).x() - runs.get(0).x(), 0.01);
	}
}
