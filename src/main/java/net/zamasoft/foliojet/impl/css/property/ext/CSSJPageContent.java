package net.zamasoft.foliojet.impl.css.property.ext;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractCompositePrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJPageContentValue;
import net.zamasoft.foliojet.layout.util.ByteList;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSJPageContent extends AbstractCompositePrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO_NAME = new CSSJPageContent();
	public static final PrimitivePropertyInfo INFO_PAGE = new CSSJPageContent();

	private static final PrimitivePropertyInfo[] PRIMITIVES = { INFO_NAME, INFO_PAGE };

	public static String getName(CSSStyle style) {
		Value value = style.get(INFO_NAME);
		if (value == KeywordValue.NONE) {
			return null;
		}
		return ((StringValue) value).getString();
	}

	public static byte[] getPages(CSSStyle style) {
		Value value = style.get(INFO_PAGE);
		if (value == KeywordValue.NONE) {
			return null;
		}
		return ((CSSJPageContentValue) value).getPages();
	}

	private CSSJPageContent() {
		super("-cssj-page-content");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	protected PrimitivePropertyInfo[] getPrimitives() {
		return PRIMITIVES;
	}

	protected Entry[] parseValues(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.isInherit()) {
			return new Entry[] { new Entry(CSSJPageContent.INFO_NAME, KeywordValue.INHERIT),
					new Entry(CSSJPageContent.INFO_PAGE, KeywordValue.INHERIT) };
		}
		final Value name, page;
		{
			final CssToken lu = tokens.next();
			if (lu instanceof CssToken.Ident ident) {
				if (ident.is("none")) {
					name = KeywordValue.NONE;
				} else {
					name = new StringValue(ident.name());
				}
			} else if (lu instanceof CssToken.Str str) {
				name = new StringValue(str.value());
			} else {
				throw new PropertyException();
			}
		}
		{
			if (!tokens.hasNext()) {
				page = KeywordValue.NONE;
			} else {
				ByteList list = new ByteList();
				do {
					final String ident = tokens.ident();
					if (ident == null) {
						throw new PropertyException();
					}
					switch (ident.toLowerCase()) {
					case "first":
						list.add(CSSElement.PC_FIRST);
						break;
					case "right":
						list.add(CSSElement.PC_RIGHT);
						break;
					case "left":
						list.add(CSSElement.PC_LEFT);
						break;
					case "single":
						list.add((byte) 0);
						break;
					default:
						throw new PropertyException();
					}
				} while (tokens.hasNext());
				page = new CSSJPageContentValue(list.toArray());
			}
		}
		return new Entry[] { new Entry(CSSJPageContent.INFO_NAME, name), new Entry(CSSJPageContent.INFO_PAGE, page) };
	}
}