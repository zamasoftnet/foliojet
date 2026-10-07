package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
 * Verify that inline MathML <b>uses the body text's font size and aligns to its baseline</b>
 * (2026-10-04, TECH-20261003-004 items ①②; found while typesetting the Jigen Ango book).
 *
 * <ul>
 * <li>① Formulas did not receive CSS font-size/color/font-family and used JEuclid's
 * defaults (12 pt, black). A 12 pt formula appeared inside 9 pt body text.</li>
 * <li>② Formulas were images whose <b>bottom edge</b> sat on the body-text baseline,
 * raising the whole formula by its depth (subscripts, parentheses, y descenders).
 * Align images with depth ({@code BaselineImage}) by their baselines.</li>
 * </ul>
 */
public class MathMLHostStyleTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	/** Formula images appear as frames (AbsoluteRectFrame) in display lists. */
	private static final Pattern FRAME_IN_DUMP = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	public MathMLHostStyleTest(String name) {
		super(name);
	}

	private static final String MATH = "http://www.w3.org/1998/Math/MathML";

	private static String document(final String body) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<?jp.cssj.property name="output.page-width" value="300pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<style>
				@page{margin:10pt}
				body{margin:0;font:9pt/1.6 serif}
				p{margin:0}
				</style></head><body>
				%s
				</body></html>
				""".formatted(body);
	}

	/** ① Formula size follows body font-size (roughly 2× from 9 pt to 18 pt). */
	public void testMathFollowsFontSize() throws Exception {
		final String math = "<math xmlns=\"" + MATH + "\"><mi>x</mi><mo>+</mo><mn>1</mn></math>";
		final List<double[]> frames = frames(convert("font-size", document(
				"<p>T0 " + math + " T1</p><p style=\"font-size:18pt\">T2 " + math + " T3</p>")));
		assertEquals("数式 2 つ", 2, frames.size());
		final double small = frames.get(0)[3], large = frames.get(1)[3];
		assertEquals("18pt の式は 9pt の式の 2 倍の高さ(9pt=" + small + ", 18pt=" + large + ")", 2.0, large / small,
				0.05);
		// Previously, JEuclid used its default 12 pt regardless of CSS,
		// making x+1 12 pt tall even on a 9 pt line.
		assertTrue("9pt の式が 12pt 相当より低い: h=" + small, small < 9.0);
	}

	/** ② A formula with depth (y) has a lower bottom edge than one without depth (1). */
	public void testMathSitsOnBaseline() throws Exception {
		final String descending = "<math xmlns=\"" + MATH + "\"><mi>y</mi></math>";
		final String flat = "<math xmlns=\"" + MATH + "\"><mn>1</mn></math>";
		final List<double[]> frames = frames(
				convert("baseline", document("<p>T0 " + descending + " T1 " + flat + " T2</p>")));
		assertEquals("数式 2 つ", 2, frames.size());
		final double yBottom = frames.get(0)[1] + frames.get(0)[3];
		final double oneBottom = frames.get(1)[1] + frames.get(1)[3];
		// Previously both bottom edges sat on the baseline and were equal.
		assertTrue("y の下の出が基準線の下に出ていない: y の下端=" + yBottom + "、1 の下端=" + oneBottom,
				yBottom > oneBottom + 1.0);
	}

	private static List<double[]> frames(final String dump) {
		final List<double[]> frames = new ArrayList<>();
		final Matcher m = FRAME_IN_DUMP.matcher(dump);
		while (m.find()) {
			frames.add(new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
					Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)) });
		}
		return frames;
	}

	/** Convert a one-page document and return its page's display list. */
	private static String convert(final String name, final String html) throws Exception {
		final File dir = new File("local/mathml-host-style/" + name);
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
		}, "mathml-host-style-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		assertEquals(name + ": 頁数", 1, pages.length);
		return java.nio.file.Files.readString(pages[0].toPath(), StandardCharsets.UTF_8);
	}
}
