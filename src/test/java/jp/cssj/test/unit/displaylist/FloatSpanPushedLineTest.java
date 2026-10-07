package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

/**
 * A line that does not fit beside a float spanning several pages (2026-10-07, fit sweep seed 11885076).
 *
 * <p>
 * The line was located below the float's unfragmented end, past the page limit. At the page break the text block
 * started the fragmentainer, so the line cutter kept it and the rescue split clipped it as one tall line: the real
 * line stayed outside page 1 and only artifact copies reached the following pages (on the last one above the end of
 * the float). The text block is now moved on while the float's head stays, and the line is located again against the
 * float's continuation, so it lands right after the float's last line (T11), on the paper, as real content.
 * </p>
 */
public class FloatSpanPushedLineTest extends TestCase {
	public FloatSpanPushedLineTest(final String name) {
		super(name);
	}

	/** {@code x=… y=… [artifact ]Text["T43"} in the display-list dump. */
	private static final Pattern TOKEN = Pattern
			.compile("x=([-0-9.]+) y=([-0-9.]+) (artifact )?(?:Text|RubyUnit)\\[\"(T43|T11)\"");

	public void testHorizontal() throws Exception {
		this.check("float-span-pushed-line-horizontal", false);
	}

	public void testVertical() throws Exception {
		this.check("float-span-pushed-line-vertical", true);
	}

	private void check(final String name, final boolean vertical) throws Exception {
		final long before = net.zamasoft.foliojet.layout.box.content.FlowContainer.PUSHED_LINE_MOVES.get();
		final File fixture = new File("files/fuzz-repro/" + name + ".html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File dl = new File("build/fuzz-regressions/" + name + "-dl");
		RandomDocumentFuzzTest.checkDocument(generated, new File("build/fuzz-regressions/" + name + ".html"), dl, true,
				name + "-regression");
		assertTrue("the text block is moved past the float",
				net.zamasoft.foliojet.layout.box.content.FlowContainer.PUSHED_LINE_MOVES.get() > before);
		final File[] pages = dl.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		int realLines = 0;
		for (final File page : pages) {
			double t11 = Double.NaN, t43 = Double.NaN;
			for (final String line : Files.readAllLines(page.toPath(), StandardCharsets.UTF_8)) {
				final Matcher m = TOKEN.matcher(line);
				if (!m.find()) {
					continue;
				}
				assertNull("no artifact copy of the line or the float: " + page.getName() + ": " + line, m.group(3));
				final double x = Double.parseDouble(m.group(1)), y = Double.parseDouble(m.group(2));
				final double pageAxis = vertical ? -x : y;
				if (m.group(4).equals("T11")) {
					t11 = pageAxis;
					continue;
				}
				++realLines;
				// on the paper (content area of 390 x 110 / 110 x 390 with the 5pt margin as slack)
				assertTrue("T43 on the paper: " + line, vertical ? x >= -5 && x <= 115 : y >= -5 && y <= 115);
				t43 = pageAxis;
			}
			if (!Double.isNaN(t43)) {
				assertFalse("T43 shares the page with the float's end: " + page.getName(), Double.isNaN(t11));
				assertTrue("T43 comes after the float's last line: " + page.getName(), t43 > t11);
			}
		}
		assertEquals("T43 is drawn once", 1, realLines);
	}
}
