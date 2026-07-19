package net.zamasoft.foliojet.impl.css.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * top / right / bottom / left 特性です。
 *
 * @author MIYABE Tatsuhiko
 */
public final class Inset extends AbstractPrimitivePropertyInfo {
	public static final Inset TOP = new Inset(Side.TOP);

	public static final Inset RIGHT = new Inset(Side.RIGHT);

	public static final Inset BOTTOM = new Inset(Side.BOTTOM);

	public static final Inset LEFT = new Inset(Side.LEFT);

	private static final Inset[] BY_SIDE = { TOP, RIGHT, BOTTOM, LEFT };

	/** inset-block-start/end・inset-inline-start/end(論理プロパティ)。 */
	public static final Inset BLOCK_START = new Inset("inset-block-start");

	public static final Inset BLOCK_END = new Inset("inset-block-end");

	public static final Inset INLINE_START = new Inset("inset-inline-start");

	public static final Inset INLINE_END = new Inset("inset-inline-end");

	private static final Inset[] BY_LOGICAL_SIDE = { BLOCK_START, BLOCK_END, INLINE_START, INLINE_END };

	private Inset(Side side) {
		super(side.text());
	}

	private Inset(String name) {
		super(name);
	}

	public static Value get(CSSStyle style, Side side) {
		return LogicalSide.resolve(style, side, BY_SIDE, BY_LOGICAL_SIDE);
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
		final Value value = BoxValueUtils.toTRLB(ua, lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}
}
