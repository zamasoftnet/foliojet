package net.zamasoft.foliojet.ua;

/**
 * UserAgent.prepare の処理段階です。
 */
public enum PrepareMode {
	/** 文書処理の開始。 */
	DOCUMENT,
	/** 中間パス(計測のみ)。 */
	MIDDLE_PASS,
	/** 最終パス。 */
	LAST_PASS;
}
