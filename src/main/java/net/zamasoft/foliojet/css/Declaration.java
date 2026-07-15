package net.zamasoft.foliojet.css;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.property.Property;

/**
 * スタイル宣言です。
 * 
 * <p>
 * スタイル宣言とはCSSの特性を列挙した部分です。
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 */
public class Declaration {
	private final List<Property> properties = new ArrayList<Property>();

	/**
	 * スタイル宣言を合成します。
	 * 
	 * @param declaration
	 *            追加するスタイル宣言。nullの場合は何もしません。
	 */
	public void merge(Declaration declaration) {
		if (declaration == null) {
			return;
		}
		for (int i = 0; i < declaration.getLength(); ++i) {
			Property property = declaration.get(i);
			this.addProperty(property);
		}
	}

	/**
	 * 特性を追加します。
	 * 
	 * @param property
	 */
	public void addProperty(Property property) {
		this.properties.add(property);
	}

	public Property get(int i) {
		return (Property) this.properties.get(i);
	}

	public int getLength() {
		return this.properties.size();
	}

	/**
	 * 特性を先頭から順に適用します。
	 * 
	 * @param style
	 */
	public void applyProperties(CSSStyle style) {
		for (int i = 0; i < this.properties.size(); ++i) {
			Property property = (Property) this.properties.get(i);
			property.applyProperty(style);
		}
	}

	public String toString() {
		StringBuilder buff = new StringBuilder();
		for (int i = 0; i < this.properties.size(); ++i) {
			buff.append(this.properties.get(i));
			buff.append(";\n");
		}
		return buff.toString();
	}
}
