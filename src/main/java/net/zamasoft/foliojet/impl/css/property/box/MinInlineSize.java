package net.zamasoft.foliojet.impl.css.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.text.BlockFlow;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * min-inline-size 特性(論理プロパティ)です。writing-modeにより
 * min-width か min-height のいずれかに対応します。
 */
public final class MinInlineSize extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MinInlineSize();

	public static Value get(CSSStyle style) {
		PrimitivePropertyInfo physicalInfo = BlockFlow.get(style).isVertical() ? MinHeight.INFO : MinWidth.INFO;
		if (style.isDeclared(physicalInfo)) {
			return style.get(physicalInfo);
		}
		if (style.isDeclared(INFO)) {
			return style.get(INFO);
		}
		return style.get(physicalInfo);
	}

	private MinInlineSize() {
		super("min-inline-size");
	}

	public Value getDefault(CSSStyle style) {
		return AbsoluteLengthValue.ZERO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		Value value = BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
