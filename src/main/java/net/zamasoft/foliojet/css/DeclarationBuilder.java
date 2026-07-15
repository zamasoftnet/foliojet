package net.zamasoft.foliojet.css;

import java.net.URI;
import java.util.List;

import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.property.PropertySet;
import net.zamasoft.foliojet.css.parser.CSSException;
import net.zamasoft.foliojet.css.parser.InputSource;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.parser.StyleSheetHandler;
import net.zamasoft.foliojet.css.selector.Selector;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 解析イベントからDeclarationオブジェクトを構築します。
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: DeclarationBuilder.java 1554 2018-04-26 03:34:02Z miyabe $
 */
public class DeclarationBuilder implements StyleSheetHandler {
	private final UserAgent ua;

	private PropertySet propertySet;

	private Declaration declaration;

	private URI uri;

	public DeclarationBuilder(UserAgent ua) {
		assert ua != null;
		this.ua = ua;
	}

	/**
	 * 宣言の存在するスタイルシートのURIを設定します。
	 *
	 * @param uri
	 */
	public void setURI(URI uri) {
		assert uri != null;
		this.uri = uri;
	}

	public URI getURI() {
		return this.uri;
	}

	public void setPropertySet(PropertySet propertySet) {
		assert propertySet != null;
		this.propertySet = propertySet;
	}

	public void setDeclaration(Declaration declaration) {
		this.declaration = declaration;
	}

	public Declaration getDeclaration() {
		return this.declaration;
	}

	public void startDocument(InputSource source) throws CSSException {
		// ignore
	}

	public void endDocument(InputSource source) throws CSSException {
		// ignore
	}

	public void importStyle(String href, String mediaTypes) throws CSSException {
		// ignore
	}

	public void startMedia(List<String> mediaTypes) throws CSSException {
		// ignore
	}

	public void endMedia() throws CSSException {
		// ignore
	}

	public void startPage(String name, String pseudoPage) throws CSSException {
		// ignore
	}

	public void endPage(String name, String pseudoPage) throws CSSException {
		// ignore
	}

	public void startFontFace() throws CSSException {
		// ignore
	}

	public void endFontFace() throws CSSException {
		// ignore
	}

	public void startSelector(List<Selector> selectors) throws CSSException {
		// ignore
	}

	public void endSelector(List<Selector> selectors) throws CSSException {
		// ignore
	}

	public void property(String name, java.util.List<CssToken> value, boolean important) throws CSSException {
		assert name != null && this.uri != null && value != null;
		if (this.inProperMedia()) {
			Property property = this.propertySet.parseDeclaration(name, value, this.ua, this.uri, important);
			if (property == null) {
				return;
			}
			if (this.declaration == null) {
				this.declaration = new Declaration();
			}
			this.declaration.addProperty(property);
			// System.out.println(property);
		}
	}

	protected boolean inProperMedia() {
		return true;
	}
}
