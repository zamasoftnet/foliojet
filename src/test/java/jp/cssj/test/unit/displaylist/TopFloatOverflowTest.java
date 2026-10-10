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
 * A top float placed on the page after a box whose content runs past it leaves room for that content (2026-10-10).
 * The cursor stops at the end of a box of definite height (or max-height), and the top float, translating the page's
 * content down by its own height, was let in by the cursor alone: the overflowing lines went off the 60pt paper and
 * were lost. The float now goes to the next page when the overflow leaves it no room.
 */
public class TopFloatOverflowTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final double PAPER = 60;

	private static final Pattern TEXT = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"([^\"]*)\" asc=([\\d.]+) desc=([\\d.]+)\\]");

	private static final String STYLE = """
			@page { size: 200pt 60pt; margin: 0 }
			body { margin: 0; font: 10pt/10pt serif }
			.x { position: relative }
			.x::after { content: ""; position: absolute; left: 0; top: 0; width: 2pt; height: 2pt; background: #f00 }
			.f { display: flex; height: 20pt; background: #ddd; align-items: flex-start }
			.f > div { width: 40pt; background: #aaa }
			.b { height: 20pt; background: #ddd }
			.tf { float: top; height: 20pt; width: 100pt; background: #0c0 }
			""";

	private static final String TAIL = "<div class=\"tf\">FLOAT</div><p>END</p></body></html>";

	/** A 20pt flex holding a 40pt item, at y=15: the item ends at 55pt, and the 20pt float does not fit above it. */
	public void testFlexOfDefiniteHeight() throws Exception {
		assertAllOnPaper(convertPages("flex", page("<div style=\"margin-top: 15pt\"><div class=\"f\">"
				+ "<div class=\"x\" style=\"height: 40pt\">A1<br/>a<br/>b<br/>c</div><div>A2</div></div></div>")),
				"A1", "a", "b", "c", "A2", "FLOAT", "END");
	}

	/** The same with a block of four lines in 20pt. */
	public void testBlockOfDefiniteHeight() throws Exception {
		assertAllOnPaper(convertPages("block",
				page("<div style=\"margin-top: 15pt\"><div class=\"b\">A1<br/>a<br/>b<br/>c</div></div>")),
				"A1", "a", "b", "c", "FLOAT", "END");
	}

	/** The same with max-height. */
	public void testBlockOfMaxHeight() throws Exception {
		assertAllOnPaper(convertPages("max-height", page("<div style=\"margin-top: 15pt\">"
				+ "<div class=\"b\" style=\"height: auto; max-height: 20pt\">A1<br/>a<br/>b<br/>c</div></div>")),
				"A1", "a", "b", "c", "FLOAT", "END");
	}

	/**
	 * The flex moved whole to the next page, where it is laid out again (its item holds an absolutely positioned box):
	 * a flex of definite height keeps it there too (DefiniteFlexMovedWholeTest), and the overflow must keep the float
	 * out the same way.
	 */
	public void testFlexMovedWhole() throws Exception {
		assertAllOnPaper(convertPages("moved-whole", page("<div style=\"height: 42pt\">TOP</div>"
				+ "<div style=\"margin-top: 15pt\"><div class=\"f\">"
				+ "<div class=\"x\" style=\"height: 40pt\">A1<br/>a<br/>b<br/>c</div><div>A2</div></div></div>")),
				"TOP", "A1", "a", "b", "c", "A2", "FLOAT", "END");
	}

	/**
	 * The overflow of a block inside a flex item, which the item's own builder lays out: the flex is as tall as the item
	 * (20pt), and its content runs to 40pt.
	 */
	public void testOverflowInFlexItem() throws Exception {
		assertAllOnPaper(convertPages("flex-item", page("<div style=\"margin-top: 15pt\">"
				+ "<div class=\"f\" style=\"height: auto\"><div><div class=\"b\">A1<br/>a<br/>b<br/>c</div></div>"
				+ "<div>A2</div></div></div>")), "A1", "a", "b", "c", "A2", "FLOAT", "END");
	}

	/** No overflow: the float fits above the 20pt box and stays on the first page. */
	public void testFloatFitsWithoutOverflow() throws Exception {
		final List<String> pages = convertPages("fits",
				page("<div style=\"margin-top: 15pt\"><div class=\"b\">A1</div></div>"));
		assertAllOnPaper(pages, "A1", "FLOAT", "END");
		assertTrue("FLOAT が 1 頁目に無い:\n" + pages.get(0), pages.get(0).contains("Text[\"FLOAT\""));
	}

	/** The overflow is clipped by an ancestor of the same height: nothing paints past it, and the float fits. */
	public void testClippedOverflowLetsTheFloatIn() throws Exception {
		final List<String> pages = convertPages("clipped", page("<div style=\"margin-top: 15pt; height: 20pt;"
				+ " overflow: hidden\"><div class=\"b\">A1<br/>a<br/>b<br/>c</div></div>"));
		assertTrue("FLOAT が 1 頁目に無い:\n" + pages.get(0), pages.get(0).contains("Text[\"FLOAT\""));
	}

	private static String page(final String body) {
		return "<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/><style>"
				+ STYLE + "</style></head><body>" + body + TAIL;
	}

	/** Each token is drawn once, and on the paper (its line box ends within it). */
	private static void assertAllOnPaper(final List<String> pages, final String... tokens) {
		final Map<String, Integer> counts = new HashMap<>();
		for (final String page : pages) {
			final Matcher m = TEXT.matcher(page);
			while (m.find()) {
				counts.merge(m.group(3), 1, Integer::sum);
				final double bottom = Double.parseDouble(m.group(2)) + Double.parseDouble(m.group(4))
						+ Double.parseDouble(m.group(5));
				assertTrue(m.group(3) + " が紙の外(下端 " + bottom + "pt):\n" + page, bottom <= PAPER + 0.01);
			}
		}
		for (final String token : tokens) {
			assertEquals(token + " の回数 " + counts, Integer.valueOf(1), counts.get(token));
		}
	}

	private static List<String> convertPages(final String name, final String html) throws Exception {
		final File dir = new File("local/top-float-overflow/" + name);
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
		}, "top-float-overflow-" + name, 64L * 1024 * 1024);
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
