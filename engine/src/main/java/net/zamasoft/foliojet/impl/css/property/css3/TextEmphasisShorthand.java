package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.InheritValue;
import net.zamasoft.foliojet.css.value.NoneValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextEmphasisShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TextEmphasisShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new TextEmphasisShorthand();

	protected TextEmphasisShorthand() {
		super("-cssj-text-emphasis");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
			primitives.set(TextEmphasisStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(TextEmphasisColor.INFO, InheritValue.INHERIT_VALUE);
			return;
		}
		byte fill = 0, type = 0;
		Value color = null;
		boolean none = false;
		String str = null;
		do {
			switch (lu.getLexicalUnitType()) {
			case LexicalUnit.SAC_IDENT:
				if (ValueUtils.isNone(lu)) {
					if (type != 0 || fill != 0 || str != null || none) {
						throw new PropertyException();
					}
					primitives.set(TextEmphasisStyle.INFO, NoneValue.NONE_VALUE);
					none = true;
					break;
				}
				String ident = lu.getStringValue().toLowerCase();
				if (ident.equals("filled")) {
					if (fill != 0 || str != null || none) {
						throw new PropertyException();
					}
					fill = 1;
				} else if (ident.equals("open")) {
					if (fill != 0 || str != null || none) {
						throw new PropertyException();
					}
					fill = 2;
				} else if (ident.equals("dot")) {
					if (type != 0 || str != null || none) {
						throw new PropertyException();
					}
					type = 1;
				} else if (ident.equals("circle")) {
					if (type != 0 || str != null || none) {
						throw new PropertyException();
					}
					type = 2;
				} else if (ident.equals("double-circle")) {
					if (type != 0 || str != null || none) {
						throw new PropertyException();
					}
					type = 3;
				} else if (ident.equals("triangle")) {
					if (type != 0 || str != null || none) {
						throw new PropertyException();
					}
					type = 4;
				} else if (ident.equals("sesame")) {
					if (type != 0 || str != null || none) {
						throw new PropertyException();
					}
					type = 5;
				} else {
					if (color != null) {
						throw new PropertyException();
					}
					color = ColorValueUtils.toColor(ua, lu);
					if (color == null) {
						throw new PropertyException();
					}
				}
				break;
			case LexicalUnit.SAC_STRING_VALUE:
				if (fill != 0 || str != null) {
					throw new PropertyException();
				}
				str = lu.getStringValue();
				break;

			default:
				if (color != null) {
					throw new PropertyException();
				}
				color = ColorValueUtils.toColor(ua, lu);
				if (color == null) {
					throw new PropertyException();
				}
				break;
			}
			lu = lu.getNextLexicalUnit();
		} while (lu != null);
		if (str != null) {
			primitives.set(TextEmphasisStyle.INFO, new StringValue(str));
		} else if (!none) {
			if (type == 0) {
				type = -1;
			}
			Value strv;
			switch (type) {
			case -1:
				if (fill != 2) {
					strv = TextEmphasisStyle.AUTO_FILLED;
				} else {
					strv = TextEmphasisStyle.AUTO_OPEN;
				}
				break;
			case 1:
				if (fill != 2) {
					strv = TextEmphasisStyle.FILLED_DOT;
				} else {
					strv = TextEmphasisStyle.OPEN_DOT;
				}
				break;
			case 2:
				if (fill != 2) {
					strv = TextEmphasisStyle.FILLED_CIRCLE;
				} else {
					strv = TextEmphasisStyle.OPEN_CIRCLE;
				}
				break;
			case 3:
				if (fill != 2) {
					strv = TextEmphasisStyle.FILLED_DOUBLE_CIRCLE;
				} else {
					strv = TextEmphasisStyle.OPEN_DOUBLE_CIRCLE;
				}
				break;
			case 4:
				if (fill != 2) {
					strv = TextEmphasisStyle.FILLED_TRIANGLE;
				} else {
					strv = TextEmphasisStyle.OPEN_TRIANGLE;
				}
				break;
			case 5:
				if (fill != 2) {
					strv = TextEmphasisStyle.FILLED_SESAME;
				} else {
					strv = TextEmphasisStyle.OPEN_SESAME;
				}
				break;
			default:
				throw new PropertyException();
			}
			primitives.set(TextEmphasisStyle.INFO, strv);
		}
		if (color != null) {
			primitives.set(TextEmphasisColor.INFO, color);
		}
	}

}