package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

/**
 * A rowspan cell split across pages keeps its column on the next page (2026-10-08, fit sweep seed 12070374).
 *
 * <p>
 * When a later row's colspan covers the rowspan's column (an HTML table model error), the builder gives that row no
 * extension entry for the rowspan cell. At the page break {@code TableRowBox.cutUnextendedRowspanCells} appended the
 * cell's remainder after the moving row's own cells, so the continuation went past them, out of the table (T7 to T9 at
 * x=29.50 in a three-column table instead of 11.17). The remainder now keeps the cell's line-axis offset, as Chrome
 * keeps the column (it draws the overlapping cells over each other). The short-row table guards the case where an
 * empty column lies before the rowspan.
 * </p>
 */
public class RowspanSplitColumnTest extends TestCase {
	public RowspanSplitColumnTest(final String name) {
		super(name);
	}

	/** {@code x=… y=… Text["T5"} in the display-list dump (real drawings only). */
	private static final Pattern TOKEN = Pattern.compile("x=([-0-9.]+) y=([-0-9.]+) Text\\[\"T(\\d+)\"");

	public void testOverlappingRowHorizontal() throws Exception {
		this.check("overlapping-rowspan-split-horizontal", false, 1, 9);
	}

	public void testOverlappingRowVertical() throws Exception {
		this.check("overlapping-rowspan-split-vertical", true, 1, 9);
	}

	public void testShortRowHorizontal() throws Exception {
		this.check("rowspan-gap-split-horizontal", false, 2, 9);
	}

	/** Tokens T{@code first}..T{@code last} form the rowspan cell's lines. */
	private void check(final String name, final boolean vertical, final int first, final int last) throws Exception {
		final File fixture = new File("files/fuzz-repro/" + name + ".html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File dl = new File("build/fuzz-regressions/" + name + "-dl");
		RandomDocumentFuzzTest.checkDocument(generated, new File("build/fuzz-regressions/" + name + ".html"), dl, true,
				name + "-regression");
		final File[] pages = dl.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		final List<Double> lineAxis = new ArrayList<>();
		int pagesWithCell = 0;
		for (final File page : pages) {
			boolean onThisPage = false;
			for (final String line : Files.readAllLines(page.toPath(), StandardCharsets.UTF_8)) {
				final Matcher m = TOKEN.matcher(line);
				if (!m.find() || line.contains(" artifact ")) {
					continue;
				}
				final int token = Integer.parseInt(m.group(3));
				if (token < first || token > last) {
					continue;
				}
				onThisPage = true;
				lineAxis.add(Double.valueOf(m.group(vertical ? 2 : 1)));
			}
			pagesWithCell += onThisPage ? 1 : 0;
		}
		assertTrue("the rowspan cell splits across pages: " + pagesWithCell, pagesWithCell >= 2);
		assertEquals("every line of the cell is drawn once", last - first + 1, lineAxis.size());
		for (final Double position : lineAxis) {
			assertEquals("the cell keeps its column on every page: " + lineAxis, lineAxis.get(0).doubleValue(),
					position.doubleValue(), 0.01);
		}
	}
}
