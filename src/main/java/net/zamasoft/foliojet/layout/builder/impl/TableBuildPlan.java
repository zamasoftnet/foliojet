package net.zamasoft.foliojet.layout.builder.impl;

import java.util.EnumSet;

/**
 * 表構築の実行計画です(C4-B、2026-07-19)。
 *
 * <p>
 * {@link Mode#RETAINED}は必ず1つ以上の{@link TableRetentionReason}を伴う。
 * {@code approximate}は{@code processing.strict-one-pass}による
 * {@link Mode#INCREMENTAL}への近似縮退(reasonsがあるにもかかわらず
 * Incrementalを選んだ)ことを表す——テストが「TwoPassカウンタが増えたか」
 * ではなく「どの理由でどちらの計画になったか」を直接検証できるようにする。
 * </p>
 *
 * @param mode        実行計画の種別
 * @param reasons     Retainedを要する理由(Incrementalかつapproximate=falseなら空)
 * @param approximate strict-one-passによる近似縮退か
 * @author MIYABE Tatsuhiko
 */
public record TableBuildPlan(Mode mode, EnumSet<TableRetentionReason> reasons, boolean approximate) {
	public TableBuildPlan {
		assert mode == Mode.INCREMENTAL || !reasons.isEmpty() : "RETAINEDにはreasonsが1つ以上要る";
		assert mode == Mode.INCREMENTAL || !approximate : "近似(approximate)はINCREMENTALへの縮退時のみ真";
	}

	public enum Mode {
		/** 早期コミット可能(行単位でストリーミングし、確定した行から先へ流す)。 */
		INCREMENTAL,
		/** 表全体(または該当row-group全体)を保持してからコミットする。 */
		RETAINED
	}
}
