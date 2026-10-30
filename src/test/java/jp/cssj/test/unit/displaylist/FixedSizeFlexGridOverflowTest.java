package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

/**
 * Content overflowing a flex or grid of a definite page-axis size goes on to the next page (2026-10-30, fit sweep seed
 * 49415336).
 *
 * <p>
 * The items are laid out apart from the page's builder, so the cursor stopped at the end of the container's own box
 * and no page break was looked for: a 50pt flex of 30 lines on a 200pt page drew T17 to T30 below the paper (y = 192 to
 * 348), and a vertical-lr flex of a definite width drew its last words about 800pt right of the paper. Chrome carries
 * the overflow to the next page (T17 at the top of page 2). A container of {@code overflow: hidden} clips the overflow
 * and stays on one page.
 * </p>
 */
public class FixedSizeFlexGridOverflowTest extends TestCase {
	public FixedSizeFlexGridOverflowTest(final String name) {
		super(name);
	}

	private static final Pattern T17 = Pattern.compile("x=([-0-9.]+) y=([-0-9.]+) Text\\[\"T17\"");

	public void testSeed() throws Exception {
		this.check("flex-overflow-seed");
	}

	public void testFlex() throws Exception {
		assertEquals("T17 starts page 2", 0, this.find("flex-fixed-height-overflow", 2, T17)[1], 0.1);
	}

	public void testGrid() throws Exception {
		assertEquals("T17 starts page 2", 0, this.find("grid-fixed-height-overflow", 2, T17)[1], 0.1);
	}

	public void testGridFixedRow() throws Exception {
		assertEquals("T17 starts page 2", 0, this.find("grid-fixed-row-overflow", 2, T17)[1], 0.1);
	}

	public void testColumnFlex() throws Exception {
		assertEquals("T17 starts page 2", 0, this.find("flex-column-fixed-height-overflow", 2, T17)[1], 0.1);
	}

	public void testOverflowHiddenStays() throws Exception {
		final File dl = this.check("flex-fixed-height-overflow-hidden");
		assertFalse("one page", new File(dl, "page-0002.txt").exists());
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

	/** Returns the position of the first match on the page. */
	private double[] find(final String name, final int page, final Pattern pattern) throws Exception {
		final File dl = this.check(name);
		final File list = new File(dl, String.format("page-%04d.txt", page));
		assertTrue(list + " exists", list.exists());
		for (final String line : Files.readAllLines(list.toPath(), StandardCharsets.UTF_8)) {
			final Matcher m = pattern.matcher(line);
			if (m.find() && !line.contains(" artifact ")) {
				return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
			}
		}
		fail(pattern + " is not drawn on page " + page);
		return null;
	}
}
