package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * A float whose continuation stops at what its max-size left goes on past it in a multicol too (2026-10-11,
 * NEXT-SESSION §5-7): a float of max-height: 60pt split over two columns kept L1 to L7 and lost the rest, the multicol
 * counting the float by its box, not by what it paints (horizontal since 132a5fcf). Every line is drawn once, on the
 * paper. The vertical case guards the same once vertical continuations are capped too.
 */
public class FragmentContinuationMaxSizeTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern TEXT = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(\\w+)\" asc=([\\d.]+) desc=([\\d.]+)\\]");

	private static String lines(final int count) {
		final StringBuilder lines = new StringBuilder();
		for (int i = 1; i <= count; ++i) {
			lines.append("<div>L").append(i).append("</div>");
		}
		return lines.toString();
	}

	/** 100x200pt pages with 10pt margins in vertical writing, a 50pt block, then {@code body}. */
	private static String vertical(final String mode, final String style, final String body) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 100pt 200pt; margin: 10pt }
				body { margin: 0; font: 10pt/10pt serif; writing-mode: %s }
				%s
				</style></head><body><div style="width: 50pt"></div>%s</body></html>
				""".formatted(mode, style, body);
	}

	/** max-height: 60pt on a float split over two columns: every line once, on the paper (horizontal). */
	public void testMulticolHorizontal() throws Exception {
		final List<String> pages = convertPages("multicol-h", """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 200pt 100pt; margin: 10pt }
				body { margin: 0; font: 10pt/10pt serif }
				.c { column-count: 2; column-gap: 10pt }
				.f { float: left; width: 30pt; max-height: 60pt; background: #ccc }
				</style></head><body><div style="height: 50pt"></div><div class="c"><div class="f">%s</div>T1 T2 T3</div>
				<p>after</p></body></html>
				""".formatted(lines(16)));
		assertAllOnce("", pages, 100, 200, 16);
	}

	/** The same with max-width in vertical-rl. */
	public void testMulticolVertical() throws Exception {
		final List<String> pages = convertPages("multicol-v", vertical("vertical-rl",
				".c { column-count: 2; column-gap: 10pt } .f { float: left; height: 30pt; max-width: 60pt; background: #ccc }",
				"<div class=\"c\"><div class=\"f\">" + lines(16) + "</div>T1 T2 T3</div><p>after</p>"));
		assertAllOnce("", pages, 200, 100, 16);
	}

	/**
	 * L1 to L{@code count} once each, every line inside the paper: its start and its end along the page axis (the
	 * line's ascent and descent: down in horizontal writing, across in vertical).
	 */
	private static void assertAllOnce(final String label, final List<String> pages, final double paperHeight,
			final double paperWidth, final int count) {
		final boolean vertical = paperHeight > paperWidth;
		final Map<String, Integer> counts = new HashMap<>();
		for (final String page : pages) {
			final Matcher m = TEXT.matcher(page);
			while (m.find()) {
				counts.merge(m.group(3), 1, Integer::sum);
				final double x = Double.parseDouble(m.group(1));
				final double y = Double.parseDouble(m.group(2));
				final double extent = Double.parseDouble(m.group(4)) + Double.parseDouble(m.group(5));
				assertTrue(label + m.group(3) + " が紙の外 (" + x + ", " + y + "):\n" + page,
						x >= -0.01 && x + (vertical ? extent : 0) <= paperWidth + 0.01 && y >= -0.01
								&& y + (vertical ? 0 : extent) <= paperHeight + 0.01);
			}
		}
		for (int i = 1; i <= count; ++i) {
			assertEquals(label + "L" + i + " の回数 " + counts, Integer.valueOf(1), counts.get("L" + i));
		}
	}

	private static List<String> convertPages(final String name, final String html) throws Exception {
		final File dir = new File("local/fragment-continuation-max-size/" + name);
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
		}, "fragment-continuation-max-size-" + name, 64L * 1024 * 1024);
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
		final List<String> result = new ArrayList<>();
		for (final File page : pages) {
			result.add(java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return result;
	}
}
