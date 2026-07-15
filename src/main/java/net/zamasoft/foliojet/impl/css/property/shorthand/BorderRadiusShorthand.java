package net.zamasoft.foliojet.impl.css.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.css3.BorderRadiusValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.impl.css.property.border.BorderRadius;

/**
 * @author MIYABE Tatsuhiko
 */
public class BorderRadiusShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderRadiusShorthand();

	protected BorderRadiusShorthand() {
		super("border-radius");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (tokens.isInherit()) {
			primitives.set(BorderRadius.TOP_LEFT, KeywordValue.INHERIT);
			primitives.set(BorderRadius.TOP_RIGHT, KeywordValue.INHERIT);
			primitives.set(BorderRadius.BOTTOM_RIGHT, KeywordValue.INHERIT);
			primitives.set(BorderRadius.BOTTOM_LEFT, KeywordValue.INHERIT);
			return;
		}
		final LengthValue tlh, trh, brh, blh;

		tlh = ValueUtils.toLength(ua, tokens.next());
		if (tlh == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			final BorderRadiusValue tl = BorderRadiusValue.create(tlh, tlh);
			primitives.set(BorderRadius.TOP_LEFT, tl);
			primitives.set(BorderRadius.TOP_RIGHT, tl);
			primitives.set(BorderRadius.BOTTOM_RIGHT, tl);
			primitives.set(BorderRadius.BOTTOM_LEFT, tl);
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
			primitives.set(BorderRadius.TOP_LEFT, tl);
			primitives.set(BorderRadius.TOP_RIGHT, tr);
			primitives.set(BorderRadius.BOTTOM_RIGHT, tl);
			primitives.set(BorderRadius.BOTTOM_LEFT, tr);
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
			primitives.set(BorderRadius.TOP_LEFT, tl);
			primitives.set(BorderRadius.TOP_RIGHT, tr);
			primitives.set(BorderRadius.BOTTOM_RIGHT, br);
			primitives.set(BorderRadius.BOTTOM_LEFT, tr);
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
			primitives.set(BorderRadius.TOP_LEFT, tl);
			primitives.set(BorderRadius.TOP_RIGHT, tr);
			primitives.set(BorderRadius.BOTTOM_RIGHT, br);
			primitives.set(BorderRadius.BOTTOM_LEFT, bl);
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
		primitives.set(BorderRadius.TOP_LEFT, tl);
		primitives.set(BorderRadius.TOP_RIGHT, tr);
		primitives.set(BorderRadius.BOTTOM_RIGHT, br);
		primitives.set(BorderRadius.BOTTOM_LEFT, bl);
	}
}