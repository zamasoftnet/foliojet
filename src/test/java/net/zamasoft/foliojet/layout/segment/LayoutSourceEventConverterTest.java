package net.zamasoft.foliojet.layout.segment;

import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;

/**
 * M6d-A3c(2026-07-22新設)、{@code LayoutSource.Event}を
 * {@link SegmentEvent}へ変換するアダプタの単体テストです。
 * {@code LayoutSource}本体・{@code SourceReplayer}への実際の配線
 * (production経路からの呼び出し)はまだ行わない。
 */
public class LayoutSourceEventConverterTest extends TestCase {
	/** イベント数は1:1(ordinal対応を壊さない、M6d-A2との整合性)。 */
	public void testEventCountIsPreserved1to1() {
		final LayoutSource log = new LayoutSource();
		log.append(new LayoutSource.Start(LayoutSource.BoxKind.FLOW, new BlockParams(), new FlowPos()));
		log.append(new LayoutSource.Chars(0, "abc".toCharArray(), false));
		log.append(new LayoutSource.EndBlock());
		final LayoutSource.ReplaySlice slice = log.capture(0, 2);

		final List<SegmentEvent> converted = LayoutSourceEventConverter.convert(slice);
		assertEquals(3, converted.size());
	}

	/** BoxKind.FLOWは内容を失わずBoxRecipe.Flowへ変換される。 */
	public void testFlowStartConvertsToBeginBoxWithFlowRecipe() {
		final BlockParams params = new BlockParams();
		params.orphans = 5;
		final FlowPos pos = new FlowPos();
		pos.align = net.zamasoft.foliojet.layout.box.params.Align.END;

		final SegmentEvent converted = LayoutSourceEventConverter
				.convert(new LayoutSource.Start(LayoutSource.BoxKind.FLOW, params, pos));

		assertTrue(converted instanceof SegmentEvent.BeginBox);
		final SegmentEvent.BeginBox begin = (SegmentEvent.BeginBox) converted;
		assertTrue(begin.recipe() instanceof BoxRecipe.Flow);
		final BoxRecipe.Flow flow = (BoxRecipe.Flow) begin.recipe();
		assertEquals(5, flow.params().materialize().orphans);
		assertEquals(net.zamasoft.foliojet.layout.box.params.Align.END, flow.pos().materialize().align);
	}

	/** BoxKind.INLINEは内容を失わずBoxRecipe.Inlineへ変換される。 */
	public void testInlineStartConvertsToBeginBoxWithInlineRecipe() {
		final InlineParams params = new InlineParams();
		final InlinePos pos = new InlinePos();
		pos.lineHeight = 2.0;

		final SegmentEvent converted = LayoutSourceEventConverter
				.convert(new LayoutSource.Start(LayoutSource.BoxKind.INLINE, params, pos));

		assertTrue(converted instanceof SegmentEvent.BeginBox);
		final BoxRecipe.Inline inline = (BoxRecipe.Inline) ((SegmentEvent.BeginBox) converted).recipe();
		assertEquals(2.0, inline.pos().materialize().lineHeight);
	}

	/** 未対応のBoxKind(例: TABLE)はBarrierへ変換され、種別が明示される。 */
	public void testUnsupportedBoxKindConvertsToBarrierWithKind() {
		final SegmentEvent converted = LayoutSourceEventConverter
				.convert(new LayoutSource.Start(LayoutSource.BoxKind.TABLE, null, null));

		assertTrue(converted instanceof SegmentEvent.Barrier);
		final SegmentEvent.Barrier barrier = (SegmentEvent.Barrier) converted;
		assertEquals(BoxKind.TABLE, barrier.kind().get());
		assertEquals(BarrierReason.NOT_YET_SUPPORTED, barrier.reason());
	}

	/** EndBlockはEndBoxへ、Charsは配列がStringへ変換される(charOffset/fixedは保持)。 */
	public void testEndBlockAndCharsConvert() {
		assertTrue(LayoutSourceEventConverter.convert(new LayoutSource.EndBlock()) instanceof SegmentEvent.EndBox);

		final SegmentEvent text = LayoutSourceEventConverter
				.convert(new LayoutSource.Chars(7, "hello".toCharArray(), true));
		assertTrue(text instanceof SegmentEvent.Text);
		final SegmentEvent.Text t = (SegmentEvent.Text) text;
		assertEquals(7, t.sourceOffset());
		assertEquals("hello", t.text());
		assertTrue(t.fixed());
	}

	/** Opaqueは種別情報を持たないため、kindが空のBarrierへ変換される。 */
	public void testOpaqueConvertsToBarrierWithoutKind() {
		final SegmentEvent converted = LayoutSourceEventConverter.convert(new LayoutSource.Opaque());
		assertTrue(converted instanceof SegmentEvent.Barrier);
		assertTrue(((SegmentEvent.Barrier) converted).kind().isEmpty());
	}
}
