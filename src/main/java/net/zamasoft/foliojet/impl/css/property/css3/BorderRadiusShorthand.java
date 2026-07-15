package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.InheritValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.BorderRadiusValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderRadiusShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderRadiusShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderRadiusShorthand();

	protected BorderRadiusShorthand() {
		super("border-radius");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (tokens.isInherit()) {
			primitives.set(BorderTopLeftRadius.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderTopRightRadius.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderBottomRightRadius.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderBottomLeftRadius.INFO, InheritValue.INHERIT_VALUE);
			return;
		}
		final LengthValue tlh, trh, brh, blh;

		tlh = ValueUtils.toLength(ua, tokens.next());
		if (tlh == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			final BorderRadiusValue tl = BorderRadiusValue.create(tlh, tlh);
			primitives.set(BorderTopLeftRadius.INFO, tl);
			primitives.set(BorderTopRightRadius.INFO, tl);
			primitives.set(BorderBottomRightRadius.INFO, tl);
			primitives.set(BorderBottomLeftRadius.INFO, tl);
			return;
		}
		if (tokens.eatSlash()) {
			trh = brh = blh = tlh;
			parseVertical(tokens, ua, primitives, tlh, trh, brh, blh);
			return;
		}
		trh = ValueUtils.toLength(ua, tokens.next());
		if (trh == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			final BorderRadiusValue tl = BorderRadiusValue.create(tlh, tlh);
			final BorderRadiusValue tr = BorderRadiusValue.create(trh, trh);
			primitives.set(BorderTopLeftRadius.INFO, tl);
			primitives.set(BorderTopRightRadius.INFO, tr);
			primitives.set(BorderBottomRightRadius.INFO, tl);
			primitives.set(BorderBottomLeftRadius.INFO, tr);
			return;
		}
		if (tokens.eatSlash()) {
			brh = tlh;
			blh = trh;
			parseVertical(tokens, ua, primitives, tlh, trh, brh, blh);
			return;
		}
		brh = ValueUtils.toLength(ua, tokens.next());
		if (brh == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			final BorderRadiusValue tl = BorderRadiusValue.create(tlh, tlh);
			final BorderRadiusValue tr = BorderRadiusValue.create(trh, trh);
			final BorderRadiusValue br = BorderRadiusValue.create(brh, brh);
			primitives.set(BorderTopLeftRadius.INFO, tl);
			primitives.set(BorderTopRightRadius.INFO, tr);
			primitives.set(BorderBottomRightRadius.INFO, br);
			primitives.set(BorderBottomLeftRadius.INFO, tr);
			return;
		}
		if (tokens.eatSlash()) {
			blh = trh;
			parseVertical(tokens, ua, primitives, tlh, trh, brh, blh);
			return;
		}
		blh = ValueUtils.toLength(ua, tokens.next());
		if (blh == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			final BorderRadiusValue tl = BorderRadiusValue.create(tlh, tlh);
			final BorderRadiusValue tr = BorderRadiusValue.create(trh, trh);
			final BorderRadiusValue br = BorderRadiusValue.create(brh, brh);
			final BorderRadiusValue bl = BorderRadiusValue.create(blh, blh);
			primitives.set(BorderTopLeftRadius.INFO, tl);
			primitives.set(BorderTopRightRadius.INFO, tr);
			primitives.set(BorderBottomRightRadius.INFO, br);
			primitives.set(BorderBottomLeftRadius.INFO, bl);
			return;
		}
		if (tokens.eatSlash()) {
			parseVertical(tokens, ua, primitives, tlh, trh, brh, blh);
			return;
		}
		throw new PropertyException();
	}

	private void parseVertical(TokenStream tokens, final UserAgent ua, final Primitives primitives,
			final LengthValue tlh, final LengthValue trh, final LengthValue brh, final LengthValue blh)
			throws PropertyException {
		if (!tokens.hasNext()) {
			throw new PropertyException();
		}
		final LengthValue tlv, trv, brv, blv;
		tlv = ValueUtils.toLength(ua, tokens.next());
		if (tlv == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			trv = brv = blv = tlv;
		} else {
			trv = ValueUtils.toLength(ua, tokens.next());
			if (trv == null) {
				throw new PropertyException();
			}
			if (!tokens.hasNext()) {
				brv = tlv;
				blv = trv;
			} else {
				brv = ValueUtils.toLength(ua, tokens.next());
				if (brv == null) {
					throw new PropertyException();
				}
				if (!tokens.hasNext()) {
					blv = trv;
				} else {
					blv = ValueUtils.toLength(ua, tokens.next());
					if (blv == null) {
						throw new PropertyException();
					}
					if (tokens.hasNext()) {
						throw new PropertyException();
					}
				}
			}
		}
		final BorderRadiusValue tl = BorderRadiusValue.create(tlh, tlv);
		final BorderRadiusValue tr = BorderRadiusValue.create(trh, trv);
		final BorderRadiusValue br = BorderRadiusValue.create(brh, brv);
		final BorderRadiusValue bl = BorderRadiusValue.create(blh, blv);
		primitives.set(BorderTopLeftRadius.INFO, tl);
		primitives.set(BorderTopRightRadius.INFO, tr);
		primitives.set(BorderBottomRightRadius.INFO, br);
		primitives.set(BorderBottomLeftRadius.INFO, bl);
	}
}