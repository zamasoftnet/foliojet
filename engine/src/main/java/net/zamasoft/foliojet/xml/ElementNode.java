package net.zamasoft.foliojet.xml;

import net.zamasoft.foliojet.css.CSSElement;

/**
 * マークアップ言語のノードを表します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: ElementNode.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ElementNode {
	public final String uri, lName, qName;

	public ElementNode(String uri, String prefix, String lName) {
		this.uri = uri;
		this.lName = lName;
		this.qName = prefix + ":" + lName;
	}

	public boolean equals(String uri, String lName) {
		if (lName == null) {
			return false;
		}
		return this.lName.equals(lName) && this.uri.equals(uri);
	}

	public boolean equalsElement(CSSElement ce) {
		return this.equals(ce.uri, ce.lName);
	}
}