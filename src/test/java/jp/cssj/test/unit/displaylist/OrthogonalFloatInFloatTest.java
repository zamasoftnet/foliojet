package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

/**
 * A float of the other writing axis inside a shrink-to-fit float is as long along the outer float's lines as it is
 * thick (2026-10-31, fit sweep seed 52425737).
 *
 * <p>
 * The measurement counted a horizontal float in a vertical float by its width, so the outer float grew as tall as that.
 * {@code float: right} puts the inner one at the bottom of the outer float, below the paper (T40 at y = 222 on a 140pt
 * high type area), and the outer float took the whole column, so the next paragraph went off the left of the paper
 * (x = -14.4). Chrome puts the inner float at the top and T99 in the first column below it.
 * </p>
 */
public class OrthogonalFloatInFloatTest extends TestCase {
	public OrthogonalFloatInFloatTest(final String name) {
		super(name);
	}

	public void testSeed() throws Exception {
		this.check("orthogonal-float-seed");
	}

	public void testInnerFloatOnTopAndNextInFirstColumn() throws Exception {
		final File list = new File(this.check("orthogonal-float-in-vertical-float"), "page-0001.txt");
		assertEquals("T40 at the top", 0, find(list, "T40")[1], 0.1);
		final double[] t99 = find(list, "T99");
		assertEquals("T99 in the first column", 275.6, t99[0], 0.1);
		assertTrue("T99 below the inner float: " + t99[1], t99[1] > 0 && t99[1] < 140);
	}

	/** Its own min-width holds where it is not laid out on trial: in a table cell, the cell is as wide. */
	public void testMinWidthInCell() throws Exception {
		final File list = new File(this.check("orthogonal-float-min-width-in-cell"), "page-0001.txt");
		assertEquals("T2 after the 100pt cell", 100, find(list, "T2")[0], 0.1);
	}

	/** Lays the fixture out with every sweep invariant and returns the display list directory. */
	private File check(final String name) throws Exception {
		final File fixture = new File("files/fuzz-repro/" + name + ".html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File dl = new File("build/fuzz-regressions/" + name + "-dl");
		RandomDocumentFuzzTest.checkDocument(generated, new File("build/fuzz-regressions/" + name + ".html"), dl, true,
				name + "-regression");
		return dl;
	}

	/** Returns the position of the text on the page. */
	private static double[] find(final File list, final String text) throws Exception {
		final Pattern pattern = Pattern.compile("x=([-0-9.]+) y=([-0-9.]+) Text\\[\"" + text + "\"");
		for (final String line : Files.readAllLines(list.toPath(), StandardCharsets.UTF_8)) {
			final Matcher m = pattern.matcher(line);
			if (m.find() && !line.contains(" artifact ")) {
				return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
			}
		}
		fail(text + " is not drawn in " + list);
		return null;
	}
}
