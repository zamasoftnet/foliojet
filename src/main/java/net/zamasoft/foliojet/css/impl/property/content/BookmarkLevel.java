package net.zamasoft.foliojet.css.impl.property.content;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.IntegerValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.BookmarkSpec;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * GCPM {@code bookmark-level: auto | none | <integer>}(2026-10-04)。
 *
 * <p>
 * 仕様の初期値は{@code none}だが、Copper は h1〜h6 から段数を取って
 * しおりを作ってきたので、初期値を{@code auto}(文書の見出しの段数に従う、
 * Copper の拡張)にする。{@code none}で見出しをしおりから外し、整数で
 * 見出しでない要素もしおりにできる。
 * </p>
 */
public class BookmarkLevel extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BookmarkLevel();

	/** 0 は none、{@link BookmarkSpec#LEVEL_AUTO}は auto。 */
	public static int get(final CSSStyle style) {
		final Value value = style.get(INFO);
		if (value == KeywordValue.AUTO) {
			return BookmarkSpec.LEVEL_AUTO;
		}
		if (value == KeywordValue.NONE) {
			return 0;
		}
		return ((IntegerValue) value).getInteger();
	}

	private BookmarkLevel() {
		super("bookmark-level");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		if (lu instanceof CssToken.Ident ident) {
			if (ident.is("auto")) {
				return KeywordValue.AUTO;
			}
			if (ident.is("none")) {
				return KeywordValue.NONE;
			}
		} else if (lu instanceof CssToken.Num num && num.integer() && num.intValue() >= 1) {
			return IntegerValue.create(num.intValue());
		}
		throw new PropertyException();
	}
}
