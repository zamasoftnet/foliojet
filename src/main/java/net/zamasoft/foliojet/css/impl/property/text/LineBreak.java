package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.LineBreakValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code line-break}です(css-text-3 §5.2、2026-08-29新設)。
 *
 * <p>
 * {@code auto | loose | normal | strict | anywhere}。継承、既定
 * {@code auto}。禁則の強さは{@code LanguageProfile_ja}が
 * {@code word-break}と組み合わせて{@code JlreqBreakingRules}へ渡す。
 * {@code auto}はUA裁量(仕様)で、本実装ではJLREQの行頭・行末禁則を
 * そのまま使う{@code strict}相当——印刷物向けの既定を保つため
 * (ブラウザの{@code auto}は{@code normal}相当で、拗促音・長音の
 * 行頭を許す)。
 * </p>
 */
public class LineBreak extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new LineBreak();

	public static LineBreakValue get(final CSSStyle style) {
		return (LineBreakValue) style.get(INFO);
	}

	protected LineBreak() {
		super("line-break");
	}

	public Value getDefault(final CSSStyle style) {
		return LineBreakValue.AUTO;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken token = tokens.next();
		if (token instanceof CssToken.Ident ident && !tokens.hasNext()) {
			for (final LineBreakValue value : LineBreakValue.values()) {
				if (ident.is(value.toString())) {
					return value;
				}
			}
		}
		throw new PropertyException();
	}
}
