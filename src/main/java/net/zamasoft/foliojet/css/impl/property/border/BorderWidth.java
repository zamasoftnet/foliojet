package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.ua.BorderWidthKeyword;

/**
 * border-top-width / border-right-width / border-bottom-width /
 * border-left-width 特性です。
 *
 * @author MIYABE Tatsuhiko
 */
public final class BorderWidth extends AbstractPrimitivePropertyInfo {
	public static final BorderWidth TOP = new BorderWidth(Side.TOP);

	public static final BorderWidth RIGHT = new BorderWidth(Side.RIGHT);

	public static final BorderWidth BOTTOM = new BorderWidth(Side.BOTTOM);

	public static final BorderWidth LEFT = new BorderWidth(Side.LEFT);

	private static final BorderWidth[] BY_SIDE = { TOP, RIGHT, BOTTOM, LEFT };

	private BorderWidth(Side side) {
		super("border-" + side.text() + "-width");
	}

	public static double get(CSSStyle style, Side side) {
		return ((AbsoluteLengthValue) style.get(BY_SIDE[side.resolve(style).ordinal()])).getLength();
	}

	/**
	 * 物理的な辺に対応するborder-widthプロパティを返します(2026-07-20、
	 * UAデフォルトスタイルの論理プロパティ一本化用)。
	 */
	public static BorderWidth forSide(Side side) {
		return BY_SIDE[side.ordinal()];
	}

	public Value getDefault(CSSStyle style) {
		return style.getUserAgent().getBorderWidth(BorderWidthKeyword.MEDIUM);
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		LengthValue value = BorderValueUtils.toBorderWidth(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
