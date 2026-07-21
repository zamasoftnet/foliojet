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

	/**
	 * {@code ORTHOGONAL_FLOW}/{@code UNSUPPORTED_BOX}はmodeによらず常に
	 * 収集不能(2026-07-21のB5でFLOW_SUBTYPE/SAME_AXIS_DIRECTION_CHANGEは
	 * 自動改ページに限り収集可能になったため、このテストの対象からは
	 * 除外した——下記{@code testFlowSubtypeAndSameAxisSupportPageSplitThroughOnlyForAutoBreak}参照)。
	 */
	public void testOrthogonalAndUnsupportedNeverSupportPageSplitThrough() {
		final net.zamasoft.foliojet.layout.box.content.BreakMode auto = new net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode(
				plainFlowBlockBox(WritingMode.TB));
		assertFalse(ContinuationCapability.ORTHOGONAL_FLOW.supportsPageSplitThrough(auto));
		assertFalse(ContinuationCapability.UNSUPPORTED_BOX.supportsPageSplitThrough(auto));
	}

	/**
	 * B5(2026-07-21): {@code FLOW_SUBTYPE}(唯一の実装である
	 * {@code RubyBodyBox})・{@code SAME_AXIS_DIRECTION_CHANGE}(RL/LR
	 * 混在)は、{@code MULTICOL}と同じ規則(自動改ページのみ収集可能、
	 * 強制改ページは見送り)で解禁する。codex/grok/agyへの設計相談で
	 * 「crossExtent/FragmentStateはisVertical()のみに依存し、サブタイプ・
	 * RL/LRの向きを見ない」ことを確認した上での解禁。
	 */
	public void testFlowSubtypeAndSameAxisSupportPageSplitThroughOnlyForAutoBreak() {
		final net.zamasoft.foliojet.layout.box.content.BreakMode auto = new net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode(
				plainFlowBlockBox(WritingMode.TB));
		final net.zamasoft.foliojet.layout.box.content.BreakMode force = new net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode(
				plainFlowBlockBox(WritingMode.TB), net.zamasoft.foliojet.layout.box.params.PageBreakMode.PAGE);
		assertTrue("自動改ページではFLOW_SUBTYPEを収集可能にするはずです",
				ContinuationCapability.FLOW_SUBTYPE.supportsPageSplitThrough(auto));
		assertFalse("強制改ページではFLOW_SUBTYPEを収集不能のままにするはずです",
				ContinuationCapability.FLOW_SUBTYPE.supportsPageSplitThrough(force));
		assertTrue("自動改ページではSAME_AXIS_DIRECTION_CHANGEを収集可能にするはずです",
				ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE.supportsPageSplitThrough(auto));
		assertFalse("強制改ページではSAME_AXIS_DIRECTION_CHANGEを収集不能のままにするはずです",
				ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE.supportsPageSplitThrough(force));
	}

	/**
	 * B5(2026-07-21): {@code classify()}は軸判定をサブタイプ判定より先に
	 * 行うため、直交writing-modeの{@code MulticolumnBlockBox}/
	 * {@code RubyBodyBox}は{@code MULTICOL}/{@code FLOW_SUBTYPE}ではなく
	 * {@code ORTHOGONAL_FLOW}に分類される(codexへの設計相談で発見した、
	 * 旧実装の誤分類を修正)。
	 */
	public void testOrthogonalMulticolAndRubyClassifyAsOrthogonalFlowNotSubtype() {
		final AbstractContainerBox orthogonalMulticol = multicolumnBlockBox(WritingMode.RL);
		assertEquals(ContinuationCapability.ORTHOGONAL_FLOW,
				ContinuationCapability.classify(orthogonalMulticol, WritingMode.TB));

		final AbstractContainerBox orthogonalRuby = rubyBodyBox(WritingMode.RL);
		assertEquals(ContinuationCapability.ORTHOGONAL_FLOW,
				ContinuationCapability.classify(orthogonalRuby, WritingMode.TB));

		final AbstractContainerBox sameAxisMulticol = multicolumnBlockBox(WritingMode.LR);
		assertEquals(ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE,
				ContinuationCapability.classify(sameAxisMulticol, WritingMode.RL));

		final AbstractContainerBox sameAxisRuby = rubyBodyBox(WritingMode.LR);
		assertEquals(ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE,
				ContinuationCapability.classify(sameAxisRuby, WritingMode.RL));
	}

	/**
	 * M6b Phase B4/B5残作業(task #73): 選択されたCOLUMN owner(段組)の
	 * 内側にさらに別の段組が現れる場合、そのdescendant側が
	 * {@link OpenPathScan#captureColumn}で{@code MULTICOL}barrierとして
	 * 検出され、収集対象(approvedBoxes)から除外されることを直接検証する。
	 *
	 * <p>
	 * 当初はHTMLフィクスチャ経由(実文書レンダリング)で「外側ownerの
	 * 内側にdescendant multicolがbarrierとして残る」ことを確認する
	 * 設計だったが、{@code AbstractContainerBox#canColumnBreak()}の
	 * 実際のセマンティクス({@code isSpecifiedPageSize()}が真なら
	 * {@code columnCount>=2}であるだけで無条件に{@code true}を返す)と
	 * {@code findColumnBreak()}の内側優先探索の組み合わせにより、
	 * 高さ指定つきの入れ子段組では常に最内側がownerとして選ばれてしまい、
	 * 「外側がowner・内側descendantがbarrierとして残る」構成をHTML上で
	 * 自然に発生させることができないと判明した(実測でも
	 * {@code testDescendantMulticolInsideColumnOwnerStaysBarrier}という
	 * 旧テストが実際に失敗することを確認済み)。このため
	 * {@link OpenPathScan#captureColumn}を箱を直接構築して単体で呼ぶ形へ
	 * 置き換えた——CSS上の到達可能性ではなく、分類ロジック自体が
	 * 正しくbarrierを検出することを検証する。
	 * </p>
	 */
	public void testCaptureColumnTreatsDescendantMulticolAsBarrier() {
		final AbstractContainerBox owner = multicolumnBlockBox(WritingMode.TB);
		final AbstractContainerBox descendant = multicolumnBlockBox(WritingMode.TB);
		final OpenPathScan scan = OpenPathScan.captureColumn(java.util.List.of(owner, descendant),
				new net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode(descendant));

		assertTrue("descendantのMULTICOLはowner内側で収集不能(barrier)のはずです",
				scan.snapshot().firstBarrier().isPresent());
		final OpenPathSnapshot.CapabilityBarrier barrier = scan.snapshot().firstBarrier().get();
		assertEquals(1, barrier.openPathIndex());
		assertEquals(ContinuationCapability.MULTICOL, barrier.reason());
		assertTrue("barrier以降は収集(approvedBoxes)されないはずです", scan.approvedBoxes().isEmpty());
	}
}
