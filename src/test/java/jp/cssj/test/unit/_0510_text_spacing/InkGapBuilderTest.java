package jp.cssj.test.unit._0510_text_spacing;

import static jp.cssj.test.unit._0510_text_spacing.InkGapTestSupport.*;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.content.BreakToken;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.params.*;
import net.zamasoft.foliojet.layout.builder.impl.*;
import net.zamasoft.pdfg2d.font.GlyphBounds;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

/** 登録容量・行の採否・適用を同じ制御字面で検証する。 */
public class InkGapBuilderTest extends TestCase {
	private static BlockParams params(FontStyle style) {
		final var params = new BlockParams();
		params.fontStyle = style;
		params.lineHeight = style.getSize();
		params.textAutospace = 0;
		return params;
	}

	private static BlockBuilder builder(BlockParams params) throws Exception {
		final var box = new FlowBlockBox(params, new FlowPos());
		set(box, "width", 100.0);
		set(box, "height", 100.0);
		return new BlockBuilder(null, box);
	}

	private static TextBuilder textBuilder(BlockBuilder builder) throws Exception {
		final var textBuilder = new TextBuilder(builder, BreakToken.NONE);
		set(builder, "textBuilder", textBuilder);
		return textBuilder;
	}

	@SuppressWarnings("unchecked")
	private static List<Object>[] capacities(TextImpl prev, int pi, TextImpl cur, int ci, boolean trim) throws Exception {
		final Class<?> glyph = Class.forName(TextBuilder.class.getName() + "$JlreqGlyph");
		final var ctor = glyph.getDeclaredConstructor(TextImpl.class, int.class, int.class);
		ctor.setAccessible(true);
		final List<Object>[] stages = new List[8];
		for (int i = 1; i < stages.length; ++i) stages[i] = new ArrayList<>();
		call(TextBuilder.class, "addJlreqBoundaryShrinkPoints", new Class<?>[] {List[].class, glyph, glyph, boolean.class},
				stages, ctor.newInstance(prev, pi, (int) prev.chars[pi]), ctor.newInstance(cur, ci, (int) cur.chars[ci]), trim);
		return stages;
	}

	private static double capacity(List<Object> stage) throws Exception {
		double sum = 0;
		for (Object point : stage) sum += (double) get(point, "capacity");
		return sum;
	}

	public void testSharedBudgetAndProbeAndInsufficientNoMutation() throws Exception {
		final var s = style(10, FontStyle.Direction.LTR);
		// advance 10 + autospace 2.5 + current inkStart 0 - prev inkEnd 9.5 = R 3pt。
		final var m = metrics(s, s.getDirection(), gid -> gid == '・'
				? new GlyphBounds(100, -700, 950, 0) : new GlyphBounds(0, -700, 900, 0), 880, 0, 0);
		final var run = text(s, m, "・A");
		run.addXAdvance(1, 2.5);
		final var stages = capacities(run, 0, run, 1, true);
		assertEquals(2.5, capacity(stages[4]), 1e-9);
		assertEquals(0.0, capacity(stages[5]), 1e-9);
		assertEquals(.5, capacity(stages[6]), 1e-9);
		final var tb = textBuilder(builder(params(s)));
		@SuppressWarnings("unchecked") final List<Object> buffer = (List<Object>) get(tb, "textBuffer");
		buffer.add(run);
		set(tb, "lineAxis", run.getAdvance());
		final Class<?>[] types = {double.class, boolean.class};
		// LayoutUtils.compareの既存許容差も超える不足量を与える。
		assertEquals(false, call(tb, "tryJlreqLineShrink", types, 4.0, false));
		assertEquals(false, call(tb, "tryJlreqLineShrink", types, 4.0, true));
		assertEquals(true, call(tb, "tryJlreqLineShrink", types, 3.0, false));
		assertEquals(2.5, run.xAdvances().get(1), 0);
		assertEquals(22.5, (double) get(tb, "lineAxis"), 0);
		assertEquals(0.0, (double) get(tb, "pendingEndHang"), 0);
		assertEquals(true, call(tb, "tryJlreqLineShrink", types, 3.0, true));
		assertEquals(-.5, run.xAdvances().get(1), 1e-9);
		assertEquals(19.5, (double) get(tb, "lineAxis"), 1e-9);
	}

	public void testDistanceUsesPreviousSpacingSameRunKerningAndExistingOnce() throws Exception {
		final var s = style(10, FontStyle.Direction.LTR);
		final var m = metrics(s, s.getDirection(), gid -> gid == '；'
				? new GlyphBounds(100, -700, 1000, 0) : new GlyphBounds(100, -700, 900, 0), 880, 0, 100);
		final var run = text(s, m, "；め");
		run.setLetterSpacing(2);
		run.addXAdvance(1, -.5);
		// C4=2.5-.5=2; R=10+2-1-.5+1-10=1.5。
		assertEquals(1.5, capacity(capacities(run, 0, run, 1, true)[4]), 1e-9);
		final var cur = text(style(12, s.getDirection()), metrics(style(12, s.getDirection()), s.getDirection(),
				gid -> new GlyphBounds(0, -700, 900, 0), 880, 0, 300), "め");
		cur.setLetterSpacing(50); // 後runの字間を参照したら上限が消えて試験が落ちる。
		cur.addXAdvance(0, -.5);
		// run間kerning=0。R=10+2-.5+0-10=1.5。
		assertEquals(1.5, capacity(capacities(run, 0, cur, 0, true)[4]), 1e-9);
	}

	public void testNoNominalCapacityDoesNotReadBounds() throws Exception {
		final var s = style(10, FontStyle.Direction.LTR);
		final var m = metrics(s, s.getDirection(), gid -> { throw new AssertionError("unneeded bounds"); }, 880, 0, 0);
		final var run = text(s, m, "体め");
		final var stages = capacities(run, 0, run, 1, true);
		for (int i = 4; i <= 6; ++i) assertTrue(stages[i].isEmpty());
	}

	public void testStageFourPrecedesFiveAndTakenTrimIsNotDeductedTwice() throws Exception {
		final var s = style(10, FontStyle.Direction.LTR);
		final var m = metrics(s, s.getDirection(), gid -> gid == '；'
				? new GlyphBounds(100, -700, 1000, 0) : new GlyphBounds(300, -700, 900, 0), 880, 0, 0);
		final var run = text(s, m, "；（");
		final var stages = capacities(run, 0, run, 1, true);
		assertEquals(2.5, capacity(stages[4]), 1e-9);
		assertEquals(.5, capacity(stages[5]), 1e-9);
		// 既存の詰め1ptをC5から控除し、R=2ptはそのままC4に使う。
		run.addXAdvance(1, -1);
		final var taken = capacities(run, 0, run, 1, true);
		assertEquals(2.0, capacity(taken[4]), 1e-9);
		assertEquals(0.0, capacity(taken[5]), 1e-9);
		run.addXAdvance(1, -2);
		final var exhausted = capacities(run, 0, run, 1, true);
		assertEquals(0.0, capacity(exhausted[4]), 1e-9);
		assertEquals(0.0, capacity(exhausted[5]), 1e-9);
	}

	public void testUnmeasurableBoundaryPreservesNominalCapacities() throws Exception {
		final var s = style(10, FontStyle.Direction.LTR);
		final var m = metrics(s, s.getDirection(), gid -> null, 880, 0, 0);
		final var run = text(s, m, "・A");
		run.addXAdvance(1, 2.5);
		final var stages = capacities(run, 0, run, 1, true);
		assertEquals(2.5, capacity(stages[4]), 1e-9);
		assertEquals(1.25, capacity(stages[6]), 1e-9);
	}

	public void testBoundaryUndoUsesSplitRunMetricsInsteadOfCurrentBuilderMetrics() throws Exception {
		final var s = style(10, FontStyle.Direction.TB);
		final var m = realMetrics("gothic-inkgap-test.ttf", s);
		final var run = text(s, m, "；（");
		net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.applyRunTrims(run);
		final var head = (TextImpl) run.split(1);
		final var tb = textBuilder(builder(params(s)));
		// 現在builderの別runがGPOSを持っていても分割対象の計算を変えない。
		set(tb, "fontMetrics", metrics(s, s.getDirection(), gid -> null, 880, 0, 100));
		final double adjustment = (double) call(tb, "boundaryAdjustment",
				new Class<?>[] {TextImpl.class, TextImpl.class}, head, run);
		assertEquals(-3.5, adjustment, .01);
		run.addXAdvance(0, -adjustment);
		assertEquals(0.0, run.xAdvances().get(0), 1e-9);
	}

	public void testPrettyCandidateWidthMatchesTextBuilderReplay() throws Exception {
		final var s = style(10, FontStyle.Direction.LTR);
		final var p = params(s);
		p.textWrapStyle = AbstractTextParams.TEXT_WRAP_STYLE_PRETTY;
		p.letterSpacing = Length.create(.5, LengthType.ABSOLUTE);
		final var m = metrics(s, s.getDirection(), gid -> gid == '；'
				? new GlyphBounds(400, -700, 1000, 0) : new GlyphBounds(350, -700, 900, 0), 880, 0, 0);
		final var b = builder(p);
		final var tb = textBuilder(b);
		final Class<?> type = Class.forName("net.zamasoft.foliojet.layout.builder.impl.TotalFitSession");
		final Object session = call(type, "tryBegin", new Class<?>[] {BlockBuilder.class, TextBuilder.class, BreakToken.class},
				b, tb, BreakToken.NONE);
		assertNotNull("horizontal pretty must enter TotalFitSession", session);
		assertEquals(true, call(session, "recordRun", new Class<?>[] {FontStyle.class, net.zamasoft.pdfg2d.gc.font.FontMetrics.class}, s, m));
		final char[] chars = "；（".toCharArray();
		for (int i = 0; i < chars.length; ++i) {
			assertEquals(true, call(session, "recordGlyph", new Class<?>[] {int.class, char[].class, int.class, byte.class, int.class},
					i, chars, i, (byte) 1, (int) chars[i]));
		}
		final double candidate = (double) get(session, "pendingBoxWidth");
		assertEquals(17.5, candidate, 1e-9);
		call(session, "recordRunEnd", new Class<?>[0]);
		call(session, "finishSession", new Class<?>[0]);
		final var buffer = (List<?>) get(tb, "textBuffer");
		assertEquals(1, buffer.size());
		final var replay = (TextImpl) buffer.get(0);
		assertEquals(candidate, replay.getAdvance(), 1e-9);
		assertEquals(-3.5, replay.xAdvances().get(1), 1e-9);
	}

	public void testIntrinsicMeasurerIncludesCapAndBothLetterSpacings() throws Exception {
		final var s = style(10, FontStyle.Direction.TB);
		final var m = realMetrics("gothic-inkgap-test.ttf", s);
		final var p = params(s);
		p.flow = WritingMode.RL;
		p.letterSpacing = Length.create(.5, LengthType.ABSOLUTE);
		final var b = new TwoPassBlockBuilder(null, new FlowBlockBox(p, new FlowPos()));
		b.startTextRun(0, s, m);
		final char[] chars = "；（".toCharArray();
		for (int i = 0; i < chars.length; ++i) b.glyph(i, chars, i, (byte) 1, m.getFont().toGID(chars[i]));
		b.endTextRun();
		b.finish();
		assertEquals(1.65 * 10 + 2 * .5, b.getIntrinsicSizes().maxContent(), .01);
	}
}
