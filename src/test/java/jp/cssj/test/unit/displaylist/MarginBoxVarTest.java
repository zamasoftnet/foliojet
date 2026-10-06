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
 * {@code :root} で宣言した変数が {@code @page} とその欄外の箱で効くことを固定します(2026-10-06、jigensha の報告:
 * 欄外の箱の {@code var()} が解決されず、ノンブルが既定の書体・大きさになった)。css-page-3 §6 では頁の文脈は
 * 根要素から継ぐ。
 */
public class MarginBoxVarTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	public MarginBoxVarTest(final String name) {
		super(name);
	}

	public void testRootVariablesInMarginBoxes() throws Exception {
		final String page = convert("""
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml" lang="ja"><head><meta charset="UTF-8"/>
				<style>:root{--big:20pt}
				@page{size:100mm 60mm;margin:15mm;
				  @top-right{content:"VAR";font-size:var(--big)}
				  @bottom-right{content:"DIRECT";font-size:20pt}}
				p{margin:0}</style></head><body><p>body</p></body></html>
				""")[0];
		assertEquals(page, ascent(page, "DIRECT"), ascent(page, "VAR"));
	}

	private static String ascent(final String page, final String text) {
		final Matcher m = Pattern.compile("Text\\[\"" + text + "\" asc=([0-9.]+)").matcher(page);
		assertTrue(text + " が無い: " + page, m.find());
		return m.group(1);
	}

	/** 変換して、各頁の表示リストを頁順に返します。 */
	private static String[] convert(final String html) throws Exception {
		final File dir = new File("local/margin-box-var/" + Integer.toHexString(html.hashCode()));
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
		}, "margin-box-var", 64L * 1024 * 1024);
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
