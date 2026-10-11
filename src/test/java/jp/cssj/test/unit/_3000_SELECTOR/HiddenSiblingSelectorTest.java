package jp.cssj.test.unit._3000_SELECTOR;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * An element with display: none is still a sibling for the selectors (2026-10-11): :nth-child() counted from 1 again
 * after it and the next element took :first-child, so that .w > :nth-child(n+2) hid every other child (mozilla-blog's
 * section:nth-child(n+4) hid from the 5th on). The paragraphs left shown are Chrome's (copper4/tmp/ab/repro/nth/n4.html).
 */
public class HiddenSiblingSelectorTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([A-Z][0-9])\"");

	private static final String[][] CASES = { { ".w > :nth-child(n+2)", "1" }, { ".w > :nth-child(-n+2)", "3 4 5 6" },
			{ ".w > :nth-last-child(n+2)", "6" }, { ".w > p:nth-of-type(n+2)", "1" },
			{ ".w > p:nth-last-of-type(-n+2)", "1 2 3 4" }, { ".w > :nth-child(2n+1)", "2 4 6" },
			{ ".w > :nth-child(3)", "1 2 4 5 6" }, { ".w > p:first-child, .w > p:first-child + p", "3 4 5 6" },
			{ ".w > .h, .w > .h ~ p", "1" }, { ".w > p:first-child", "2 3 4 5 6" } };

	public void testHiddenSiblings() throws Exception {
		final StringBuilder css = new StringBuilder();
		final StringBuilder body = new StringBuilder();
		for (int i = 0; i < CASES.length; ++i) {
			final char letter = (char) ('A' + i);
			css.append(CASES[i][0].replace(".w", ".w" + i)).append(" { display: none }\n");
			body.append("<div class=\"w").append(i).append("\">");
			for (int n = 1; n <= 6; ++n) {
				body.append(n == 2 ? "<p class=\"h\">" : "<p>").append(letter).append(n).append("</p>");
			}
			body.append("</div>");
		}
		final List<String> shown = shown(css.toString(), body.toString());
		for (int i = 0; i < CASES.length; ++i) {
			final char letter = (char) ('A' + i);
			final StringBuilder got = new StringBuilder();
			for (final String t : shown) {
				if (t.charAt(0) == letter) {
					got.append(got.length() == 0 ? "" : " ").append(t.substring(1));
				}
			}
			assertEquals(CASES[i][0], CASES[i][1], got.toString());
		}
	}

	/** An inline svg is a sibling too: the paragraph after it is no :first-child (codex). */
	public void testInlineObjectSibling() throws Exception {
		final List<String> shown = shown(".v > p:first-child, .v > i:first-child + p { display: none }",
				"<div class=\"v\"><svg width=\"1\" height=\"1\"></svg><p>X1</p></div>"
						+ "<div class=\"v\"><svg width=\"1\" height=\"1\"></svg><i style=\"display: none\"></i><p>Y1</p></div>");
		assertEquals("[X1, Y1]", shown.toString());
	}

	/** The paragraphs drawn, in order (two passes for :nth-last-child() and :nth-last-of-type()). */
	private static List<String> shown(final String css, final String body) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 400pt 900pt; margin: 20pt } body { margin: 0; font: 8pt serif } p { margin: 0 }\n" + css
				+ "</style></head><body>" + body + "</body></html>";
		final File dir = Files.createTempDirectory("hidden-sibling-selector").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.property("processing.pass-count", "2");
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final String page = Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		final List<String> shown = new ArrayList<>();
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			shown.add(m.group(1));
		}
		return shown;
	}
}
