package net.zamasoft.foliojet.css.value;

/**
 * {@code -cssj-page-ref()}(独自拡張)と標準の{@code target-counter()}/
 * {@code target-counters()}が共通で使う値。id/attr参照先の{@code PageRef}
 * フラグメントからカウンタ値を読み出す点で3構文とも同じデータ形状のため、
 * 同じクラスで表現する。
 *
 * @author MIYABE Tatsuhiko
 */
public class TargetCounterValue implements Value {
	public static final byte REF = 1;
	public static final byte ATTR = 2;

	private final byte type;

	private final String ref, counter, separator;

	private final short numberStyleType;

	public TargetCounterValue(byte type, String ref, String counter, short numberStyleType, String separator) {
		this.type = type;
		this.ref = ref;
		this.counter = counter;
		this.numberStyleType = numberStyleType;
		this.separator = separator;
	}

	public byte getType() {
		return this.type;
	}

	public String getRef() {
		return this.ref;
	}

	public String getCounter() {
		return this.counter;
	}

	public short getNumberStyleType() {
		return this.numberStyleType;
	}

	public String getSeparator() {
		return this.separator;
	}

	public String toString() {
		return "TargetCounterValue(" + this.ref + "," + this.counter + "," + this.numberStyleType + ")";
	}
}
