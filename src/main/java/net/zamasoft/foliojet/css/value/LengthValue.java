package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: LengthValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface LengthValue extends Value, QuantityValue {
	// 絶対
	public static final short UNIT_IN = LexicalUnit.SAC_INCH;

	public static final short UNIT_CM = LexicalUnit.SAC_CENTIMETER;

	public static final short UNIT_MM = LexicalUnit.SAC_MILLIMETER;

	public static final short UNIT_PT = LexicalUnit.SAC_POINT;

	public static final short UNIT_PC = LexicalUnit.SAC_PICA;

	// デバイス相対
	public static final short UNIT_PX = LexicalUnit.SAC_PIXEL;

	// フォント相対
	public static final short UNIT_FR = LexicalUnit.SAC_EM;

	public short getUnitType();

	public boolean isZero();

	public boolean isNegative();

	public AbsoluteLengthValue toAbsoluteLength(CSSStyle style);
}