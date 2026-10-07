package net.zamasoft.foliojet.layout.builder.impl;

import java.util.EnumSet;

/**
 * Execution plan for table construction (C4-B, 2026-07-19).
 *
 * <p>
 * {@link Mode#RETAINED} always carries at least one {@link TableRetentionReason},
 * so tests can directly check which plan was selected and why, rather than whether
 * the TwoPass counter increased.
 * </p>
 *
 * @param mode    the execution plan type
 * @param reasons reasons Retained is required (empty for Incremental)
 * @author MIYABE Tatsuhiko
 */
public record TableBuildPlan(Mode mode, EnumSet<TableRetentionReason> reasons) {
	public TableBuildPlan {
		assert mode == Mode.INCREMENTAL || !reasons.isEmpty() : "RETAINEDにはreasonsが1つ以上要る";
	}

	public enum Mode {
		/** Can commit early (streams by row and forwards each row as soon as it is finalized). */
		INCREMENTAL,
		/** Retains the entire table (or the entire relevant row-group) before committing. */
		RETAINED
	}
}
