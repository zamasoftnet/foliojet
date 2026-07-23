package net.zamasoft.foliojet.layout.builder.impl;

/**
 * 新規floatの配置確定の種別です(2026-07-23新設、排除域P1増分2——
 * `docs/consultations/consult-exclusion-p1-design-codex.txt`の設計)。
 *
 * <p>
 * 従来の{@code transferFloatToNextPage}(判定名だが{@code breakFloats}
 * への追加という副作用を持っていた)を、副作用のない分類
 * ({@code BlockBuilder.classifyFloatPlacement})と記録hook
 * ({@code BlockBuilder.recordBreakFloat})へ分解した際の分類結果。
 * 物理的な実測位置(フラグメント境界に対するはみ出し・ページ先頭か
 * どうか)だけから決まり、論理的な由来は持ち込まない。
 * </p>
 */
enum FloatCommitKind {
	/**
	 * 通常配置: 排除域台帳へ登録し、{@code breakFloats}は変更しない
	 * (はみ出していない、またはREPLACEDがページ先頭で残される場合)。
	 */
	PLACED,
	/**
	 * 分割前提の配置: 排除域台帳へ登録し、{@code breakFloats}へも追加
	 * する(はみ出したBLOCK型floatを後続の{@code splitFloatings}が
	 * フラグメント境界で切る)。
	 */
	SPLIT_AT_BREAK,
	/**
	 * 丸ごと次フラグメントへ: 排除域台帳へは登録せず、
	 * {@code breakFloats}へ追加する(avoid指定のBLOCK、またはページ
	 * 先頭でないREPLACED)。
	 */
	MOVE_TO_NEXT,
	/**
	 * clearによる先送り: 既に先送り済みのfloatに対するclear指定を持つ
	 * floatを、探索なしでフラグメント境界(pageLimit)へ置いて次
	 * フラグメントへ送る。排除域台帳へは登録せず{@code breakFloats}へ
	 * 追加する。親extentの更新は通常の{@code extendParents}ではなく
	 * root直下のみという現行規則を保存する(2026-07-23、codex設計——
	 * この非対称をP1で黙って正規化しない)。
	 */
	MOVE_BY_CLEAR
}
