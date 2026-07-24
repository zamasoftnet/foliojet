package net.zamasoft.foliojet.layout.box.content;

import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/**
 * {@code Floatings.splitPageAxis}の分岐表テストです(2026-07-24新設、
 * 排除域P2のP2-1。正本:
 * {@code docs/history/2026-07-24-p2-splitfloatings-branch-table.md}の
 * 「P2-1テスト行列(最低限)」)。
 *
 * <p>
 * 合成のFloatings/Floating(scripted splitのスタブボックス)を組み立てて
 * {@code splitPageAxis}を直接呼び、結果の分類(KeepAll/MoveAll/Partition
 * ——P2-1時点の旧sentinel null/this/新と1:1対応)・各floatの行き先・
 * SPLITのremainder座標(0,0)とserial引き継ぎ・caseフォールスルー経路
 * (分岐表4→5)を固定する。<b>既存挙動の固定</b>であり、P2-3のplan駆動
 * commit化・P2-5の型付き一本化を通じて全シナリオの期待値は不変。
 * </p>
 */
public class FloatingsSplitPageAxisTest extends TestCase {
	private static final byte NO_FLAGS = 0;
	private static final byte FIRST = IPageBreakableBox.FLAGS_FIRST;

	@Override
	protected void setUp() {
		ContinuationStats.reset();
	}

	// ------------------------------------------------------------------
	// 補助: 結果の分類(旧sentinel null/this/新との対応を固定)
	// ------------------------------------------------------------------

	/** 旧null: 全て前のフラグメントに残る。 */
	private static void assertKeepAll(final FloatSplitResult result) {
		assertTrue("KeepAllのはず: " + result, result instanceof FloatSplitResult.KeepAll);
	}

	/** 旧this: 全て次のフラグメントへ(遅延表現——元リストは無傷)。 */
	private static void assertMoveAll(final FloatSplitResult result) {
		assertTrue("MoveAllのはず: " + result, result instanceof FloatSplitResult.MoveAll);
	}

	/** 旧新Floatings: 部分移動。remainder台帳を返す。 */
	private static Floatings partitionRemainder(final FloatSplitResult result) {
		assertTrue("Partitionのはず: " + result, result instanceof FloatSplitResult.Partition);
		return ((FloatSplitResult.Partition) result).remainder();
	}

	// ------------------------------------------------------------------
	// 補助: 合成ボックス
	// ------------------------------------------------------------------

	private static BlockParams blockParams(final WritingMode flow, final PageBreakMode pageBreakInside) {
		final BlockParams params = new BlockParams();
		params.flow = flow;
		params.pageBreakInside = pageBreakInside;
		params.fontStyle = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		return params;
	}

	/** splitPageAxisの第1引数(owner)。書字方向だけが参照される。 */
	private static AbstractContainerBox owner(final WritingMode flow) {
		return new FloatBlockBox(blockParams(flow, PageBreakMode.AUTO), new FloatPos());
	}

	/**
	 * ページ方向寸法と切断結果をスクリプトできるBLOCK浮動ボックスです。
	 * シナリオの記述は従来どおり{@code SplitResult}型で行い、A-3a-2以降の
	 * 実切断経路({@code splitFloatFragment})へ翻訳する——Splitは「canned
	 * remainderをそのまま返すrecipe」を持つPreparedFloatFragmentになる
	 * (materializeの一回性・serial引き継ぎは本物の機構を通る)。
	 * scripted==nullのシナリオで切断が呼ばれたら失敗する。
	 */
	private static class StubBlockFloat extends FloatBlockBox {
		private final double pageExtent;
		private final SplitResult scripted;
		int splitCalls;
		double seenPageLimit = Double.NaN;
		byte seenFlags = -1;
		BreakMode seenMode;

		StubBlockFloat(final BlockParams params, final double pageExtent, final SplitResult scripted) {
			super(params, new FloatPos());
			this.pageExtent = pageExtent;
			this.scripted = scripted;
		}

		@Override
		public net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit splitFloatFragment(final int serial,
				final double pageLimit, final BreakMode mode, final byte flags) {
			++this.splitCalls;
			this.seenPageLimit = pageLimit;
			this.seenMode = mode;
			this.seenFlags = flags;
			if (this.scripted == null) {
				throw new AssertionError("切断はこのシナリオでは呼ばれないはず");
			}
			return switch (this.scripted) {
			case SplitResult.Keep keep -> net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.KEEP;
			case SplitResult.Move move -> net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.MOVE;
			case SplitResult.Split(final IPageBreakableBox remainder) ->
				new net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.Prepared(
						new net.zamasoft.foliojet.layout.fragment.PreparedFloatFragment(serial,
								(state, container) -> (net.zamasoft.foliojet.layout.box.AbstractBlockBox) remainder,
								null, null, 0));
			case SplitResult.Frame frame -> throw new AssertionError("Frameはfloatではスクリプトしない");
			};
		}

		@Override
		public double getPageExtent(final WritingMode flow) {
			return this.pageExtent;
		}
	}

	/** REPLACED(atomic)浮動ボックス。splitPageAxisはREPLACEDをcastしない。 */
	private static final class StubReplacedFloat extends StubBlockFloat {
		StubReplacedFloat(final BlockParams params, final double pageExtent) {
			super(params, pageExtent, null);
		}

		@Override
		public BoxType getType() {
			return BoxType.REPLACED;
		}
	}

	private static StubBlockFloat block(final double pageExtent) {
		return new StubBlockFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO), pageExtent, null);
	}

	private static StubBlockFloat blockSplitting(final double pageExtent, final SplitResult scripted) {
		return new StubBlockFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO), pageExtent, scripted);
	}

	private static Floatings.Floating floating(final int serial, final StubBlockFloat box, final double pageAxis) {
		return new Floatings.Floating(serial, box, 5, pageAxis);
	}

	private static Floatings floatingsOf(final Floatings.Floating... floats) {
		final Floatings floatings = new Floatings();
		for (final Floatings.Floating f : floats) {
			floatings.addFloating(f);
		}
		return floatings;
	}

	// ------------------------------------------------------------------
	// 結果分類(旧nextFloatings状態機械のsentinel対応)の固定
	// ------------------------------------------------------------------

	/** 行列: 全KEEP→KeepAll(旧null)。 */
	public void testAllKeepReturnsKeepAll() {
		final Floatings.Floating f0 = floating(1, block(10), 0);
		final Floatings.Floating f1 = floating(2, block(10), 20);
		final Floatings floatings = floatingsOf(f0, f1);
		assertKeepAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		// 元のリストは無傷
		assertEquals(2, floatings.getCount());
		assertSame(f0, floatings.getFloating(0));
		assertSame(f1, floatings.getFloating(1));
	}

	/** 行列: 全MOVE→MoveAll(旧this。遅延表現——元リストから動かさない)。 */
	public void testAllMoveReturnsMoveAll() {
		final Floatings.Floating f0 = floating(1, block(10), 150);
		final Floatings.Floating f1 = floating(2, block(10), 160);
		final Floatings floatings = floatingsOf(f0, f1);
		assertMoveAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		// MoveAllは遅延表現——floatは元リストに残っている
		assertEquals(2, floatings.getCount());
		assertSame(f0, floatings.getFloating(0));
		assertSame(f1, floatings.getFloating(1));
	}

	/** 行列: KEEP後MOVE(先頭KEEP後のMOVEでPartition)。 */
	public void testKeepThenMovePartition() {
		final Floatings.Floating keep = floating(1, block(50), 0);
		final Floatings.Floating move = floating(2, block(10), 150);
		final Floatings floatings = floatingsOf(keep, move);
		final Floatings next = partitionRemainder(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertNotSame(floatings, next);
		assertEquals(1, floatings.getCount());
		assertSame(keep, floatings.getFloating(0));
		assertEquals(1, next.getCount());
		assertSame(move, next.getFloating(0));
	}

	/** 行列: MOVE prefix後KEEP(先行MOVE群の移送——移送ロジックの本丸)。 */
	public void testMovePrefixThenKeepTransfersPrefix() {
		final Floatings.Floating move0 = floating(1, block(10), 150);
		final Floatings.Floating move1 = floating(2, block(10), 160);
		final Floatings.Floating keep = floating(3, block(50), 0);
		final Floatings floatings = floatingsOf(move0, move1, keep);
		final Floatings next = partitionRemainder(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertNotSame(floatings, next);
		// 先行MOVE群は元順序のままnext側へ、KEEPは元に残る
		assertEquals(2, next.getCount());
		assertSame(move0, next.getFloating(0));
		assertSame(move1, next.getFloating(1));
		assertEquals(1, floatings.getCount());
		assertSame(keep, floatings.getFloating(0));
	}

	/** 行列: SPLIT単独——元はthis側に残り、remainderは座標(0,0)+serial引き継ぎでnext側へ。 */
	public void testSplitSingleKeepsOriginalAndSendsRemainder() {
		final StubBlockFloat remainder = block(60);
		final StubBlockFloat box = blockSplitting(100, new SplitResult.Split(remainder));
		final Floatings.Floating f = floating(7, box, 40);
		final Floatings floatings = floatingsOf(f);
		final Floatings next = partitionRemainder(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertNotSame(floatings, next);
		// 元のFloatingはthis側に残る
		assertEquals(1, floatings.getCount());
		assertSame(f, floatings.getFloating(0));
		assertSame(box, floatings.getFloating(0).box);
		// remainderは座標(0,0)=次フラグメント先頭、serialは引き継ぐ
		assertEquals(1, next.getCount());
		final Floatings.Floating rf = next.getFloating(0);
		assertSame(remainder, rf.box);
		assertEquals(7, rf.serial);
		assertEquals(0.0, rf.lineAxis, 0);
		assertEquals(0.0, rf.pageAxis, 0);
		// split呼び出しはfloat座標系の切断線とFLAGS_SPLIT
		assertEquals(1, box.splitCalls);
		assertEquals(60.0, box.seenPageLimit, 0);
		assertEquals(IPageBreakableBox.FLAGS_SPLIT, box.seenFlags);
		assertSame(BreakMode.DEFAULT_BREAK_MODE, box.seenMode);
	}

	/** 行列: MOVE+SPLIT+KEEP混在(prefix移送とSPLITとKEEPの複合)。 */
	public void testMoveSplitKeepMixed() {
		final Floatings.Floating move = floating(11, block(10), 150);
		final StubBlockFloat remainder = block(60);
		final StubBlockFloat splitBox = blockSplitting(100, new SplitResult.Split(remainder));
		final Floatings.Floating split = floating(22, splitBox, 40);
		final Floatings.Floating keep = floating(33, block(50), 0);
		final Floatings floatings = floatingsOf(move, split, keep);
		final Floatings next = partitionRemainder(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertNotSame(floatings, next);
		// next側: [MOVEされた元Floating, SPLITのremainder] の元順序
		assertEquals(2, next.getCount());
		assertSame(move, next.getFloating(0));
		assertSame(remainder, next.getFloating(1).box);
		assertEquals(22, next.getFloating(1).serial);
		// this側: [SPLIT元(そのまま残る), KEEP] の元順序
		assertEquals(2, floatings.getCount());
		assertSame(split, floatings.getFloating(0));
		assertSame(keep, floatings.getFloating(1));
	}

	// ------------------------------------------------------------------
	// 分類側: first×avoid×書字軸×BLOCK/REPLACED、境界、split結果
	// ------------------------------------------------------------------

	/** 分岐表3: 非first・avoidなし・書字軸一致のBLOCKはFLAGS_SPLITで切断。 */
	public void testNonFirstBlockSplitUsesSplitFlag() {
		final StubBlockFloat box = blockSplitting(100, new SplitResult.Split(block(60)));
		final Floatings floatings = floatingsOf(floating(1, box, 40));
		floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS);
		assertEquals(1, box.splitCalls);
		assertEquals(IPageBreakableBox.FLAGS_SPLIT, box.seenFlags);
		assertEquals(60.0, box.seenPageLimit, 0);
	}

	/** 分岐表3: first(FLAGS_FIRSTかつ物理的にフラグメント先頭)のBLOCKはFLAGS_FIRSTで切断。 */
	public void testFirstBlockSplitUsesFirstFlag() {
		final StubBlockFloat box = blockSplitting(200, new SplitResult.Split(block(100)));
		final Floatings floatings = floatingsOf(floating(1, box, 0));
		final Floatings next = partitionRemainder(floatings.splitPageAxis(owner(WritingMode.TB), 100, FIRST));
		assertEquals(1, box.splitCalls);
		assertEquals(IPageBreakableBox.FLAGS_FIRST, box.seenFlags);
		assertEquals(100.0, box.seenPageLimit, 0);
		assertNotSame(floatings, next);
	}

	/** 分岐表4→5フォールスルー: avoidかつ非firstのBLOCKはREPLACED扱いで丸ごとMOVE(splitは呼ばれない)。 */
	public void testAvoidNonFirstFallsThroughToMove() {
		final StubBlockFloat box = new StubBlockFloat(blockParams(WritingMode.TB, PageBreakMode.AVOID), 100, null);
		final Floatings floatings = floatingsOf(floating(1, box, 40));
		assertMoveAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(0, box.splitCalls);
	}

	/** 分岐表3: avoidでも物理firstならavoidヒントを上書きして実際に切断する(2026-07-23規則)。 */
	public void testAvoidFirstOverridesAvoidAndSplits() {
		final StubBlockFloat box = new StubBlockFloat(blockParams(WritingMode.TB, PageBreakMode.AVOID), 200,
				new SplitResult.Split(block(100)));
		final Floatings floatings = floatingsOf(floating(1, box, 0));
		floatings.splitPageAxis(owner(WritingMode.TB), 100, FIRST);
		assertEquals(1, box.splitCalls);
		assertEquals(IPageBreakableBox.FLAGS_FIRST, box.seenFlags);
	}

	/** 分岐表4→5フォールスルー: 書字軸不一致のBLOCK(非first)はatomic扱いで丸ごとMOVE。 */
	public void testAxisMismatchNonFirstFallsThroughToMove() {
		final StubBlockFloat box = new StubBlockFloat(blockParams(WritingMode.RL, PageBreakMode.AUTO), 100, null);
		final Floatings floatings = floatingsOf(floating(1, box, 40));
		assertMoveAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(0, box.splitCalls);
	}

	/** 分岐表4→5フォールスルー: 書字軸不一致のBLOCK(first)ははみ出し許容でKEEP。 */
	public void testAxisMismatchFirstKeepsOverflowing() {
		final StubBlockFloat box = new StubBlockFloat(blockParams(WritingMode.RL, PageBreakMode.AUTO), 200, null);
		final Floatings floatings = floatingsOf(floating(1, box, 0));
		assertKeepAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, FIRST));
		assertEquals(0, box.splitCalls);
	}

	/** 分岐表5: REPLACED(非first)は丸ごとMOVE。 */
	public void testReplacedNonFirstMoves() {
		final StubReplacedFloat box = new StubReplacedFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO), 100);
		final Floatings floatings = floatingsOf(floating(1, box, 40));
		assertMoveAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(0, box.splitCalls);
	}

	/** 分岐表5: REPLACED(first)ははみ出し許容でKEEP。 */
	public void testReplacedFirstKeepsOverflowing() {
		final StubReplacedFloat box = new StubReplacedFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO), 200);
		final Floatings floatings = floatingsOf(floating(1, box, 0));
		assertKeepAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, FIRST));
		assertEquals(0, box.splitCalls);
	}

	/** 「first」は物理位置——FLAGS_FIRSTでもpageAxisが先頭でなければfirstではない(分岐表2でMOVE)。 */
	public void testFirstFlagRequiresPhysicalHead() {
		final StubBlockFloat box = new StubBlockFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO), 200, null);
		final Floatings floatings = floatingsOf(floating(1, box, 10));
		assertMoveAll(floatings.splitPageAxis(owner(WritingMode.TB), 5, FIRST));
		assertEquals(0, box.splitCalls);
	}

	/** 分岐表1境界: LayoutUtils.compareの許容誤差(0.5)内のはみ出しはKEEP。 */
	public void testBoundaryWithinToleranceKeeps() {
		// ちょうど
		final StubBlockFloat exact = block(100);
		final Floatings f1 = floatingsOf(floating(1, exact, 0));
		assertKeepAll(f1.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(0, exact.splitCalls);
		// 0.4はみ出し(<0.5)も同一視
		final StubBlockFloat nearly = block(100.4);
		final Floatings f2 = floatingsOf(floating(2, nearly, 0));
		assertKeepAll(f2.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(0, nearly.splitCalls);
	}

	/** 分岐表1境界: 0.5以上のはみ出しは切断対象(分岐表3へ)。 */
	public void testBoundaryBeyondToleranceSplits() {
		final StubBlockFloat box = blockSplitting(100.5, SplitResult.KEEP);
		final Floatings floatings = floatingsOf(floating(1, box, 0));
		assertKeepAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(1, box.splitCalls);
	}

	/** 分岐表3: split結果Keep→全体が前フラグメントに残る(KeepAll)。 */
	public void testSplitResultKeepLeavesAllInPlace() {
		final StubBlockFloat box = blockSplitting(100, SplitResult.KEEP);
		final Floatings.Floating f = floating(1, box, 40);
		final Floatings floatings = floatingsOf(f);
		assertKeepAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(1, box.splitCalls);
		assertEquals(1, floatings.getCount());
		assertSame(f, floatings.getFloating(0));
	}

	/** 分岐表3: split結果Move→丸ごとMOVE(MoveAll、元リスト無傷)。 */
	public void testSplitResultMoveMovesWhole() {
		final StubBlockFloat box = blockSplitting(100, SplitResult.MOVE);
		final Floatings.Floating f = floating(1, box, 40);
		final Floatings floatings = floatingsOf(f);
		assertMoveAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(1, box.splitCalls);
		assertEquals(1, floatings.getCount());
		assertSame(f, floatings.getFloating(0));
	}

	// ------------------------------------------------------------------
	// P2-1/P2-2: 純データ(FloatMeasurement)と純判定(FloatSplitPlan)
	// ------------------------------------------------------------------

	/** FloatMeasurementが各floatの判定入力を読み取り専用で固定すること。 */
	public void testMeasureFixesPerFloatInputs() {
		final StubBlockFloat blockBox = new StubBlockFloat(blockParams(WritingMode.RL, PageBreakMode.AVOID), 100, null);
		final StubReplacedFloat replacedBox = new StubReplacedFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO),
				30);
		final Floatings floatings = floatingsOf(floating(5, blockBox, 40), floating(6, replacedBox, 0));
		final List<FloatMeasurement> measurements = floatings.measure(WritingMode.TB);
		assertEquals(2, measurements.size());
		final FloatMeasurement m0 = measurements.get(0);
		assertEquals(0, m0.ordinal());
		assertEquals(5, m0.serial());
		assertSame(blockBox, m0.box());
		assertEquals(40.0, m0.pageStart(), 0);
		assertEquals(100.0, m0.pageExtent(), 0);
		assertEquals(140.0, m0.pageEnd(), 0);
		assertFalse(m0.sameWritingAxis()); // owner TB vs float RL
		assertFalse(m0.fragmentHead());
		assertEquals(BoxType.BLOCK, m0.boxType());
		assertEquals(PageBreakMode.AVOID, m0.pageBreakInside());
		final FloatMeasurement m1 = measurements.get(1);
		assertEquals(1, m1.ordinal());
		assertEquals(6, m1.serial());
		assertSame(replacedBox, m1.box());
		assertTrue(m1.sameWritingAxis()); // REPLACEDは常にtrue(軸判定を通らない)
		assertTrue(m1.fragmentHead());
		assertEquals(BoxType.REPLACED, m1.boxType());
		assertNull(m1.pageBreakInside());
	}

	/** 純判定が分岐表1・2・3(印だけ)・4→5をKEEP/MOVE/SPLIT_ON_COMMITへ写すこと。 */
	public void testPlanDirectClassifiesBranchTableRows() {
		final Floatings.Floating keep = floating(1, block(50), 0);
		final Floatings.Floating move = floating(2, block(10), 150);
		final Floatings.Floating split = floating(3, blockSplitting(100, null), 40);
		final Floatings floatings = floatingsOf(keep, move, split);
		final FloatSplitPlan plan = FloatSplitPlan.planDirect(floatings, WritingMode.TB, 100, NO_FLAGS);
		assertSame(floatings, plan.expectedSource());
		assertEquals(100.0, plan.pageLimit(), 0);
		assertEquals(NO_FLAGS, plan.flags());
		assertTrue(plan.children().isEmpty());
		assertEquals(3, plan.direct().size());
		assertTrue(plan.direct().get(0) instanceof FloatSplitPlan.FloatItemPlan.Keep);
		assertEquals(1, plan.direct().get(0).expected().serial());
		assertTrue(plan.direct().get(1) instanceof FloatSplitPlan.FloatItemPlan.Move);
		assertEquals(2, plan.direct().get(1).expected().serial());
		// 分岐表3は「SPLIT_ON_COMMITとして印だけ」——結果を予言せず、
		// innerLimit(float座標系の切断線)とsplitFlagsのみ保持する
		final FloatSplitPlan.FloatItemPlan.SplitOnCommit sc = (FloatSplitPlan.FloatItemPlan.SplitOnCommit) plan
				.direct().get(2);
		assertEquals(3, sc.expected().serial());
		assertEquals(60.0, sc.innerLimit(), 0);
		assertEquals(IPageBreakableBox.FLAGS_SPLIT, sc.splitFlags());
	}

	/** 純判定のfirst系: avoid上書き・軸不一致・REPLACEDのフォールスルー分類。 */
	public void testClassifyFirstAndFallthroughRows() {
		final double pageLimit = 100;
		// avoid + first → SPLIT_ON_COMMIT(FLAGS_FIRST)——avoidヒント上書き
		final StubBlockFloat avoidBox = new StubBlockFloat(blockParams(WritingMode.TB, PageBreakMode.AVOID), 200, null);
		final FloatMeasurement avoidFirst = FloatMeasurement.of(0, floating(1, avoidBox, 0), WritingMode.TB);
		final FloatSplitPlan.FloatItemPlan.SplitOnCommit sc = (FloatSplitPlan.FloatItemPlan.SplitOnCommit) FloatSplitPlan
				.classify(avoidFirst, pageLimit, FIRST);
		assertEquals(IPageBreakableBox.FLAGS_FIRST, sc.splitFlags());
		assertEquals(100.0, sc.innerLimit(), 0);
		// avoid + 非first → 4→5フォールスルーでMOVE
		final FloatMeasurement avoidCrossing = FloatMeasurement.of(0, floating(2, avoidBox, 40), WritingMode.TB);
		assertTrue(FloatSplitPlan.classify(avoidCrossing, pageLimit,
				NO_FLAGS) instanceof FloatSplitPlan.FloatItemPlan.Move);
		// 軸不一致 + first → 4→5フォールスルーでKEEP
		final StubBlockFloat rlBox = new StubBlockFloat(blockParams(WritingMode.RL, PageBreakMode.AUTO), 200, null);
		final FloatMeasurement axisMismatchFirst = FloatMeasurement.of(0, floating(3, rlBox, 0), WritingMode.TB);
		assertTrue(FloatSplitPlan.classify(axisMismatchFirst, pageLimit,
				FIRST) instanceof FloatSplitPlan.FloatItemPlan.Keep);
		// REPLACED + 非first(境界跨ぎ) → MOVE
		final StubReplacedFloat replacedBox = new StubReplacedFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO),
				100);
		final FloatMeasurement replacedCrossing = FloatMeasurement.of(0, floating(4, replacedBox, 40), WritingMode.TB);
		assertTrue(FloatSplitPlan.classify(replacedCrossing, pageLimit,
				NO_FLAGS) instanceof FloatSplitPlan.FloatItemPlan.Move);
	}

	/** P2-3/P2-5: 型付き結果の3分類と台帳内容の複合固定(混在シナリオ)。 */
	public void testTypedResultMatchesSentinelContract() {
		// KeepAll(旧null)——元リスト無傷
		final Floatings allKeep = floatingsOf(floating(1, block(10), 0));
		assertKeepAll(allKeep.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(1, allKeep.getCount());
		// MoveAll(旧this)——遅延表現、元リスト無傷
		final Floatings allMove = floatingsOf(floating(2, block(10), 150));
		assertMoveAll(allMove.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(1, allMove.getCount());
		// Partition(旧新Floatings)——MOVE+SPLIT残余がremainder、KEEP+SPLIT元がsource
		final Floatings.Floating move = floating(1, block(10), 150);
		final Floatings.Floating split = floating(2, blockSplitting(100, new SplitResult.Split(block(60))), 40);
		final Floatings.Floating keep = floating(3, block(50), 0);
		final Floatings floatings = floatingsOf(move, split, keep);
		final Floatings remainder = partitionRemainder(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(2, remainder.getCount());
		assertSame(move, remainder.getFloating(0));
		assertEquals(2, remainder.getFloating(1).serial);
		assertEquals(2, floatings.getCount());
		assertSame(split, floatings.getFloating(0));
		assertSame(keep, floatings.getFloating(1));
	}
}
