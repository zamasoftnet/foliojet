package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;

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
 * {@code @page :blank}(css-page-3)を固定します(2026-10-04、TECH-20261003-004
 * の⑤。時限暗号の本で、章を右ページから始めたときの白紙に柱が出ていた)。
 *
 * <p>
 * 強制改ページで始まり、何も描かずに閉じたページ(左右の改ページで挟んだ
 * 白紙)が{@code :blank}に合う。ページを描く時点でしか分からないので、
 * 効くのはマージンボックスだけ。
 * </p>
 */
public class PageBlankTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final long WATCHDOG_MS = 60_000L;

	private static final String HTML = """
			<!DOCTYPE html>
			<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
			<?jp.cssj.property name="output.page-width" value="200pt"?>
			<?jp.cssj.property name="output.page-height" value="200pt"?>
			<style>
			@page { margin: 30pt; @top-center { content: "HEAD" } }
			@page :blank { @top-center { content: none } @bottom-center { content: "BLANK" } }
			body { margin: 0; font: 10pt serif }
			h1 { break-before: right; font-size: 10pt; margin: 0 }
			</style></head><body>
			<h1>T0</h1>
			<h1>T1</h1>
			</body></html>
			""";

	public void testBlankPageSelectsBlankRule() throws Exception {
		final File[] pages = convert("blank", HTML);
		// T0(1、右)・白紙(2)・T1(3、右)。文書先頭の break-before は頁を作らない
		// (以前は柱があると、柱だけの1頁目と白紙が前に付いて5頁になった)
		assertEquals("頁数", 3, pages.length);
		final String p1 = read(pages[0]), p2 = read(pages[1]), p3 = read(pages[2]);
		assertTrue("1 頁目に T0", p1.contains("\"T0\""));
		assertTrue("3 頁目に T1", p3.contains("\"T1\""));
		assertTrue("1 頁目に柱", p1.contains("\"HEAD\""));
		assertTrue("1 頁目は :blank でない", !p1.contains("\"BLANK\""));
		assertTrue("白紙に柱が出ている", !p2.contains("\"HEAD\""));
		assertTrue("白紙に :blank の内容が無い", p2.contains("\"BLANK\""));
		assertTrue("3 頁目に柱", p3.contains("\"HEAD\""));
		assertTrue("3 頁目は :blank でない", !p3.contains("\"BLANK\""));
	}

	private static String read(final File f) throws java.io.IOException {
		return java.nio.file.Files.readString(f.toPath(), StandardCharsets.UTF_8);
	}

	private static File[] convert(final String name, final String html) throws Exception {
		final File dir = new File("local/page-blank/" + name);
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
		}, "page-blank-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		java.util.Arrays.sort(pages);
		return pages;
	}
}
