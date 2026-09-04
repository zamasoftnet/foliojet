package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class TextEmphasisStyle extends AbstractPrimitivePropertyInfo {

	public static final PrimitivePropertyInfo INFO = new TextEmphasisStyle();

	public static final Value AUTO_FILLED = new StringValue("filled");
	public static final Value AUTO_OPEN = new StringValue("open");
	public static final Value FILLED_DOT = new StringValue("\u2022");
	public static final Value OPEN_DOT = new StringValue("\u25E6");
	public static final Value FILLED_CIRCLE = new StringValue("\u25CF");
	public static final Value OPEN_CIRCLE = new StringValue("\u25CB");
	public static final Value FILLED_DOUBLE_CIRCLE = new StringValue("\u25C9");
	public static final Value OPEN_DOUBLE_CIRCLE = new StringValue("\u25CE");
	public static final Value FILLED_TRIANGLE = new StringValue("\u25B2");
	public static final Value OPEN_TRIANGLE = new StringValue("\u25B3");
	public static final Value FILLED_SESAME = new StringValue("\uFE45");
	public static final Value OPEN_SESAME = new StringValue("\uFE46");

	public static String get(CSSStyle style) {
		Value value = (Value) style.get(INFO);
		if (value == KeywordValue.NONE) {
			return null;
		}
		return ((StringValue) value).getString();
	}

	protected TextEmphasisStyle() {
		super("-cssj-text-emphasis-style");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		if (value == AUTO_FILLED) {
			if (TypesettingMode.isVertical(BlockFlow.get(style), WritingModeVariant.get(style))) {
				value = FILLED_SESAME;
			} else {
				value = FILLED_CIRCLE;
			}
		} else if (value == AUTO_OPEN) {
			if (TypesettingMode.isVertical(BlockFlow.get(style), WritingModeVariant.get(style))) {
				value = OPEN_SESAME;
			} else {
				value = OPEN_CIRCLE;
			}
		}
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		byte fill = 0, type = 0;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (lu instanceof CssToken.Ident luIdent) {
				if (ValueUtils.isNone(lu)) {
					if (tokens.hasNext()) {
						throw new PropertyException();
					}
					return KeywordValue.NONE;
				}
				String ident = luIdent.lower();
				if (ident.equals("filled")) {
					if (fill != 0) {
						throw new PropertyException();
					}
					fill = 1;
				} else if (ident.equals("open")) {
					if (fill != 0) {
						throw new PropertyException();
					}
					fill = 2;
				} else if (ident.equals("dot")) {
					if (type != 0) {
						throw new PropertyException();
					}
					type = 1;
				} else if (ident.equals("circle")) {
					if (type != 0) {
						throw new PropertyException();
					}
					type = 2;
				} else if (ident.equals("double-circle")) {
					if (type != 0) {
						throw new PropertyException();
					}
					type = 3;
				} else if (ident.equals("triangle")) {
					if (type != 0) {
						throw new PropertyException();
					}
					type = 4;
				} else if (ident.equals("sesame")) {
					if (type != 0) {
						throw new PropertyException();
					}
					type = 5;
				} else {
					throw new PropertyException();
				}
			} else if (lu instanceof CssToken.Str str) {
				if (fill != 0 || tokens.hasNext()) {
					throw new PropertyException();
				}
				return new StringValue(str.value());
			} else {
				throw new PropertyException();
			}
		}
		if (type == 0) {
			type = -1;
		}
		Value str;
		switch (type) {
		case -1:
			if (fill != 2) {
				str = AUTO_FILLED;
			} else {
				str = AUTO_OPEN;
			}
			break;
		case 1:
			if (fill != 2) {
				str = FILLED_DOT;
			} else {
				str = OPEN_DOT;
			}
			break;
		case 2:
			if (fill != 2) {
				str = FILLED_CIRCLE;
			} else {
				str = OPEN_CIRCLE;
			}
			break;
		case 3:
			if (fill != 2) {
				str = FILLED_DOUBLE_CIRCLE;
			} else {
				str = OPEN_DOUBLE_CIRCLE;
			}
			break;
		case 4:
			if (fill != 2) {
				str = FILLED_TRIANGLE;
			} else {
				str = OPEN_TRIANGLE;
			}
			break;
		case 5:
			if (fill != 2) {
				str = FILLED_SESAME;
			} else {
				str = OPEN_SESAME;
			}
			break;
		default:
			throw new PropertyException();
		}
		return str;
	}

}
