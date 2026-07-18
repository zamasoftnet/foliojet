package net.zamasoft.foliojet.css;

/**
 * CSS規則の出所(cascade origin, CSS Cascading and Inheritance仕様)です。
 * 列挙順が優先順位(昇順)を兼ねます: USER_AGENT の規則は固有性・出現順に
 * かかわらず常に AUTHOR の規則に劣後します。
 */
public enum Origin {
	/** ユーザーエージェント既定スタイルシート。 */
	USER_AGENT,
	/** 文書(著者)スタイルシート・インラインstyle属性。 */
	AUTHOR;
}
