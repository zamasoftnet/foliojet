package net.zamasoft.foliojet.layout.fragment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * {@link ContinuationCapability#classify}の直接単体テストです
 * (2026-07-21新設、M6b Phase B B1)。実文書を経由せず、対象の箱を
 * 直接構築して各分類分岐を検証する——ChatGPT Pro相談で指摘された
 * 「段組だけが発火条件ではない」という訂正の根拠(RL/LR不一致・
 * 横縦直交)を、分類ロジック単体で固定する(かつて検証していた
 * {@code RubyBodyBox}サブタイプ={@code FLOW_SUBTYPE}分類は、ルビの
 * 注釈付きテキスト化——2026-07-25仕様裁定——で対象が消滅し撤去した)。
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
	 * B3a(2026-07-21)で{@code MULTICOL}はPAGE自動改ページのみ収集可能に
	 * なり、強制改ページ({@code ForceBreakMode})は
	 * {@code FlowContainer.splitPageAxis}の強制改ページ分岐が選択された
	 * チェーンメンバーの{@code KEEP}/{@code MOVE}を無条件に
	 * {@code AssertionError("force break failed")}へ落とすため見送って
	 * いた。B3b-2(2026-07-21)でこの生の{@code AssertionError}を正規の
	 * KEEP/MOVE処理へ書き換えたため、B3b-1(2026-07-21)でmodeによらず
	 * 収集可能にした——自動改ページ・強制改ページのどちらでも同じ
	 * {@code splitForContinuation}経路を通る。
	 */
	public void testMulticolSupportsPageSplitThroughRegardlessOfMode() {
		final ContinuationCapability multicol = ContinuationCapability.MULTICOL;
		assertTrue("自動改ページではMULTICOLを収集可能にするはずです",
				multicol.supportsPageSplitThrough(
						new net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode(plainFlowBlockBox(WritingMode.TB))));
		assertTrue("B3b-1(2026-07-21)以降、強制改ページでもMULTICOLを収集可能にするはずです",
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
	 * {@code ORTHOGONAL_FLOW}/{@code UNSUPPORTED_BOX}/
	 * {@code SAME_AXIS_DIRECTION_CHANGE}はmodeによらず常に収集不能
	 * (2026-07-22の改ページ契約でatomic対象と確定、
	 * docs/history/2026-07-22-pagination-contract-consultation.md参照
	 * ——{@code SAME_AXIS_DIRECTION_CHANGE}はB5bで一時収集可能にして
	 * いたが撤回した)。{@code MULTICOL}はmode非依存で収集可能なまま。
	 */
	public void testOrthogonalAndUnsupportedNeverSupportPageSplitThrough() {
		final net.zamasoft.foliojet.layout.box.content.BreakMode auto = new net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode(
				plainFlowBlockBox(WritingMode.TB));
		final net.zamasoft.foliojet.layout.box.content.BreakMode force = new net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode(
				plainFlowBlockBox(WritingMode.TB), net.zamasoft.foliojet.layout.box.params.PageBreakMode.PAGE);
		assertFalse(ContinuationCapability.ORTHOGONAL_FLOW.supportsPageSplitThrough(auto));
		assertFalse(ContinuationCapability.UNSUPPORTED_BOX.supportsPageSplitThrough(auto));
		assertFalse("改ページ契約(2026-07-22)によりSAME_AXIS_DIRECTION_CHANGEはatomic対象です",
				ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE.supportsPageSplitThrough(auto));
		assertFalse("強制改ページでも同様にatomic対象です",
				ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE.supportsPageSplitThrough(force));
	}

	/**
	 * B5(2026-07-21): {@code classify()}は軸判定をサブタイプ判定より先に
	 * 行うため、直交writing-modeの{@code MulticolumnBlockBox}は
	 * {@code MULTICOL}ではなく{@code ORTHOGONAL_FLOW}に分類される
	 * (codexへの設計相談で発見した、旧実装の誤分類を修正)。
	 */
	public void testOrthogonalMulticolClassifiesAsOrthogonalFlowNotSubtype() {
		final AbstractContainerBox orthogonalMulticol = multicolumnBlockBox(WritingMode.RL);
		assertEquals(ContinuationCapability.ORTHOGONAL_FLOW,
				ContinuationCapability.classify(orthogonalMulticol, WritingMode.TB));

		final AbstractContainerBox sameAxisMulticol = multicolumnBlockBox(WritingMode.LR);
		assertEquals(ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE,
				ContinuationCapability.classify(sameAxisMulticol, WritingMode.RL));
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
