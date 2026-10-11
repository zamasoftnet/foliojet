package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * field-sizing (css-ui-4 §6, 2026-10-11): {@code content} sizes a form control to its content instead of its natural
 * size (an input's size, a textarea's cols and rows). Held as {@link KeywordValue#FIT_CONTENT} for {@code content} and
 * {@link KeywordValue#NONE} for {@code fixed}, the initial value. Chrome: plantuml's textareas with rows="20" are as high
 * as their lines and as wide as the longest.
 */
public class FieldSizing extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FieldSizing();

	/** Whether the control takes the size of its content. */
	public static boolean isContent(final CSSStyle style) {
		return style.get(INFO) == KeywordValue.FIT_CONTENT;
	}

	protected FieldSizing() {
		super("field-sizing");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident) {
			switch (ident.lower()) {
			case "content":
				return KeywordValue.FIT_CONTENT;
			case "fixed":
				return KeywordValue.NONE;
			default:
				break;
			}
		}
		throw new PropertyException();
	}
}
