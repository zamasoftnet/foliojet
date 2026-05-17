package jp.cssj.homare.xml;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

/**
 * マークアップ言語のノードを表します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AttributeNode.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class AttributeNode {
	public final String uri, lName, qName;

	public AttributeNode(String uri, String prefix, String lName) {
		assert uri != null;
		assert lName != null;
		this.uri = uri;
		this.lName = lName;
		if (prefix == null) {
			this.qName = lName;
		} else {
			this.qName = prefix + ":" + lName;
		}
	}

	public AttributeNode(String lName) {
		this("", null, lName);
	}

	public String getValue(Attributes atts) {
		if (this.uri.length() == 0) {
			return atts.getValue(this.lName);
		}
		return atts.getValue(this.uri, this.lName);
	}

	public void removeValue(AttributesImpl atts) {
		int ix = atts.getIndex(this.uri, this.lName);
		if (ix == -1) {
			return;
		}
		atts.removeAttribute(ix);
	}

	public void addValue(AttributesImpl atts, String value) {
		atts.addAttribute(this.uri, this.lName, this.qName, "CDATA", value);
	}
}