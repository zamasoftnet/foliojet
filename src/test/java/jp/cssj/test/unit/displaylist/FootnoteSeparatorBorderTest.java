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
 * Verify that {@code @footnote}'s {@code border-top} separates notes from body text
 * (2026-10-04, TECH-20261003-004 item ⑤). Previously it warned of an "unsupported footnote-area
 * descriptor", leaving the separator at the UA default (0.5 pt, one third of the type area).
 */
public class FootnoteSeparatorBorderTest extends TestCase {
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

	/** Without a declaration, use the UA default line (0.5 pt, one third of the 240 pt type area). */
	public void testDefaultSeparator() throws Exception {
		final Matcher m = SEPARATOR.matcher(convert("default", document("")));
		assertTrue("既定の区切り線が無い", m.find());
		assertEquals("長さ", 80.0, Double.parseDouble(m.group(1)), 0.01);
		assertEquals("太さ", 0.5, Double.parseDouble(m.group(2)), 0.01);
	}

	/** A border-top declaration spans the area's full width at the specified thickness. */
	public void testBorderTopSeparator() throws Exception {
		final Matcher m = SEPARATOR
				.matcher(convert("border", document("@footnote { border-top: 2pt solid red; }")));
		assertTrue("区切り線が無い", m.find());
		assertEquals("長さ(版面の幅いっぱい)", 240.0, Double.parseDouble(m.group(1)), 0.01);
		assertEquals("太さ", 2.0, Double.parseDouble(m.group(2)), 0.01);
	}

	/** Draw no line for border-top: none. */
	public void testBorderTopNone() throws Exception {
		final String dump = convert("none", document("@footnote { border-top: none; }"));
		assertFalse("none なのに区切り線がある", SEPARATOR.matcher(dump).find());
		assertTrue("脚注が無い", dump.contains("\"T1\""));
	}

	private static final Pattern SEPARATOR_Y = Pattern.compile("y=(-?[\\d.]+) (?:artifact )?FootnoteSeparator\\[");
	private static final Pattern NOTE_Y = Pattern.compile("y=(-?[\\d.]+) Text\\[\"T1\"");

	private static double find(final Pattern pattern, final String dump, final String what) {
		final Matcher m = pattern.matcher(dump);
		assertTrue(what + "が無い", m.find());
		return Double.parseDouble(m.group(1));
	}

	/**
	 * margin-top and padding-top space the separator (2026-10-15, TECH-20261015-006 ④). Without them the 6 pt gap
	 * holds the line at its center; with them the line sits margin-top below the body text and padding-top above the
	 * notes. Compared through the distance from the line's top to the note's text, which cancels the font metrics.
	 */
	public void testMarginAndPaddingTop() throws Exception {
		final String line = "border-top: 1pt solid black;";
		final String plain = convert("space-plain", document("@footnote { " + line + " }"));
		final String both = convert("space-both",
				document("@footnote { " + line + " margin-top: 10pt; padding-top: 4pt; }"));
		final String margin = convert("space-margin", document("@footnote { " + line + " margin-top: 10pt; }"));
		final double plainGap = find(NOTE_Y, plain, "注") - find(SEPARATOR_Y, plain, "区切り線");
		// The plain line's top is 3 + 0.5 pt above the notes; with padding-top 4 pt it is 4 + 1 pt above them.
		assertEquals("padding-top の分だけ線が注から離れる", plainGap + 1.5,
				find(NOTE_Y, both, "注") - find(SEPARATOR_Y, both, "区切り線"), 0.01);
		// Without padding-top the line's bottom touches the notes.
		assertEquals("padding-top が無ければ線の下は注に接する", plainGap - 2.5,
				find(NOTE_Y, margin, "注") - find(SEPARATOR_Y, margin, "区切り線"), 0.01);
	}

	/**
	 * A line thicker than the UA's 6 pt gap takes its own thickness (2026-10-15, TECH-20261015-007 ⑤): before, it stayed
	 * centered in 6 pt and ran over the first note line. A line up to 6 pt keeps the old gap.
	 */
	public void testThickLineTakesItsThickness() throws Exception {
		final String plain = convert("thin", document("@footnote { border-top: 1pt solid black; }"));
		final String thick = convert("thick", document("@footnote { border-top: 12pt solid black; }"));
		final double plainGap = find(NOTE_Y, plain, "注") - find(SEPARATOR_Y, plain, "区切り線");
		// The thin line's top is 3 + 0.5 pt above the notes; the thick one's top is 12 pt above them.
		assertEquals("太い線の下に注", plainGap - 3.5 + 12,
				find(NOTE_Y, thick, "注") - find(SEPARATOR_Y, thick, "区切り線"), 0.01);
	}

	/**
	 * The same spacing in the bottom band of a vertical page, which places the line from the body side of the gap
	 * (horizontal notes, so the note's y is comparable).
	 */
	public void testMarginAndPaddingTopInBottomBand() throws Exception {
		final String line = "float: bottom; writing-mode: horizontal-tb; border-top: 1pt solid black;";
		final String plain = convert("band-plain", document("@footnote { " + line + " }")
				.replace("font: 10pt serif", "font: 10pt serif; writing-mode: vertical-rl"));
		final String both = convert("band-both", document("@footnote { " + line + " margin-top: 10pt; padding-top: 4pt; }")
				.replace("font: 10pt serif", "font: 10pt serif; writing-mode: vertical-rl"));
		final String margin = convert("band-margin", document("@footnote { " + line + " margin-top: 10pt; }")
				.replace("font: 10pt serif", "font: 10pt serif; writing-mode: vertical-rl"));
		final double plainGap = find(NOTE_Y, plain, "注") - find(SEPARATOR_Y, plain, "区切り線");
		assertEquals("padding-top の分だけ線が注から離れる", plainGap + 1.5,
				find(NOTE_Y, both, "注") - find(SEPARATOR_Y, both, "区切り線"), 0.01);
		assertEquals("padding-top が無ければ線の下は注に接する", plainGap - 2.5,
				find(NOTE_Y, margin, "注") - find(SEPARATOR_Y, margin, "区切り線"), 0.01);
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
