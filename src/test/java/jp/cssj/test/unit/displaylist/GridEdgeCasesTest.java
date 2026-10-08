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
 * Grid defects found by the real-world A/B of 2026-10-08 (tmp/ab/NOTES.md); expected values measured in Chrome.
 *
 * <ul>
 * <li>An empty grid or flex container with an intrinsic width failed the conversion (TwoPass NO_RANGE).</li>
 * <li>Inline SVGs with {@code display: grid}/{@code flex} nested into each other, and from the fourth the conversion
 * failed: the image arrived after display was computed.</li>
 * <li>A track {@code calc()} mixing a percentage and a length dropped the whole {@code grid-template-columns}.</li>
 * <li>A grid without items lost its explicit rows (height 0).</li>
 * </ul>
 */
public class GridEdgeCasesTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(\\w+)\"");

	private static final Pattern FRAME = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	public GridEdgeCasesTest(final String name) {
		super(name);
	}

	private static String document(final String style, final String body) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 500pt 600pt; margin: 0 }
				body { margin: 0; font-size: 10pt; line-height: 20pt }
				p { margin: 0 }
				%s
				</style></head><body>%s</body></html>
				""".formatted(style, body);
	}

	/** Empty grid/flex containers sized by their content (width: fit-content/max-content, orthogonal flow). */
	public void testEmptyIntrinsicContainers() throws Exception {
		final String page = convert("empty", document("""
				div { background: #ccc; padding: 5pt }
				""", "<div style=\"display: grid; width: fit-content\"></div>"
				+ "<div style=\"display: flex; width: max-content\"></div>"
				+ "<div style=\"display: grid; width: fit-content; grid-template-columns: 100pt\"></div>"
				+ "<div style=\"display: grid; writing-mode: vertical-rl; height: 40pt\"></div><p>A</p>"));
		final List<double[]> frames = frames(page);
		assertEquals("枠が4つ:\n" + page, 4, frames.size());
		assertEquals("空の grid(fit-content)は padding だけの幅", 10, frames.get(0)[2], 0.01);
		assertEquals("空の grid(fit-content)は padding だけの高さ", 10, frames.get(0)[3], 0.01);
		assertEquals("空の flex(max-content)は padding だけの幅", 10, frames.get(1)[2], 0.01);
		assertEquals("空の grid の固定トラックは幅に効く", 110, frames.get(2)[2], 0.01);
		assertEquals("縦組みの空の grid は padding だけの幅", 10, frames.get(3)[2], 0.01);
		assertEquals("縦組みの空の grid の高さ", 50, frames.get(3)[3], 0.01);
		assertEquals("後ろの段落", 10 + 10 + 10 + 50, y(page, "A"), 0.01);
	}

	/** Inline SVGs with display: grid/flex/table are block-level replaced elements, one under the other. */
	public void testSvgWithContainerDisplay() throws Exception {
		final String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"%s\" viewBox=\"0 0 200 50\">"
				+ "<rect width=\"200\" height=\"50\"/></svg>";
		final String page = convert("svg", document("""
				svg { width: 100pt }
				.g { display: grid } .f { display: flex } .t { display: table }
				""", svg.formatted("g") + svg.formatted("g") + svg.formatted("g") + svg.formatted("g")
				+ svg.formatted("f") + svg.formatted("f") + svg.formatted("t") + "<p>A</p>"));
		final List<double[]> frames = frames(page);
		assertEquals("SVG が7つ:\n" + page, 7, frames.size());
		for (int i = 0; i < frames.size(); ++i) {
			assertEquals("SVG " + i + " の x", 0, frames.get(i)[0], 0.01);
			assertEquals("SVG " + i + " の y", 25 * i, frames.get(i)[1], 0.01);
			assertEquals("SVG " + i + " の幅", 100, frames.get(i)[2], 0.01);
			assertEquals("SVG " + i + " の高さ", 25, frames.get(i)[3], 0.01);
		}
		assertEquals("後ろの段落", 25 * 7, y(page, "A"), 0.01);
	}

	/** Track calc() mixing a percentage with a length or a font-relative unit. */
	public void testCalcTracks() throws Exception {
		final String page = convert("calc", document("""
				.g { display: grid; width: 400pt }
				.g > div.x { background: #ccc; height: 10pt }
				.a { grid-template-columns: 30pt calc(50% + 10pt) 30pt }
				.b { grid-template-columns: 30pt calc(100% - 60pt) 30pt }
				.c { grid-template-columns: 30pt calc(2em + 5pt) 1fr }
				.d { grid-template-columns: repeat(2, calc(25% - 5pt)) 1fr }
				.e { grid-template-columns: minmax(calc(25% + 5pt), 1fr) 300pt }
				.f { grid-template-columns: 30pt calc(50%) 30pt }
				""", "<div class=\"g a\"><div></div><div class=\"x\"></div></div>"
				+ "<div class=\"g b\"><div></div><div class=\"x\"></div></div>"
				+ "<div class=\"g c\"><div></div><div class=\"x\"></div></div>"
				+ "<div class=\"g d\"><div></div><div class=\"x\"></div></div>"
				+ "<div class=\"g e\"><div class=\"x\"></div><div></div></div>"
				+ "<div class=\"g f\"><div></div><div class=\"x\"></div></div>"));
		final List<double[]> frames = frames(page);
		assertEquals("枠が6つ:\n" + page, 6, frames.size());
		final double[][] expected = { { 30, 210 }, { 30, 340 }, { 30, 25 }, { 95, 95 }, { 0, 105 }, { 30, 200 } };
		final String[] names = { "calc(50% + 10pt)", "calc(100% - 60pt)", "calc(2em + 5pt)",
				"repeat(2, calc(25% - 5pt))", "minmax(calc(25% + 5pt), 1fr)", "calc(50%)" };
		for (int i = 0; i < expected.length; ++i) {
			assertEquals(names[i] + " の x", expected[i][0], frames.get(i)[0], 0.01);
			assertEquals(names[i] + " の幅", expected[i][1], frames.get(i)[2], 0.01);
		}
	}

	/** An empty grid keeps its explicit rows; implicit rows (grid-auto-rows) need items. */
	public void testEmptyGridRows() throws Exception {
		final String page = convert("rows", document("""
				div { background: #ccc }
				""", "<div style=\"display: grid; grid-template-rows: 50pt\"></div>"
				+ "<div style=\"display: grid; grid-template-rows: 20pt 30pt; row-gap: 10pt\"></div>"
				+ "<div style=\"display: grid; width: fit-content; grid-template-columns: 100pt;"
				+ " grid-template-rows: 50pt\"></div>"
				+ "<div style=\"display: grid; grid-auto-rows: 50pt\"></div>"
				+ "<div style=\"display: grid; grid-template-areas: 'x' 'y'; grid-auto-rows: 15pt\"></div>"
				+ "<div style=\"display: grid; grid-template-rows: auto 40pt\"></div><p>A</p>"));
		final List<double[]> frames = frames(page);
		final double[][] expected = { { 500, 50 }, { 500, 60 }, { 100, 50 }, { 500, 0 }, { 500, 30 }, { 500, 40 } };
		final String[] names = { "50pt", "20pt 30pt と行間 10pt", "fit-content", "grid-auto-rows だけ", "領域の 2 行",
				"auto 40pt" };
		assertEquals("枠が6つ:\n" + page, 6, frames.size());
		for (int i = 0; i < expected.length; ++i) {
			assertEquals(names[i] + " の幅", expected[i][0], frames.get(i)[2], 0.01);
			assertEquals(names[i] + " の高さ", expected[i][1], frames.get(i)[3], 0.01);
		}
		assertEquals("後ろの段落", 50 + 60 + 50 + 0 + 30 + 40, y(page, "A"), 0.01);
	}

	/** Frames in display-list order: x, y, w, h. */
	private static List<double[]> frames(final String page) {
		final List<double[]> frames = new ArrayList<>();
		final Matcher m = FRAME.matcher(page);
		while (m.find()) {
			frames.add(new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
					Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)) });
		}
		return frames;
	}

	private static double y(final String page, final String text) {
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			if (m.group(3).equals(text)) {
				return Double.parseDouble(m.group(2));
			}
		}
		throw new AssertionError(text + " が無い:\n" + page);
	}

	/** Convert and return the first page's display list. */
	private static String convert(final String name, final String html) throws Exception {
		final File dir = new File("local/grid-edge-cases/" + name);
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
		}, "grid-edge-cases-" + name, 64L * 1024 * 1024);
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
		return java.nio.file.Files.readString(pages[0].toPath(), StandardCharsets.UTF_8);
	}
}
