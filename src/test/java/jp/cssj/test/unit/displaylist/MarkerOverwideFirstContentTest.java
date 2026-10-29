package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

/**
 * An outside list marker takes no room on the line, so the line does not break after it (2026-10-30, fit sweep seed
 * 47551580).
 *
 * <p>
 * The marker is an inline block of no advance, and its content left a break opportunity behind it. A list item's
 * first image, inline block or word wider than the line went to a second line, leaving the marker alone on an empty
 * first line; in a vertical-rl list item in nested columns the extra line pushed T0 left of the page area. Expected
 * positions are Chrome's (the first content in the first line, T1 or T0 on the next line). An inside marker takes room and
 * keeps its break.
 * </p>
 */
public class MarkerOverwideFirstContentTest extends TestCase {
	public MarkerOverwideFirstContentTest(final String name) {
		super(name);
	}

	private static final Pattern T0 = Pattern.compile("x=([-0-9.]+) y=([-0-9.]+) Text\\[\"T0");

	private static final Pattern BLOCK = Pattern.compile("x=([-0-9.]+) y=([-0-9.]+) \\w+\\[w=200\\.00 h=10\\.00");

	public void testSeed() throws Exception {
		final double[] at = this.find("marker-overwide-seed", T0);
		assertTrue("T0 lies in the page area: " + at[0], at[0] >= 0);
	}

	public void testWord() throws Exception {
		final double[] at = this.find("marker-overwide-word", T0);
		assertEquals("the long word starts the first line", 0, at[1], 0.1);
	}

	public void testInlineBlock() throws Exception {
		final double[] at = this.find("marker-overwide-inline-block", BLOCK);
		// On the baseline of the first line (13.2pt), not below it
		assertTrue("the inline block sits in the first line: " + at[1], at[1] < 5);
	}

	public void testInsideMarkerKeepsBreak() throws Exception {
		final double[] at = this.find("marker-inside-break", T0);
		assertTrue("the long word goes after the inside marker's line: " + at[1], at[1] > 10);
	}

	/** Lays the fixture out with every sweep invariant and returns the position of the first match on page 1. */
	private double[] find(final String name, final Pattern pattern) throws Exception {
		final File fixture = new File("files/fuzz-repro/" + name + ".html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File dl = new File("build/fuzz-regressions/" + name + "-dl");
		RandomDocumentFuzzTest.checkDocument(generated, new File("build/fuzz-regressions/" + name + ".html"), dl, true,
				name + "-regression");
		for (final String line : Files.readAllLines(new File(dl, "page-0001.txt").toPath(), StandardCharsets.UTF_8)) {
			final Matcher m = pattern.matcher(line);
			if (m.find() && !line.contains(" artifact ")) {
				return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
			}
		}
		fail(pattern + " is not drawn on page 1");
		return null;
	}
}
