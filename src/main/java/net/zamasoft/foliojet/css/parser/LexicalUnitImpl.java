package net.zamasoft.foliojet.css.parser;

/**
 * LexicalUnit の実装。ph-css の式から LexicalUnits が構築します。
 */
final class LexicalUnitImpl implements LexicalUnit {
	private final short type;

	private float floatValue;

	private String dimensionUnit;

	private String functionName;

	private LexicalUnit parameters;

	private String stringValue;

	private LexicalUnitImpl prev, next;

	LexicalUnitImpl(short type) {
		this.type = type;
	}

	static LexicalUnitImpl number(short type, float value, String unit) {
		LexicalUnitImpl lu = new LexicalUnitImpl(type);
		lu.floatValue = value;
		lu.dimensionUnit = unit;
		return lu;
	}

	static LexicalUnitImpl string(short type, String value) {
		LexicalUnitImpl lu = new LexicalUnitImpl(type);
		lu.stringValue = value;
		return lu;
	}

	static LexicalUnitImpl function(short type, String name, LexicalUnit parameters) {
		LexicalUnitImpl lu = new LexicalUnitImpl(type);
		lu.functionName = name;
		lu.parameters = parameters;
		return lu;
	}

	/**
	 * next をこのユニットの後ろに連結し、next を返します。
	 */
	LexicalUnitImpl append(LexicalUnitImpl next) {
		this.next = next;
		next.prev = this;
		return next;
	}

	public short getLexicalUnitType() {
		return this.type;
	}

	public LexicalUnit getNextLexicalUnit() {
		return this.next;
	}

	public LexicalUnit getPreviousLexicalUnit() {
		return this.prev;
	}

	public int getIntegerValue() {
		return (int) this.floatValue;
	}

	public float getFloatValue() {
		return this.floatValue;
	}

	public String getDimensionUnitText() {
		return this.dimensionUnit;
	}

	public String getFunctionName() {
		return this.functionName;
	}

	public LexicalUnit getParameters() {
		return this.parameters;
	}

	public String getStringValue() {
		return this.stringValue;
	}

	public LexicalUnit getSubValues() {
		return this.parameters;
	}

	public String toString() {
		StringBuilder buff = new StringBuilder();
		switch (this.type) {
		case SAC_OPERATOR_COMMA:
			buff.append(',');
			break;
		case SAC_OPERATOR_SLASH:
			buff.append('/');
			break;
		case SAC_INHERIT:
			buff.append("inherit");
			break;
		case SAC_INTEGER:
			buff.append(this.getIntegerValue());
			break;
		case SAC_URI:
			buff.append("url(").append(this.stringValue).append(')');
			break;
		case SAC_STRING_VALUE:
			buff.append('"').append(this.stringValue).append('"');
			break;
		case SAC_IDENT:
		case SAC_UNICODERANGE:
			buff.append(this.stringValue);
			break;
		case SAC_ATTR:
			buff.append("attr(").append(this.stringValue).append(')');
			break;
		case SAC_RGBCOLOR:
			buff.append("rgb(").append(paramsToString()).append(')');
			break;
		case SAC_COUNTER_FUNCTION:
		case SAC_COUNTERS_FUNCTION:
		case SAC_RECT_FUNCTION:
		case SAC_FUNCTION:
			buff.append(this.functionName).append('(').append(paramsToString()).append(')');
			break;
		default:
			buff.append(this.floatValue);
			if (this.dimensionUnit != null) {
				buff.append(this.dimensionUnit);
			}
			break;
		}
		return buff.toString();
	}

	private String paramsToString() {
		StringBuilder buff = new StringBuilder();
		for (LexicalUnit lu = this.parameters; lu != null; lu = lu.getNextLexicalUnit()) {
			if (buff.length() > 0) {
				buff.append(' ');
			}
			buff.append(lu);
		}
		return buff.toString();
	}
}
