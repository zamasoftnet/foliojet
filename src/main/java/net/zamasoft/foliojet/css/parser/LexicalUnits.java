package net.zamasoft.foliojet.css.parser;

import org.htmlunit.cssparser.parser.LexicalUnit.LexicalUnitType;

public final class LexicalUnits {
	private LexicalUnits() {
		// utility
	}

	public static LexicalUnit wrap(org.htmlunit.cssparser.parser.LexicalUnit unit) {
		return unit == null ? null : new Adapter(unit);
	}

	private static final class Adapter implements LexicalUnit {
		private final org.htmlunit.cssparser.parser.LexicalUnit unit;

		Adapter(org.htmlunit.cssparser.parser.LexicalUnit unit) {
			this.unit = unit;
		}

		public short getLexicalUnitType() {
			return toLegacyType(this.unit.getLexicalUnitType());
		}

		public LexicalUnit getNextLexicalUnit() {
			return wrap(this.unit.getNextLexicalUnit());
		}

		public LexicalUnit getPreviousLexicalUnit() {
			return wrap(this.unit.getPreviousLexicalUnit());
		}

		public int getIntegerValue() {
			return this.unit.getIntegerValue();
		}

		public float getFloatValue() {
			return (float) this.unit.getDoubleValue();
		}

		public String getDimensionUnitText() {
			return this.unit.getDimensionUnitText();
		}

		public String getFunctionName() {
			return this.unit.getFunctionName();
		}

		public LexicalUnit getParameters() {
			return wrap(this.unit.getParameters());
		}

		public String getStringValue() {
			return this.unit.getStringValue();
		}

		public LexicalUnit getSubValues() {
			return wrap(this.unit.getSubValues());
		}

		public String toString() {
			return this.unit.toString();
		}
	}

	private static short toLegacyType(LexicalUnitType type) {
		switch (type) {
		case OPERATOR_COMMA:
			return LexicalUnit.SAC_OPERATOR_COMMA;
		case OPERATOR_PLUS:
			return LexicalUnit.SAC_OPERATOR_PLUS;
		case OPERATOR_MINUS:
			return LexicalUnit.SAC_OPERATOR_MINUS;
		case OPERATOR_MULTIPLY:
			return LexicalUnit.SAC_OPERATOR_MULTIPLY;
		case OPERATOR_SLASH:
			return LexicalUnit.SAC_OPERATOR_SLASH;
		case OPERATOR_MOD:
			return LexicalUnit.SAC_OPERATOR_MOD;
		case OPERATOR_EXP:
			return LexicalUnit.SAC_OPERATOR_EXP;
		case OPERATOR_LT:
			return LexicalUnit.SAC_OPERATOR_LT;
		case OPERATOR_GT:
			return LexicalUnit.SAC_OPERATOR_GT;
		case OPERATOR_LE:
			return LexicalUnit.SAC_OPERATOR_LE;
		case OPERATOR_GE:
			return LexicalUnit.SAC_OPERATOR_GE;
		case OPERATOR_TILDE:
			return LexicalUnit.SAC_OPERATOR_TILDE;
		case INHERIT:
			return LexicalUnit.SAC_INHERIT;
		case INTEGER:
			return LexicalUnit.SAC_INTEGER;
		case REAL:
			return LexicalUnit.SAC_REAL;
		case EM:
			return LexicalUnit.SAC_EM;
		case EX:
			return LexicalUnit.SAC_EX;
		case REM:
			return LexicalUnit.SAC_REM;
		case CH:
			return LexicalUnit.SAC_CH;
		case PIXEL:
			return LexicalUnit.SAC_PIXEL;
		case INCH:
			return LexicalUnit.SAC_INCH;
		case CENTIMETER:
			return LexicalUnit.SAC_CENTIMETER;
		case MILLIMETER:
			return LexicalUnit.SAC_MILLIMETER;
		case POINT:
			return LexicalUnit.SAC_POINT;
		case PICA:
			return LexicalUnit.SAC_PICA;
		case PERCENTAGE:
			return LexicalUnit.SAC_PERCENTAGE;
		case URI:
			return LexicalUnit.SAC_URI;
		case COUNTER_FUNCTION:
			return LexicalUnit.SAC_COUNTER_FUNCTION;
		case COUNTERS_FUNCTION:
			return LexicalUnit.SAC_COUNTERS_FUNCTION;
		case RGBCOLOR:
			return LexicalUnit.SAC_RGBCOLOR;
		case DEGREE:
			return LexicalUnit.SAC_DEGREE;
		case GRADIAN:
			return LexicalUnit.SAC_GRADIAN;
		case RADIAN:
			return LexicalUnit.SAC_RADIAN;
		case MILLISECOND:
			return LexicalUnit.SAC_MILLISECOND;
		case SECOND:
			return LexicalUnit.SAC_SECOND;
		case HERTZ:
			return LexicalUnit.SAC_HERTZ;
		case KILOHERTZ:
			return LexicalUnit.SAC_KILOHERTZ;
		case IDENT:
			return LexicalUnit.SAC_IDENT;
		case STRING_VALUE:
			return LexicalUnit.SAC_STRING_VALUE;
		case ATTR:
			return LexicalUnit.SAC_ATTR;
		case RECT_FUNCTION:
			return LexicalUnit.SAC_RECT_FUNCTION;
		case UNICODERANGE:
			return LexicalUnit.SAC_UNICODERANGE;
		case FUNCTION:
		case FUNCTION_CALC:
		case HSLCOLOR:
		case HWBCOLOR:
		case LABCOLOR:
		case LCHCOLOR:
			return LexicalUnit.SAC_FUNCTION;
		case DIMENSION:
		case VW:
		case VH:
		case VMIN:
		case VMAX:
		case DVW:
		case DVH:
		case DVMIN:
		case DVMAX:
		case LVW:
		case LVH:
		case LVMIN:
		case LVMAX:
		case SVW:
		case SVH:
		case SVMIN:
		case SVMAX:
		case QUATER:
		case TURN:
			return LexicalUnit.SAC_DIMENSION;
		default:
			return LexicalUnit.SAC_IDENT;
		}
	}
}
