package jp.cssj.test.unit._0125_footnote;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verifies that unsupported footnote label content produces <b>a warning, not a conversion failure</b>
 * (2026-09-02, design review §1-6).
 *
 * <p>
 * Previously, any {@code content} in {@code ::footnote-call} other than strings and
 * {@code counter(footnote)} (e.g. {@code counter(footnote, lower-roman)}) caused
 * {@code FootnoteOverflowException} and failed the entire document. The limitation is now reported
 * with 2823, and layout uses only numbers and strings.
 * </p>
 */
public class FootnoteLabelWarningTest extends TestCase {
	private static final String HTML = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
			+ "@page{size:200pt 200pt;margin:10pt}body{margin:0}"
			+ "::footnote-call{content:\"[\" counter(footnote, lower-roman) \"]\"}"
			+ "</style></head><body><p>Alpha<span style=\"float:footnote\">first note</span> beta."
			+ "<span style=\"float:footnote\">second note</span> gamma.</p></body></html>";

	/** An unsupported label emits 2823 once, and the PDF is completed. */
	public void testUnsupportedLabelWarnsOnceAndStillConverts() throws Exception {
		final List<String[]> messages = new ArrayList<>();
		final ByteArrayOutputStream pdf = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(pdf)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.setMessageHandler((code, args, mes) -> {
				final String[] m = new String[(args == null ? 0 : args.length) + 1];
				m[0] = Integer.toString(code & 0xFFFF);
				if (args != null) {
					System.arraycopy(args, 0, m, 1, args.length);
				}
				messages.add(m);
			});
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///footnote-label.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		final String code = Integer.toString(MessageCodes.WARN_INEFFECTIVE_CSS_COMBINATION & 0xFFFF);
		final List<String[]> reported = messages.stream().filter(m -> m[0].equals(code)).toList();
		assertEquals("the unsupported label must be reported exactly once (two calls share one style)", 1,
				reported.size());
		assertEquals("::footnote-call content", reported.get(0)[1]);
		final String head = pdf.toString(StandardCharsets.ISO_8859_1);
		assertTrue("the document must still convert to a PDF: " + head.substring(0, Math.min(8, head.length())),
				head.startsWith("%PDF"));
	}
}
