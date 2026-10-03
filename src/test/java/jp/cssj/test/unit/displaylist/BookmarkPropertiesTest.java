package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * CSS の{@code bookmark-level}・{@code bookmark-label}(css-gcpm-3)を固定します
 * (2026-10-04、TECH-20261003-004 の⑤。以前は未対応のプロパティとして警告され、
 * しおりは h1〜h6 の段数と文字からしか作れなかった)。
 */
public class BookmarkPropertiesTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final String HTML = """
			<!DOCTYPE html>
			<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
			<style>
			.hidden { bookmark-level: none }
			.part { bookmark-level: 1; bookmark-label: "Part " attr(data-n) ": " content() }
			.renamed { bookmark-label: "Renamed" }
			</style></head><body>
			<div class="part" data-n="I">One</div>
			<h1>Alpha</h1>
			<h2 class="hidden">Hidden</h2>
			<h2 class="renamed">Beta</h2>
			<h2>Gamma</h2>
			</body></html>
			""";

	public void testBookmarkLevelAndLabel() throws Exception {
		final List<String> outline = outline(convert(HTML));
		assertEquals("しおり", List.of("1:Part I: One", "1:Alpha", "2:Renamed", "2:Gamma"), outline);
	}

	private static List<String> outline(final byte[] pdf) throws Exception {
		final List<String> result = new ArrayList<>();
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertNotNull("しおりが無い", doc.getDocumentCatalog().getDocumentOutline());
			walk(doc.getDocumentCatalog().getDocumentOutline(), 1, result);
		}
		return result;
	}

	private static void walk(final PDOutlineNode node, final int level, final List<String> result) {
		for (PDOutlineItem item = node.getFirstChild(); item != null; item = item.getNextSibling()) {
			result.add(level + ":" + item.getTitle());
			walk(item, level + 1, result);
		}
	}

	private static byte[] convert(final String html) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.pdf.bookmarks", "true");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///bookmark-properties.xhtml"), "application/xhtml+xml", null);
		} finally {
			session.close();
		}
		return out.toByteArray();
	}
}
