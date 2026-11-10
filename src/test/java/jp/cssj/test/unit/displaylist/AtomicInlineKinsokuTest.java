package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
 * Verifies that kinsoku (line-breaking rules) prohibitions at line start apply even immediately after
 * atomic inlines (formulas, images) (2026-10-04, item ⑧ of TECH-20261003-004).
 * In the book Time-Lock Encryption (時限暗号), a break immediately after a formula left the next line
 * starting with "、".
 *
 * <p>
 * Line-break positions vary by font, so test 40 paragraph widths in 1 pt increments
 * and verify that neither "、" nor "。" starts a line at any width.
 * </p>
 *
 * <p>
 * The other tests check the soft wrap opportunities around an inline-block holding text (2026-11-10, triage §194):
 * line breaking took the text inside for its own, so its neighbours joined it into one word and nothing broke
 * around it. Chrome breaks in each of these shapes.
 * </p>
 */
public class AtomicInlineKinsokuTest extends TestCase {
	private static final long WATCHDOG_MS = 120_000L;

	/** First character of a line (the first character of Text at the line's left edge, x=0). */
	private static final Pattern LINE_HEAD = Pattern.compile("x=0\\.00 y=[-\\d.]+ Text\\[\"(.)");

	private static final String NARROW_BOX = """
			<!DOCTYPE html>
			<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
			<?jp.cssj.property name="output.page-width" value="%s"?>
			<?jp.cssj.property name="output.page-height" value="%s"?>
			<style>
			@page{margin:0}
			body{margin:0;font:6pt/1.2 serif%s}
			p,div{margin:0;padding:0}
			span{display:inline-block;width:20pt;border:1pt solid red}
			</style></head><body><div style="width:29pt">%s</div></body></html>
			""";

	/** Inline-blocks holding text with nothing between them go one to a line in a box narrower than two. */
	public void testInlineBlocksHoldingTextWrapOneToALine() throws Exception {
		final String dump = convert("inline-blocks",
				NARROW_BOX.formatted("120pt", "120pt", "", "<span>T1</span><span>T2</span><span>T3</span>"));
		final double[] t1 = position(dump, "T1"), t2 = position(dump, "T2"), t3 = position(dump, "T3");
		assertEquals("T2 の行頭", t1[0], t2[0], 0.01);
		assertEquals("T3 の行頭", t1[0], t3[0], 0.01);
		assertTrue("T2 が T1 の次の行にない: " + t1[1] + " " + t2[1], t2[1] > t1[1] + 5);
		assertTrue("T3 が T2 の次の行にない: " + t2[1] + " " + t3[1], t3[1] > t2[1] + 5);
	}

	/** A word right after an inline-block holding text goes to the next line when it does not fit. */
	public void testWordAfterAnInlineBlockHoldingTextWraps() throws Exception {
		final String dump = convert("word-after",
				NARROW_BOX.formatted("120pt", "120pt", "", "T1<span>T2</span>T3"));
		final double[] t1 = position(dump, "T1"), t3 = position(dump, "T3");
		assertEquals("T3 が行頭にない", 0, t3[0], 0.01);
		assertTrue("T3 が T1 の次の行にない: " + t1[1] + " " + t3[1], t3[1] > t1[1] + 5);
	}

	/**
	 * The fuzz seed's shape: in vertical-rl a box of width:29pt holds a horizontal box with three textareas, which
	 * takes the width of one and stacks them, as Chrome does (x = -68), instead of lining up all three (x = -316).
	 */
	public void testTextareasInAnOrthogonalBoxStack() throws Exception {
		final String dump = convert("textareas", NARROW_BOX.formatted("60pt", "200pt", ";writing-mode:vertical-rl",
				"<div style=\"writing-mode:horizontal-tb\"><form><textarea rows=\"2\">T1</textarea>"
						+ "<textarea rows=\"2\">T2</textarea><textarea rows=\"2\">T3</textarea></form></div>"));
		final double[] t1 = position(dump, "T1"), t2 = position(dump, "T2"), t3 = position(dump, "T3");
		assertEquals("T2 の行頭", t1[0], t2[0], 0.01);
		assertEquals("T3 の行頭", t1[0], t3[0], 0.01);
		assertTrue("1 つ目が紙から遠い: x=" + t1[0], t1[0] > -100);
	}

	private static final String CHARACTER_UNITS = """
			<!DOCTYPE html>
			<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
			<?jp.cssj.property name="output.page-width" value="180pt"?>
			<?jp.cssj.property name="output.page-height" value="200pt"?>
			<style>
			@page{margin:0}
			body{margin:0;font:12pt/24pt serif%s}
			p{margin:0 0 24pt;hyphens:none;%s}
			.e{text-emphasis:filled dot}
			.t{text-combine-upright:all}
			</style></head><body>%s</body></html>
			""";

	/**
	 * Emphasised characters are inline-blocks inside, but stand for themselves: an emphasised T1 keeps T2 on its
	 * line, and an emphasised opening bracket keeps T3 after it, in a line too narrow for either (Chrome alike).
	 */
	public void testEmphasisedCharactersStayWithTheirNeighbours() throws Exception {
		final String dump = convert("emphasis", CHARACTER_UNITS.formatted("", "width:20pt",
				"<p><span class=\"e\">T1</span>T2</p><p><span class=\"e\">「</span>T3</p>"));
		final double[] t2 = position(dump, "T2"), bracket = position(dump, "「"), t3 = position(dump, "T3");
		assertTrue("T2 が 1 行目にない: y=" + t2[1], t2[1] < 12);
		assertTrue("T3 が「と同じ行にない: " + bracket[1] + " " + t3[1], Math.abs(t3[1] - bracket[1]) < 12);
	}

	/** Tate-chu-yoko units side by side stay in one vertical line too short for them (Chrome alike). */
	public void testTateChuYokoUnitsStayInOneLine() throws Exception {
		final String dump = convert("tate-chu-yoko", CHARACTER_UNITS.formatted(";writing-mode:vertical-rl",
				"height:30pt", "<p><span class=\"t\">T1</span><span class=\"t\">T2</span><span class=\"t\">T3</span></p>"));
		final double[] t1 = position(dump, "T1"), t2 = position(dump, "T2"), t3 = position(dump, "T3");
		assertEquals("T2 の列", t1[0], t2[0], 0.01);
		assertEquals("T3 の列", t1[0], t3[0], 0.01);
	}

	private static double[] position(final String dump, final String token) {
		final Matcher m = Pattern.compile("x=([-\\d.]+) y=([-\\d.]+) Text\\[\"" + token + "\"").matcher(dump);
		assertTrue(token + " が描かれていない", m.find());
		return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
	}

	public void testNoLineStartsWithClosingPunctuationAfterMath() throws Exception {
		final StringBuilder body = new StringBuilder();
		for (int w = 110; w < 150; ++w) {
			body.append("<p style=\"width:").append(w).append("pt\">あいうえおかきくけこさし")
					.append("<math xmlns=\"http://www.w3.org/1998/Math/MathML\"><mi>Enc</mi></math>")
					.append("、たちつてと。あいうえおかきく<math xmlns=\"http://www.w3.org/1998/Math/MathML\">")
					.append("<mi>y</mi></math>。</p>\n");
		}
		final String html = """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<?jp.cssj.property name="output.page-width" value="300pt"?>
				<?jp.cssj.property name="output.page-height" value="4000pt"?>
				<style>
				@page{margin:0}
				body{margin:0;font:10pt serif;text-align:justify}
				p{margin:0 0 6pt 0}
				</style></head><body>
				%s
				</body></html>
				""".formatted(body);
		final String dump = convert("kinsoku", html);
		final Matcher m = LINE_HEAD.matcher(dump);
		int lines = 0;
		while (m.find()) {
			++lines;
			final String head = m.group(1);
			assertFalse("行頭に「" + head + "」(" + m.group(0) + ")", "、".equals(head) || "。".equals(head));
		}
		assertTrue("行が読めない", lines > 40);
	}

	private static String convert(final String name, final String html) throws Exception {
		final File dir = new File("local/atomic-inline-kinsoku/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html);
		}
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
				final DirectSession session = (DirectSession) new DirectDriver()
						.getSession(URI.create("copper:direct:"), null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "atomic-inline-kinsoku-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		java.util.Arrays.sort(pages);
		final StringBuilder all = new StringBuilder();
		for (final File page : pages) {
			all.append(java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return all.toString();
	}
}
