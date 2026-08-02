package net.zamasoft.foliojet.css.value;

/**
 * 著者定義カウンタスタイル({@code @counter-style})を指す
 * {@code list-style-type}の値です(2026-08-02)。
 *
 * <p>
 * 実体(記号の並べ方)は文書ごとの登録簿が持ち、値はコードだけを運ぶ
 * ——組み込みスタイルと同じ持ち回り方にすることで、カウンタを扱う
 * 既存の経路({@code counter()}・マーカー・{@code target-counter()})が
 * そのまま著者定義スタイルにも効く。
 * </p>
 */
public final class CounterStyleValue implements ListStyleTypeSource {

	private final short code;

	public CounterStyleValue(final short code) {
		this.code = code;
	}

	public short getListStyleType() {
		return this.code;
	}
}
