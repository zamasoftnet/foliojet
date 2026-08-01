package net.zamasoft.foliojet.layout.sizing;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.FlexDirection;
import net.zamasoft.foliojet.layout.box.params.FlexWrap;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * 論理軸写像の全組み合わせテストです(Flex F4a——3 writing-mode×
 * 4 direction×3 wrap。写像はdirection/wrapのみで決まり書字方向に
 * 依存しないこと自体も固定する)。
 */
public class FlexAxesTest extends TestCase {

	public void testMapping() {
		for (final WritingMode flow : new WritingMode[] { WritingMode.TB, WritingMode.RL, WritingMode.LR }) {
			for (final FlexWrap wrap : FlexWrap.values()) {
				assertAxes(flow, FlexDirection.ROW, wrap, true, false);
				assertAxes(flow, FlexDirection.ROW_REVERSE, wrap, true, true);
				assertAxes(flow, FlexDirection.COLUMN, wrap, false, false);
				assertAxes(flow, FlexDirection.COLUMN_REVERSE, wrap, false, true);
			}
		}
	}

	private static void assertAxes(final WritingMode flow, final FlexDirection direction, final FlexWrap wrap,
			final boolean mainIsLine, final boolean mainReversed) {
		final FlexAxes axes = FlexAxes.of(flow, direction, wrap);
		final String at = flow + "/" + direction + "/" + wrap;
		assertEquals(at, mainIsLine, axes.mainIsLine());
		assertEquals(at, !mainIsLine, axes.mainIsPage());
		assertEquals(at, mainReversed, axes.mainReversed());
		assertEquals(at, wrap == FlexWrap.WRAP_REVERSE, axes.crossReversed());
	}
}
