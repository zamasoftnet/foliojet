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
import java.util.List;
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
 * A flex container with a definite height moved whole to the next page keeps it (2026-10-10, NEXT-SESSION §5 item 2).
 * An absolutely positioned descendant makes the move lay the container out again, and it came out as tall as its items
 * (shadcn's 27pt tab list: 32.4pt; Chrome keeps 27pt, the buttons overflowing it). Content that runs past the page still
 * grows the box, so that the page breaks carry it on (a flex of width: 0 in vertical-rl went off the paper when it kept
 * its size: fuzz-repro/flex-resplit-duplicates-content).
 */
public class DefiniteFlexMovedWholeTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern FRAME = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static final Pattern TEXT = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"([^\"]*)\" asc=([\\d.]+) desc=([\\d.]+)\\]");

	/** 200x100pt paper: the 27pt list after 75pt and an 18pt margin does not fit, and moves to the next page. */
	public void testTabListKeepsItsHeight() throws Exception {
		final String html = """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 200pt 100pt; margin: 0 }
				body { margin: 0; font: 12pt/1.5 sans-serif }
				.list { display: flex; width: fit-content; height: 27pt; padding: 2.25pt 0; box-sizing: border-box;
					align-items: center; gap: 12pt; background: #ddd }
				.tab { position: relative; display: inline-flex; height: calc(100% - 1px); align-items: center;
					padding: 3pt 0 9pt; border: 0; border-bottom: 2px solid #000; font: 12pt sans-serif; background: #fff }
				.tab::after { content: ""; position: absolute; left: 0; right: 0; bottom: -5px; height: 2px;
					background: #000 }
				</style></head><body><div style="height: 75pt">TOP</div><div style="margin-top: 18pt"><div class="list">
				<button class="tab">Command</button><button class="tab">Manual</button></div></div><p>END</p></body></html>
				""";
		final List<String> pages = convertPages("tabs", html);
		final String last = pages.get(pages.size() - 1);
		final Matcher m = FRAME.matcher(last);
		assertTrue("タブの帯が無い:\n" + last, m.find());
		assertEquals("タブの帯の位置(次の頁の頭)", 18, Double.parseDouble(m.group(2)), 0.01);
		assertEquals("タブの帯の高さ", 27, Double.parseDouble(m.group(4)), 0.01);
		assertEquals("頁数", 2, pages.size());
		assertTexts(pages, 100, "TOP", "Command", "Manual", "END");
	}

	/**
	 * Items running past the page grow the container moved whole, whatever the cursor says: a negative trailing margin
	 * (-100pt on the last item) pulls the cursor back over the 80pt item, which went off the 60pt paper when the 20pt
	 * flex kept its size (codex review). The overflow is carried on to the next page, as Chrome does.
	 */
	public void testOverflowPastPageGrows() throws Exception {
		final String html = """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 200pt 60pt; margin: 0 }
				body { margin: 0; font: 10pt/10pt serif }
				.x { position: relative }
				.x::after { content: ""; position: absolute; left: 0; top: 0; width: 2pt; height: 2pt; background: #f00 }
				.f { display: flex; height: 20pt; background: #ddd; align-items: flex-start }
				.f > div { width: 40pt; background: #aaa }
				</style></head><body><div style="height: 42pt"></div><div style="margin-top: 15pt"><div class="f">
				<div class="x" style="height: 40pt">A1</div><div style="height: 80pt">A2<br/>a<br/>b<br/>c<br/>d<br/>e<br/>f<br/>g</div>
				<div style="height: 20pt; margin-bottom: -100pt">A3</div></div></div><p>END</p></body></html>
				""";
		assertTexts(convertPages("overflow", html), 60, "A1", "A2", "a", "b", "c", "d", "e", "f", "g", "A3", "END");
	}

	/** Each token is drawn once, and on the paper (its line box ends within {@code paper}). */
	private static void assertTexts(final List<String> pages, final double paper, final String... tokens) {
		final java.util.Map<String, Integer> counts = new java.util.HashMap<>();
		for (final String page : pages) {
			final Matcher m = TEXT.matcher(page);
			while (m.find()) {
				counts.merge(m.group(3), 1, Integer::sum);
				final double bottom = Double.parseDouble(m.group(2)) + Double.parseDouble(m.group(4))
						+ Double.parseDouble(m.group(5));
				assertTrue(m.group(3) + " が紙の外(下端 " + bottom + "pt):\n" + page, bottom <= paper + 0.01);
			}
		}
		for (final String token : tokens) {
			assertEquals(token + " の回数 " + counts, Integer.valueOf(1), counts.get(token));
		}
	}

	private static List<String> convertPages(final String name, final String html) throws Exception {
		final File dir = new File("local/definite-flex-moved-whole/" + name);
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
		}, "definite-flex-moved-whole-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		assertTrue(name + ": ページが1枚も出ていない", pages.length > 0);
		Arrays.sort(pages);
		final List<String> result = new ArrayList<>();
		for (final File page : pages) {
			result.add(java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return result;
	}
}
