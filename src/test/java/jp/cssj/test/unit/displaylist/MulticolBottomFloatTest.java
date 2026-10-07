package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
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
 * Verify that {@code float: bottom} inside multi-column layout does not overlap already laid-out column text
 * (2026-10-05, jigensha report 4).
 *
 * <p>
 * When the anchor was in a later column (the right column of two), placement checked only the current
 * column's position. Even if the left column already reached the page bottom, the image was placed
 * at that page's bottom, overlapping the left column's last few lines. If an earlier column already
 * reaches the placement area, send the image to the next page's bottom, as with a single column.
 * An anchor early in the first column still places the image at the same page's bottom.
 * </p>
 */
public class MulticolBottomFloatTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern FRAME = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static final Pattern TEXT = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) (?:artifact )?Text\\[\"[^\"]*\" asc=([\\d.]+) desc=([\\d.]+)\\]");

	public MulticolBottomFloatTest(String name) {
		super(name);
	}

	private static String document(final int before, final String span) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page{size:148mm 210mm;margin:20mm 16mm}
				body{margin:0;font:9.2pt/1.8 serif}
				p{margin:0;text-indent:1em}
				.text{column-count:2;column-gap:2em}
				.ill{float:bottom;width:100%%;text-align:center;margin-top:1rem%s}
				.ill div{display:inline-block;width:60%%;height:40mm;background:#c66}
				</style></head><body><div class="text">
				<p>一つ目。%s</p><div class="ill"><div></div></div><p>二つ目。%s</p>
				</div></body></html>
				""".formatted(span, "本文。".repeat(before), "本文。".repeat(160));
	}

	/** Anchor early in the left column: place at the same page's bottom without overlapping either column's text. */
	public void testAnchorInFirstColumn() throws Exception {
		final String[] pages = convert("first", document(50, ""));
		assertEquals("絵は 1 頁目", 0, floatPage(pages));
		assertNoOverlap(pages);
	}

	/**
	 * Anchor in the right column: the left already reaches the page bottom, so place the image on the next page's
	 * bottom.
	 */
	public void testAnchorInSecondColumn() throws Exception {
		final String[] pages = convert("second", document(160, ""));
		assertEquals("絵は 2 頁目", 1, floatPage(pages));
		assertNoOverlap(pages);
	}

	public void testAnchorInSecondColumnSpanAll() throws Exception {
		final String[] pages = convert("second-span", document(160, ";column-span:all"));
		assertNoOverlap(pages);
	}

	private static int floatPage(final String[] pages) {
		int found = -1;
		for (int p = 0; p < pages.length; ++p) {
			if (FRAME.matcher(pages[p]).find()) {
				assertEquals("絵が 2 か所にある", -1, found);
				found = p;
			}
		}
		assertTrue("絵が無い", found >= 0);
		return found;
	}

	/**
	 * No line on the same page intersects the image (a frame with background) on the block axis. The image spans both
	 * columns.
	 */
	private static void assertNoOverlap(final String[] pages) {
		for (int p = 0; p < pages.length; ++p) {
			final Matcher f = FRAME.matcher(pages[p]);
			while (f.find()) {
				final double top = Double.parseDouble(f.group(2));
				final double bottom = top + Double.parseDouble(f.group(4));
				final List<String> hits = new ArrayList<>();
				final Matcher t = TEXT.matcher(pages[p]);
				while (t.find()) {
					final double y = Double.parseDouble(t.group(2));
					final double end = y + Double.parseDouble(t.group(3)) + Double.parseDouble(t.group(4));
					if (end > top + 0.5 && y < bottom - 0.5) {
						hits.add(t.group());
					}
				}
				assertTrue((p + 1) + " 頁で絵 [" + top + ", " + bottom + "] と重なる行: " + hits, hits.isEmpty());
			}
		}
	}

	/** Convert and return each page's display list in page order. */
	private static String[] convert(final String name, final String html) throws Exception {
		final File dir = new File("local/multicol-bottom-float/" + name);
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
					CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "multicol-bottom-float-" + name, 64L * 1024 * 1024);
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
		final String[] dumps = new String[pages.length];
		for (int i = 0; i < pages.length; ++i) {
			dumps[i] = java.nio.file.Files.readString(pages[i].toPath(), StandardCharsets.UTF_8);
		}
		return dumps;
	}
}
