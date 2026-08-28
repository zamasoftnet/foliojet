package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.text.TextWrapStyle;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * CSS Text 4 の {@code text-wrap} 短縮形です(2026-07-25新設)。
 *
 * <p>
 * 品質側({@code text-wrap-style})の値
 * {@code auto}/{@code pretty}/{@code balance}/{@code stable}に加え、
 * 2026-08-29からmode側({@code text-wrap-mode})の{@code nowrap}を
 * {@code white-space: nowrap}相当として、{@code wrap}を何もしない値として
 * 受理します(実サイトで{@code text-wrap: nowrap}が使われていた)。
 * {@code white-space}との相互作用は完全ではなく、{@code nowrap}は
 * white-spaceの原始値を上書きします。
 * </p>
 *
 * <p>
 * {@code balance}/{@code stable}は{@link TextWrapStyle}と同じく
 * {@code auto}として扱います(未対応)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class TextWrapShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new TextWrapShorthand();

	protected TextWrapShorthand() {
		super("text-wrap");
	}

	@Override
	protected PrimitivePropertyInfo[] longhands() {
		return new PrimitivePropertyInfo[] { TextWrapStyle.INFO };
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives)
			throws PropertyException {
		boolean style = false, mode = false;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (!(lu instanceof CssToken.Ident ident)) {
				throw new PropertyException();
			}
			final String name = ident.lower();
			if (!mode && (name.equals("wrap") || name.equals("nowrap"))) {
				mode = true;
				if (name.equals("nowrap")) {
					primitives.set(net.zamasoft.foliojet.css.impl.property.text.WhiteSpace.INFO,
							net.zamasoft.foliojet.css.value.WhiteSpaceValue.NOWRAP_VALUE);
				}
				continue;
			}
			final Value value = TextWrapStyle.toValue(name);
			if (value == null || style) {
				throw new PropertyException();
			}
			style = true;
			primitives.set(TextWrapStyle.INFO, value);
		}
		if (!style && !mode) {
			throw new PropertyException();
		}
	}
}
