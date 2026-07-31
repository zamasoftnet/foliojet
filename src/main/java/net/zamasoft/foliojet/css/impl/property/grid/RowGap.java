package net.zamasoft.foliojet.css.impl.property.grid;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code row-gap}です(Grid G0)。固定長のみ({@code normal}=0)。
 * 列間隔は既存の{@code column-gap}(multicolと共用——Gridでのnormalの
 * 解決0はBoxStyleMapper側)を使う。
 *
 * @author MIYABE Tatsuhiko
 */
public class RowGap extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new RowGap();

	public static double get(CSSStyle style) {
		final Value value = style.get(INFO);
		if (value == KeywordValue.NORMAL) {
			return 0;
		}
		return ((AbsoluteLengthValue) value).getLength();
	}

	protected RowGap() {
		super("row-gap");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NORMAL;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ValueUtils.isNormal(lu)) {
			return KeywordValue.NORMAL;
		}
		final Value value = ValueUtils.toLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
