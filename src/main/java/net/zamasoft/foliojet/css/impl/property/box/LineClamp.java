package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.IntegerValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code line-clamp} / {@code -webkit-line-clamp}(css-overflow-4、2026-08-29)。
 *
 * <p>
 * 「N行で切って以降を隠す」指定。50サイト中24サイトが抜粋や見出しの省略に
 * 使う({@code display:-webkit-box; -webkit-box-orient:vertical;
 * -webkit-line-clamp:3; overflow:hidden} の定番)。行数で切る機構は無いので、
 * {@code BoxStyleMapper}で高さの上限 N×line-height と overflow:hidden へ
 * 近似する。省略記号は付かない。捨てると抜粋の全文が露出して後続に重なる。
 * </p>
 */
public class LineClamp extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new LineClamp();

	/** 行数。none なら0。 */
	public static int get(final CSSStyle style) {
		final Value value = style.get(INFO);
		return value instanceof IntegerValue integer ? integer.getInteger() : 0;
	}

	protected LineClamp() {
		super("line-clamp");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		if (ValueUtils.isNone(lu)) {
			return KeywordValue.NONE;
		}
		if (lu instanceof CssToken.Num num && num.integer() && num.intValue() >= 1) {
			return IntegerValue.create(num.intValue());
		}
		throw new PropertyException();
	}
}
