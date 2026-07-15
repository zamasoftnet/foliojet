package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.CSSStyle;


/**
 * @author MIYABE Tatsuhiko
 * @version $Id: LengthValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface LengthValue extends Value, QuantityValue {
	// 絶対
	public static final short UNIT_IN = 18; // 旧SAC_INCH

	public static final short UNIT_CM = 19; // 旧SAC_CENTIMETER

	public static final short UNIT_MM = 20; // 旧SAC_MILLIMETER

	public static final short UNIT_PT = 21; // 旧SAC_POINT

	public static final short UNIT_PC = 22; // 旧SAC_PICA

	// デバイス相対
	public static final short UNIT_PX = 17; // 旧SAC_PIXEL

	// フォント相対
	public static final short UNIT_FR = 15; // 旧SAC_EM

	public short getUnitType();

	public boolean isZero();

	public boolean isNegative();

	public AbsoluteLengthValue toAbsoluteLength(CSSStyle style);
}