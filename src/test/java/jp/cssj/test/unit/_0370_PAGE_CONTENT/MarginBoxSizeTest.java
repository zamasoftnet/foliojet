package jp.cssj.test.unit._0370_PAGE_CONTENT;

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
 * Absolute {@code width}/{@code height} of page-margin boxes (2026-10-15, TECH-20261015-006 ③). Before, the box always
 * filled its region's height and took its content's width, so a 12mm round page-number badge became a thin oval.
 * The expected positions are Vivliostyle's: the box sits against the page area, a center box stays centered, auto
 * margins center the box across the band, and a box without a size keeps the old region.
 *
 * <p>
 * The sheet is 300x200 pt with 30 pt margins, so the page area is 240x140 pt; the display list's origin is the page
 * area's top left.
 * </p>
 */
public class MarginBoxSizeTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern FRAME = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) (?:artifact )?AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static String document(final String marginBox) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<?jp.cssj.property name="output.page-width" value="300pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<style>
				@page { margin: 30pt; %s }
				body { margin: 0; font: 10pt serif }
				</style></head><body><p>T0</p></body></html>
				""".formatted(marginBox);
	}

	/** {x, y, w, h} of the only frame on the page. */
	private static double[] frame(final String name, final String marginBox) throws Exception {
		final Matcher m = FRAME.matcher(convert(name, document(marginBox)));
		assertTrue(name + ": 余白の箱の枠が無い", m.find());
		final double[] frame = new double[4];
		for (int i = 0; i < 4; ++i) {
			frame[i] = Double.parseDouble(m.group(i + 1));
		}
		assertFalse(name + ": 枠が2つ以上ある", m.find());
		return frame;
	}

	private static void assertFrame(final String what, final double[] expected, final double[] actual) {
		assertEquals(what + " x", expected[0], actual[0], 0.01);
		assertEquals(what + " y", expected[1], actual[1], 0.01);
		assertEquals(what + " w", expected[2], actual[2], 0.01);
		assertEquals(what + " h", expected[3], actual[3], 0.01);
	}

	/** The reporter's case: a fixed-size bottom-center box is centered and touches the page area. */
	public void testBottomCenterSize() throws Exception {
		assertFrame("下中央", new double[] { 110, 140, 20, 12 }, frame("bottom-center",
				"@bottom-center { content: '1'; width: 20pt; height: 12pt; background: black; color: white }"));
	}

	/** A top-left box starts at the band's start and sits on the page area's top edge. */
	public void testTopLeftSize() throws Exception {
		assertFrame("左上", new double[] { 0, -12, 20, 12 },
				frame("top-left", "@top-left { content: 'L'; width: 20pt; height: 12pt; background: black }"));
	}

	/** Auto margins center the box across the band (vertically), not along it. */
	public void testAutoMarginsCenterAcrossTheBand() throws Exception {
		assertFrame("上下 auto", new double[] { 110, 149, 20, 12 }, frame("auto-center",
				"@bottom-center { content: '1'; width: 20pt; height: 12pt; margin: auto; background: black }"));
		assertFrame("左上の左右 auto は効かない", new double[] { 0, -21, 20, 12 },
				frame("auto-left", "@top-left { content: 'L'; width: 20pt; height: 12pt; margin: auto; background: black }"));
	}

	/** padding and border add to a content-box size; a side box sits against the page area's side. */
	public void testSideBoxWithPadding() throws Exception {
		assertFrame("左中央", new double[] { -16, 58, 16, 24 }, frame("left-middle",
				"@left-middle { content: 'M'; width: 12pt; height: 20pt; padding: 2pt; background: black }"));
	}

	/** A size larger than the margin is kept, reaching out toward the sheet's edge (codex review 2026-10-15). */
	public void testSizeLargerThanMargin() throws Exception {
		assertFrame("余白より大きい", new double[] { 100, 140, 40, 40 }, frame("larger",
				"@bottom-center { content: '1'; width: 40pt; height: 40pt; background: black }"));
	}

	/** Percentage padding keeps the old region instead of an outer size computed from the ratio (codex review). */
	public void testPercentPaddingKeepsRegion() throws Exception {
		final double[] f = frame("percent-padding",
				"@bottom-center { content: 'TEXT'; width: 100pt; height: 12pt; padding: 0 10%; background: black }");
		assertEquals("高さは指定どおり", 12, f[3], 0.01);
	}

	/** Without width/height the box keeps its old region: the band's full height. */
	public void testAutoSizeUnchanged() throws Exception {
		final double[] auto = frame("auto", "@bottom-center { content: '1'; background: black }");
		assertEquals("高さは余白いっぱい", 30, auto[3], 0.01);
		assertEquals("下端は用紙の端", 140, auto[1], 0.01);
	}

	private static String convert(final String name, final String html) throws Exception {
		final File dir = new File("local/margin-box-size/" + name);
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
		}, "margin-box-size-" + name, 64L * 1024 * 1024);
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
