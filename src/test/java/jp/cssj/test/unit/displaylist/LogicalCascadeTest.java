package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
 * A physical property and the logical property for the same side: the later one in the cascade wins (2026-10-08, EPUB
 * brush-up D-8; CSS Logical 1 §4, as in Chrome).
 *
 * <p>
 * An explicit physical property used to win whatever came after it, so the {@code margin: 0; padding: 0} reset of EPUB
 * style sheets silently cancelled every {@code margin-block}/{@code padding-inline} a print style sheet added, and the
 * UA's {@code body { margin: 8px }} beat an author's {@code body { margin-block: 0 }}.
 * </p>
 */
public class LogicalCascadeTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(\\w+)\"");

	private static final Pattern FRAME = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	public LogicalCascadeTest(final String name) {
		super(name);
	}

	private static String document(final String writingMode, final String style, final String body) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 300pt 300pt; margin: 0 }
				html { writing-mode: %s }
				body { font-size: 10pt; line-height: 20pt }
				p { margin: 0; padding: 0 }
				%s
				</style></head><body>%s</body></html>
				""".formatted(writingMode, style, body);
	}

	/** Horizontal writing: inline-start is the left side, block-start the top. */
	public void testHorizontal() throws Exception {
		final String page = convert("h", document("horizontal-tb", """
				body { margin-block: 0; margin-inline: 0 }
				p.a { margin-inline-start: 20pt }
				p.b { margin-left: 0; margin-inline-start: 30pt }
				p.c { margin-inline-start: 30pt; margin-left: 5pt }
				p.d { margin-inline-start: 30pt }
				p.d { margin-left: 7pt !important }
				p.e { padding-block-start: 10pt }
				""", "<p class=\"a\">A</p><p class=\"b\">B</p><p class=\"c\">C</p><p class=\"d\">D</p><p class=\"e\">E</p>"));
		assertEquals("より詳細な規則の論理が p { margin: 0 } に勝つ", 20, x(page, "A"), 0.01);
		assertEquals("同じ規則の後ろの論理が勝つ", 30, x(page, "B"), 0.01);
		assertEquals("同じ規則の後ろの物理が勝つ", 5, x(page, "C"), 0.01);
		assertEquals("!important の物理が後の論理に勝つ", 7, x(page, "D"), 0.01);
		// UA body { margin: 8px } loses to the author's margin-block/margin-inline: A starts at the paper's corner
		assertEquals("UA の body の余白に作者の margin-block が勝つ", 0, y(page, "A"), 0.01);
		assertEquals("padding-block-start", 4 * 20 + 10, y(page, "E"), 0.01);
	}

	/** Vertical writing (vertical-rl): inline-start is the top, block-start the right side. */
	public void testVertical() throws Exception {
		final String page = convert("v", document("vertical-rl", """
				body { margin: 0 }
				p.a { margin-inline-start: 20pt }
				p.b { margin-top: 0; margin-inline-start: 30pt }
				p.c { padding: 0 }
				p.c.d { padding-block-start: 10pt }
				""", "<p class=\"a\">A</p><p class=\"b\">B</p><p class=\"c d\">C</p>"));
		assertEquals("縦組みの inline-start は上", 20, y(page, "A"), 0.01);
		assertEquals("同じ規則の後ろの論理が勝つ(縦)", 30, y(page, "B"), 0.01);
		// block-start is the right side: C's line moves 10pt to the left of where it would be
		assertEquals("縦組みの padding-block-start は右", x(page, "B") - 20 - 10, x(page, "C"), 0.01);
	}

	/** width and inline-size, border-left-width and border-inline-start-width: the later declaration wins. */
	public void testSizesAndBorders() throws Exception {
		final String page = convert("sizes", document("horizontal-tb", """
				body { margin: 0 }
				div { height: 10pt; background: #ccc }
				div.a { width: 50pt; inline-size: 80pt }
				div.b { inline-size: 80pt; width: 50pt }
				div.c { border: 1pt solid; border-inline-start-width: 5pt; width: 50pt }
				""", "<div class=\"a\"></div><div class=\"b\"></div><div class=\"c\"></div>"));
		final Matcher m = FRAME.matcher(page);
		final double[] widths = new double[3];
		for (int i = 0; i < 3; ++i) {
			assertTrue("枠が足りない:\n" + page, m.find());
			widths[i] = Double.parseDouble(m.group(3));
		}
		assertEquals("後の inline-size が width に勝つ", 80, widths[0], 0.01);
		assertEquals("後の width が inline-size に勝つ", 50, widths[1], 0.01);
		assertEquals("後の border-inline-start-width が border に勝つ", 50 + 5 + 1, widths[2], 0.01);
	}

	private static double x(final String page, final String text) {
		return coordinate(page, text, 1);
	}

	private static double y(final String page, final String text) {
		return coordinate(page, text, 2);
	}

	private static double coordinate(final String page, final String text, final int group) {
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			if (m.group(3).equals(text)) {
				return Double.parseDouble(m.group(group));
			}
		}
		throw new AssertionError(text + " が無い:\n" + page);
	}

	/** Convert and return the first page's display list. */
	private static String convert(final String name, final String html) throws Exception {
		final File dir = new File("local/logical-cascade/" + name);
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
					CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "logical-cascade-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		Arrays.sort(pages);
		return java.nio.file.Files.readString(pages[0].toPath(), StandardCharsets.UTF_8);
	}
}
