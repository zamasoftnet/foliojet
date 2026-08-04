package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.value.BorderStyleValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.impl.property.box.Side;

/**
 * border-top-style / border-right-style / border-bottom-style /
 * border-left-style 特性です。
 *
 * @author MIYABE Tatsuhiko
 */
public final class BorderStyle extends AbstractPrimitivePropertyInfo {
	public static final BorderStyle TOP = new BorderStyle(Side.TOP);

	public static final BorderStyle RIGHT = new BorderStyle(Side.RIGHT);

	public static final BorderStyle BOTTOM = new BorderStyle(Side.BOTTOM);

	public static final BorderStyle LEFT = new BorderStyle(Side.LEFT);

	private static final BorderStyle[] BY_SIDE = { TOP, RIGHT, BOTTOM, LEFT };

	private BorderStyle(Side side) {
		super("border-" + side.text() + "-style");
	}

	public static short get(CSSStyle style, Side side) {
		final Side physical = side.resolve(style);
		Value declared = style.isDeclared(BY_SIDE[physical.ordinal()]) ? null
				: LogicalBorder.declaredFor(style, LogicalBorder.Aspect.STYLE, physical);
		BorderStyleValue value = (BorderStyleValue) (declared != null ? declared
				: style.get(BY_SIDE[physical.ordinal()]));
		return value.getBorderStyle();
	}

	/**
	 * 物理的な辺に対応するborder-styleプロパティを返します(2026-07-20、
	 * UAデフォルトスタイルの論理プロパティ一本化用)。
	 */
	public static BorderStyle forSide(Side side) {
		return BY_SIDE[side.ordinal()];
	}

	public Value getDefault(CSSStyle style) {
		return BorderStyleValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		Value value = BorderValueUtils.toBorderStyle(lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
