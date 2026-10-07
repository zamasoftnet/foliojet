package net.zamasoft.foliojet.css.property;

import java.net.URI;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.CssToken;

/**
 * A custom property (--name) declaration. Unlike ordinary properties, this performs
 * no type validation and retains the declaration's raw token sequence unchanged
 * (to defer processing until var() resolution; see {@link DeferredProperty}).
 *
 * @author MIYABE Tatsuhiko
 */
public final class CustomProperty implements Property {
	private final String name;
	private final List<CssToken> tokens;
	private final URI uri;
	private final boolean important;

	public CustomProperty(String name, List<CssToken> tokens, URI uri, boolean important) {
		this.name = name;
		this.tokens = tokens;
		this.uri = uri;
		this.important = important;
	}

	public String getName() {
		return this.name;
	}

	public URI getURI() {
		return this.uri;
	}

	public boolean isImportant() {
		return this.important;
	}

	public void applyProperty(CSSStyle style) {
		style.setCustomProperty(this.name, this.tokens,
				this.important ? CSSStyle.MODE_IMPORTANT : CSSStyle.MODE_NORMAL);
	}

	public String toString() {
		return this.name + ": " + this.tokens + (this.important ? " !important" : "");
	}
}
