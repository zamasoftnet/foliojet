package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
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
 * Footnotes are laid out at the footnote area's width whatever box holds the call, and @footnote's margin-top keeps
 * the body text away from the separator (2026-10-15, TECH-20261015-006 ① and ④). Before, a note called from a
 * paragraph inside a grid item, a table cell or a block of a given width wrapped at that box's width.
 *
 * <p>
 * The page area is 240x140 pt. The note's eight words take about 130 pt at 10 pt: one line at the area's width, several
 * in a 40 pt box.
 * </p>
 */
public class FootnoteAreaWidthTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern TEXT = Pattern
			.compile("y=(-?[\\d.]+) Text\\[\"([A-Z]\\d+)\" asc=([\\d.]+) desc=([\\d.]+)\\]");
	private static final Pattern SEPARATOR_Y = Pattern.compile("y=(-?[\\d.]+) (?:artifact )?FootnoteSeparator\\[");

	private static final String NOTE = "<span class='fn'>N1 N2 N3 N4 N5 N6 N7 N8</span>";

	private static String document(final String style, final String body) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<?jp.cssj.property name="output.page-width" value="300pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<style>
				@page { margin: 30pt }
				body { margin: 0; font: 10pt serif }
				p { margin: 0 }
				.fn { float: footnote }
				%s
				</style></head><body>%s</body></html>
				""".formatted(style, body);
	}

	/** Distinct y of the note's words (N1..N8) on the first page. */
	private static TreeSet<Double> noteLines(final String name, final String html) throws Exception {
		final TreeSet<Double> ys = new TreeSet<>();
		final Matcher m = TEXT.matcher(convert(name, html).get(0));
		while (m.find()) {
			if (m.group(2).startsWith("N")) {
				ys.add(Double.parseDouble(m.group(1)));
			}
		}
		assertFalse(name + ": 注が無い", ys.isEmpty());
		return ys;
	}

	public void testParagraphInGridItem() throws Exception {
		final TreeSet<Double> lines = noteLines("grid", document(
				".g { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4pt }",
				"<div class='g'><div><p>T0" + NOTE + "</p></div><div>B</div><div>C</div><div>D</div></div>"));
		assertEquals("grid の項目の段落の注は1行: " + lines, 1, lines.size());
	}

	public void testParagraphInTableCell() throws Exception {
		final TreeSet<Double> lines = noteLines("cell", document("td { width: 40pt }",
				"<table><tr><td><p>T0" + NOTE + "</p></td><td>B</td></tr></table>"));
		assertEquals("表のセルの段落の注は1行: " + lines, 1, lines.size());
	}

	public void testParagraphInSizedBlock() throws Exception {
		final TreeSet<Double> lines = noteLines("sized",
				document(".w { width: 40pt }", "<div class='w'><p>T0" + NOTE + "</p></div>"));
		assertEquals("幅を指定したブロックの段落の注は1行: " + lines, 1, lines.size());
	}

	/** margin-top lies between the body text and the separator: body lines stop that far above it. */
	public void testMarginTopKeepsBodyAway() throws Exception {
		final StringBuilder body = new StringBuilder("<p>L0" + NOTE + "</p>");
		for (int i = 1; i < 20; ++i) {
			body.append("<p>L").append(i).append("</p>");
		}
		final String page = convert("margin-top",
				document("@page { @footnote { border-top: 1pt solid black; margin-top: 40pt } }", body.toString()))
				.get(0);
		final Matcher rule = SEPARATOR_Y.matcher(page);
		assertTrue("区切り線が無い", rule.find());
		final double ruleTop = Double.parseDouble(rule.group(1));
		double bodyEnd = 0;
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			if (m.group(2).startsWith("L")) {
				bodyEnd = Math.max(bodyEnd, Double.parseDouble(m.group(1)) + Double.parseDouble(m.group(3))
						+ Double.parseDouble(m.group(4)));
			}
		}
		assertTrue("本文がある", bodyEnd > 0);
		assertTrue("本文の下端 " + bodyEnd + " は線の上端 " + ruleTop + " から 40pt 以上離れる", bodyEnd <= ruleTop - 40 + 0.01);
		assertTrue("本文は頁の残りまで組む(線から 52pt 以内): " + bodyEnd, bodyEnd > ruleTop - 52);
	}

	private static List<String> convert(final String name, final String html) throws Exception {
		final File dir = new File("local/footnote-area-width/" + name);
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
		}, "footnote-area-width-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] files = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", files);
		java.util.Arrays.sort(files);
		final List<String> pages = new ArrayList<>();
		for (final File f : files) {
			pages.add(java.nio.file.Files.readString(f.toPath(), StandardCharsets.UTF_8));
		}
		return pages;
	}
}
