package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.css3.BorderRadiusValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.impl.property.border.BorderRadius;

/**
 * @author MIYABE Tatsuhiko
 */
public class BorderRadiusShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderRadiusShorthand();

	protected BorderRadiusShorthand() {
		super("border-radius");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(BorderRadius.TOP_LEFT, global);
			primitives.set(BorderRadius.TOP_RIGHT, global);
			primitives.set(BorderRadius.BOTTOM_RIGHT, global);
			primitives.set(BorderRadius.BOTTOM_LEFT, global);
			return;
		}
		final QuantityValue tlh, trh, brh, blh;

		tlh = BorderValueUtils.toRadiusComponent(ua, tokens.next());
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
		trh = BorderValueUtils.toRadiusComponent(ua, tokens.next());
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
		brh = BorderValueUtils.toRadiusComponent(ua, tokens.next());
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
		blh = BorderValueUtils.toRadiusComponent(ua, tokens.next());
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
			final QuantityValue tlh, final QuantityValue trh, final QuantityValue brh, final QuantityValue blh)
			throws PropertyException {
		if (!tokens.hasNext()) {
			throw new PropertyException();
		}
		final QuantityValue tlv, trv, brv, blv;
		tlv = BorderValueUtils.toRadiusComponent(ua, tokens.next());
		if (tlv == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			trv = brv = blv = tlv;
		} else {
			trv = BorderValueUtils.toRadiusComponent(ua, tokens.next());
			if (trv == null) {
				throw new PropertyException();
			}
			if (!tokens.hasNext()) {
				brv = tlv;
				blv = trv;
			} else {
				brv = BorderValueUtils.toRadiusComponent(ua, tokens.next());
				if (brv == null) {
					throw new PropertyException();
				}
				if (!tokens.hasNext()) {
					blv = trv;
				} else {
					blv = BorderValueUtils.toRadiusComponent(ua, tokens.next());
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