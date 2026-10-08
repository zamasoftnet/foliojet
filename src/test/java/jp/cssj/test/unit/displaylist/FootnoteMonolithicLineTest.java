package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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
 * A figure that fills most of the page stays whole when the next paragraph's footnote does not fit beside it
 * (2026-10-08, EPUB brush-up D-11).
 *
 * <p>
 * The footnote was reserved as soon as its call was laid out, which shortened the page below the figure that was already
 * on it. The page break then cut at the shortened limit, found the figure at the page start taller than that and
 * rescue-sliced it, so the figure's last 21 pt were drawn again at the top of the next page. The call moves to the next
 * page anyway and takes its note along, so the figure is kept whole and the paragraph with the call starts the next
 * page, as in Vivliostyle.
 * </p>
 */
public class FootnoteMonolithicLineTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	public FootnoteMonolithicLineTest(final String name) {
		super(name);
	}

	/** Horizontal: 91.44 mm type area. */
	private static final String HORIZONTAL = "size: 188mm 127mm; margin: 19mm 24mm 16.56mm 30.65mm";

	/** Vertical: the same type area turned, 91.44 mm along the page axis. */
	private static final String VERTICAL = "size: 127mm 188mm; margin: 24mm 19mm 30.65mm 16.56mm";

	private static String document(final String page, final String writingMode, final String figure) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml" lang="ja"><head><meta charset="UTF-8"/>
				<style>
				@page { %s }
				html { writing-mode: %s }
				body { margin: 0; font-size: 9pt; line-height: 16.2pt }
				p { margin: 0 }
				.fn { float: footnote; font-size: 7pt; line-height: 11pt }
				</style></head><body>
				%s
				<p>N7<span class="fn">N7 Pierre-Auguste Renoir, Bal du moulin de la Galette, 1876, painting.
				http://allart.biz/photos/image/Pierre_Auguste_Renoir_2_Bal_du_moulin_de_la_Galette_Smaller_version.html
				(derivative work) Public Domain</span></p>
				<p>T9</p>
				</body></html>
				""".formatted(page, writingMode, figure);
	}

	/** An atomic inline 85 mm along the page axis of the 91.44 mm type area: one unbreakable line. */
	private static final String INLINE_FIGURE = "<p><span style=\"display: inline-block; inline-size: 60mm; block-size: 85mm;"
			+ " background: #ccf\"></span></p>";

	public void testInlineFigureHorizontal() throws Exception {
		assertFigureWhole(convert("inline-h", document(HORIZONTAL, "horizontal-tb", INLINE_FIGURE)));
	}

	public void testInlineFigureVertical() throws Exception {
		assertFigureWhole(convert("inline-v", document(VERTICAL, "vertical-rl", INLINE_FIGURE)));
	}

	public void testBlockImageHorizontal() throws Exception {
		final String src = new File("files/unittest/blue.png").getAbsoluteFile().toURI().toString();
		assertFigureWhole(convert("block-h", document(HORIZONTAL, "horizontal-tb",
				"<img src=\"" + src + "\" style=\"display: block; width: 60mm; height: 85mm\"/>")));
	}

	/** Page 1 holds the whole figure; the call, its note and the next paragraph start page 2; no sliced copies. */
	private static void assertFigureWhole(final String[] pages) {
		assertEquals("頁数", 2, pages.length);
		for (final String page : pages) {
			assertFalse("図の切れ端が再描画された:\n" + page, page.contains("artifact AbsoluteRectFrame"));
		}
		assertFalse("1 頁の図が切られた:\n" + pages[0],
				pages[0].lines().anyMatch(l -> l.contains("AbsoluteRectFrame") && l.contains("clip=")));
		assertFalse("呼び出しが 1 頁に残った", pages[0].contains("Text[\"N7\""));
		assertTrue("呼び出しが 2 頁に無い", pages[1].contains("Text[\"N7\""));
		assertTrue("注が 2 頁に無い", pages[1].contains("FootnoteLabel"));
		assertTrue("後の段落が 2 頁に無い", pages[1].contains("Text[\"T9\""));
	}

	/** Convert and return each page's display list in page order. */
	private static String[] convert(final String name, final String html) throws Exception {
		final File dir = new File("local/footnote-monolithic-line/" + name);
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
		}, "footnote-monolithic-line-" + name, 64L * 1024 * 1024);
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
