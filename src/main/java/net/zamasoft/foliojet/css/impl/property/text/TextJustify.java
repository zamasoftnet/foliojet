package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.TextJustifyValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * CSS Text 3 の {@code text-justify} です(2026-09-02新設)。
 *
 * <p>
 * 両端揃え({@code text-align: justify})で行の余りをどこへ配るかを決める。
 * 継承する。既定の{@code auto}は<b>言語で決める</b>: 和文の行は JLREQ の
 * 段階的な配分(語間→和欧間→文字間)、韓国語({@code lang}が{@code ko})は
 * <b>語間だけ</b>——Chrome を実測すると空白だけが伸びて音節の送りは1画素も
 * 動かない(2026-09-01)——、それ以外は従来どおり分離可能な境界へ均等に配る。
 * </p>
 *
 * <p>
 * {@code inter-word}は語間だけ(語間が無い行は動かさない)、
 * {@code inter-character}(別名{@code distribute})は文字間にも配る、
 * {@code none}は両端揃えをしない。
 * </p>
 */
public class TextJustify extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextJustify();

	public static byte get(final CSSStyle style) {
		return ((TextJustifyValue) style.get(INFO)).getTextJustify();
	}

	protected TextJustify() {
		super("text-justify");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return TextJustifyValue.AUTO_VALUE;
	}

	@Override
	public boolean isInherited() {
		return true;
	}

	@Override
	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	@Override
	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			final Value value = toValue(((CssToken.Ident) lu).lower());
			if (value != null) {
				return value;
			}
		}
		throw new PropertyException();
	}

	/** 識別子を値へ。未知なら{@code null}。 */
	public static Value toValue(final String ident) {
		switch (ident) {
		case "auto":
			return TextJustifyValue.AUTO_VALUE;
		case "none":
			return TextJustifyValue.NONE_VALUE;
		case "inter-word":
			return TextJustifyValue.INTER_WORD_VALUE;
		case "inter-character":
		case "distribute":
			return TextJustifyValue.INTER_CHARACTER_VALUE;
		default:
			return null;
		}
	}
}
