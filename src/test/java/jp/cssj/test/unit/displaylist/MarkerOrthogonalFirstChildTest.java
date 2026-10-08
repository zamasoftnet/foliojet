package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

/**
 * An outside list marker whose list item starts with an orthogonal child takes no room in the child's lines
 * (2026-10-08, fit sweep seed 12297742).
 *
 * <p>
 * The marker used to wait for the first character, which came in the orthogonal child's first line, and took that
 * line's start in the other axis: the text moved down by the marker, a block in an inline moved over by a line, and
 * a floated list item grew past its shrink-to-fit width. It is now settled before the orthogonal child, overlaying,
 * as before a table. Expected positions are Chrome's (T0 at y=0, at x=3.45 inside the table, at x=288 in the float).
 * </p>
 */
public class MarkerOrthogonalFirstChildTest extends TestCase {
	public MarkerOrthogonalFirstChildTest(final String name) {
		super(name);
	}

	private static final Pattern T0 = Pattern.compile("x=([-0-9.]+) y=([-0-9.]+) Text\\[\"T0\"");

	public void testText() throws Exception {
		final double[] at = this.t0("marker-orthogonal-text");
		assertEquals("T0 starts the vertical line", 0, at[1], 0.1);
	}

	public void testBlockInInline() throws Exception {
		final double[] at = this.t0("marker-orthogonal-block-in-inline");
		assertTrue("the table is not moved over by a line: " + at[0], at[0] < 5);
	}

	public void testFloat() throws Exception {
		final double[] at = this.t0("marker-orthogonal-float");
		assertEquals("T0 starts the vertical line", 0, at[1], 0.1);
		assertTrue("the float holds T0 on the paper: " + at[0], at[0] > 280 && at[0] < 289);
	}

	/** Lays the fixture out with every sweep invariant and returns T0's position on page 1. */
	private double[] t0(final String name) throws Exception {
		final File fixture = new File("files/fuzz-repro/" + name + ".html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File dl = new File("build/fuzz-regressions/" + name + "-dl");
		RandomDocumentFuzzTest.checkDocument(generated, new File("build/fuzz-regressions/" + name + ".html"), dl, true,
				name + "-regression");
		for (final String line : Files.readAllLines(new File(dl, "page-0001.txt").toPath(), StandardCharsets.UTF_8)) {
			final Matcher m = T0.matcher(line);
			if (m.find() && !line.contains(" artifact ")) {
				return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
			}
		}
		fail("T0 is not drawn on page 1");
		return null;
	}
}
