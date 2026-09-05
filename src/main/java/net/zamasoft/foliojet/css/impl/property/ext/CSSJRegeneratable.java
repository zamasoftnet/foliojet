package net.zamasoft.foliojet.css.impl.property.ext;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.IntegerValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.ua.UserAgent;

/** 全頁を対象とする -cssj-page-content の別名です。頁ごとの再カスケードは行いません。 */
public final class CSSJRegeneratable extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new CSSJRegeneratable();

	private CSSJRegeneratable() {
		super("-cssj-regeneratable");
	}

	protected PrimitivePropertyInfo[] longhands() {
		return new PrimitivePropertyInfo[] { CSSJPageContent.INFO_NAME, CSSJPageContent.INFO_PAGES };
	}

	public void parseValues(final TokenStream tokens, final UserAgent ua, final URI uri,
			final Primitives primitives) throws PropertyException {
		final String name = CSSJPageContent.parseName(tokens);
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		primitives.set(CSSJPageContent.INFO_NAME, name == null ? KeywordValue.NONE : new StringValue(name));
		primitives.set(CSSJPageContent.INFO_PAGES, IntegerValue.ZERO);
	}
}
