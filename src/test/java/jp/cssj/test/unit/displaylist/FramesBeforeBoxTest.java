package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
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
 * A box that only the frames before it keep from the sheet (2026-11-02, triage §157, fit seed 57861579). {@code
 * FLAGS_FIRST} means the flow is at 0, which the padding and the margins above it are not part of, so the box stayed at
 * the start of the page and ran off it. On a vertical page a box of horizontal writing has the head of each line on the
 * side that runs off: on A4 a box of 560pt behind 50pt of padding lost 15pt of every line.
 */
public class FramesBeforeBoxTest extends TestCase {
	private static final String DOCUMENT = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
			+ "@page { size: 595pt 842pt; margin: 0 } body { margin: 0; font: 10pt/1.2 serif; writing-mode: vertical-rl }"
			+ "p, div, td { margin: 0; padding: 0 }</style></head><body>%s</body></html>";

	private static final Pattern TEXT = Pattern.compile("x=([-\\d.]+) y=([-\\d.]+) Text\\[\"(T\\d+)\"");

	private static final String WORDS = "T1 " + "word ".repeat(300);

	private static final String BOX = "writing-mode: horizontal-tb; width: 560pt";

	public void testBoxPastPadding() throws Exception {
		assertOnTheSheet(dump(page("<div style=\"padding-right: 50pt\"><div style=\"" + BOX + "\">" + WORDS
				+ "</div></div><p>T9</p>")));
	}

	public void testBoxPastTwoLevelsOfPadding() throws Exception {
		assertOnTheSheet(dump(page("<div style=\"padding-right: 30pt\"><div style=\"padding-right: 30pt\"><div style=\""
				+ BOX + "\">" + WORDS + "</div></div></div><p>T9</p>")));
	}

	public void testInlineBlockPastPadding() throws Exception {
		assertOnTheSheet(dump(page("<div style=\"padding-right: 50pt\"><div style=\"display: inline-block; " + BOX
				+ "\">" + WORDS + "</div></div><p>T9</p>")));
	}

	/** A margin of the box itself goes at the head of a page, as in a browser after a break. */
	public void testOwnMarginAtTheStartOfTheDocument() throws Exception {
		assertOnTheSheet(dump(page("<div style=\"margin-right: 50pt; " + BOX + "\">" + WORDS + "</div><p>T9</p>")));
	}

	/** Moved to the next page by the text before it, the box kept its margin there and ran off that page. */
	public void testOwnMarginOnTheNextPage() throws Exception {
		assertOnTheSheet(dump(page("<p>T8</p><div style=\"margin-right: 15pt; writing-mode: horizontal-tb; width: 590pt\">"
				+ WORDS + "</div><p>T9</p>")));
	}

	/** With room on the sheet: the margin goes, and the box stands at the start of the page area, past the end of it. */
	public void testOwnMarginWithRoomOnTheSheet() throws Exception {
		final File dir = dump("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>@page { size: 140pt 300pt; margin: 20pt }"
				+ "body { margin: 0; font: 10pt/1.2 serif; writing-mode: vertical-rl } p, div { margin: 0; padding: 0 }</style>"
				+ "</head><body><div style=\"margin-right: 10pt; writing-mode: horizontal-tb; width: 115pt\">T1</div><p>T9</p>"
				+ "<p>T10</p></body></html>");
		final Matcher m = TEXT.matcher(Files.readString(pages(dir)[0].toPath(), StandardCharsets.UTF_8));
		assertTrue(m.find());
		assertEquals("T1", m.group(3));
		assertEquals(-15, Double.parseDouble(m.group(1)), 0.5);
	}

	/** A cell's content past its frames in a flex item: on the sheet, where the row splits as in Chrome. */
	public void testBoxInAFlexItemInACell() throws Exception {
		assertOnTheSheet(dump(page("<table style=\"border-collapse: collapse\"><tr><td style=\"padding: 0\"><div style=\"display: flex\">"
				+ "<div style=\"padding-right: 50pt\"><div style=\"" + BOX + "\">" + WORDS + "</div></div></div></td><td>T2</td>"
				+ "</tr></table>")));
	}

	/** Nothing broke the page for a float that does not split, and nothing after it needed to. */
	public void testFloatPastPadding() throws Exception {
		assertOnTheSheet(dump(page("<div style=\"padding-right: 50pt\"><div style=\"float: left; " + BOX + "\">" + WORDS
				+ "</div></div><p>T9</p>")));
	}

	/** The seed: the float splits, what does not is inside it. */
	public void testBoxInAFloatPastMargins() throws Exception {
		assertOnTheSheet(dump("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>@page { size: 60pt 60pt; margin: 0 }"
				+ "body { margin: 0; font: 7pt/1.2 serif; writing-mode: vertical-rl } p, div { margin: 0; padding: 0 }"
				+ "</style></head><body><div style=\"margin: 7pt 6pt; padding: 5pt\"><div style=\"float: left\">"
				+ "<div style=\"writing-mode: horizontal-tb; width: 56pt\"><p>T0</p><p>T1</p></div></div></div>"
				+ "</body></html>"));
	}

	/** A horizontal page: the box of vertical lines runs off the bottom. */
	public void testHorizontalPage() throws Exception {
		assertOnTheSheet(dump("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>@page { size: 595pt 842pt; margin: 0 }"
				+ "body { margin: 0; font: 10pt/1.2 serif } div { margin: 0; padding: 0 }</style></head><body>"
				+ "<div style=\"padding-top: 50pt\"><div style=\"writing-mode: vertical-rl; height: 800pt; width: 300pt\">"
				+ WORDS + "</div></div><div>T9</div></body></html>"));
	}

	/** What overflows into the margin of the page stays on the sheet, and on this page. */
	public void testOverflowIntoThePageMargin() throws Exception {
		final File dir = dump("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>@page { size: 300pt 300pt; margin: 20pt }"
				+ "body { margin: 0; font: 10pt/1.2 serif; writing-mode: vertical-rl } div { margin: 0; padding: 0 }</style>"
				+ "</head><body><div style=\"padding-right: 10pt\"><div style=\"writing-mode: horizontal-tb; width: 255pt\">T1</div>"
				+ "</div></body></html>");
		assertEquals(1, pages(dir).length);
	}

	/** Past the cut line but on the sheet, which the frames after the box move: the row stays where it is. */
	public void testCellPastTheCutLineOnly() throws Exception {
		final File dir = dump(page("<table style=\"border-collapse: collapse\"><tr><td style=\"padding: 40pt\">"
				+ "<div style=\"writing-mode: horizontal-tb; width: 540pt\">" + WORDS + "</div></td><td>T2</td></tr></table>"));
		assertEquals(1, pageOf(dir, "T1"));
		assertEquals(1, pageOf(dir, "T2"));
	}

	/** Chrome moves it too, and with the other cell in the same way the whole row goes. */
	public void testBoxInACellPastItsPadding() throws Exception {
		final String cell = "<td style=\"padding: 0 50pt 0 0\"><div style=\"" + BOX + "\">" + WORDS + "</div></td>";
		assertOnTheSheet(dump(page("<table style=\"border-collapse: collapse\"><tr>" + cell + "</tr></table><p>T9</p>")));
		assertOnTheSheet(dump(page("<table style=\"border-collapse: collapse\"><tr>" + cell + cell + "</tr></table><p>T9</p>")));
	}

	private static String page(final String body) {
		return String.format(DOCUMENT, body);
	}

	private static int pageOf(final File dir, final String token) throws Exception {
		final File[] pages = pages(dir);
		for (int i = 0; i < pages.length; ++i) {
			if (Files.readString(pages[i].toPath(), StandardCharsets.UTF_8).contains("Text[\"" + token + "\"")) {
				return i + 1;
			}
		}
		return -1;
	}

	private static File[] pages(final File dir) {
		final File[] pages = dir.listFiles((d, name) -> name.startsWith("page-"));
		Arrays.sort(pages);
		return pages;
	}

	private static void assertOnTheSheet(final File dir) throws Exception {
		int tokens = 0;
		for (final File page : pages(dir)) {
			final String dump = Files.readString(page.toPath(), StandardCharsets.UTF_8);
			final Matcher m = TEXT.matcher(dump);
			while (m.find()) {
				++tokens;
				final double x = Double.parseDouble(m.group(1)), y = Double.parseDouble(m.group(2));
				assertTrue(m.group(3) + " x=" + x + " in " + page.getName(), x >= 0 && x < 595);
				assertTrue(m.group(3) + " y=" + y + " in " + page.getName(), y >= 0 && y < 842);
			}
		}
		assertTrue(tokens > 0);
	}

	private static File dump(final String html) throws Exception {
		final File dir = Files.createTempDirectory("frames-before-box").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		return dir;
	}
}
