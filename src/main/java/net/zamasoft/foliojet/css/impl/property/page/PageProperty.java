package net.zamasoft.foliojet.css.impl.property.page;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code page}プロパティです(名前付きページN1b、2026-07-31——
 * consult-codex-2026-07-31-named-pages.txt Q1)。値は
 * {@code auto | <custom-ident>}。非継承だが、used valueは最も近い
 * 非autoの祖先から伝播する(CSS Page 3——通常の継承フラグではなく
 * {@link #getUsed}で解決する)。名前はCSS識別子として大文字小文字を
 * 区別し、{@code auto}のみ予約。
 *
 * @author MIYABE Tatsuhiko
 */
public class PageProperty extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new PageProperty();

	/**
	 * used value(最も近い非autoの祖先のページ名)です。無ければnull
	 * (=無名ページ)。
	 */
	public static String getUsed(CSSStyle style) {
		for (CSSStyle s = style; s != null; s = s.getParentStyle()) {
			final Value value = s.get(INFO);
			if (value != KeywordValue.AUTO) {
				return ((StringValue) value).getString();
			}
		}
		return null;
	}

	protected PageProperty() {
		super("page");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.eat("auto")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return KeywordValue.AUTO;
		}
		final String name = tokens.ident();
		if (name == null || tokens.hasNext()) {
			throw new PropertyException();
		}
		return new StringValue(name);
	}
}
