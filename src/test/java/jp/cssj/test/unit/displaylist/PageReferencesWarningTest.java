package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 頁参照({@code processing.page-references})が無効なのに{@code target-counter()}
 * を使った文書へ警告を出すことを固定します(2026-10-04、TECH-20261003-004 の⑩。
 * 以前は黙って空になり、時限暗号の本の目次の頁番号が抜けたまま出た)。
 */
public class PageReferencesWarningTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final String HTML = """
			<!DOCTYPE html>
			<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
			<style>a::after { content: leader(".") target-counter(attr(href), page) }</style></head><body>
			<p><a href="#c1">One</a></p>
			<p><a href="#c2">Two</a></p>
			<h1 id="c1">C1</h1><h1 id="c2">C2</h1>
			</body></html>
			""";

	public void testWarnsOnceWhenPageReferencesAreDisabled() throws Exception {
		assertEquals("警告 2823(頁参照)の数", 1, pageReferenceWarnings(false));
	}

	public void testNoWarningWhenPageReferencesAreEnabled() throws Exception {
		assertEquals("頁参照が有効なのに警告が出た", 0, pageReferenceWarnings(true));
	}

	private static int pageReferenceWarnings(final boolean enabled) throws Exception {
		final List<String> warnings = new ArrayList<>();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
			session.setMessageHandler(new MessageHandler() {
				@Override
				public void message(final short code, final String[] args, final String message) {
					if (code == 0x2823 && message != null && message.contains("processing.page-references")) {
						warnings.add(message);
					}
				}
			});
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			if (enabled) {
				session.property("processing.page-references", "true");
				session.property("processing.pass-count", "2");
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///page-references.xhtml"), "application/xhtml+xml", null);
		} finally {
			session.close();
		}
		return warnings.size();
	}
}
