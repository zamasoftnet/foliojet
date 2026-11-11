package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * The side on which a flex line that does not fit runs out of its box (2026-11-12, sweep fit seed 90416903).
 *
 * <p>
 * Three items of 120pt in a box of 260pt. A line keeps its first items in the box and runs past the end of the main
 * axis; with {@code flex-end} it keeps the last ones. Until 2026-11-12 the line always started at the physical start
 * when it did not fit, so a reversed line put the first item of the document past the far edge. The expected places
 * are Chrome's (A1 at 141.2 for row-reverse, A3 at 141.2 for flex-end). What stays packed at the start on purpose:
 * {@code center} and space-around (the safe fallback; Chrome centers the overflow of center), a line with an auto
 * margin and {@code flex-end} on a reversed axis (Chrome: A3 at 0), {@code direction: rtl} (not mirrored anywhere
 * yet), and a column whose main axis is the page axis (what does not fit goes on to the next pages; Chrome puts the
 * first items of column-reverse above the box). FlexBuilder.distributeMain says why.
 * </p>
 */
public class FlexOverflowSideTest extends TestCase {
	private static final String DOC = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
			+ "@page{size:300pt 300pt;margin:20pt}body{margin:0;font:10pt serif%s}"
			+ ".f{display:flex;flex-wrap:nowrap;%s}.f>div{flex:0 0 120pt;border:1pt solid #888;box-sizing:border-box}"
			+ "</style></head><body><div class=\"f\"><div>A1</div><div>A2</div><div>A3</div></div></body></html>";

	public void testRowKeepsTheFirstItems() throws Exception {
		final String dump = convert("row", "", "flex-direction:row");
		assertEquals(1, position(dump, "A1")[0], 0.5);
		assertEquals(241, position(dump, "A3")[0], 0.5);
	}

	public void testRowReverseKeepsTheFirstItemsAtTheFarSide() throws Exception {
		final String dump = convert("row-reverse", "", "flex-direction:row-reverse");
		assertEquals(141, position(dump, "A1")[0], 0.5);
		assertEquals(21, position(dump, "A2")[0], 0.5);
		assertEquals(-99, position(dump, "A3")[0], 0.5);
	}

	public void testFlexEndKeepsTheLastItems() throws Exception {
		final String dump = convert("flex-end", "", "justify-content:flex-end");
		assertEquals(141, position(dump, "A3")[0], 0.5);
		assertEquals(-99, position(dump, "A1")[0], 0.5);
	}

	public void testRowReverseFlexEndStartsAtTheNearSide() throws Exception {
		final String dump = convert("row-reverse-flex-end", "", "flex-direction:row-reverse;justify-content:flex-end");
		assertEquals(1, position(dump, "A3")[0], 0.5);
		assertEquals(241, position(dump, "A1")[0], 0.5);
	}

	public void testColumnReverseStillRunsOnDownThePage() throws Exception {
		final String dump = convert("column-reverse", "", "flex-direction:column-reverse;height:200pt");
		assertEquals(1, position(dump, "A3")[1], 0.5);
		assertEquals(241, position(dump, "A1")[1], 0.5);
	}

	public void testAutoMarginLeavesFlexEndOnAReversedAxisAtTheStart() throws Exception {
		final String dump = convert("row-reverse-flex-end-auto-margin", "",
				"flex-direction:row-reverse;justify-content:flex-end}.f>div:last-child{margin-left:auto");
		assertEquals(1, position(dump, "A3")[0], 0.5);
	}

	public void testSpaceAroundOnAReversedAxisStaysAtTheStart() throws Exception {
		final String dump = convert("row-reverse-space-around", "", "flex-direction:row-reverse;justify-content:space-around");
		assertEquals(1, position(dump, "A3")[0], 0.5);
	}

	public void testSpaceBetweenOnAReversedAxisKeepsTheFirstItems() throws Exception {
		final String dump = convert("row-reverse-space-between", "", "flex-direction:row-reverse;justify-content:space-between");
		assertEquals(141, position(dump, "A1")[0], 0.5);
	}

	public void testRtlIsLeftAsItWas() throws Exception {
		final String dump = convert("rtl-row-reverse", "", "direction:rtl;flex-direction:row-reverse");
		// The text of an rtl line is dumped in another form, so look at the items' frames: at 0, 120 and 240
		assertTrue(dump, dump.contains("x=0.00 y=0.00 AbsoluteRectFrame[w=120.00"));
		assertTrue(dump, dump.contains("x=240.00 y=0.00 AbsoluteRectFrame[w=120.00"));
	}

	public void testVerticalRowReverseKeepsTheFirstItemsAtTheBottom() throws Exception {
		final String dump = convert("vertical-row-reverse", ";writing-mode:vertical-rl", "flex-direction:row-reverse");
		assertEquals(141, position(dump, "A1")[1], 0.5);
		assertEquals(21, position(dump, "A2")[1], 0.5);
	}

	public void testCenterStaysAtTheStart() throws Exception {
		final String dump = convert("center", "", "justify-content:center");
		assertEquals(1, position(dump, "A1")[0], 0.5);
	}

	private static double[] position(final String dump, final String text) {
		final Matcher m = Pattern.compile("x=([-\\d.]+) y=([-\\d.]+) Text\\[\"" + text + "\"").matcher(dump);
		assertTrue(text + " is not drawn: " + dump, m.find());
		return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
	}

	private static String convert(final String name, final String body, final String flex) throws Exception {
		final File dir = new File("local/flex-overflow-side/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), DOC.formatted(body, flex), StandardCharsets.UTF_8);
		try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
				AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				CTISessionHelper.transcodeFile(session, input, "text/html", null);
			} finally {
				session.close();
			}
		}
		final StringBuilder dump = new StringBuilder();
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(pages);
		java.util.Arrays.sort(pages);
		for (final File page : pages) {
			dump.append(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return dump.toString();
	}
}
