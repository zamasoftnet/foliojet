package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.box.LogicalSide;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.BorderStyleValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * <b>論理境界プロパティ</b>です(2026-08-03新設)——
 * {@code border-block-start-*} / {@code border-block-end-*} /
 * {@code border-inline-start-*} / {@code border-inline-end-*} の12個。
 *
 * <p>
 * 書字方向によってどの物理辺になるかが変わる({@link LogicalSide})。
 * {@code block-size}/{@code inline-size}と同じ扱いで、<b>物理側が宣言されて
 * いればそちらが勝つ</b>——CSSの規定では出現順で決まるが、この実装は
 * 既存の論理寸法プロパティに合わせる(既知の逸脱)。
 *
 * <p>
 * <b>なぜ要るか</b>: HTMLの{@code <hr noshade>}は「block方向の終端側だけに
 * 罫線を引く」という指定で、これを表現する語彙がCSS側に無かったためJavaに
 * 残っていた(2026-08-03のHTMLStyle移送で判明)。論理プロパティ自体は
 * Baselineに入っている標準機能で、利用者にも直接役立つ。
 */
public final class LogicalBorder extends AbstractPrimitivePropertyInfo {
	/** 何を指定するか。 */
	public enum Aspect {
		WIDTH("width"), STYLE("style"), COLOR("color");

		final String text;

		Aspect(String text) {
			this.text = text;
		}
	}

	private static final LogicalBorder[][] BY_ASPECT_SIDE = new LogicalBorder[Aspect.values().length][LogicalSide
			.values().length];

	static {
		for (final Aspect aspect : Aspect.values()) {
			for (final LogicalSide side : LogicalSide.values()) {
				BY_ASPECT_SIDE[aspect.ordinal()][side.ordinal()] = new LogicalBorder(aspect, side);
			}
		}
	}

	public static LogicalBorder of(Aspect aspect, LogicalSide side) {
		return BY_ASPECT_SIDE[aspect.ordinal()][side.ordinal()];
	}

	/** 登録用に全12個を返します。 */
	public static LogicalBorder[] all() {
		final LogicalBorder[] all = new LogicalBorder[Aspect.values().length * LogicalSide.values().length];
		int i = 0;
		for (final Aspect aspect : Aspect.values()) {
			for (final LogicalSide side : LogicalSide.values()) {
				all[i++] = of(aspect, side);
			}
		}
		return all;
	}

	/**
	 * 物理辺{@code side}に対応する論理プロパティが宣言されていればそれを返し、
	 * 無ければnull。物理側の宣言が優先されるかは呼び出し側が決める。
	 */
	public static Value declaredFor(CSSStyle style, Aspect aspect, Side side) {
		for (final LogicalSide logical : LogicalSide.values()) {
			if (logical.toPhysical(style) != side) {
				continue;
			}
			final PrimitivePropertyInfo info = of(aspect, logical);
			if (style.isDeclared(info)) {
				return style.get(info);
			}
		}
		return null;
	}

	private final Aspect aspect;

	private LogicalBorder(Aspect aspect, LogicalSide side) {
		super("border-" + text(side) + "-" + aspect.text);
		this.aspect = aspect;
	}

	private static String text(LogicalSide side) {
		switch (side) {
		case BLOCK_START:
			return "block-start";
		case BLOCK_END:
			return "block-end";
		case INLINE_START:
			return "inline-start";
		default:
			return "inline-end";
		}
	}

	public Value getDefault(CSSStyle style) {
		switch (this.aspect) {
		case STYLE:
			return BorderStyleValue.NONE_VALUE;
		case COLOR:
			return KeywordValue.NONE;
		default:
			return net.zamasoft.foliojet.css.value.AbsoluteLengthValue.ZERO;
		}
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return this.aspect == Aspect.WIDTH ? ValueUtils.emExToAbsoluteLength(value, style) : value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		// 型付き attr()(2026-08-03)。属性から罫線の幅・色を取る
		final Value attrValue = net.zamasoft.foliojet.css.util.AttrValueUtils.toTypedAttr(ua, lu, this.aspect == Aspect.COLOR ? net.zamasoft.foliojet.css.value.TypedAttrValue.Kind.COLOR
						: net.zamasoft.foliojet.css.value.TypedAttrValue.Kind.LENGTH);
		if (attrValue != null) {
			return attrValue;
		}
		final Value value;
		switch (this.aspect) {
		case STYLE:
			value = BorderValueUtils.toBorderStyle(lu);
			break;
		case COLOR:
			value = ColorValueUtils.toColor(ua, lu);
			break;
		default: {
			final LengthValue width = BorderValueUtils.toBorderWidth(ua, lu);
			value = width;
			break;
		}
		}
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
