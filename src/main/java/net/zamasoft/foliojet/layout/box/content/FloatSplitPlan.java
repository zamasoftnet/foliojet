package net.zamasoft.foliojet.layout.box.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * 浮動体のページ分割の純計画です(2026-07-24新設、排除域P2のP2-2。
 * {@code docs/consultations/consult-exclusion-p2-design-codex.txt}§2.1の型)。
 *
 * <p>
 * {@link Floatings#splitPageAxis}の分岐表
 * ({@code docs/history/2026-07-24-p2-splitfloatings-branch-table.md})の
 * 分類部だけを純関数({@link #classify})へ写した計画で、破壊的なbox
 * splitは含まない——{@link FloatItemPlan.SplitOnCommit}は「commit時に
 * 一度だけ{@code split}を呼ぶ」という印であり、その結果
 * (Keep/Move/Split)を<b>予言しない</b>(codex設計§2.1「全
 * {@code IPageBreakableBox.split}の純化までP2へ持ち込まない」)。
 * </p>
 *
 * <p>
 * P2-2時点ではshadow配線({@link Floatings#splitPageAxis}内での既存実行
 * 結果との突き合わせ)のみに使う。{@code children}(子flow再帰の計画)は
 * P2-3以降で埋める——現状は常に空。
 * </p>
 *
 * @param expectedSource 計画の対象{@link Floatings}(identity anchor)
 * @param pageLimit      切断線(owner座標)
 * @param flags          {@code IPageBreakableBox.FLAGS_*}のスナップショット
 * @param ownerFlow      ownerの書字方向
 * @param direct         直接保持するfloatの計画(安定序数順)
 * @param children       子flowの計画(P2-2では常に空)
 */
public record FloatSplitPlan(
		Floatings expectedSource,
		double pageLimit,
		byte flags,
		WritingMode ownerFlow,
		List<FloatItemPlan> direct,
		List<ChildFloatPlan> children) {

	/** 単一floatの行き先計画です。 */
	public sealed interface FloatItemPlan {
		/** 計画対象floatの実測スナップショット。 */
		FloatMeasurement expected();

		/** 元のフラグメントに残す(分岐表1、および4→5フォールスルーのfirst)。 */
		public record Keep(FloatMeasurement expected) implements FloatItemPlan {
		}

		/** 丸ごと次のフラグメントへ送る(分岐表2、および4→5フォールスルーの非first)。 */
		public record Move(FloatMeasurement expected) implements FloatItemPlan {
		}

		/**
		 * commit時に一度だけ{@code split(innerLimit, DEFAULT, splitFlags)}を
		 * 呼ぶ(分岐表3)。結果(Keep/Move/Split)は予言しない。
		 *
		 * @param expected   計画対象floatの実測スナップショット
		 * @param innerLimit float座標系の切断線({@code pageLimit - pageStart}。
		 *                   frame控除は{@code split}内部で行われる)
		 * @param splitFlags {@code FLAGS_FIRST}(物理first)または
		 *                   {@code FLAGS_SPLIT}
		 */
		public record SplitOnCommit(FloatMeasurement expected, double innerLimit, byte splitFlags)
				implements FloatItemPlan {
		}
	}

	/**
	 * 子flow(BLOCK)への再帰分の計画です(P2-3以降で使用。
	 * {@code FlowContainer.splitFloatings(pageLimit, flags, index)}の
	 * 子flowループを写す)。
	 *
	 * @param flowOrdinal  呼び出し時点のflowsスナップショットでの序数
	 * @param expectedFlow 対象flow(identity anchor)
	 * @param childLimit   子座標系へ変換した切断線
	 * @param childFlags   {@code lflags & flags}(LAST判定は
	 *                     originalFlowCountから計算すること——分岐表doc参照)
	 * @param childPlan    子コンテナの計画
	 */
	public record ChildFloatPlan(
			int flowOrdinal,
			FlowContainer.Flow expectedFlow,
			double childLimit,
			byte childFlags,
			FloatSplitPlan childPlan) {
	}

	/**
	 * 直接保持分のみの純計画を作ります(読み取り専用——{@code source}にも
	 * 各ボックスにも一切影響しない)。{@code children}は空。
	 *
	 * @param source    対象の{@link Floatings}
	 * @param ownerFlow ownerの書字方向
	 * @param pageLimit 切断線(owner座標)
	 * @param flags     {@code IPageBreakableBox.FLAGS_*}
	 * @return 純計画
	 */
	public static FloatSplitPlan planDirect(final Floatings source, final WritingMode ownerFlow, final double pageLimit,
			final byte flags) {
		final List<FloatMeasurement> measurements = source.measure(ownerFlow);
		final List<FloatItemPlan> direct = new ArrayList<>(measurements.size());
		for (final FloatMeasurement measurement : measurements) {
			direct.add(classify(measurement, pageLimit, flags));
		}
		return new FloatSplitPlan(source, pageLimit, flags, ownerFlow, Collections.unmodifiableList(direct),
				List.of());
	}

	/**
	 * 分岐表の分類部の純関数版です。{@link Floatings#splitPageAxis}の
	 * ループ本体の分岐(分岐表1・2・3・4→5)と1:1対応する——4→5の
	 * caseフォールスルー(BLOCKでavoid非first・書字軸不一致はREPLACEDと
	 * 同じ処理へ落ちる)もここで明示的に写している。
	 *
	 * @param m         対象floatの実測値
	 * @param pageLimit 切断線(owner座標)
	 * @param flags     {@code IPageBreakableBox.FLAGS_*}
	 * @return 行き先計画
	 */
	public static FloatItemPlan classify(final FloatMeasurement m, final double pageLimit, final byte flags) {
		final boolean first = (flags & IPageBreakableBox.FLAGS_FIRST) != 0 && m.fragmentHead();
		if (LayoutUtils.compare(m.pageEnd(), pageLimit) <= 0) {
			// 分岐表1: 全体が切断線以前
			return new FloatItemPlan.Keep(m);
		}
		if (!first && LayoutUtils.compare(pageLimit, m.pageStart()) < 0) {
			// 分岐表2: 全体が切断線より後
			return new FloatItemPlan.Move(m);
		}
		switch (m.boxType()) {
		case BLOCK:
			if ((m.pageBreakInside() != PageBreakMode.AVOID || first) && m.sameWritingAxis()) {
				// 分岐表3: commit時に一度だけsplitする印(結果は予言しない)
				final byte splitFlags = first ? IPageBreakableBox.FLAGS_FIRST : IPageBreakableBox.FLAGS_SPLIT;
				return new FloatItemPlan.SplitOnCommit(m, pageLimit - m.pageStart(), splitFlags);
			}
			// 分岐表4: avoid非first・書字軸不一致はREPLACED扱いへフォールスルー
			//$FALL-THROUGH$
		case REPLACED:
			// 分岐表5: firstならはみ出し許容で残し、非firstなら丸ごと送る
			return first ? new FloatItemPlan.Keep(m) : new FloatItemPlan.Move(m);
		default:
			throw new IllegalStateException(m.box().toString());
		}
	}
}
