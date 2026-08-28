package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * inline-size 特性(論理プロパティ)です。writing-modeにより width か
 * height のいずれかに対応します。
 */
public final class InlineSize extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new InlineSize();

	public static Value get(CSSStyle style) {
		PrimitivePropertyInfo physicalInfo = BlockFlow.get(style).isVertical() ? Height.INFO : Width.INFO;
		if (style.isDeclared(physicalInfo)) {
			return style.get(physicalInfo);
		}
		if (style.isDeclared(INFO)) {
			return style.get(INFO);
		}
		return style.get(physicalInfo);
	}

	private InlineSize() {
		super("inline-size");
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
		// 固有寸法キーワード max-content/min-content/fit-content(L)(2026-08-29)
		final Value intrinsic = BoxValueUtils.toIntrinsicSize(ua, lu);
		if (intrinsic != null) {
			return intrinsic;
		}
		Value value = BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
