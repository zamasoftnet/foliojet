package net.zamasoft.foliojet.css.impl.property.page;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.PageMarksValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code @page { marks }} (CSS Paged Media 3, 2026-08-02).
 * Values are {@code none | [crop || cross]}.
 *
 * <p>
 * <b>Defaults to following the I/O property</b> (the same idea as
 * {@code size: auto} delegating to {@code output.page-width/height}).
 * Overrides {@code output.marks} only when explicitly specified in CSS.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class PageMarks extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new PageMarks();

	public static PageMarksValue get(final CSSStyle style) {
		return (PageMarksValue) style.get(INFO);
	}

	protected PageMarks() {
		super("marks");
	}

	public Value getDefault(final CSSStyle style) {
		return PageMarksValue.UNSPECIFIED;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		boolean crop = false;
		boolean cross = false;
		boolean none = false;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (!(lu instanceof CssToken.Ident ident)) {
				throw new PropertyException();
			}
			switch (ident.lower()) {
			case "none" -> none = true;
			case "crop" -> crop = true;
			case "cross" -> cross = true;
			default -> throw new PropertyException();
			}
		}
		if (none && (crop || cross)) {
			throw new PropertyException();
		}
		if (none) {
			return PageMarksValue.NONE;
		}
		if (crop && cross) {
			return PageMarksValue.BOTH;
		}
		if (crop) {
			return PageMarksValue.CROP;
		}
		if (cross) {
			return PageMarksValue.CROSS;
		}
		throw new PropertyException();
	}
}
