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
 * Pin down {@code text-transform: full-width} (css-text-3) (2026-10-06, jigensha report).
 * Previously, the value was rejected and ignored with warning 2816.
 * It can combine with uppercase/lowercase conversion in either order.
 */
public class TextTransformFullWidthTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	public TextTransformFullWidthTest(final String name) {
		super(name);
	}

	public void testFullWidthAndCombinations() throws Exception {
		final String text = text(convert("""
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml" lang="ja"><head><meta charset="UTF-8"/>
				<style>@page{size:120mm 80mm;margin:5mm} p{margin:0}</style></head><body>
				<p style="text-transform:full-width">ab12!</p>
				<p style="text-transform:uppercase full-width">cd</p>
				<p style="text-transform:full-width capitalize">ef</p>
				<p style="text-transform:none full-width">gh</p>
				</body></html>
				"""));
		assertTrue(text, text.contains("ａｂ１２！"));
		assertTrue(text, text.contains("ＣＤ"));
		assertTrue(text, text.contains("Ｅｆ"));
		// Combining with none is invalid (ignore the entire declaration).
		assertTrue(text, text.contains("gh"));
	}

	/** Digits converted to full-width remain upright in vertical writing (jigensha's "2倍を1回"). */
	public void testFullWidthDigitsInVerticalText() throws Exception {
		final String text = text(convert("""
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml" lang="ja"><head><meta charset="UTF-8"/>
				<style>@page{size:80mm 80mm;margin:5mm} html{writing-mode:vertical-rl} p{margin:0}</style></head><body>
				<p>と<span style="text-transform:full-width">2</span>倍を<span style="text-transform:full-width">1</span>回</p>
				</body></html>
				"""));
		assertTrue(text, text.contains("２") && text.contains("１"));
		assertFalse(text, text.contains("2") || text.contains("1"));
	}

	private static String text(final String[] pages) {
		final StringBuilder s = new StringBuilder();
		for (final String page : pages) {
			final Matcher m = TEXT.matcher(page);
			while (m.find()) {
				s.append(m.group(1)).append('|');
			}
		}
		return s.toString().replace("|", "");
	}

	/** Convert and return each page's display list in page order. */
	private static String[] convert(final String html) throws Exception {
		final File dir = new File("local/text-transform-full-width/" + Integer.toHexString(html.hashCode()));
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
		}, "text-transform-full-width", 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse("変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError("変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("ページが1枚も出ていない", pages);
		Arrays.sort(pages);
		final String[] dumps = new String[pages.length];
		for (int i = 0; i < pages.length; ++i) {
			dumps[i] = java.nio.file.Files.readString(pages[i].toPath(), StandardCharsets.UTF_8);
		}
		return dumps;
	}
}
