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
 * Verifies CSS {@code bookmark-level} and {@code bookmark-label} behavior (css-gcpm-3)
 * (2026-10-04, item ⑤ of TECH-20261003-004).
 * Previously, they warned as unsupported properties, and bookmarks (PDF outline) could only be
 * created from h1–h6 levels and text.
 */
public class BookmarkPropertiesTest extends TestCase {
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

	/**
	 * A section split across pages starts its bookmark once (2026-10-08, EPUB brush-up D-14). Each page's fragment of
	 * the section was taken as a new section start, so a chapter got a level-1 bookmark on every page it spans and its
	 * subsections were hung under the copies.
	 */
	public void testFragmentedSectionsOnce() throws Exception {
		final String para = "<p>" + "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(8) + "</p>";
		final String html = """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 105mm 148mm; margin: 10mm }
				body { font-size: 9pt }
				h1, h2 { bookmark-level: none }
				section.ch { bookmark-level: 1; bookmark-label: attr(data-title) }
				section.sec { bookmark-level: 2; bookmark-label: attr(data-title) }
				</style></head><body>
				<section class="ch" data-title="Chapter A"><h1>Chapter A</h1>
				<section class="sec" data-title="Section A.1"><h2>Section A.1</h2>%1$s</section>
				<section class="sec" data-title="Section A.2"><h2>Section A.2</h2>%1$s</section>
				</section>
				<section class="ch" data-title="Chapter B"><h1>Chapter B</h1>%2$s</section>
				</body></html>
				""".formatted(para.repeat(6), para.repeat(3));
		final byte[] pdf = convert(html);
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertTrue("章が頁をまたがない", doc.getNumberOfPages() >= 4);
		}
		assertEquals("しおり", List.of("1:Chapter A", "2:Section A.1", "2:Section A.2", "1:Chapter B"), outline(pdf));
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
