package net.zamasoft.foliojet.css;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.property.CustomProperty;
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
	 * <p>
	 * カスタムプロパティ({@link CustomProperty})は、その他のプロパティ
	 * (var()を参照しうる)より必ず先に適用する2パス構成にする——CSS仕様上、
	 * var()による置換は要素の全カスタムプロパティが確定した後の使用値計算
	 * 時に行われるべきもので、同一要素内でたまたま出現順(カスケード順)が
	 * 逆(var()を使う宣言の方が先に適用される順序)になっただけで参照先が
	 * 見えなくなるのは仕様に反するため。1パス目・2パス目それぞれの内部の
	 * 相対順序(カスケード順)は保つ。
	 * </p>
	 *
	 * @param style
	 */
	public void applyProperties(CSSStyle style) {
		for (int i = 0; i < this.properties.size(); ++i) {
			Property property = (Property) this.properties.get(i);
			if (property instanceof CustomProperty) {
				property.applyProperty(style);
			}
		}
		for (int i = 0; i < this.properties.size(); ++i) {
			Property property = (Property) this.properties.get(i);
			if (!(property instanceof CustomProperty)) {
				property.applyProperty(style);
			}
		}
	}

	/**
	 * {@code !important}が付いた宣言だけを適用します(2026-08-03新設)。
	 *
	 * <p>
	 * {@code @layer}と{@code !important}を併用したときの<b>優先順位の反転</b>
	 * (CSS Cascade 5——importantどうしではレイヤー外が最弱・先のレイヤーほど
	 * 強い)を表すために使います。通常の順序で一度カスケードを適用したあと、
	 * important宣言だけを<b>反転した順序でもう一度</b>適用する
	 * ({@link CSSStyle#set}はimportantどうしなら後勝ちなので、最も強い
	 * important宣言が最後に載る)。normal宣言を二度適用しないよう、ここでは
	 * important以外を触らない。
	 * </p>
	 */
	public void applyImportantProperties(CSSStyle style) {
		for (int i = 0; i < this.properties.size(); ++i) {
			Property property = (Property) this.properties.get(i);
			if (property.isImportant() && property instanceof CustomProperty) {
				property.applyProperty(style);
			}
		}
		for (int i = 0; i < this.properties.size(); ++i) {
			Property property = (Property) this.properties.get(i);
			if (property.isImportant() && !(property instanceof CustomProperty)) {
				property.applyProperty(style);
			}
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
