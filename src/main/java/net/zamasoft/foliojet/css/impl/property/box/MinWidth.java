package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class MinWidth extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MinWidth();

	public static Value get(CSSStyle style) {
		boolean image = CSSJInternalImage.getImage(style) != null;
		if (style.isDeclared(INFO)) {
			return style.get(INFO);
		}
		// 2026-07-20、-cssj-direction-mode廃止によりmin-inline-size/
		// min-block-sizeへ一本化。
		if (!image) {
			PrimitivePropertyInfo logicalInfo = BlockFlow.get(style).isVertical() ? MinBlockSize.INFO : MinInlineSize.INFO;
			if (style.isDeclared(logicalInfo)) {
				return style.get(logicalInfo);
			}
		}
		return style.get(INFO);
	}

	public static Length getLength(CSSStyle style) {
		return BoxValueUtils.toMinLength(MinWidth.get(style));
	}

	private MinWidth() {
		super("min-width");
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