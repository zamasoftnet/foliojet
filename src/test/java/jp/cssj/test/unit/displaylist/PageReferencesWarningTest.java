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
 * Verify warnings for documents using {@code target-counter()} while page references
 * ({@code processing.page-references}) are disabled (2026-10-04, TECH-20261003-004 item ⑩).
 * Previously this silently yielded empty output, leaving page numbers missing from the
 * Jigen Ango book's table of contents. Decimal numbers now work even in one-pass PDFs
 * (OnePassTargetCounterTest), so check a format unsuitable for a field (lower-roman) here.
 */
public class PageReferencesWarningTest extends TestCase {
	private static final String HTML = """
			<!DOCTYPE html>
			<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
			<style>a::after { content: leader(".") target-counter(attr(href), page, lower-roman) }</style></head><body>
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
