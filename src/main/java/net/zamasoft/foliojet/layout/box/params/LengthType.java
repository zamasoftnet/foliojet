package net.zamasoft.foliojet.layout.box.params;

/**
 * 長さ値の指定方法です。Length/Dimension/Insets/Offsetで共用します。
 * <p>
 * MIXEDはcalc()が絶対長さと割合を混在させた結果(例: calc(50% + 10px))。
 * 実長さ = absolute + ratio * ref。4値ちょうどで2ビットパッキング
 * (Dimension/Insets/Offsetのflags)に収まる。
 * </p>
 */
public enum LengthType {
	ABSOLUTE, RELATIVE, AUTO, MIXED;

	static final LengthType[] VALUES = values();

	/**
	 * 実際の長さを得るのに基準値(コンテナサイズ等)が要るかどうか。
	 * RELATIVE(純粋な割合)とMIXED(calc()による絶対長さと割合の混在)は
	 * いずれも基準値に依存する。ABSOLUTE/AUTOは依存しない。
	 */
	public boolean needsReference() {
		return this == RELATIVE || this == MIXED;
	}
}
