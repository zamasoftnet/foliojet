package net.zamasoft.foliojet.layout.fragment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.impl.RubyBodyBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * {@link ContinuationCapability#classify}の直接単体テストです
 * (2026-07-21新設、M6b Phase B B1)。実文書を経由せず、対象の箱を
 * 直接構築して各分類分岐を検証する——ChatGPT Pro相談で指摘された
 * 「段組だけが発火条件ではない」という訂正の根拠(RL/LR不一致・
 * {@code RubyBodyBox}等のサブタイプ・横縦直交)を、分類ロジック単体で
 * 固定する。
 *
 * <p>
 * {@link ContinuationCapability#UNSUPPORTED_BOX}(表等、{@code
 * FlowBlockBox}でない箱)は、{@code TableBox}の構築コストが高いため
 * ここでは検証しない——{@code !(b instanceof FlowBlockBox)}という
 * 分岐自体は自明であり、実文書レベルの回帰は
 * {@code OpenChainCollectablePrefixTest#testTableLeafNeverTriggersOpenChain}
 * が別の角度(表がリーフの場合はOpenChain自体に到達しない)で間接的に
 * カバーしている。
 * </p>
 */
public class ContinuationCapabilityTest extends TestCase {
	/**
	 * {@code AbstractBlockBox}のコンストラクタは{@code assert params
	 * .fontStyle != null}を要求する(通常はCSSスタイル解決の産物)。
	 * このテストは分類ロジックのみを見るため、値そのものはどうでもよく
	 * non-nullでさえあればよい。
	 */
	private static final FontStyle DUMMY_FONT_STYLE = new FontStyle() {
		public Direction getDirection() {
			return Direction.LTR;
		}

		public Weight getWeight() {
			return Weight.W_400;
		}

		public Style getStyle() {
			return Style.NORMAL;
		}

		public net.zamasoft.pdfg2d.gc.font.FontFamilyList getFamily() {
			return null;
		}

		public double getSize() {
			return 10;
		}

		public net.zamasoft.pdfg2d.gc.font.FontPolicyList getPolicy() {
			return null;
		}
	};

	private static BlockParams blockParams(final WritingMode flow) {
		final BlockParams params = new BlockParams();
		params.flow = flow;
		params.fontStyle = DUMMY_FONT_STYLE;
		return params;
	}

	private static FlowBlockBox plainFlowBlockBox(final WritingMode flow) {
		return new FlowBlockBox(blockParams(flow), new FlowPos());
	}

	private static MulticolumnBlockBox multicolumnBlockBox(final WritingMode flow) {
		return new MulticolumnBlockBox(blockParams(flow), new FlowPos());
	}

	private static RubyBodyBox rubyBodyBox(final WritingMode flow) {
		return new RubyBodyBox(blockParams(flow), new FlowPos());
	}

	public void testPlainFlowBlockBoxMatchingRootIsCollectable() {
		final AbstractContainerBox b = plainFlowBlockBox(WritingMode.TB);
		final ContinuationCapability c = ContinuationCapability.classify(b, WritingMode.TB);
		assertEquals(ContinuationCapability.PLAIN_FLOW, c);
		assertTrue(c.isCollectable());
	}

	public void testMulticolumnBlockBoxIsMulticol() {
		final AbstractContainerBox b = multicolumnBlockBox(WritingMode.TB);
		final ContinuationCapability c = ContinuationCapability.classify(b, WritingMode.TB);
		assertEquals(ContinuationCapability.MULTICOL, c);
		assertFalse(c.isCollectable());
	}

	/**
	 * {@code RubyBodyBox}は{@code FlowBlockBox}のサブタイプであり、
	 * {@code getClass() == FlowBlockBox.class}という完全一致判定で
	 * 機械的に{@code FLOW_SUBTYPE}へ分類される(段組と同型の除外理由)。
	 * 2026-07-20セッションでは「段組だけが発火条件」と誤って結論して
	 * おり、このケースを見落としていた(ChatGPT Pro相談で指摘)。
	 */
	public void testRubyBodyBoxIsFlowSubtype() {
		final AbstractContainerBox b = rubyBodyBox(WritingMode.TB);
		final ContinuationCapability c = ContinuationCapability.classify(b, WritingMode.TB);
		assertEquals(ContinuationCapability.FLOW_SUBTYPE, c);
		assertFalse(c.isCollectable());
	}

	/**
	 * ルートと同じ軸(縦書き)だが方向が異なる({@code vertical-rl}祖先の
	 * 途中に{@code vertical-lr})場合、{@code SAME_AXIS_DIRECTION_CHANGE}
	 * へ分類される——実際の内部切断可否・自動改ページ障壁はどちらも
	 * {@code isVertical()}の一致しか見ないため、切断自体はできるのに
	 * 事前検分だけが不必要に厳しいケース。
	 */
	public void testVerticalRlToVerticalLrIsSameAxisDirectionChange() {
		final AbstractContainerBox b = plainFlowBlockBox(WritingMode.LR);
		final ContinuationCapability c = ContinuationCapability.classify(b, WritingMode.RL);
		assertEquals(ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE, c);
		assertFalse(c.isCollectable());
	}

	/** ルートと軸そのものが異なる(横書き⇄縦書き)場合はORTHOGONAL_FLOW。 */
	public void testHorizontalToVerticalIsOrthogonalFlow() {
		final AbstractContainerBox b = plainFlowBlockBox(WritingMode.RL);
		final ContinuationCapability c = ContinuationCapability.classify(b, WritingMode.TB);
		assertEquals(ContinuationCapability.ORTHOGONAL_FLOW, c);
		assertFalse(c.isCollectable());
	}

	/** ルート自身と同じ縦書き方向(RL→RL)は一致として収集可能。 */
	public void testMatchingVerticalDirectionIsCollectable() {
		final AbstractContainerBox b = plainFlowBlockBox(WritingMode.RL);
		final ContinuationCapability c = ContinuationCapability.classify(b, WritingMode.RL);
		assertEquals(ContinuationCapability.PLAIN_FLOW, c);
		assertTrue(c.isCollectable());
	}

	/**
	 * B3a(2026-07-21): {@code MULTICOL}はPAGE自動改ページ
	 * ({@code AutoBreakMode})では収集可能(split-through)だが、
	 * 強制改ページ({@code ForceBreakMode})では収集不能のまま——
	 * {@code FlowContainer.splitPageAxis}の強制改ページ分岐は選択された
	 * チェーンメンバーの{@code KEEP}/{@code MOVE}を無条件に
	 * {@code AssertionError("force break failed")}へ落とすため、安全と
	 * 確認できるまで見送る(ChatGPT Pro相談で指摘・検証済み)。
	 */
	public void testMulticolSupportsPageSplitThroughOnlyForAutoBreak() {
		final ContinuationCapability multicol = ContinuationCapability.MULTICOL;
		assertTrue("自動改ページではMULTICOLを収集可能にするはずです",
				multicol.supportsPageSplitThrough(
						new net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode(plainFlowBlockBox(WritingMode.TB))));
		assertFalse("強制改ページではMULTICOLを収集不能のままにするはずです(force break failed回避)",
				multicol.supportsPageSplitThrough(new net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode(
						plainFlowBlockBox(WritingMode.TB), net.zamasoft.foliojet.layout.box.params.PageBreakMode.PAGE)));
	}

	/** {@code PLAIN_FLOW}はmodeによらず常に収集可能。 */
	public void testPlainFlowSupportsPageSplitThroughRegardlessOfMode() {
		final ContinuationCapability plain = ContinuationCapability.PLAIN_FLOW;
		assertTrue(plain.supportsPageSplitThrough(
				new net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode(plainFlowBlockBox(WritingMode.TB))));
		assertTrue(plain.supportsPageSplitThrough(new net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode(
				plainFlowBlockBox(WritingMode.TB), net.zamasoft.foliojet.layout.box.params.PageBreakMode.PAGE)));
	}

	/** それ以外の分類はmodeによらず常に収集不能。 */
	public void testOtherCapabilitiesNeverSupportPageSplitThrough() {
		final net.zamasoft.foliojet.layout.box.content.BreakMode auto = new net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode(
				plainFlowBlockBox(WritingMode.TB));
		assertFalse(ContinuationCapability.FLOW_SUBTYPE.supportsPageSplitThrough(auto));
		assertFalse(ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE.supportsPageSplitThrough(auto));
		assertFalse(ContinuationCapability.ORTHOGONAL_FLOW.supportsPageSplitThrough(auto));
		assertFalse(ContinuationCapability.UNSUPPORTED_BOX.supportsPageSplitThrough(auto));
	}
}
