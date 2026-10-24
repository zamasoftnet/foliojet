package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * A float broken page by page after the end of the input is progress for the livelock guard (2026-10-09, rubydoc-api).
 * The input position, the cursor and the break target stay the same at every break while the float gives up a page of
 * its content, and the guard took 32 such breaks for a livelock: it stopped breaking pages and laid the rest of the
 * float out in place, down to 22644pt on the 33rd page. A float carrying less to the next page than at the last break
 * has moved on; 1200 lines of 12pt take 150 pages of 200pt, all on the paper.
 */
public class FloatProgressGuardTest extends TestCase {
	private static final Pattern TEXT_Y = Pattern.compile("y=([-\\d.]+) Text\\[\"Paragraph\"");

	public void testLongFloatAtTheEnd() throws Exception {
		final StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 300pt 200pt; margin: 0 } body { margin: 0; font: 10pt/12pt serif } p { margin: 0 }"
				+ "</style></head><body><div style=\"float: left; width: 100%\">");
		for (int i = 1; i <= 1200; ++i) {
			html.append("<p>Paragraph ").append(i)
					.append(" of the long floated column, which goes on page after page after the end of the input.</p>");
		}
		html.append("</div></body></html>");
		final File dir = Files.createTempDirectory("float-progress-guard").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] pages = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		int count = 0;
		for (final File page : pages) {
			final Matcher m = TEXT_Y.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				++count;
				assertTrue(page.getName() + " paragraph below the page at " + m.group(1),
						Double.parseDouble(m.group(1)) + 12 <= 200 + 0.01);
			}
		}
		assertEquals("paragraphs", 1200, count);
		assertEquals("pages", 150, pages.length);
	}

	/**
	 * A float carry that goes down and back up again is not progress at every other break (2026-10-16, sweep seed 20581114
	 * cut down). Compared with the last carry, the guard cleared its history at every drop and page 6 of this 3-column
	 * multicol on vertical-rl paper was laid out again without end (24,000 times in 30 s; since 7181a2e6). Compared
	 * with the least carry the guard fires as it did before: two pages, every token drawn (past the paper, as before).
	 */
	public void testCarryThatGoesBackUp() throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 60pt 60pt; margin: 0 } body { margin: 0; font: 7pt/1.2 serif; writing-mode: vertical-rl }"
				+ " p, div { margin: 0; padding: 0 }</style></head><body>"
				+ "<div style=\"column-count: 3; column-gap: 6pt\"><p>T1</p>"
				+ "<div style=\"writing-mode: horizontal-tb; width: max-content; min-width: 8em; max-width: 90%\"></div>"
				+ "<div style=\"width: max-content; min-width: 8em; max-width: 90%\"></div>"
				+ "<div style=\"width: fit-content(70%); min-width: 8em; max-width: 90%\"></div>"
				+ "<div style=\"writing-mode: vertical-rl; width: 119pt\"><div style=\"float: right; width: 59pt\"><p>T2</p></div></div>"
				+ "<div style=\"writing-mode: horizontal-tb\"><div style=\"writing-mode: vertical-lr\"><p>T3</p>"
				+ "<div style=\"width: 46pt\"></div><p>T4</p><p>T5</p></div></div></div></body></html>";
		final File dir = Files.createTempDirectory("float-progress-guard").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			// The old guard never ends: abort it instead of hanging the suite
			session.property("processing.time-limit", "30000");
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] pages = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		final StringBuilder all = new StringBuilder();
		for (final File page : pages) {
			all.append(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		for (int i = 1; i <= 5; ++i) {
			assertTrue("T" + i + " drawn", all.indexOf("Text[\"T" + i + "\"") >= 0);
		}
		assertEquals("pages", 2, pages.length);
	}

	/**
	 * A nested break whose cursor goes up and down by turns while growing is a livelock too (2026-10-18, wild seed
	 * 27364217 cut down). The float is split at its column's last 4pt into an empty head and a remainder of its full
	 * size, and each resume broke again 43.5pt farther and then 30pt back; growth was counted only over the last break,
	 * so the count went back to 0 every other break and the page was laid out again until the stack overflowed (19124
	 * too). Counted over the lower of the last two breaks, the guard fires: two pages, each ruby drawn once (past the
	 * paper).
	 */
	public void testCursorThatGoesUpAndDown() throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 60pt 60pt; margin: 10pt } body { margin: 0; font: 11pt/1.2 serif; writing-mode: vertical-lr }"
				+ " p, div { margin: 0; padding: 0 }</style></head><body>"
				+ "<div style=\"column-count: 4; column-gap: 7pt\"><div style=\"writing-mode: vertical-lr; width: 98pt\">"
				+ "<div style=\"float: left\"><span style=\"display: inline-block; width: 28pt; height: 30pt\"></span>"
				+ "<span style=\"display: inline-block; width: 10pt; height: 10pt\"></span></div></div>"
				+ "<div style=\"writing-mode: horizontal-tb\"><p><ruby>T1<rt>T2</rt></ruby><ruby>T3 T4 T5 T6 T7 T8 T9 T10 T11"
				+ " T12 T13 T14<rt>T15 T16 T17 T18 T19 T20 T21 T22 T23 T24</rt></ruby></p></div></div></body></html>";
		final File dir = Files.createTempDirectory("float-progress-guard").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			// The old guard never ends: abort it instead of hanging the suite
			session.property("processing.time-limit", "30000");
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] pages = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		final StringBuilder all = new StringBuilder();
		for (final File page : pages) {
			all.append(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		assertEquals("T1 drawn once", 1, count(all, "RubyUnit[\"T1\" ruby=\"T2\""));
		assertEquals("T3 drawn once", 1, count(all, "RubyUnit[\"T3 T4 T5 T6 T7 T8 T9 T10 T11 T12 T13 T14\""
				+ " ruby=\"T15 T16 T17 T18 T19 T20 T21 T22 T23 T24\""));
		assertEquals("pages", 2, pages.length);
	}

	/**
	 * A nested break whose cursor goes down and up in longer waves while growing is a livelock too (2026-10-25, strict
	 * seed 37576227 cut down; since 8abc2bb0). A float with a min-size of 80pt on 40pt pages holds a multicol with a
	 * column flex of 98pt and a float in it; each resume broke again, 10 to 20 times lower and lower and then higher
	 * again, so the count over the lower of the last two breaks went back to 0 at every wave and the guard fired only
	 * after about 100 nested breaks (59 s). Counted over the least cursor of the run, it fires after 32: five pages,
	 * each text drawn once.
	 */
	public void testCursorInLongWaves() throws Exception {
		final String html = String.join("\n", "<!DOCTYPE html>", "<html><head><meta charset=\"utf-8\">", "<style>",
				"@page { size: 60pt 60pt; margin: 10pt }",
				"body { margin: 0; font: 10pt/1.2 serif; writing-mode: vertical-lr }", "p, div { margin: 0; padding: 0 }",
				"</style></head><body>",
				"<form><input type=\"text\" value=\"x\" size=\"1\"><select><option>x</option><option>y</option></select></form>",
				"<div style=\"column-count: 4\">", "<div style=\"float: right; min-width: 8em\">",
				"<div style=\"column-count: 4\">",
				"<div style=\"display: flex; width: 98pt; flex-direction: column; flex-wrap: wrap-reverse\">",
				"<div style=\"width: 119pt\">", "<p>T187</p>", "</div>", "<div>", "<div style=\"float: right\">",
				"<p>T189</p>", "<div style=\"min-width: 8em\">", "</div>", "</div>", "</div>", "<p>T197</p>", "</div>",
				"</div>", "</div>", "<div style=\"writing-mode: horizontal-tb\">",
				"<div><span style=\"font-size: 8pt\">T198</span></div>", "</div>", "</div>", "</body></html>", "");
		final File dir = Files.createTempDirectory("float-progress-guard").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			// The old guard takes about a minute: abort it instead of slowing the suite
			session.property("processing.time-limit", "30000");
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] pages = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		final StringBuilder all = new StringBuilder();
		for (final File page : pages) {
			all.append(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		for (final String text : new String[] { "T187", "T189", "T197", "T198" }) {
			assertEquals(text + " drawn once", 1, count(all, "Text[\"" + text + "\""));
		}
		assertEquals("pages", 5, pages.length);
	}

	private static int count(final StringBuilder text, final String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
			++n;
		}
		return n;
	}
}
