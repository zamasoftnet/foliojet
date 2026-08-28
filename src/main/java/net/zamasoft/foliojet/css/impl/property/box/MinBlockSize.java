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
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * min-block-size 特性(論理プロパティ)です。writing-modeにより
 * min-width か min-height のいずれかに対応します(min-inline-sizeと逆軸)。
 */
public final class MinBlockSize extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MinBlockSize();

	public static Value get(CSSStyle style) {
		PrimitivePropertyInfo physicalInfo = BlockFlow.get(style).isVertical() ? MinWidth.INFO : MinHeight.INFO;
		if (style.isDeclared(physicalInfo)) {
			return style.get(physicalInfo);
		}
		if (style.isDeclared(INFO)) {
			return style.get(INFO);
		}
		return style.get(physicalInfo);
	}

	private MinBlockSize() {
		super("min-block-size");
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
		// 固有寸法キーワード max-content/min-content/fit-content(L)(2026-08-29)
		final Value intrinsic = BoxValueUtils.toIntrinsicSize(ua, lu);
		if (intrinsic != null) {
			return intrinsic;
		}
		if (ValueUtils.isAuto(lu)) {
			// auto(css-sizing-3の初期値)は通常フローでは0と等価。flex/grid
			// itemの「自動最小寸法」はレイアウト側が別途持つ(2026-08-29)
			return AbsoluteLengthValue.ZERO;
		}
		Value value = BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
