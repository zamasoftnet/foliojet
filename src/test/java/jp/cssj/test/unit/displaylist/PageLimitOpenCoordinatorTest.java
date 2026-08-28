package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTIMessageCodes;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** ページ上限でSAX入力が途中終了しても、開いたFlex/Gridを対称に畳む。 */
public class PageLimitOpenCoordinatorTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testForceLimitInsideNestedCoordinatorsProducesPdf() throws Exception {
		final StringBuilder html = new StringBuilder("<!doctype html><html><head><style>"
				+ "@page{size:200pt 120pt;margin:5pt}.f{display:flex;flex-direction:column}"
				+ ".g{display:grid;grid-template-columns:1fr}</style></head><body>"
				+ "<div class=f><div class=f><div class=g>");
		for (int i = 0; i < 200; ++i) {
			html.append("<div style='height:18pt'>ROW").append(i).append("</div>");
		}
		html.append("</div></div></div></body></html>");

		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		TranscoderException aborted = null;
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.page-limit", "1");
			session.property("output.page-limit.abort", "force");
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.toString().getBytes(StandardCharsets.UTF_8)),
					URI.create("urn:test:page-limit-open-coordinator"), "text/html", "UTF-8");
		} catch (TranscoderException e) {
			aborted = e;
		} finally {
			session.close();
		}
		assertNotNull("ページ上限による中断通知がありません", aborted);
		if (aborted.getCode() != CTIMessageCodes.INFO_ABORT) {
			throw new AssertionError("内部例外で中断しました: code=" + aborted.getCode() + ", message="
					+ aborted.getMessage(), aborted.getCause());
		}
		assertEquals("内部例外ではなく通常中断になるべきです", CTIMessageCodes.INFO_ABORT,
				aborted.getCode());
	}
}
