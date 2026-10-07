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
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

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
 * Pin down two ruby cases (2026-10-06, jigensha report).
 * <ul>
 * <li>Tate-chu-yoko characters in ruby base text disappeared ("2ちゃんねる" became "にちゃんねる").
 * Convert the characters to full-width and retain them in the base text.</li>
 * <li>When a line with ruby longer than its base was followed by a line via br, punctuation made the next line
 * exceed the line length. The ruby box was passed to the line before expanding it for the overhang,
 * so the uncounted length became extra space for the next line.</li>
 * </ul>
 */
public class RubyBaseAndNextLineTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern RUBY = Pattern.compile("RubyUnit\\[\"([^\"]*)\" ruby=\"([^\"]*)\"");

	public RubyBaseAndNextLineTest(final String name) {
		super(name);
	}

	public void testTextCombineInRubyBaseKeepsItsCharacters() throws Exception {
		final String page = String.join("", convert("""
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml" lang="ja"><head><meta charset="UTF-8"/>
				<style>@page{size:113mm 176mm;margin:15mm} html{writing-mode:vertical-rl}
				body{font-size:12pt;line-height:2} p{margin:0} .tcy{text-combine-upright:all}
				ruby>rt{font-size:.5em}</style></head><body>
				<p><ruby><span class="tcy">2</span><rp>(</rp><rt>に</rt><rp>)</rp></ruby>ちゃんねる</p>
				<p><ruby>ＰＴ<span class="tcy">3</span><rt>ピーティースリー</rt></ruby>といった機器</p>
				</body></html>
				"""));
		final List<String> bases = new ArrayList<>();
		final Matcher m = RUBY.matcher(page);
		while (m.find()) {
			bases.add(m.group(1) + "/" + m.group(2));
		}
		assertEquals(page, List.of("２/に", "ＰＴ３/ピーティースリー"), bases);
	}

	/**
	 * After br, line ends align and fit within the line length both after a line with ruby (A) and without it (B).
	 * Line break positions stayed unchanged; justification stretched the lines, so measure character positions
	 * in the PDF (before the fix, A overflowed by about 3.7 mm).
	 */
	public void testLineAfterOverhangingRubyKeepsItsLength() throws Exception {
		final String rest = "あいうえお、かきくけこ、さしすせそ、たちつてと、なにぬねの、はひふへほ、まみむめも、やゆよ、";
		final File dir = convertTo("""
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml" lang="ja"><head><meta charset="UTF-8"/>
				<style>@page{size:160mm 130mm;margin:10mm}
				body{margin:0;font-size:5mm;line-height:1.75;text-align:justify} ruby>rt{font-size:.5em}
				div{margin:0 0 5mm 5mm}</style></head><body>
				<div>第７駆逐隊　<ruby>曙<rt>あけぼの</rt></ruby>、<ruby>潮<rt>うしお</rt></ruby>、<ruby>漣<rt>さざなみ</rt></ruby><br/>%s</div>
				<div>第７駆逐隊　曙、潮、漣<br/>%s</div>
				</body></html>
				""".formatted(rest, rest));
		// Take the maximum character right edge per line (y), and inspect lines starting with "あいう" (after br in A and B).
		final Map<Long, List<TextPosition>> lines = new TreeMap<>();
		try (PDDocument doc = Loader.loadPDF(new File(dir, "out.pdf"))) {
			final PDFTextStripper stripper = new PDFTextStripper() {
				@Override
				protected void processTextPosition(final TextPosition text) {
					lines.computeIfAbsent(Math.round(text.getYDirAdj() * 10d), k -> new ArrayList<>()).add(text);
				}
			};
			stripper.setSuppressDuplicateOverlappingText(false);
			stripper.getText(doc);
		}
		final List<Double> ends = new ArrayList<>();
		for (final List<TextPosition> line : lines.values()) {
			line.sort((a, b) -> Float.compare(a.getXDirAdj(), b.getXDirAdj()));
			if (line.size() > 3 && (line.get(0).getUnicode() + line.get(1).getUnicode() + line.get(2).getUnicode()).equals("あいう")) {
				double end = 0;
				for (final TextPosition t : line) {
					end = Math.max(end, t.getXDirAdj() + t.getWidthDirAdj());
				}
				ends.add(end);
			}
		}
		assertEquals(ends.toString(), 2, ends.size());
		assertEquals(ends.toString(), ends.get(1), ends.get(0), 0.5);
		// The line ends at 160 mm - 10 mm (page margin).
		assertTrue(ends.toString(), ends.get(0) <= 150 * 72 / 25.4 + 0.5);
	}

	/** Convert and return each page's display list in page order. */
	private static String[] convert(final String html) throws Exception {
		final File dir = convertTo(html);
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("ページが1枚も出ていない", pages);
		Arrays.sort(pages);
		final String[] dumps = new String[pages.length];
		for (int i = 0; i < pages.length; ++i) {
			dumps[i] = java.nio.file.Files.readString(pages[i].toPath(), StandardCharsets.UTF_8);
		}
		return dumps;
	}

	/** Convert and return the directory containing the display lists and PDF. */
	private static File convertTo(final String html) throws Exception {
		final File dir = new File("local/ruby-base-and-next-line/" + Integer.toHexString(html.hashCode()));
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
		}, "ruby-base-and-next-line", 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse("変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError("変換が例外で終わった", failure[0]);
		}
		return dir;
	}
}
