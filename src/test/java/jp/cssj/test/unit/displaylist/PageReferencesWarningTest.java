package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

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
 *
 * <p>
 * Since 2026-10-08 the counters of elements with an id are collected whenever the document is laid out more than
 * once, so {@code target-counter()} and {@code target-counters()} need only two or more passes; {@code target-text()}
 * still needs the property (it captures the text of every element with an id).
 * </p>
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

	private static final String TWO_PASS_HTML = """
			<!DOCTYPE html>
			<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
			<style>
			a.c::after { content: " [" target-counter(attr(href), page, lower-roman) "]" }
			a.s::after { content: " {" target-counters(attr(href), page, ".", upper-roman) "}" }
			h1 { break-before: page }
			</style></head><body>
			<p><a class="c" href="#c1">One</a></p>
			<p><a class="c" href="#c2">Two</a></p>
			<p><a class="s" href="#c2">Three</a></p>
			<h1 id="c1">C1</h1><h1 id="c2">C2</h1>
			</body></html>
			""";

	private static final String TARGET_TEXT_HTML = """
			<!DOCTYPE html>
			<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
			<style>a::after { content: " (" target-text(attr(href)) ")" }</style></head><body>
			<p><a href="#c1">See</a></p>
			<h1 id="c1">Chapter</h1>
			</body></html>
			""";

	public void testTwoPassesNeedNoPageReferencesForTargetCounter() throws Exception {
		final List<String> warnings = new ArrayList<>();
		final String text = text(convert(TWO_PASS_HTML, false, 2, warnings));
		assertEquals("2 パスなら設定なしで警告なし: " + warnings, 0, warnings.size());
		assertTrue("target-counter の番号(ii)が出る: " + text, text.contains("One [ii]"));
		assertTrue("target-counter の番号(iii)が出る: " + text, text.contains("Two [iii]"));
		assertTrue("target-counters の番号(III)が出る: " + text, text.contains("Three {III}"));
	}

	public void testTargetTextStillNeedsPageReferences() throws Exception {
		final List<String> warnings = new ArrayList<>();
		final String off = text(convert(TARGET_TEXT_HTML, false, 2, warnings));
		assertEquals("target-text は設定なしだと警告 1 件", 1, warnings.size());
		assertFalse("target-text は設定なしだと空: " + off, off.contains("(Chapter)"));
		warnings.clear();
		final String on = text(convert(TARGET_TEXT_HTML, true, 2, warnings));
		assertEquals("設定ありなら警告なし: " + warnings, 0, warnings.size());
		assertTrue("設定ありなら target-text が出る: " + on, on.contains("(Chapter)"));
	}

	private static String text(final byte[] pdf) throws Exception {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
		}
	}

	private static int pageReferenceWarnings(final boolean enabled) throws Exception {
		final List<String> warnings = new ArrayList<>();
		convert(HTML, enabled, enabled ? 2 : 1, warnings);
		return warnings.size();
	}

	private static byte[] convert(final String html, final boolean enabled, final int passCount,
			final List<String> warnings) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
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
			}
			session.property("processing.pass-count", String.valueOf(passCount));
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///page-references.xhtml"), "application/xhtml+xml", null);
		} finally {
			session.close();
		}
		return out.toByteArray();
	}
}
