package net.zamasoft.foliojet.css.value;

/**
 * カウンタスタイルのコードを持つ値です(2026-08-02——組み込みの
 * {@link ListStyleTypeValue}と著者定義の{@link CounterStyleValue}を
 * 同じ口で扱うため)。
 */
public interface ListStyleTypeSource extends Value {

	/** カウンタスタイルのコードです。 */
	public short getListStyleType();
}
