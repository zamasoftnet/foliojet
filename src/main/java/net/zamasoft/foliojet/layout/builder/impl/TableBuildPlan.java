package net.zamasoft.foliojet.layout.builder.impl;

import java.util.EnumSet;

/**
 * 表構築の実行計画です(C4-B、2026-07-19)。
 *
 * <p>
 * {@link Mode#RETAINED}は必ず1つ以上の{@link TableRetentionReason}を伴う——
 * テストが「TwoPassカウンタが増えたか」ではなく「どの理由でどちらの計画に
 * なったか」を直接検証できるようにする。
 * </p>
 *
 * @param mode    実行計画の種別
 * @param reasons Retainedを要する理由(Incrementalなら空)
 * @author MIYABE Tatsuhiko
 */
public record TableBuildPlan(Mode mode, EnumSet<TableRetentionReason> reasons) {
	public TableBuildPlan {
		assert mode == Mode.INCREMENTAL || !reasons.isEmpty() : "RETAINEDにはreasonsが1つ以上要る";
	}

	public enum Mode {
		/** 早期コミット可能(行単位でストリーミングし、確定した行から先へ流す)。 */
		INCREMENTAL,
		/** 表全体(または該当row-group全体)を保持してからコミットする。 */
		RETAINED
	}
}
