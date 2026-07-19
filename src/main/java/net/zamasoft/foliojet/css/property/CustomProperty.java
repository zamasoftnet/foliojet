package net.zamasoft.foliojet.css.property;

import java.net.URI;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.CssToken;

/**
 * カスタムプロパティ(--name)の宣言です。通常のプロパティと異なり型検証を
 * 一切行わず、宣言値の生トークン列をそのまま保持します(var()の解決まで
 * 遅延するため。{@link DeferredProperty}参照)。
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
