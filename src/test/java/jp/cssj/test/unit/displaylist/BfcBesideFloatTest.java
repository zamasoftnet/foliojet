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
import java.util.Comparator;
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
 * Verifies the behavior of boxes establishing independent formatting contexts
 * (flow-root or overflow other than visible) beside normal floats (2026-10-05).
 *
 * <p>
 * Per CSS 2.1 §9.5, their border boxes do not overlap floats. An auto width narrows to the space beside
 * the float; a box with a specified line-axis size moves below the float if it does not fit beside it
 * (as in Chrome). Previously, flow-root/overflow boxes did not narrow, extending their backgrounds
 * under floats while only their inner lines wrapped. Multi-column/flex/grid boxes that did not fit
 * beside floats also overflowed the type area. Ordinary boxes retain full width, with only inner lines
 * wrapping, as before.
 * </p>
 */
public class BfcBesideFloatTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	/** Float width (30 mm). */
	private static final double FLOAT_WIDTH = 30 * 72 / 25.4;

	private static final Pattern FRAME = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	public BfcBesideFloatTest(final String name) {
		super(name);
	}

	private static String document(final String... boxStyles) {
		final StringBuilder body = new StringBuilder();
		for (final String style : boxStyles) {
			body.append("<div class=\"f\"></div><div class=\"b\" style=\"").append(style)
					.append("\">").append("箱の中の字。浮動体の高さを越えて折り返すまで続く長さの文です。".repeat(3))
					.append("</div><div style=\"clear:both\"></div>");
		}
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml" lang="ja"><head><meta charset="UTF-8"/>
				<style>
				@page{size:100mm 100mm;margin:5mm}
				body{margin:0;font:9pt/1.5 serif}
				.f{float:left;width:30mm;height:12mm;background:#c66}
				.b{background:#ccf;margin:0 0 2mm 0}
				</style></head><body>%s</body></html>
				""".formatted(body);
	}

	/**
	 * flow-root and overflow:hidden narrow the whole box beside the float, so even lines below the
	 * float start at its right edge. Ordinary boxes retain full width, and lines below the float
	 * return to the left edge.
	 */
	public void testAutoWidthBoxesNarrowBesideFloat() throws Exception {
		final String page = convert("auto", document("display:flow-root", "overflow:hidden", "display:block"))[0];
		final List<double[][]> pairs = pairs(page);
		assertEquals(3, pairs.size());
		for (int i = 0; i < 3; ++i) {
			final double[] f = pairs.get(i)[0], b = pairs.get(i)[1];
			assertEquals("箱 " + i + " は浮動体と同じ高さから", f[1], b[1], 0.5);
			final double[] below = lineStartsBelow(page, b, f[1] + f[3]);
			assertTrue("箱 " + i + " に浮動体より下の行が無い", below.length > 0);
			for (final double x : below) {
				assertEquals("箱 " + i + " の浮動体より下の行の始まり", i < 2 ? f[0] + f[2] : f[0], x, 0.5);
			}
		}
	}

	/** Overflow/flex boxes too wide to fit beside the float move below it; boxes narrow enough stay beside it. */
	public void testSizedBoxesClearWhenTheyDoNotFit() throws Exception {
		final String page = convert("sized",
				document("overflow:hidden;width:80mm", "display:flex;width:80mm", "overflow:hidden;width:50mm"))[0];
		final List<double[][]> pairs = pairs(page);
		assertEquals(3, pairs.size());
		for (int i = 0; i < 2; ++i) {
			final double[] f = pairs.get(i)[0], b = pairs.get(i)[1];
			assertEquals("箱 " + i + " は浮動体の下へ", f[1] + f[3], b[1], 0.5);
		}
		final double[] f = pairs.get(2)[0], b = pairs.get(2)[1];
		assertEquals("入る幅の箱は浮動体と同じ高さから", f[1], b[1], 0.5);
		for (final double x : lineStartsBelow(page, b, b[1])) {
			assertEquals("入る幅の箱の行は浮動体の右から", f[0] + f[2], x, 0.5);
		}
	}

	/**
	 * An auto box after a full-width float moves below it instead of collapsing to width 0 (the weather box
	 * below msn tabs).
	 */
	public void testNoRoomBesideFullWidthFloat() throws Exception {
		final String page = convert("full", document("overflow:hidden").replace("width:30mm", "width:90mm"))[0];
		final List<double[]> frames = new ArrayList<>();
		final Matcher m = FRAME.matcher(page);
		while (m.find()) {
			frames.add(new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
					Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)) });
		}
		frames.sort(Comparator.comparingDouble(r -> r[1]));
		assertEquals("浮動体と箱: " + page, 2, frames.size());
		final double[] f = frames.get(0), b = frames.get(1);
		assertEquals("箱は浮動体の下へ", f[1] + f[3], b[1], 0.5);
		final double[] starts = lineStartsBelow(page, b, b[1]);
		assertTrue("箱に行が無い", starts.length > 0);
		for (final double x : starts) {
			assertEquals("箱の行は左端から", f[0], x, 0.5);
		}
	}

	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) (?:artifact )?Text\\[");

	/** Starting x of lines that start below {@code from} and lie within the box frame's height. */
	private static double[] lineStartsBelow(final String page, final double[] box, final double from) {
		final List<Double> xs = new ArrayList<>();
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			final double y = Double.parseDouble(m.group(2));
			if (y >= from - 0.5 && y >= box[1] - 0.5 && y < box[1] + box[3] - 0.5) {
				xs.add(Double.parseDouble(m.group(1)));
			}
		}
		return xs.stream().mapToDouble(Double::doubleValue).toArray();
	}

	/**
	 * Returns pairs of page floats (width 30 mm) and box background frames, ordered from top to bottom.
	 * Frame x/width in the display list exclude line-axis margins (the offset narrowed by the float
	 * is drawn as margin), so inspect text positions along the line axis.
	 */
	private static List<double[][]> pairs(final String page) {
		final List<double[]> floats = new ArrayList<>();
		final List<double[]> boxes = new ArrayList<>();
		final Matcher m = FRAME.matcher(page);
		while (m.find()) {
			final double[] r = { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
					Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)) };
			(Math.abs(r[2] - FLOAT_WIDTH) < 0.5 ? floats : boxes).add(r);
		}
		final Comparator<double[]> byY = Comparator.comparingDouble(r -> r[1]);
		floats.sort(byY);
		boxes.sort(byY);
		assertEquals("浮動体と箱の数: " + page, floats.size(), boxes.size());
		final List<double[][]> pairs = new ArrayList<>();
		for (int i = 0; i < floats.size(); ++i) {
			pairs.add(new double[][] { floats.get(i), boxes.get(i) });
		}
		return pairs;
	}

	/** Converts the document and returns each page's display list in page order. */
	private static String[] convert(final String name, final String html) throws Exception {
		final File dir = new File("local/bfc-beside-float/" + name);
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
		}, "bfc-beside-float-" + name, 64L * 1024 * 1024);
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
