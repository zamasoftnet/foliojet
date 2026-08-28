package jp.cssj.test.unit.fragment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.fragment.FragmentState;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;

/** 固定高ボックスの断片状態計算の回帰テスト。 */
public class FragmentStateTest extends TestCase {
	private static final AbsoluteRectFrame FRAME = new AbsoluteRectFrame(RectFrame.NULL_FRAME);
	private static final Dimension SIZE = Dimension.create(100, 100, LengthType.ABSOLUTE, LengthType.ABSOLUTE);

	public void testEmptyHeadDoesNotConsumeSpecifiedHeight() {
		final FragmentState state = FragmentState.of(WritingMode.TB, false, FRAME, SIZE,
				Dimension.AUTO_DIMENSION, 100, 90, 0, true, true);
		assertEquals("内容を取らない前断片が固定高を消費しています", 100,
				state.nextSize().getHeight(), 0);
	}

	public void testNonEmptyHeadConsumesSpecifiedHeight() {
		final FragmentState state = FragmentState.of(WritingMode.TB, false, FRAME, SIZE,
				Dimension.AUTO_DIMENSION, 100, 90, 10, true);
		assertEquals(10, state.nextSize().getHeight(), 0);
	}
}
