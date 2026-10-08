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
 * A multicol that overflows a square page along the page axis is cut at the type-area edge and its overflow goes on in
 * the next column, the same in vertical and horizontal writing (2026-10-08).
 *
 * <p>
 * At a multicol's close {@code BreakableBuilder.endBreakableFlowBlock} compared the page limit with the box's physical
 * height, which is the line axis in vertical writing. On a square page the vertical multicol never tried the column
 * break: the first orthogonal block was cut 6 pt past the type area (at the paper edge) and the second block's overflow
 * went to a fifth page instead of the second column (the corpus case is 0400-column-count/writing-mode-column2.html).
 * </p>
 */
public class MulticolPageAxisTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	/** Body margin; the type area is [6, 394] on both axes of the 400 pt square page. */
	private static final double EDGE = 6;

	private static final Pattern H1 = Pattern.compile("Text\\[\"H1\"");

	private static final Pattern CLIP = Pattern
			.compile("AbsoluteRectFrame\\[[^]]*\\] clip=\\[(-?[\\d.]+) (-?[\\d.]+) ([\\d.]+) ([\\d.]+)\\]");

	public MulticolPageAxisTest(final String name) {
		super(name);
	}

	private static String document(final String writingMode, final String orthogonal) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml" lang="ja"><head><meta charset="UTF-8"/>
				<style>
				@page{size:400pt 400pt;margin:0}
				body{margin:6pt;font-size:10pt;writing-mode:%s;columns:2}
				p{margin:0}
				.o{writing-mode:%s}
				.big{width:425pt;height:425pt;background:#ccf}
				</style></head><body>
				<p>T0 T1 T2</p>
				<div class="o"><div class="big">H0</div></div>
				<div class="o"><div class="big">H1</div></div>
				</body></html>
				""".formatted(writingMode, orthogonal);
	}

	public void testVertical() throws Exception {
		final String[] pages = convert("vertical", document("vertical-rl", "horizontal-tb"));
		assertColumns(pages);
		final Matcher c = CLIP.matcher(pages[1]);
		assertTrue(c.find());
		assertEquals("2 頁の切れ目は版面の左端: " + c.group(), EDGE, Double.parseDouble(c.group(1)), 0.01);
	}

	public void testHorizontal() throws Exception {
		final String[] pages = convert("horizontal", document("horizontal-tb", "vertical-rl"));
		assertColumns(pages);
		final Matcher c = CLIP.matcher(pages[1]);
		assertTrue(c.find());
		assertEquals("2 頁の切れ目は版面の下端: " + c.group(), 400 - EDGE,
				Double.parseDouble(c.group(2)) + Double.parseDouble(c.group(4)), 0.01);
	}

	/** Four pages; the second block and its overflow share page 4 (the overflow in the second column). */
	private static void assertColumns(final String[] pages) {
		assertEquals("頁数", 4, pages.length);
		final Matcher h = H1.matcher(pages[3]);
		int count = 0;
		while (h.find()) {
			++count;
		}
		assertEquals("4 頁の H1 の断片(1 段目と 2 段目)", 2, count);
	}

	/** Convert and return each page's display list in page order. */
	private static String[] convert(final String name, final String html) throws Exception {
		final File dir = new File("local/multicol-page-axis/" + name);
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
		}, "multicol-page-axis-" + name, 64L * 1024 * 1024);
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
