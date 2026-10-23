package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

/**
 * A cell after a colspan that overlaps a spanning cell from the row above takes the next free column (2026-10-23, fit
 * sweep seed 32746134).
 *
 * <p>
 * When a row's colspan covers a later column of a colspan+rowspan cell above (an HTML table model error), the builder
 * carries that cell over from the column after the colspan. The carried entries counted one column too many, so the
 * row's next cell moved one column beyond the grid: a phantom column in auto layout (T24 at y=310.76 instead of 165.60
 * on 150 pt vertical paper), a dropped cell in fixed layout. Chrome puts the cell in the next free column, as the HTML
 * table processing model does.
 * </p>
 */
public class OverlappingColspanColumnTest extends TestCase {
	public OverlappingColspanColumnTest(final String name) {
		super(name);
	}

	/** {@code x=… y=… Text["T5"} in the display-list dump (real drawings only). */
	private static final Pattern TOKEN = Pattern.compile("x=([-0-9.]+) y=([-0-9.]+) Text\\[\"T(\\d+)\"");

	/** Auto layout (the retained builder). */
	public void testAutoLayoutVertical() throws Exception {
		final Map<Integer, Double> lineAxis = this.lineAxis("overlapping-colspan-next-cell-vertical", true);
		assertEquals("T8 in T6's column: " + lineAxis, lineAxis.get(6).doubleValue(), lineAxis.get(8).doubleValue(), 0.01);
	}

	/** Fixed layout (the incremental builder). */
	public void testFixedLayout() throws Exception {
		final Map<Integer, Double> lineAxis = this.lineAxis("overlapping-colspan-next-cell-fixed", false);
		assertEquals("T9 in T4's column: " + lineAxis, lineAxis.get(4).doubleValue(), lineAxis.get(9).doubleValue(), 0.01);
		assertEquals("T7 in T4's column: " + lineAxis, lineAxis.get(4).doubleValue(), lineAxis.get(7).doubleValue(), 0.01);
	}

	/** Checks the document's sweep invariants and returns each token's line-axis position. */
	private Map<Integer, Double> lineAxis(final String name, final boolean vertical) throws Exception {
		final File fixture = new File("files/fuzz-repro/" + name + ".html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File dl = new File("build/fuzz-regressions/" + name + "-dl");
		RandomDocumentFuzzTest.checkDocument(generated, new File("build/fuzz-regressions/" + name + ".html"), dl, true,
				name + "-regression");
		final File[] pages = dl.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		final Map<Integer, Double> lineAxis = new HashMap<>();
		for (final File page : pages) {
			for (final String line : Files.readAllLines(page.toPath(), StandardCharsets.UTF_8)) {
				final Matcher m = TOKEN.matcher(line);
				if (m.find() && !line.contains(" artifact ")) {
					lineAxis.put(Integer.valueOf(m.group(3)), Double.valueOf(m.group(vertical ? 2 : 1)));
				}
			}
		}
		return lineAxis;
	}
}
