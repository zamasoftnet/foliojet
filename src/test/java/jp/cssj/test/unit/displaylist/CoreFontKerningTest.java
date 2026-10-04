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
 * 中核書体(AFM の Times-Roman)の kerning を、組版と描画で同じ組・同じ向きに掛けることを固定します
 * (2026-10-04)。
 *
 * <p>
 * それまで描画は今の字と前の字の組(逆の組)を引き、「To」(KPX T o -80)が詰まらず「oT」が詰まっていた。
 * 組版は KPX の負(詰める)を正(詰める)の約束の呼び出し側へそのまま返し、「To」を広げていた。
 * </p>
 */
public class CoreFontKerningTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** 1 つの BT〜ET: Td の x と、TJ の配列を字と数の並びにしたもの。 */
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
		// T 611 + o 500 - 80 = 1031 → 20.62pt、空白 250 → 5pt
		assertEquals(20.62 + 5, runs.get(1).x() - runs.get(0).x(), 0.01);
	}

	public void testKernOffTurnsOffBothLayoutAndDrawing() throws Exception {
		final List<Run> runs = runs("<p style=\"font-feature-settings:'kern' 0\">To oT</p>");
		assertEquals(runs.toString(), 2, runs.size());
		assertEquals("(To)", runs.get(0).tj());
		assertEquals(22.22 + 5, runs.get(1).x() - runs.get(0).x(), 0.01);
	}
}
