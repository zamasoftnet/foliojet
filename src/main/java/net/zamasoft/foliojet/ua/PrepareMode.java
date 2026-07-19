package net.zamasoft.foliojet.ua;

/**
 * UserAgent.prepare の処理段階です。
 */
public enum PrepareMode {
	/** 文書処理の開始。 */
	DOCUMENT,
	/**
	 * 構造走査(2026-07-19新設)。ボックス構築・レイアウトを一切行わない
	 * 軽量な事前パス。:has()・:last-child系等、要素の終了時点まで真偽が
	 * 確定しないセレクタを解決するために使う(docs/PLAN.md「2パス制御
	 * モード」参照)。{@code processing.pass-count}(既存)の反復回数には
	 * 数えない、独立したフェーズ。
	 */
	STRUCTURE_SCAN,
	/** 中間パス(計測のみ)。 */
	MIDDLE_PASS,
	/** 最終パス。 */
	LAST_PASS;
}
