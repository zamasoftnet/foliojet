package jp.cssj.test.unit._0420_inline;

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
 * The collapsible spaces at the end of a line go though an inline end or start with padding or a border follows, and
 * letter-spacing goes after a space too (2026-10-11, NEXT-SESSION §5-9; copper4/tmp/ab/repro/ws/pad.html). Chrome, with
 * a monospace font of 20px (9pt a character): {@code <span style="padding-right:12px">AAAA </span>} is 45pt wide, as
 * without the space, also right-aligned in a 225pt line (at x=180); with a border-right of 6px, 40.5pt; an empty span
 * with padding-left after "AAAA " starts at 36pt; "AA AA" with letter-spacing: 2px puts the second word at 31.5pt. The
 * test compares with the same text without the space, so that it does not depend on the font.
 */
public class TrailingSpaceFrameTest extends TestCase {
	private static final Pattern ITEM = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) (AbsoluteRectFrame\\[w=([\\d.]+)|Text\\[\"([^\"]*)\")");

	public void testFramedTrailingSpaces() throws Exception {
		final double plain = width(items("<div><span style=\"padding-right: 12px; background: #ccc\">AAAA</span></div>"));
		final List<String[]> padded = items("<div><span style=\"padding-right: 12px; background: #ccc\">AAAA </span></div>");
		assertEquals("padding-right", plain, width(padded), 0.01);
		final List<String[]> right = items(
				"<div style=\"text-align: right\"><span style=\"padding-right: 12px; background: #ccc\">AAAA </span></div>");
		assertEquals("right-aligned: the end", 225, Double.parseDouble(frame(right)[0]) + width(right), 0.01);
		assertEquals("right-aligned: width", plain, width(right), 0.01);
		final double border = width(items("<div><span style=\"border-right: 6px solid red\">AAAA</span></div>"));
		assertEquals("border-right", border, width(items("<div><span style=\"border-right: 6px solid red\">AAAA </span></div>")),
				0.01);
		final List<String[]> start = items("<div>AAAA <span style=\"padding-left: 12px; background: #ccc\"></span></div>");
		assertEquals("an empty span with padding-left", plain - 9, Double.parseDouble(frame(start)[0]), 0.01);
		// Not at the end of the line: kept
		final List<String[]> kept = items(
				"<div><span style=\"padding-right: 12px; background: #ccc\">AAAA </span>B</div>");
		assertTrue("before B", width(kept) > plain + 1);
		// Nor before an inline-block without width, nor before a preserved space (codex; Chrome 27pt and 18pt for "A")
		final List<String[]> block = items("<div><span style=\"padding-right: 12px; background: #ccc\">AAAA </span>"
				+ "<i style=\"display: inline-block; width: 0; height: 12px\"></i></div>");
		assertEquals("before an inline-block of no width", width(kept), width(block), 0.01);
		final double beforeB = Double.parseDouble(
				frame(items("<div>AAAA <span style=\"padding-left: 12px; background: #ccc\">B</span></div>"))[0]);
		final List<String[]> pre = items(
				"<div>AAAA <span style=\"padding-left: 12px; white-space: pre; background: #ccc\"> </span></div>");
		assertEquals("before a preserved space", beforeB, Double.parseDouble(frame(pre)[0]), 0.01);
		// Nor before text without width
		final List<String[]> zero = items("<div><span style=\"padding-right: 12px; background: #ccc\">AAAA "
				+ "<span style=\"font-size: 0\">B</span></span></div>");
		assertEquals("before text of no width", width(kept), width(zero), 0.01);
		// Nor before a preserved tab of no width
		final List<String[]> tab = items("<div><span style=\"padding-right: 12px; background: #ccc\">AAAA "
				+ "<span style=\"white-space: pre; tab-size: 0\">&#9;</span></span></div>");
		assertEquals("before a preserved tab", width(kept), width(tab), 0.01);
	}

	/** A preserved space after a framed start keeps the space before it on the line for the fit too (codex). */
	public void testPreservedSpaceWraps() throws Exception {
		// "A A " and a padded preserved space in a line a little wider than them without the space before the span:
		// Chrome puts the span on the next line (repro/ws/cx11.html: 54pt in 48pt); it stayed after "A A", beyond the
		// line. The widths come from a wide line; the line leaves the spaces less than half of their width to shrink
		final String body = "A A <span style=\"white-space: pre; padding-right: 12px; background: #ccc\"> </span>";
		final List<String[]> wide = items("<div>" + body + "</div>");
		final double before = Double.parseDouble(frame(wide)[0]), space = width(wide) - 9;
		final List<String[]> items = items("<div style=\"width: " + (before + 9 + space / 10) + "pt\">" + body + "</div>");
		final String[] span = frame(items);
		assertEquals("the span starts the line", 0, Double.parseDouble(span[0]), 0.01);
		for (final String[] item : items) {
			if (item[3] != null) {
				assertTrue("the span below " + item[3], Double.parseDouble(span[1]) > Double.parseDouble(item[1]) + 5);
			}
		}
	}

	public void testLetterSpacingAfterSpaces() throws Exception {
		// 2px (1.5pt) after each A and after the space: the second word moves 4.5pt
		assertEquals("the second word", 4.5, secondWord("letter-spacing: 2px") - secondWord("letter-spacing: 0"), 0.01);
	}

	private static double secondWord(final String style) throws Exception {
		final List<Double> xs = new ArrayList<>();
		for (final String[] item : items("<div style=\"" + style + "\">AA AA</div>")) {
			if (item[3] != null) {
				xs.add(Double.parseDouble(item[0]));
			}
		}
		assertEquals(style + ": two words", 2, xs.size());
		return xs.get(1);
	}

	private static String[] frame(final List<String[]> items) {
		for (final String[] item : items) {
			if (item[2] != null) {
				return item;
			}
		}
		fail("no frame");
		return null;
	}

	private static double width(final List<String[]> items) {
		return Double.parseDouble(frame(items)[2]);
	}

	/** {x, y, frame width or null, text or null} of the items of page 1, the page's own frame left out. */
	private static List<String[]> items(final String body) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 400pt 400pt; margin: 20pt } body { margin: 0 }"
				+ " div { width: 300px; font: 20px/1 monospace; margin: 0 0 6pt }</style></head><body>" + body
				+ "</body></html>";
		final File dir = Files.createTempDirectory("trailing-space-frame").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final String page = Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		final List<String[]> items = new ArrayList<>();
		final Matcher m = ITEM.matcher(page);
		while (m.find()) {
			if (m.group(4) != null && Double.parseDouble(m.group(1)) < 0) {
				continue;
			}
			items.add(new String[] { m.group(1), m.group(2), m.group(4), m.group(5) });
		}
		return items;
	}
}
