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
 * CSS Text 4 {@code text-wrap} shorthand (added 2026-07-25).
 *
 * <p>
 * In addition to quality ({@code text-wrap-style}) values
 * {@code auto}/{@code pretty}/{@code balance}/{@code stable}, since 2026-08-29
 * accepts mode ({@code text-wrap-mode}) value {@code nowrap} as equivalent to
 * {@code white-space: nowrap}, and {@code wrap} as a no-op
 * ({@code text-wrap: nowrap} appeared on real sites).
 * Interaction with {@code white-space} is incomplete; {@code nowrap} overrides
 * the white-space primitive value.
 * </p>
 *
 * <p>
 * As with {@link TextWrapStyle}, treats {@code balance}/{@code stable}
 * as {@code auto} (unsupported).
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
