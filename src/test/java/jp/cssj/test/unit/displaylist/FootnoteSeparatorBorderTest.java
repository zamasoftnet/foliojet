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
 * {@code @footnote}の{@code border-top}が本文との区切り線になることを固定します
 * (2026-10-04、TECH-20261003-004 の⑤。以前は「未対応の脚注領域の記述子」と
 * 警告され、区切り線はUAの既定(0.5pt・版面の 1/3)のままだった)。
 */
public class FootnoteSeparatorBorderTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern SEPARATOR = Pattern.compile("FootnoteSeparator\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static String document(final String footnoteRule) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<?jp.cssj.property name="output.page-width" value="300pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<style>
				@page { margin: 30pt; %s }
				body { margin: 0; font: 10pt serif }
				.fn { float: footnote }
				</style></head><body>
				<p>T0<span class="fn">T1</span> T2</p>
				</body></html>
				""".formatted(footnoteRule);
	}

	/** 指定が無ければUAの既定の線(0.5pt、版面 240pt の 1/3)。 */
	public void testDefaultSeparator() throws Exception {
		final Matcher m = SEPARATOR.matcher(convert("default", document("")));
		assertTrue("既定の区切り線が無い", m.find());
		assertEquals("長さ", 80.0, Double.parseDouble(m.group(1)), 0.01);
		assertEquals("太さ", 0.5, Double.parseDouble(m.group(2)), 0.01);
	}

	/** border-top の指定は領域の幅いっぱいに、その太さで。 */
	public void testBorderTopSeparator() throws Exception {
		final Matcher m = SEPARATOR
				.matcher(convert("border", document("@footnote { border-top: 2pt solid red; }")));
		assertTrue("区切り線が無い", m.find());
		assertEquals("長さ(版面の幅いっぱい)", 240.0, Double.parseDouble(m.group(1)), 0.01);
		assertEquals("太さ", 2.0, Double.parseDouble(m.group(2)), 0.01);
	}

	/** border-top: none なら引かない。 */
	public void testBorderTopNone() throws Exception {
		final String dump = convert("none", document("@footnote { border-top: none; }"));
		assertFalse("none なのに区切り線がある", SEPARATOR.matcher(dump).find());
		assertTrue("脚注が無い", dump.contains("\"T1\""));
	}

	private static String convert(final String name, final String html) throws Exception {
		final File dir = new File("local/footnote-separator-border/" + name);
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
		}, "footnote-separator-border-" + name, 64L * 1024 * 1024);
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
		return java.nio.file.Files.readString(pages[0].toPath(), StandardCharsets.UTF_8);
	}
}
