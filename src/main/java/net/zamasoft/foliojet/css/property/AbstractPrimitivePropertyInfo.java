package net.zamasoft.foliojet.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * A property that cannot be decomposed.
 *
 * @author MIYABE Tatsuhiko
 *          miyabe $
 */
public abstract class AbstractPrimitivePropertyInfo extends AbstractPropertyInfo implements PrimitivePropertyInfo {

	protected AbstractPrimitivePropertyInfo(String name) {
		super(name);
	}

	public final Property parse(TokenStream tokens, UserAgent ua, URI uri, boolean important)
			throws PropertyException {
		Value value;
		KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			// inherit / initial / unset (resolved by CSSStyle.get)
			value = global;
		} else {
			value = this.parseValue(tokens, ua, uri);
		}
		return new CompositeProperty(this.getName(), new Entry[] { new Entry(this, value) }, uri, important);
	}

	public PrimitivePropertyInfo getEffectiveInfo(CSSStyle style) {
		return this;
	}

	/**
	 * Parses the declaration's token sequence as a single value.
	 *
	 * @param tokens declaration value (inherit has already been handled)
	 * @param ua
	 * @param uri
	 * @return
	 * @throws PropertyException if the value cannot be interpreted
	 */
	public abstract Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException;
}
