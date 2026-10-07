package net.zamasoft.foliojet.css.impl.property.content;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.AttrValue;
import net.zamasoft.foliojet.css.value.ContentFunctionValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * GCPM {@code bookmark-label: <content-list>} (2026-10-04).
 *
 * <p>
 * Accepts sequences of {@code <string>}, {@code content()}, {@code content(text)}
 * (both use the element's text), and {@code attr(<name>)}. The initial value is
 * {@code content(text)}, so heading text (including generated ::before content) becomes
 * the bookmark, as before. Does not accept {@code counter()}: when the box is created,
 * the element's {@code counter-increment} has not yet taken effect, so chapter numbers
 * would be off by one. Putting the number in {@code ::before} includes it in the bookmark text.
 * </p>
 */
public class BookmarkLabel extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BookmarkLabel();

	private static final ValueListValue DEFAULT = new ValueListValue(new Value[] { ContentFunctionValue.INSTANCE });

	/** Sequence of components (null for the default). */
	public static Value[] get(final CSSStyle style) {
		final Value value = style.get(INFO);
		if (value == DEFAULT) {
			return null;
		}
		return ((ValueListValue) value).getValues();
	}

	private BookmarkLabel() {
		super("bookmark-label");
	}

	public Value getDefault(final CSSStyle style) {
		return DEFAULT;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final List<Value> parts = new ArrayList<Value>();
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (lu instanceof CssToken.Str str) {
				parts.add(new StringValue(str.value()));
			} else if (lu instanceof CssToken.Func func && func.is("attr")) {
				final TokenStream params = func.argStream();
				final String attrName = params.ident();
				if (attrName == null || params.hasNext()) {
					throw new PropertyException();
				}
				parts.add(new AttrValue(attrName));
			} else if (lu instanceof CssToken.Func func && func.is("content")) {
				final TokenStream params = func.argStream();
				if (params.hasNext()) {
					final String kind = params.ident();
					if (!"text".equals(kind) || params.hasNext()) {
						throw new PropertyException();
					}
				}
				parts.add(ContentFunctionValue.INSTANCE);
			} else {
				throw new PropertyException();
			}
		}
		if (parts.isEmpty()) {
			throw new PropertyException();
		}
		return new ValueListValue(parts.toArray(new Value[parts.size()]));
	}
}
