package net.zamasoft.foliojet.layout.box.content;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;

import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * 通常のフロー以外のボックスを一括管理します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Floatings.java 1554 2018-04-26 03:34:02Z miyabe $
 */
public class Floatings {
	/**
	 * 配置された浮動ボックスです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: Floatings.java 1554 2018-04-26 03:34:02Z miyabe $
	 */
	public static class Floating extends BoxHolder {
		public final IFloatBox box;
		public final double lineAxis, pageAxis;

		public Floating(int serial, IFloatBox box, double lineAxis, double pageAxis) {
			super(serial);
			this.box = box;
			this.lineAxis = lineAxis;
			this.pageAxis = pageAxis;
		}

		public IBox getBox() {
			return this.box;
		}

		public void restyle(BlockBuilder builder) {
			switch (this.box.getType()) {
			case BLOCK: {
				// ブロックボックス
				// 匿名ボックス
				AbstractContainerBox floatBox = (AbstractContainerBox) this.box;
				BlockBuilder floatBindBuilder = new BlockBuilder(builder, floatBox);
				floatBox.restyle(floatBindBuilder, net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED);
				floatBindBuilder.close();
				builder.addBound(floatBox);
			}
				break;
			case REPLACED: {
				// 置換されたボックス
				AbstractReplacedBox floatBox = (AbstractReplacedBox) this.box;
				builder.addBound(floatBox);
			}
				break;
			default:
				throw new IllegalStateException(this.box.toString());
			}
		}
	}

	/**
	 * 浮動ボックス。
	 */
	private final List<Floating> floatings = new ArrayList<Floating>();

	/**
	 * 浮動ボックスを追加します。
	 * 
	 * @param floating
	 */
	public void addFloating(Floating floating) {
		assert !LayoutUtils.isNone(floating.pageAxis) : "Undefined pageAxis";
		assert !LayoutUtils.isNone(floating.lineAxis) : "Undefined lineAxis";
		this.floatings.add(floating);
	}

	/**
	 * drawの反復化(2026-07-20、IBox.drawと同じ理由)。各浮動ボックスの
	 * 描画手順を、元の走査順のまま**逆順**で{@code worklist}へ積みます。
	 */
	public void pushDraw(AbstractContainerBox box, PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		assert !LayoutUtils.isNone(x) : "Undefined x";
		assert !LayoutUtils.isNone(y) : "Undefined y";
		// 浮動体
		boolean vertical = box.getBlockParams().flow.isVertical();
		if (vertical) {
			x += box.getInnerWidth();
		}
		for (int i = this.floatings.size() - 1; i >= 0; --i) {
			Floating floating = (Floating) this.floatings.get(i);
			double xx;
			double yy;
			if (vertical) {
				// 縦書き
				xx = x - floating.pageAxis - floating.box.getWidth();
				yy = y + floating.lineAxis;
			} else {
				// 横書き
				xx = x + floating.lineAxis;
				yy = y + floating.pageAxis;
			}
			worklist.push(IBox.drawStep(floating.box, pageBox, drawer, visitor, clip, transform, contextX, contextY,
					xx, yy));
		}
	}

	public int getCount() {
		return this.floatings.size();
	}

	public Floating getFloating(int i) {
		return (Floating) this.floatings.get(i);
	}

	/**
	 * 全floatの実測値を元順序で採取します(2026-07-24、P2-1。読み取り専用
	 * ——このリストにもボックスにも一切影響しない)。ordinalは採取時点の
	 * 安定序数(=このリストのindex)である。
	 *
	 * @param ownerFlow ownerの書字方向
	 * @return 実測値のリスト(変更不可)
	 */
	public List<FloatMeasurement> measure(final net.zamasoft.foliojet.layout.box.params.WritingMode ownerFlow) {
		final List<FloatMeasurement> measurements = new ArrayList<>(this.floatings.size());
		for (int i = 0; i < this.floatings.size(); ++i) {
			measurements.add(FloatMeasurement.of(i, (Floating) this.floatings.get(i), ownerFlow));
		}
		return java.util.Collections.unmodifiableList(measurements);
	}

	/**
	 * 浮動ボックスをページ分割します(2026-07-24、P2-3でplan駆動commitへ
	 * 切替、P2-5で旧sentinel契約(null=KeepAll / this=MoveAll / 新=
	 * Partition)のadapterを撤去して型付き結果へ一本化。分岐表の正本:
	 * {@code docs/history/2026-07-24-p2-splitfloatings-branch-table.md})。
	 *
	 * <p>
	 * 実装は「{@link FloatSplitPlan#planDirect}で分類(純判定・副作用なし)
	 * →plan駆動のcommit(codex設計§2.3)」の2段。commitはordinal順に一度
	 * だけ走り、{@code SplitOnCommit}はここで一度だけ
	 * {@code containerBox.splitFloatFragment}を実行してKeep/Move/Preparedへ
	 * 確定する(A-3a-2: 残余boxは即時構築せず、受け側Floatingへの接続時に
	 * 一度だけmaterialize)。旧実装との等価性は
	 * {@code FloatingsSplitPageAxisTest}の分岐表テストとP2-2 shadow比較
	 * (SMOKEコーパス不一致0)、および{@code PreparedFloatFragmentTest}の
	 * twin等価テストで固定済み。
	 * </p>
	 *
	 * <p>
	 * 結果の意味({@link FloatSplitResult}参照):
	 * KeepAll/MoveAllでは元リストは無傷(MoveAllの台帳付け替えはownerが
	 * 行う遅延表現)。Partitionでのみ元リストを「KEEP+SPLIT元」へ組み替え、
	 * remainder台帳(MOVEの元Floating+SPLIT残余、元順序)を返す。
	 * </p>
	 */
	public FloatSplitResult splitPageAxis(final AbstractContainerBox box, final double pageLimit,
			final byte flags) {
		assert !this.floatings.isEmpty();
		// 入口final snapshot(addBound事故の教訓——codex設計§2.5)。
		// 分類はここで全floatについて確定する。旧実装はfloat iのsplit実行後に
		// float i+1を分類していたが、各floatのboxは独立でsplitは他floatの
		// 実測に影響しないため等価(P2-2 shadowで確認済み)。
		final int originalFloatCount = this.floatings.size();
		final FloatSplitPlan plan = FloatSplitPlan.planDirect(this, box.getBlockParams().flow, pageLimit, flags);
		assert plan.direct().size() == originalFloatCount;
		// commit(codex設計§2.3): ordinal順に一度だけ。source側(KEEP+
		// SPLIT元)とremainder側(MOVE+SPLIT残余)のリストを構築する。
		// ordinalは安定序数——旧実装のようなremove/--iによるindex変異はない。
		final List<Floating> sourceSide = new ArrayList<Floating>(originalFloatCount);
		final List<Floating> remainderSide = new ArrayList<Floating>();
		boolean allWholeMoves = true;
		for (int ordinal = 0; ordinal < originalFloatCount; ++ordinal) {
			final Floating floating = (Floating) this.floatings.get(ordinal);
			final FloatSplitPlan.FloatItemPlan item = plan.direct().get(ordinal);
			assert item.expected().box() == floating.box : "plan/commitのidentity不一致 ordinal=" + ordinal;
			switch (item) {
			case FloatSplitPlan.FloatItemPlan.Keep keep -> {
				// 分岐表1、および4→5フォールスルーのfirst: 元に残す
				sourceSide.add(floating);
				allWholeMoves = false;
			}
			case FloatSplitPlan.FloatItemPlan.Move move ->
				// 分岐表2、および4→5フォールスルーの非first: 丸ごと送る
				remainderSide.add(floating);
			case FloatSplitPlan.FloatItemPlan.SplitOnCommit(final FloatMeasurement expected, final double innerLimit,
					final byte splitFlags) -> {
				// 分岐表3: ここで一度だけ切断し、Keep/Move/Preparedへ確定。
				// A-3a-2: 残余boxは切断内部で即時構築せず、材料
				// (PreparedFloatFragment)で受け取り、受け側Floatingへ接続
				// するこの場で一度だけmaterializeする(構築は旧即時経路と
				// 同一のcontinueFragmentによる)
				final net.zamasoft.foliojet.layout.box.AbstractBlockBox containerBox = (net.zamasoft.foliojet.layout.box.AbstractBlockBox) floating.box;
				switch (containerBox.splitFloatFragment(floating.serial, innerLimit, BreakMode.DEFAULT_BREAK_MODE,
						splitFlags)) {
				case net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.Keep keep -> {
					sourceSide.add(floating);
					allWholeMoves = false;
				}
				case net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.Move move ->
					remainderSide.add(floating);
				case net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.Prepared(
						final net.zamasoft.foliojet.layout.fragment.PreparedFloatFragment fragment) -> {
					// 元のFloatingはthis側に残り、残余は座標(0,0)=
					// 次フラグメント先頭、serial引き継ぎでnext側へ
					sourceSide.add(floating);
					remainderSide.add(new Floating(fragment.serial(), fragment.materialize(), 0, 0));
					allWholeMoves = false;
				}
				}
			}
			}
		}
		final FloatSplitResult result;
		if (remainderSide.isEmpty()) {
			// 全KEEP——元リストは無傷
			result = FloatSplitResult.KEEP_ALL;
		} else if (allWholeMoves) {
			// 全floatが丸ごとMOVE——遅延表現(元リストから動かさない。
			// 台帳ごとの付け替えはownerが行う)
			result = FloatSplitResult.MOVE_ALL;
		} else {
			this.floatings.clear();
			this.floatings.addAll(sourceSide);
			final Floatings remainder = new Floatings();
			remainder.floatings.addAll(remainderSide);
			result = new FloatSplitResult.Partition(remainder);
		}
		// commit結果の分類がplanと整合することの検査(P2-3でP2-2のshadow
		// 比較を置き換えたもの。assert無効の本番ではFINE診断のみ)
		final boolean consistent = commitConsistentWithPlan(plan, result);
		assert consistent : "commit結果がplanと不整合: pageLimit=" + pageLimit + " flags=" + flags;
		assert !(result instanceof FloatSplitResult.Partition(final Floatings r) && r.floatings.isEmpty());
		return result;
	}

	/**
	 * commit結果の分類がplanの分類と整合するかを検査します(P2-3)。
	 * {@code SplitOnCommit}は結果を予言しないため制約を緩める:
	 * Moveを含むplanはKeepAllになれず、Keepを含むplanはMoveAllになれず、
	 * PartitionはMoveまたはSplitOnCommitなしには生じない。不整合は
	 * FINEログにも出す(本番でassertが無効でも観測できるように)。
	 */
	private static boolean commitConsistentWithPlan(final FloatSplitPlan plan, final FloatSplitResult result) {
		boolean anyKeepPlan = false;
		boolean anyMovePlan = false;
		boolean anySplitPlan = false;
		for (final FloatSplitPlan.FloatItemPlan item : plan.direct()) {
			switch (item) {
			case FloatSplitPlan.FloatItemPlan.Keep keep -> anyKeepPlan = true;
			case FloatSplitPlan.FloatItemPlan.Move move -> anyMovePlan = true;
			case FloatSplitPlan.FloatItemPlan.SplitOnCommit splitOnCommit -> anySplitPlan = true;
			}
		}
		final boolean consistent = switch (result) {
		case FloatSplitResult.KeepAll keepAll -> !anyMovePlan;
		case FloatSplitResult.MoveAll moveAll -> !anyKeepPlan;
		case FloatSplitResult.Partition partition -> anyMovePlan || anySplitPlan;
		};
		if (!consistent) {
			java.util.logging.Logger.getLogger(Floatings.class.getName())
					.fine(() -> "FloatSplit commit/plan不整合: plan=" + plan + " result=" + result);
		}
		return consistent;
	}

	public String toString() {
		return super.toString() + ": floatings.size=" + this.floatings.size();
	}
}
