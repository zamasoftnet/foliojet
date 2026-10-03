package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
 * 原子インライン(数式・画像)の直後でも行頭禁則が効くことを固定します
 * (2026-10-04、TECH-20261003-004 の⑧。時限暗号の本で、数式の直後で改行して
 * 次の行が「、」で始まっていた)。
 *
 * <p>
 * 改行位置は書体で動くので、段落の幅を 1pt 刻みで 40 通り並べ、どの幅でも
 * 「、」「。」が行頭に来ないことを見る。
 * </p>
 */
public class AtomicInlineKinsokuTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final long WATCHDOG_MS = 120_000L;

	/** 行頭の字(行の左端 x=0 にある Text の最初の字)。 */
	private static final Pattern LINE_HEAD = Pattern.compile("x=0\\.00 y=[-\\d.]+ Text\\[\"(.)");

	public void testNoLineStartsWithClosingPunctuationAfterMath() throws Exception {
		final StringBuilder body = new StringBuilder();
		for (int w = 110; w < 150; ++w) {
			body.append("<p style=\"width:").append(w).append("pt\">あいうえおかきくけこさし")
					.append("<math xmlns=\"http://www.w3.org/1998/Math/MathML\"><mi>Enc</mi></math>")
					.append("、たちつてと。あいうえおかきく<math xmlns=\"http://www.w3.org/1998/Math/MathML\">")
					.append("<mi>y</mi></math>。</p>\n");
		}
		final String html = """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<?jp.cssj.property name="output.page-width" value="300pt"?>
				<?jp.cssj.property name="output.page-height" value="4000pt"?>
				<style>
				@page{margin:0}
				body{margin:0;font:10pt serif;text-align:justify}
				p{margin:0 0 6pt 0}
				</style></head><body>
				%s
				</body></html>
				""".formatted(body);
		final String dump = convert("kinsoku", html);
		final Matcher m = LINE_HEAD.matcher(dump);
		int lines = 0;
		while (m.find()) {
			++lines;
			final String head = m.group(1);
			assertFalse("行頭に「" + head + "」(" + m.group(0) + ")", "、".equals(head) || "。".equals(head));
		}
		assertTrue("行が読めない", lines > 40);
	}

	private static String convert(final String name, final String html) throws Exception {
		final File dir = new File("local/atomic-inline-kinsoku/" + name);
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
		}, "atomic-inline-kinsoku-" + name, 64L * 1024 * 1024);
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
		final StringBuilder all = new StringBuilder();
		for (final File page : pages) {
			all.append(java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return all.toString();
	}
}
