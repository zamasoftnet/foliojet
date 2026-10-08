package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

/**
 * A multicol that overflows the page only when it closes still moves to the next page (2026-10-08, fit sweep seed
 * 12465506).
 *
 * <p>
 * The multicol's last child is a float after an orthogonal box longer than the paper along the page axis, so no
 * in-flow content inside it checks the page limit. At its close {@code BreakableBuilder.endBreakableFlowBlock} tries a
 * column break, which is refused (no break point); the refusal had already reset the pending break state, so the
 * interflow check that follows was skipped and the whole document stayed on page 1, off the paper. The list before
 * the multicol stays on page 1 and the multicol goes to page 2, as when an in-flow block follows the float.
 * </p>
 */
public class MulticolRefusedColumnBreakTest extends TestCase {
	public MulticolRefusedColumnBreakTest(final String name) {
		super(name);
	}

	/** {@code x=… y=… Text["H0"} etc. in the display-list dump (real drawings only). */
	private static final Pattern TOKEN = Pattern.compile("x=([-0-9.]+) y=([-0-9.]+) Text\\[\"([TFH][0-9]+)\"");

	public void testVertical() throws Exception {
		this.check("multicol-float-after-overwide-box-vertical");
	}

	public void testHorizontal() throws Exception {
		this.check("multicol-float-after-overwide-box-horizontal");
	}

	private void check(final String name) throws Exception {
		final File fixture = new File("files/fuzz-repro/" + name + ".html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File dl = new File("build/fuzz-regressions/" + name + "-dl");
		RandomDocumentFuzzTest.checkDocument(generated, new File("build/fuzz-regressions/" + name + ".html"), dl, true,
				name + "-regression");
		final File[] pages = dl.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		java.util.Arrays.sort(pages);
		assertEquals("pages", 2, pages.length);
		for (int i = 0; i < pages.length; ++i) {
			for (final String line : Files.readAllLines(pages[i].toPath(), StandardCharsets.UTF_8)) {
				final Matcher m = TOKEN.matcher(line);
				if (!m.find() || line.contains(" artifact ")) {
					continue;
				}
				final boolean list = m.group(3).startsWith("T");
				assertEquals(m.group(3) + " on page " + (i + 1), list ? 0 : 1, i);
			}
		}
	}
}
