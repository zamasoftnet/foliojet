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
 * 脚注ラベルの未対応の内容は<b>変換の失敗ではなく警告</b>であることを固定します
 * (2026-09-02、設計レビュー §1-6)。
 *
 * <p>
 * 以前は{@code ::footnote-call}の{@code content}に文字列と
 * {@code counter(footnote)}以外(例えば{@code counter(footnote, lower-roman)})が
 * あると{@code FootnoteOverflowException}で文書全体が失敗していた。仕様の
 * 制限は 2823 で知らせて、番号と文字列だけで組む。
 * </p>
 */
public class FootnoteLabelWarningTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final String HTML = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
			+ "@page{size:200pt 200pt;margin:10pt}body{margin:0}"
			+ "::footnote-call{content:\"[\" counter(footnote, lower-roman) \"]\"}"
			+ "</style></head><body><p>Alpha<span style=\"float:footnote\">first note</span> beta."
			+ "<span style=\"float:footnote\">second note</span> gamma.</p></body></html>";

	/** 未対応のラベルは 2823 を1回出して、PDF は出来上がる。 */
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
