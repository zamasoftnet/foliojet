package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.css.impl.property.box.Side;

/**
 * border-top-color / border-right-color / border-bottom-color /
 * border-left-color 特性です。
 *
 * @author MIYABE Tatsuhiko
 */
public final class BorderColor extends AbstractPrimitivePropertyInfo {
	public static final BorderColor TOP = new BorderColor(Side.TOP);

	public static final BorderColor RIGHT = new BorderColor(Side.RIGHT);

	public static final BorderColor BOTTOM = new BorderColor(Side.BOTTOM);

	public static final BorderColor LEFT = new BorderColor(Side.LEFT);

	private static final BorderColor[] BY_SIDE = { TOP, RIGHT, BOTTOM, LEFT };

	private BorderColor(Side side) {
		super("border-" + side.text() + "-color");
	}

	public static net.zamasoft.pdfg2d.gc.paint.Color get(CSSStyle style, Side side) {
		final Side physical = side.resolve(style);
		Value declared = style.isDeclared(BY_SIDE[physical.ordinal()]) ? null
				: LogicalBorder.declaredFor(style, LogicalBorder.Aspect.COLOR, physical);
		Value value = declared != null ? declared : style.get(BY_SIDE[physical.ordinal()]);
		if (value == KeywordValue.TRANSPARENT) {
			return null;
		}
		return ((ColorValue) value).getColor();
	}

	/**
	 * 物理的な辺に対応するborder-colorプロパティを返します(2026-07-20、
	 * UAデフォルトスタイルの論理プロパティ一本化用)。
	 */
	public static BorderColor forSide(Side side) {
		return BY_SIDE[side.ordinal()];
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.DEFAULT;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		// **型付き attr() をここで解く**(2026-08-04)。解かずに通すと
		// BorderColor.get() の ColorValue へのキャストで落ちる
		// (<table border bordercolor> で実際に落ちた)
		value = ValueUtils.emExToAbsoluteLength(value, style);
		if (value == KeywordValue.DEFAULT || value == KeywordValue.NONE) {
			// DEFAULT は currentColor。NONE は解決できなかった attr()
			value = style.get(CSSColor.INFO);
		}
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		// 型付き attr()(2026-08-03)。属性から罫線の幅・色を取る
		final Value attrValue = net.zamasoft.foliojet.css.util.AttrValueUtils.toTypedAttr(ua, lu, net.zamasoft.foliojet.css.value.TypedAttrValue.Kind.COLOR);
		if (attrValue != null) {
			return attrValue;
		}

		if (ColorValueUtils.isTransparent(lu)) {
			return KeywordValue.TRANSPARENT;
		}
		Value value = ColorValueUtils.toColor(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
