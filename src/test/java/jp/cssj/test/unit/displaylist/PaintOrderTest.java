package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** {@code paint-order}による文字の塗り順をPDFのテキスト描画モードで固定します。 */
public class PaintOrderTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testStrokeFillUsesTwoPasses() throws Exception {
		assertEquals(List.of(1, 0), textModes("stroke fill"));
	}

	public void testNormalUsesSingleFillStrokePass() throws Exception {
		assertEquals(List.of(2), textModes("normal"));
	}

	private static List<Integer> textModes(final String paintOrder) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:100pt 100pt;margin:0}body{margin:0;font:12pt serif}"
				+ "p{margin:0;-cssj-text-stroke:1pt #000;paint-order:" + paintOrder
				+ "}</style></head><body><p>PAINT</p></body></html>";
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)), URI.create("file:///paint-order.html"),
					"text/html", "UTF-8");
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final PDFStreamParser parser = new PDFStreamParser(doc.getPage(0));
			final List<Integer> modes = new ArrayList<>();
			final List<Object> operands = new ArrayList<>();
			Object token;
			while ((token = parser.parseNextToken()) != null) {
				if (!(token instanceof final Operator operator)) {
					operands.add(token);
					continue;
				}
				if ("Tr".equals(operator.getName()) && !operands.isEmpty()
						&& operands.get(operands.size() - 1) instanceof final COSNumber number) {
					modes.add(number.intValue());
				}
				operands.clear();
			}
			return modes;
		}
	}
}
