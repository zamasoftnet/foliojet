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
 * A table's specified page-axis size ({@code width} in vertical writing, {@code height} in horizontal) is clamped by its
 * page-axis max and min sizes (2026-10-24, fit sweep seed 34634928).
 *
 * <p>
 * The retained table builder resolved the page-axis size against the nearest box with a fixed page-axis size but never
 * read {@code max-width}/{@code min-width} (vertical) or {@code max-height}/{@code min-height} (horizontal). A
 * vertical-rl float table of {@code width: calc(35% + 8em); min-width: 8em; max-width: 90%} in a 96 pt box took
 * 129.6 pt and ran off the paper (T0 at x=210.4 on 200 pt paper). Chrome takes 96 pt, and clamps a table's block size
 * by max-height and min-height alike.
 * </p>
 */
public class TablePageAxisMinMaxTest extends TestCase {
	public TablePageAxisMinMaxTest(final String name) {
		super(name);
	}

	/** {@code x=… y=… Text["T5"} in the display-list dump (real drawings only). */
	private static final Pattern TOKEN = Pattern.compile("x=([-0-9.]+) y=([-0-9.]+) Text\\[\"T(\\d+)\"");

	/** The seed's shape: the float table takes 96 pt (min-width over max-width: 90%), as with width: 96pt. */
	public void testVerticalFloatTable() throws Exception {
		final Map<Integer, double[]> tokens = this.tokens("table-page-axis-minmax-vertical");
		// The 96 pt box sits at x = 94..190; the table's line starts at its right edge (190 - 14.4; before: 209.2).
		assertEquals("T0", 175.6, tokens.get(0)[0], 0.01);
	}

	/** Specified heights: 150pt with max-height: 80pt, 50pt with min-height: 120pt (Chrome 80 and 120). */
	public void testHorizontalTables() throws Exception {
		final Map<Integer, double[]> tokens = this.tokens("table-page-axis-minmax-horizontal");
		// Two rows share the table's height less the spacing and borders (before: 74.25 and 24.25).
		assertEquals("row pitch under max-height", 39.25, tokens.get(1)[1] - tokens.get(0)[1], 0.01);
		assertEquals("row pitch under min-height", 59.25, tokens.get(3)[1] - tokens.get(2)[1], 0.01);
		// min-height: 120pt wins over max-height: 80pt (Chrome 120), and the following T6 comes after the table (its
		// wrapper capped at 80 pt would put T6 at 280, above T5).
		assertEquals("row pitch under min over max", 59.25, tokens.get(5)[1] - tokens.get(4)[1], 0.01);
		assertEquals("T6 after the table", 96.82, tokens.get(6)[1] - tokens.get(4)[1], 0.01);
		// Content taller than max-height: 30pt grows the table, and T10 follows it (it overlapped before).
		assertEquals("T10 after the table", 16.9, tokens.get(10)[1] - tokens.get(9)[1], 0.01);
	}

	/** Checks the document's sweep invariants and returns each token's position. */
	private Map<Integer, double[]> tokens(final String name) throws Exception {
		final File fixture = new File("files/fuzz-repro/" + name + ".html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File dl = new File("build/fuzz-regressions/" + name + "-dl");
		RandomDocumentFuzzTest.checkDocument(generated, new File("build/fuzz-regressions/" + name + ".html"), dl, true,
				name + "-regression");
		final File[] pages = dl.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		final Map<Integer, double[]> tokens = new HashMap<>();
		for (final File page : pages) {
			for (final String line : Files.readAllLines(page.toPath(), StandardCharsets.UTF_8)) {
				final Matcher m = TOKEN.matcher(line);
				if (m.find() && !line.contains(" artifact ")) {
					tokens.put(Integer.valueOf(m.group(3)),
							new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) });
				}
			}
		}
		return tokens;
	}
}
