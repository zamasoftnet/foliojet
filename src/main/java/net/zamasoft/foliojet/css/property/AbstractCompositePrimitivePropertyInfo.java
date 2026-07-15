package net.zamasoft.foliojet.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * 複合特性です。
 * 
 * @author MIYABE Tatsuhiko
 *          07:03:19Z miyabe $
 */
public abstract class AbstractCompositePrimitivePropertyInfo extends AbstractPropertyInfo
		implements PrimitivePropertyInfo {

	protected AbstractCompositePrimitivePropertyInfo(String name) {
		super(name);
	}

	protected abstract PrimitivePropertyInfo[] getPrimitives();

	protected abstract Entry[] parseValues(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException;

	public final Property parse(TokenStream tokens, UserAgent ua, URI uri, boolean important)
			throws PropertyException {
		Entry[] entries;
		if (tokens.isInherit()) {
			// 継承
			PrimitivePropertyInfo[] primitives = this.getPrimitives();
			entries = new Entry[primitives.length];
			for (int i = 0; i < entries.length; ++i) {
				entries[i] = new Entry(primitives[i], KeywordValue.INHERIT);
			}
		} else {
			entries = this.parseValues(tokens, ua, uri);
		}
		return new CompositeProperty(this.getName(), entries, uri, important);
	}

	public PrimitivePropertyInfo getEffectiveInfo(CSSStyle style) {
		return this;
	}
}