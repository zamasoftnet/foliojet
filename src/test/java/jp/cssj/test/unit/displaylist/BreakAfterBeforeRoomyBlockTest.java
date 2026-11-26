package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import junit.framework.TestCase;

/**
 * A pending break-after breaks before the next block when that block takes room at its start (padding, a margin, a
 * height of its own), as break-before does, so that its start goes to the new page with it, as in Chrome
 * (2026-11-26). Before, the break waited for the first line inside: the padding and the margin stayed on the previous
 * page, a block of a height alone stayed there whole, and sections of a page's height holding only absolutely
 * positioned boxes never broke, the third drawn off the paper (found laying out 9,903 local SVG files a page each;
 * copperpdf4 docs/history/2026-11-25-realweb-test.md section 8). The expected positions are Chrome's.
 */
public class BreakAfterBeforeRoomyBlockTest extends TestCase {
	private static List<String> pages(final String name, final String css, final String body) throws Exception {
		final File dir = new File("local/break-after-roomy-in");
		dir.mkdirs();
		final File input = new File(dir, name + ".html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset='UTF-8'/>"
				+ "<?jp.cssj.property name='output.page-width' value='300pt'?>"
				+ "<?jp.cssj.property name='output.page-height' value='200pt'?>"
				+ "<style>@page{margin:10pt} body{margin:0;font:normal 10pt/1.2 serif} p{margin:0}"
				+ " .a{break-after:page}" + css + "</style></head><body>" + body + "</body></html>",
				StandardCharsets.UTF_8);
		return DisplayListPages.of(input.getPath(), "break-after-roomy-" + name);
	}

	/** Asserts the page (from 1) and the y of a text. */
	private static void at(final List<String> pages, final String text, final int page, final double y) {
		final double[] found = DisplayListPages.find(pages, text);
		assertNotNull(text + " " + pages, found);
		assertEquals(text + " page", page, (int) found[0]);
		assertEquals(text + " y", y, found[1], 0.01);
	}

	public void testMarginIsKept() throws Exception {
		at(pages("margin", "", "<div class='a'>T1</div><div style='margin-top:30pt'>T2</div>"), "T2", 2, 30);
	}

	public void testPaddingGoesWithIt() throws Exception {
		at(pages("padding", "", "<div class='a'>T1</div><div style='padding-top:20pt;background:#eee'>T2</div>"),
				"T2", 2, 20);
	}

	public void testBlockOfAHeightGoesWithIt() throws Exception {
		final List<String> p = pages("height", "",
				"<div class='a'>T1</div><div style='height:50pt;background:#ccc'></div><p>T2</p>");
		at(p, "T2", 2, 50);
		assertEquals(2, p.size());
		assertTrue(p.get(0), !p.get(0).contains("h=50.00"));
	}

	public void testNestedStart() throws Exception {
		at(pages("nested", "", "<div class='a'>T1</div><div><div style='padding-top:10pt'>"
				+ "<p style='margin-top:15pt'>T2</p></div></div>"), "T2", 2, 25);
	}

	/** Each section on a page of its own (T3 was on the second page at y=190, below its 180pt). */
	public void testAbsolutelyPositionedContentOnly() throws Exception {
		final List<String> p = pages("abs",
				" section{height:180pt;position:relative;break-after:page} .x{position:absolute;left:10pt;top:10pt}",
				"<section><div class='x'>T1</div></section><section><div class='x'>T2</div></section>"
						+ "<section><div class='x'>T3</div></section>");
		at(p, "T2", 2, 10);
		at(p, "T3", 3, 10);
	}

	/** A block moved to the new page takes room there: a break-before after it breaks again (codex). */
	public void testBreakBeforeAfterAMovedBlock() throws Exception {
		at(pages("moved-then-before", "", "<div class='a'>T1</div><div style='height:40pt;background:red'></div>"
				+ "<div style='break-before:page'>T2</div>"), "T2", 3, 0);
	}

	/** The same break point: the later value decides the side, one break (css-break-3 3.1; codex). */
	public void testBreakAfterAndBeforeCombine() throws Exception {
		at(pages("recto-verso", "", "<div style='break-after:recto'>T1</div>"
				+ "<div style='padding-top:1pt;break-before:verso'>T2</div>"), "T2", 2, 1);
	}

	/** No room taken, no page: an empty block, or a margin that calc() makes 0 (codex). */
	public void testEmptyBlockMakesNoPage() throws Exception {
		assertEquals(1, pages("trailing-empty", "", "<div class='a'>T1</div><div></div>").size());
		assertEquals(1, pages("calc-zero", " body{width:200pt}",
				"<div class='a'>T1</div><div style='margin-top:calc(10pt - 5%)'></div>").size());
	}

	/** Unchanged: a start border, and break-before. */
	public void testBorderAndBreakBeforeAsBefore() throws Exception {
		at(pages("border", "", "<div class='a'>T1</div><div style='border-top:1pt solid;margin-top:10pt'>T2</div>"),
				"T2", 2, 11);
		at(pages("before", "", "<div>T1</div><div style='break-before:page;margin-top:30pt'>T2</div>"), "T2", 2,
				30);
	}
}
