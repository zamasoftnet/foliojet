package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * <a href="http://www.w3.org/TR/CSS21/box.html#propdef-border-left-width">
 * border-left-width 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class ColumnWidth extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ColumnWidth();

	public static double get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.AUTO) {
			return StyleUtils.NONE;
		}
		return BoxValueUtils.toLength(value).getLength();
	}

	protected ColumnWidth() {
		super("-cssj-column-width");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ValueUtils.isAuto(lu)) {
			return KeywordValue.AUTO;
		}

		LengthValue value = ValueUtils.toLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		if (value.isNegative()) {
			return null;
		}
		return value;
	}

}