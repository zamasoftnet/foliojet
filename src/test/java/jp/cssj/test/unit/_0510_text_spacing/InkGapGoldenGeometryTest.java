package jp.cssj.test.unit._0510_text_spacing;

import static jp.cssj.test.unit._0510_text_spacing.InkGapTestSupport.get;
import static net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.inkStart;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.pdfg2d.gc.text.Text;

/** 通常goldenが省くrun内のxadvanceを、同じfixtureの表示リストから検証する。 */
public class InkGapGoldenGeometryTest extends AbstractTestCase {
	public InkGapGoldenGeometryTest(String name) { super(name); }

	private record Run(double x, double y, Text text) {
		String chars() { return new String(text.getChars(), 0, text.getCharCount()); }
		double extra(int index) { return text.xAdvances() == null ? 0 : text.xAdvances().get(index); }
	}

	@Override
	protected void transcode() throws Exception {
		final List<Run> runs = new ArrayList<>();
		try (final var observation = DisplayListDumper.observePages((drawer, page) -> {
			try {
				assertEquals(1, (int) page);
				for (final var command : (List<?>) get(drawer, "paintCommands")) {
					final var drawable = get(command, "drawable");
					if (!drawable.getClass().getSimpleName().equals("TextSequenceDrawable")) continue;
					final double x = (double) get(command, "x");
					double y = (double) get(command, "y");
					final var contents = (List<?>) get(drawable, "contents");
					final int offset = (int) get(drawable, "off"), length = (int) get(drawable, "len");
					for (int i = offset; i < offset + length; ++i) {
						final var text = (Text) contents.get(i);
						runs.add(new Run(x, y, text));
						y += text.getAdvance();
					}
				}
			} catch (Exception e) { throw new AssertionError(e); }
		})) {
			CTISessionHelper.transcodeFile(this.session,
					new File("files/unittest/0510-text-spacing/jlreq-shrink-vertical-colon.html"), "text/html", null);
		}
		assertEquals(10, runs.size());
		assertEquals("体；め", runs.get(0).chars());
		assertEquals("仮", runs.get(1).chars());
		assertEquals(18.0, runs.get(0).x - runs.get(1).x, .001);
		assertEquals(0.0, runs.get(0).extra(1), .001);
		assertEquals(0.0, runs.get(0).extra(2), .001);
		assertEquals("体・め仮", runs.get(2).chars());
		assertEquals(-3.0, runs.get(2).extra(1), .001);
		assertEquals(-3.0, runs.get(2).extra(2), .001);
		assertEquals("；（", runs.get(3).chars());
		assertEquals(-4.2, runs.get(3).extra(1), .01);
		assertEquals("体；", runs.get(4).chars());
		assertEquals("（め", runs.get(5).chars());
		assertEquals(18.0, runs.get(4).x - runs.get(5).x, .001);
		assertEquals(0.0, runs.get(5).extra(0), .001);
		final var tail = runs.get(5).text;
		assertEquals(4.2, inkStart(tail.getFontMetrics(), tail.getGlyphIds()[0], 12, tail.getFontStyle()), .01);
		assertEquals(-4.064, runs.get(7).extra(0), .01);
		assertEquals("体；め", runs.get(8).chars());
		assertEquals("仮", runs.get(9).chars());
		assertEquals(0.0, runs.get(8).extra(1), .001);
		assertEquals(0.0, runs.get(8).extra(2), .001);
		for (int i = 0; i < runs.size(); ++i) {
			final var run = runs.get(i);
			final List<Double> extras = new ArrayList<>();
			for (int j = 0; j < run.text.getGlyphCount(); ++j) extras.add(run.extra(j));
			System.err.printf(Locale.ROOT, "inkgap run=%d chars=%s x=%.3f y=%.3f advance=%.3f xadvance=%s%n",
					i, run.chars(), run.x, run.y, run.text.getAdvance(), extras);
		}
	}
}
