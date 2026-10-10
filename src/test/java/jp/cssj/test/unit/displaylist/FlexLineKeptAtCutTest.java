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
 * A flex line that crosses the page end only with the space after its items stays on the page, and the flex box breaks
 * there as a block does (2026-10-11, NEXT-SESSION §5-1). Measured in Chrome: a 33pt band (one line, its item centered)
 * below a 36pt bar in a 78pt header that the page end breaks 69.75+8.25pt breaks 31.5+1.5pt, its text on the first page,
 * and what follows the header starts at 20.25pt on the second page. The band went whole to the next page and ran past the
 * 8pt left of the header there, over what followed (shopify-doc).
 */
public class FlexLineKeptAtCutTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern RECT = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"([^\"]*)\"");

	private static final String STYLE = """
			@page { size: 200pt 120pt; margin: 0 }
			body { margin: 0; font: 10pt/10pt sans-serif }
			header { height: 78pt; background: #eee }
			.top { height: 36pt; background: #ccc }
			nav { display: flex; align-items: center; height: 33pt; margin-top: 2.25pt; background: #999 }
			.next { margin-top: 12pt; height: 20pt; background: #f88 }
			""";

	/** The band in the header (shopify-doc). */
	public void testBandInBrokenHeader() throws Exception {
		final List<String> pages = convertPages("header", "<div style=\"height: 50pt\"></div><header><div class=\"top\">"
				+ "TOP</div><nav><span>BAND</span></nav></header><div class=\"next\">NEXT</div>");
		assertEquals("頁数", 2, pages.size());
		assertEquals("BAND は 1 頁目", 1, pageOf(pages, "BAND"));
		assertEquals("BAND の回数", 1, count(pages, "BAND"));
		// The rectangles of the dump are margin boxes: the band's starts with its 2.25pt top margin at 86pt.
		final double[] band1 = rect(pages.get(0), 86, 34);
		assertEquals("1 頁目の帯は頁の終わりまで", 120, band1[1] + band1[3], 0.01);
		final double[] band2 = rect(pages.get(1), 0, 33 - 31.75);
		final double next = textY(pages.get(1), "NEXT");
		assertTrue("NEXT(" + next + ")が帯の残り(" + (band2[1] + band2[3]) + ")に重なる",
				next >= band2[1] + band2[3]);
		assertEquals("NEXT の位置(ヘッダーの残り 8pt と margin 12pt の後)", 20, next, 0.01);
	}

	/** A flex taller than the page at its start: the rest of its height goes on to the next page, before what follows. */
	public void testFlexTallerThanPage() throws Exception {
		final List<String> pages = convertPages("tall", "<nav style=\"height: 150pt; margin: 0\"><span>BAND</span></nav>"
				+ "<div class=\"next\" style=\"margin-top: 0\">NEXT</div>");
		assertEquals("頁数", 2, pages.size());
		rect(pages.get(0), 0, 120);
		rect(pages.get(1), 0, 30);
		assertEquals("BAND は 1 頁目", 1, pageOf(pages, "BAND"));
		assertEquals("BAND の回数", 1, count(pages, "BAND"));
		assertEquals("NEXT は帯の残りの後", 30, textY(pages.get(1), "NEXT"), 0.01);
	}

	private static int count(final List<String> pages, final String word) {
		int n = 0;
		for (final String page : pages) {
			for (int i = page.indexOf("Text[\"" + word + "\""); i >= 0; i = page.indexOf("Text[\"" + word + "\"", i + 1)) {
				++n;
			}
		}
		return n;
	}

	private static int pageOf(final List<String> pages, final String word) {
		for (int i = 0; i < pages.size(); ++i) {
			if (pages.get(i).contains("Text[\"" + word + "\"")) {
				return i + 1;
			}
		}
		return -1;
	}

	/** The first full-width rectangle at y with height h on the page: {x, y, w, h}. */
	private static double[] rect(final String page, final double y, final double h) {
		final Matcher m = RECT.matcher(page);
		while (m.find()) {
			if (Math.abs(Double.parseDouble(m.group(2)) - y) < 0.01 && Math.abs(Double.parseDouble(m.group(4)) - h) < 0.01
					&& Math.abs(Double.parseDouble(m.group(3)) - 200) < 0.01) {
				return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
						Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)) };
			}
		}
		fail("y=" + y + " h=" + h + " の矩形が無い:\n" + page);
		return null;
	}

	private static double textY(final String page, final String word) {
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			if (m.group(3).equals(word)) {
				return Double.parseDouble(m.group(2));
			}
		}
		fail(word + " が無い:\n" + page);
		return 0;
	}

	private static List<String> convertPages(final String name, final String body) throws Exception {
		final String html = "<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/>"
				+ "<style>" + STYLE + "</style></head><body>" + body + "</body></html>";
		final File dir = new File("local/flex-line-kept-at-cut/" + name);
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
		}, "flex-line-kept-at-cut-" + name, 64L * 1024 * 1024);
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
