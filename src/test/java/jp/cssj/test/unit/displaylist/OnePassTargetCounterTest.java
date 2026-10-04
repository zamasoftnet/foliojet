package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 1パスのPDFで{@code target-counter()}の頁番号が出ることを固定します(2026-10-04、
 * docs/design/one-pass-target-counter-design.md)。番号は固定幅の欄に組まれ、
 * 後ろの頁の番号は頁から参照した部品へ文書を閉じるときに書かれる。
 */
public class OnePassTargetCounterTest extends TestCase {
	private static final String STYLE = """
			<style>
			nav a::after { content: leader(".") target-counter(attr(href), page) }
			.back::after { content: " (p. " target-counter(attr(href), page) ")" }
			nav.roman a::after { content: leader(".") target-counter(attr(href), page, lower-roman) }
			h1 { break-before: page }
			</style>""";

	private record Output(byte[] pdf, List<String> warnings) {
	}

	private static Output convert(final String body, final String... props) throws Exception {
		final String html = "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/>"
				+ STYLE + "</head><body>" + body + "</body></html>";
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final List<String> warnings = new ArrayList<>();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(new MessageHandler() {
				@Override
				public void message(final short code, final String[] args, final String message) {
					if (code == 0x2822 || code == 0x2823) {
						warnings.add(Integer.toHexString(code) + " " + message);
					}
				}
			});
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			for (int i = 0; i < props.length; i += 2) {
				session.property(props[i], props[i + 1]);
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///one-pass.xhtml"), "application/xhtml+xml", null);
		} finally {
			session.close();
		}
		// 目視確認用に残す(build/test-output/one-pass/)
		final java.nio.file.Path dump = java.nio.file.Path.of("build", "test-output", "one-pass",
				Integer.toHexString(html.hashCode()) + ".pdf");
		java.nio.file.Files.createDirectories(dump.getParent());
		java.nio.file.Files.write(dump, out.toByteArray());
		return new Output(out.toByteArray(), warnings);
	}

	private static String pageText(final byte[] pdf, final int page) throws IOException {
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			final PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(page);
			stripper.setEndPage(page);
			return stripper.getText(doc);
		}
	}

	private static final String CHAPTERS = """
			<nav><p><a href="#one">One</a></p><p><a href="#two">Two</a></p></nav>
			<h1 id="one">Chapter One</h1><p>first</p>
			<h1 id="two">Chapter Two</h1><p>second <a class="back" href="#one">back</a></p>
			""";

	/** 前方参照(目次)は部品で、後方参照はその場で描かれ、どちらも番号が出る。 */
	public void testForwardAndBackwardReferences() throws Exception {
		final Output out = convert(CHAPTERS);
		final String toc = pageText(out.pdf(), 1);
		assertTrue(toc, Pattern.compile("One[ .]*2\\b").matcher(toc).find());
		assertTrue(toc, Pattern.compile("Two[ .]*3\\b").matcher(toc).find());
		final String last = pageText(out.pdf(), 3);
		assertTrue(last, last.contains("(p. 2)"));
		final String raw = new String(out.pdf(), StandardCharsets.ISO_8859_1);
		assertTrue("the forward numbers must be deferred forms", raw.contains("/Subtype /Form"));
		assertEquals("no warning: " + out.warnings(), List.of(), out.warnings());
	}

	/** 同じ頁で後に描かれる要素への参照も部品で埋まる。 */
	public void testSamePageForwardReference() throws Exception {
		final Output out = convert("<nav><p><a href=\"#here\">Here</a></p></nav><p id=\"here\">target</p>");
		final String text = pageText(out.pdf(), 1);
		assertTrue(text, Pattern.compile("Here[ .]*1\\b").matcher(text).find());
	}

	/** 参照先が無ければ空(2パスのときと同じ)。 */
	public void testMissingTargetIsEmpty() throws Exception {
		final Output out = convert("<nav><p><a href=\"#none\">Nowhere</a></p></nav>");
		final String text = pageText(out.pdf(), 1);
		assertTrue(text, text.contains("Nowhere"));
		assertFalse(text, Pattern.compile("\\d").matcher(text).find());
	}

	/** 欄の桁数を超える番号は左へはみ出して描かれ、警告が1回出る。 */
	public void testOverflowExtendsLeftAndWarnsOnce() throws Exception {
		final StringBuilder body = new StringBuilder("<nav>");
		for (int i = 1; i <= 11; ++i) {
			body.append("<p><a href=\"#c").append(i).append("\">C").append(i).append("</a></p>");
		}
		body.append("</nav>");
		for (int i = 1; i <= 11; ++i) {
			body.append("<h1 id=\"c").append(i).append("\">Chapter ").append(i).append("</h1>");
		}
		final Output out = convert(body.toString(), "processing.target-counter.digits", "1");
		final String toc = pageText(out.pdf(), 1);
		assertTrue(toc, Pattern.compile("C11[ .]*12\\b").matcher(toc).find());
		final long overflow = out.warnings().stream().filter(w -> w.contains("processing.target-counter.digits"))
				.count();
		assertEquals(out.warnings().toString(), 1, overflow);
	}

	/** 番号は欄の右端に揃う(1桁と2桁の右端が同じ)。基準線は本文と同じ。 */
	public void testNumbersAreRightAlignedOnTheBaseline() throws Exception {
		final StringBuilder body = new StringBuilder("<nav>");
		for (int i = 1; i <= 10; ++i) {
			body.append("<p><a href=\"#c").append(i).append("\">C").append(i).append("</a></p>");
		}
		body.append("</nav>");
		for (int i = 1; i <= 10; ++i) {
			body.append("<h1 id=\"c").append(i).append("\">Chapter ").append(i).append("</h1>");
		}
		final Output out = convert(body.toString());
		// 行(基準線の y)ごとに、先頭の字の y と最後の数字の右端を集める
		final Map<Integer, List<TextPosition>> lines = new TreeMap<>();
		try (PDDocument doc = Loader.loadPDF(out.pdf())) {
			final PDFTextStripper stripper = new PDFTextStripper() {
				@Override
				protected void processTextPosition(final TextPosition text) {
					lines.computeIfAbsent((int) Math.round(text.getYDirAdj()), k -> new ArrayList<>()).add(text);
				}
			};
			stripper.setStartPage(1);
			stripper.setEndPage(1);
			stripper.getText(doc);
		}
		final List<Float> rights = new ArrayList<>();
		for (final List<TextPosition> line : lines.values()) {
			final TextPosition first = line.get(0);
			final TextPosition last = line.get(line.size() - 1);
			if (!first.getUnicode().equals("C") || !Character.isDigit(last.getUnicode().charAt(0))) {
				continue;
			}
			assertEquals("the number sits on the line's baseline", first.getYDirAdj(), last.getYDirAdj(), 0.01f);
			rights.add(last.getXDirAdj() + last.getWidthDirAdj());
		}
		assertEquals(lines.toString(), 10, rights.size());
		for (final float right : rights) {
			assertEquals("right edges: " + rights, rights.get(0), right, 0.05f);
		}
	}

	/** 欄にできない書式(lower-roman)は従来どおり空で、頁参照の警告が出る。 */
	public void testLowerRomanKeepsTheTwoPassRequirement() throws Exception {
		final Output out = convert(CHAPTERS.replace("<nav>", "<nav class=\"roman\">"));
		final String toc = pageText(out.pdf(), 1);
		assertFalse(toc, Pattern.compile("One[ .]*ii\\b").matcher(toc).find());
		assertEquals(out.warnings().toString(), 1,
				out.warnings().stream().filter(w -> w.contains("processing.page-references")).count());
	}

	/** 2パスの文書は今までどおり番号を文字で組む(部品を使わない)。 */
	public void testTwoPassesStillTypesetText() throws Exception {
		final Output out = convert(CHAPTERS, "processing.page-references", "true", "processing.pass-count", "2");
		final String toc = pageText(out.pdf(), 1);
		assertTrue(toc, Pattern.compile("One[ .]*2\\b").matcher(toc).find());
		final String raw = new String(out.pdf(), StandardCharsets.ISO_8859_1);
		assertFalse("two passes do not need deferred forms", raw.contains("/Subtype /Form"));
	}
}
