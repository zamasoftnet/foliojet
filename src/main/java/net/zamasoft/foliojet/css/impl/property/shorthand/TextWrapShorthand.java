package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.text.TextWrapStyle;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
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
 * <b>受理するのは品質側({@code text-wrap-style})の値
 * {@code auto}/{@code pretty}/{@code balance}/{@code stable}だけです。</b>
 * mode側の{@code wrap}/{@code nowrap}({@code text-wrap-mode})は受理せず
 * 宣言全体を無効にします——折り返しの可否は従来どおり
 * {@code white-space}で指定してください(この制限はcopperpdf4の
 * {@code docs/CSS-SUPPORT.md}に明記されます)。
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

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives)
			throws PropertyException {
		final CssToken lu = tokens.next();
		if (!(lu instanceof CssToken.Ident ident)) {
			throw new PropertyException();
		}
		final Value value = TextWrapStyle.toValue(ident.lower());
		if (value == null) {
			// wrap/nowrap(text-wrap-mode)を含む未対応の値
			throw new PropertyException();
		}
		primitives.set(TextWrapStyle.INFO, value);
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
	}
}
