package jp.cssj.test.unit._0510_text_spacing;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * No Japanese/Latin space (text-autospace) and no punctuation pair trim across a line break (2026-10-09, report from
 * tech: a delivery address 「丁目1番41号」 wrapped its 「号」 to the next line 0.25em in from the line start). The pair
 * adjustment is put before the later character; a line that broke between two runs (another font, or a zero-width
 * inline boundary) kept it at the start of the next line, where a break inside a run already took it back. CSS Text 4
 * text-autospace and JLREQ: nothing at the line start, nothing at the line end; unbroken pairs keep their space.
 *
 * <p>
 * Font size 20pt and page margin 20pt: a line starts at x = 20. Measured in the PDF (PDFBox), as the reporter did.
 * </p>
 */
public class AutospaceLineStartTest extends TestCase {
	private static final String HEAD = """
			<!DOCTYPE html><html lang="ja"><head><meta charset="utf-8"/><style>
			@page { size: 148mm 210mm; margin: 20pt }
			body { margin: 0; font-family: sans-serif; font-size: 20pt; line-height: 1.5 }
			div { margin: 0 0 8pt 0; padding: 0 }
			</style></head><body>
			""";

	/** The run after the break starts the line: Latin → Japanese and Japanese → Latin, by font or by span. */
	public void testWrappedRunStartsAtLineStart() throws Exception {
		final List<TextPosition> all = positions("""
				<div style="width: 6em">丁目1番41号</div>
				<div style="width: 6em">丁目1番41<span>号</span></div>
				<div style="width: 3.4em">番地A号</div>
				<div style="width: 2.5em">丁目<span>12</span></div>
				""");
		final List<TextPosition> go = all(all, "号");
		assertEquals("号 が 3 つ", 3, go.size());
		for (final TextPosition t : go) {
			assertEquals("折り返した 号 は行頭", 20, t.getXDirAdj(), 0.5);
		}
		assertEquals("折り返した 12 は行頭", 20, first(all, "1", 4).getXDirAdj(), 0.5);
	}

	/** A pair trim between punctuation in two runs is not kept at the start of the next line either (it was 10pt in). */
	public void testPunctuationTrimNotAtLineStart() throws Exception {
		final List<TextPosition> all = positions("""
				<div style="width: 5em"><span>あいうえ」</span><span>「かき」</span></div>
				<div style="width: 5em"><span>あいうえ、</span>（かき）</div>
				""");
		assertEquals("「 は行頭", 20, first(all, "「", 0).getXDirAdj(), 0.5);
		assertEquals("（ は行頭", 20, first(all, "（", 0).getXDirAdj(), 0.5);
	}

	/** Unbroken pairs keep the quarter-em; a right-aligned line that broke ends at its box, with no space after. */
	public void testUnbrokenPairsAndLineEnd() throws Exception {
		final List<TextPosition> all = positions("""
				<div style="width: 7.5em">丁目1番41<span>号</span></div>
				<div style="width: 6em; text-align: right">丁目1番41号</div>
				""");
		final TextPosition one = first(all, "1", 1);
		final TextPosition go = first(all, "号", 0);
		assertEquals("折り返さないときは 0.25em(5pt)の空き", 5,
				go.getXDirAdj() - (one.getXDirAdj() + one.getWidthDirAdj()), 0.5);
		final TextPosition lineEnd = first(all, "1", 3);
		assertEquals("右寄せの行末に空きを残さない", 140, lineEnd.getXDirAdj() + lineEnd.getWidthDirAdj(), 0.5);
	}

	/** Vertical writing: the wrapped 「号」 starts its column at the top, as 「丁」 does the first. */
	public void testVertical() throws Exception {
		final List<TextPosition> all = positions("""
				<div style="writing-mode: vertical-rl; height: 6em">丁目1番41<span>号</span></div>
				""");
		assertEquals("縦書きで折り返した 号 は列の頭", first(all, "丁", 0).getYDirAdj(), first(all, "号", 0).getYDirAdj(),
				0.5);
	}

	private static TextPosition first(final List<TextPosition> all, final String s, final int skip) {
		final List<TextPosition> found = all(all, s);
		assertTrue(s + " が " + (skip + 1) + " 個ない: " + found.size(), found.size() > skip);
		return found.get(skip);
	}

	private static List<TextPosition> all(final List<TextPosition> all, final String s) {
		final List<TextPosition> found = new ArrayList<>();
		for (final TextPosition t : all) {
			if (s.equals(t.getUnicode())) {
				found.add(t);
			}
		}
		return found;
	}

	private static List<TextPosition> positions(final String body) throws Exception {
		final File input = File.createTempFile("autospace-line-start", ".html");
		input.deleteOnExit();
		java.nio.file.Files.writeString(input.toPath(), HEAD + body + "</body></html>", StandardCharsets.UTF_8);
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		} finally {
			session.close();
		}
		final List<TextPosition> all = new ArrayList<>();
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final PDFTextStripper stripper = new PDFTextStripper() {
				@Override
				protected void processTextPosition(final TextPosition text) {
					all.add(text);
					super.processTextPosition(text);
				}
			};
			stripper.setSuppressDuplicateOverlappingText(false);
			stripper.getText(doc);
		}
		assertFalse("テキストが出力されていません", all.isEmpty());
		return all;
	}
}
