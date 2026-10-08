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
 * An SVG with a viewBox but no width/height has only a ratio: with an auto width and height it fills the containing
 * block's inline size, block-level or inline, as Chrome does (2026-10-08). The viewBox size used to stand in as its
 * natural size. Expected values measured in Chrome.
 */
public class SvgRatioFillTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern FRAME = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 200 50\" %s>"
			+ "<rect width=\"200\" height=\"50\"/></svg>";

	public SvgRatioFillTest(final String name) {
		super(name);
	}

	public void testFill() throws Exception {
		final File svg = new File("local/svg-ratio-fill/svg");
		svg.mkdirs();
		java.nio.file.Files.writeString(new File(svg, "ratio.svg").toPath(), SVG.formatted(""), StandardCharsets.UTF_8);
		final String page = convert("fill", """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 500pt 900pt; margin: 0 }
				body { margin: 0; font-size: 10pt; line-height: 20pt; width: 400pt }
				svg.b { display: block }
				</style></head><body>%s</body></html>
				""".formatted("<div>" + SVG.formatted("class=\"b\"") + "</div>"
				+ "<div>" + SVG.formatted("") + "</div>"
				+ "<div style=\"width: 200pt\">" + SVG.formatted("class=\"b\" style=\"max-width: 100pt\"") + "</div>"
				+ "<div style=\"width: 200pt\">" + SVG.formatted("class=\"b\" style=\"padding: 5pt; margin: 0 10pt\"")
				+ "</div>"
				+ "<div style=\"width: 200pt\">" + SVG.formatted("class=\"b\" style=\"height: 20pt\"") + "</div>"
				+ "<div style=\"width: 200pt; writing-mode: vertical-rl; height: 120pt\">" + SVG.formatted("class=\"b\"")
				+ "</div>"
				+ "<div style=\"width: 200pt\"><img src=\"../svg/ratio.svg\" style=\"display: block\"/></div>"
				+ "<div style=\"width: 200pt\"><img src=\"../svg/ratio.svg\"/></div>"));
		final List<double[]> frames = frames(page);
		assertEquals("枠が8つ:\n" + page, 8, frames.size());
		// The padding/margin case is checked by its height: 200 - 2 x 10 - 2 x 5 = 170 wide, 170 / 4 + 2 x 5 tall
		// (the frame of a replaced box spans its left margin and its border box)
		final double[][] expected = { { 400, 100 }, { 400, 100 }, { 100, 25 }, { -1, 52.5 }, { 80, 20 },
				{ 480, 120 }, { 200, 50 }, { 200, 50 } };
		final String[] names = { "ブロック", "インライン", "max-width: 100pt", "padding と左右の margin", "height だけ",
				"縦組みは高さいっぱい", "img のブロック", "img のインライン" };
		for (int i = 0; i < expected.length; ++i) {
			if (expected[i][0] >= 0) {
				assertEquals(names[i] + " の幅", expected[i][0], frames.get(i)[2], 0.01);
			}
			assertEquals(names[i] + " の高さ", expected[i][1], frames.get(i)[3], 0.01);
		}
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

	/** Convert and return the first page's display list. */
	private static String convert(final String name, final String html) throws Exception {
		final File dir = new File("local/svg-ratio-fill/" + name);
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
		}, "svg-ratio-fill-" + name, 64L * 1024 * 1024);
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
