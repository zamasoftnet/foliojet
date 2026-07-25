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
 * P2-3以降、{@link Floatings#splitPageAxis}はこの計画で駆動される
 * (planDirect→ordinal順commit)。{@code children}(子flow再帰の計画)は
 * 現状常に空——子flowのfloatは実行時に各コンテナが独立にplan+commitする
 * (FlowContainerの型付き再帰集約、P2-4)。
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

	/**
	 * 単一floatの行き先計画です。{@code Keep}/{@code Move}はfloat 1個
	 * 粒度(台帳全体粒度の{@link FloatSplitResult}のKeepAll/MoveAllとは
	 * 別——切断結果型ファミリの語彙対応表は{@code SplitResult}のjavadoc
	 * 参照)。
	 */
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
		 * commit時に一度だけ
		 * {@code splitFloatFragment(serial, innerLimit, DEFAULT, splitFlags)}を
		 * 呼ぶ(分岐表3。A-3a-2以降——残余boxは即時構築されず、材料
		 * {@code PreparedFloatFragment}を受け側Floatingへの接続時に一度だけ
		 * materializeする)。結果(Keep/Move/Prepared)は予言しない。
		 *
		 * @param expected   計画対象floatの実測スナップショット
		 * @param innerLimit float座標系の切断線({@code pageLimit - pageStart}。
		 *                   frame控除は切断内部で行われる)
		 * @param splitFlags {@code FLAGS_FIRST}(物理first)または
		 *                   {@code FLAGS_SPLIT}
		 */
		public record SplitOnCommit(FloatMeasurement expected, double innerLimit, byte splitFlags)
				implements FloatItemPlan {
		}

		/**
		 * commit時に救済分割(visual rescue split)を行う印です
		 * (2026-07-25新設、増分7。答申§5。
		 * {@code docs/history/2026-07-25-rescue-split-spec.md})。
		 *
		 * <p>
		 * 分岐表5の「first ははみ出し許容でKeep」——すなわちフラグメント
		 * 先頭で分割不能な浮動体がなお超過している、<b>現在はみ出したまま
		 * 描画している唯一の非進行点</b>——だけを置き換えます。commitでは
		 * 元台帳(source側)に先頭断片(head)を、残余台帳(remainder側)に
		 * 続きの断片(tail)を入れます。tailは次フラグメントで通常の
		 * float配置をやり直します。
		 * </p>
		 *
		 * <p>
		 * {@link SplitOnCommit}と違い、こちらは結果を<b>完全に予言します</b>
		 * ——救済は元ボックスに触れず(幾何を切るだけ)、判定は
		 * {@link net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner}の
		 * 純関数だからです。
		 * </p>
		 *
		 * @param expected 計画対象floatの実測スナップショット
		 * @param slice    切り出す区間(前進保証つき)
		 */
		public record RescueOnCommit(FloatMeasurement expected,
				net.zamasoft.foliojet.layout.rescue.RescueDecision.Slice slice) implements FloatItemPlan {
			public RescueOnCommit {
				if (slice == null) {
					throw new IllegalArgumentException("slice");
				}
				if (slice.lastFragment()) {
					// tailを作らない区間は救済の意味がない(非進行点は
					// 「なお超過している」ことが前提)
					throw new IllegalArgumentException("残余のない救済: " + slice);
				}
			}
		}
	}

	/**
	 * 子flow(BLOCK)への再帰分の計画です(現状未使用のseam——P2-4は
	 * 子flowの再帰集約を実行時の型付き集約(FloatAggregate)で実現した
	 * ため、計画の階層化はA-3a以降必要になった時点で埋める)。
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
		case RESCUE:
			// 分岐表5: firstならはみ出し許容で残し、非firstなら丸ごと送る
			if (!first) {
				return new FloatItemPlan.Move(m);
			}
			// 分岐表5-R(2026-07-25、救済分割・増分7): 「フラグメント先頭・
			// 分割不能・なお超過」——ここが浮動体で「はみ出したまま描画」に
			// 落ちる唯一の非進行点(答申§1・§5)。救済できるならKeepの
			// かわりに幾何学的に切る
			final FloatItemPlan rescue = rescue(m, pageLimit);
			return rescue != null ? rescue : new FloatItemPlan.Keep(m);
		default:
			throw new IllegalStateException(m.box().toString());
		}
	}

	/**
	 * 非進行点の浮動体を救済分割する計画を返します(救済しないなら
	 * {@code null})。判定そのものは
	 * {@link net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner}の
	 * 純関数に集約されており(答申§4)、ここが持つのは
	 * 「元ボックス・元寸法・消費済み量をどう取るか」だけです。
	 *
	 * @param m         対象floatの実測値(first・超過が確定している)
	 * @param pageLimit 切断線(owner座標)。フラグメンテナ容量でもある
	 *                  ——firstは物理的にフラグメント先頭を意味するため
	 */
	private static FloatItemPlan rescue(final FloatMeasurement m, final double pageLimit) {
		final double sourcePageExtent;
		final double offset;
		if (m.box() instanceof net.zamasoft.foliojet.layout.rescue.VisualRescueFloatBox fragment) {
			// 救済済み断片の続き(断片の断片は作らない)
			sourcePageExtent = fragment.getSourcePageExtent();
			offset = fragment.getOffset();
		} else {
			sourcePageExtent = m.pageExtent();
			offset = 0;
		}
		final double available = pageLimit - m.pageStart();
		final net.zamasoft.foliojet.layout.rescue.RescueDecision decision = net.zamasoft.foliojet.layout.rescue.RescueStats
				.record(m.boxType(),
						net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner.planInFragmentainer(
								m.box().getPos().getType(), true, pageLimit, available, sourcePageExtent, offset));
		if (!(decision instanceof net.zamasoft.foliojet.layout.rescue.RescueDecision.Slice slice)) {
			return null;
		}
		if (!net.zamasoft.foliojet.layout.rescue.RescuePolicy.isEnabled()) {
			return null;
		}
		if (slice.lastFragment()) {
			// 呼び出し条件(なお超過)からここには来ない。念のため救済しない
			return null;
		}
		// 実行時の前進検査(答申§5)。判定器の不変条件と二重になるが、
		// 無限ループの不在は絶対要件なので実行時にも守る
		if (!(slice.nextOffset() > offset) || !(sourcePageExtent - slice.nextOffset() > 0)) {
			return null;
		}
		net.zamasoft.foliojet.layout.rescue.RescueStats.recordEnabled();
		return new FloatItemPlan.RescueOnCommit(m, slice);
	}
}
