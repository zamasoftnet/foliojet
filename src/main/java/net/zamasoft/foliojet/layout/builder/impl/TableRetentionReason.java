package net.zamasoft.foliojet.layout.builder.impl;

/**
 * 表がRetained(表全体を保持してからコミットする実行計画)を要する理由です
 * (C4-B、2026-07-19。外部設計レビューで「現行ルーティングはfixed対autoでは
 * ない」と確定したことを受け、旧`LayoutUtils.needsIntrinsicSizing()`の
 * boolean一本化を理由ごとに型化した)。
 *
 * @author MIYABE Tatsuhiko
 */
public enum TableRetentionReason {
	/** table-layout:auto — 列幅実測に全セルへのアクセスが要る(CSS2.1 §17.5.2)。 */
	AUTO_COLUMNS,
	/** 行軸(line-axis、横書きなら幅)寸法がauto — 実測が要る。 */
	AUTO_LINE_SIZE,
	/** ページ軸(page-axis、横書きなら高さ)寸法が指定されている — 表全体の行高分配が要る。 */
	SPECIFIED_PAGE_SIZE,
	/** 通常フロー(FLOW)以外の配置 — OnePassTableBuilder.startLayout()が前提とするFLOWでない。 */
	OUT_OF_FLOW,
	/** ネストした実測パス(TwoPassBlockBuilder)の内側 — builder.isMain()が偽。 */
	NESTED_LAYOUT
}
