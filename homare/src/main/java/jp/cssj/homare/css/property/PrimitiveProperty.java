package jp.cssj.homare.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.value.Value;

/**
 * 複数のプロパティの組み合わせです。
 * 
 * <p>
 * shorthandプロパティを実装するために用います。
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: PrimitiveProperty.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class PrimitiveProperty implements Property {
	private final PrimitivePropertyInfo info;
	private final Value value;
	private final URI uri;
	private final boolean important;

	protected PrimitiveProperty(PrimitivePropertyInfo info, Value value, URI uri, boolean important) {
		this.info = info;
		this.value = value;
		this.uri = uri;
		this.important = important;
	}

	public boolean isImportant() {
		return this.important;
	}

	public String getName() {
		return this.info.getName();
	}

	public URI getURI() {
		return this.uri;
	}

	/**
	 * プロパティを適用します。
	 */
	public void applyProperty(CSSStyle style) {
		byte important = this.important ? CSSStyle.MODE_IMPORTANT : CSSStyle.MODE_NORMAL;
		PrimitivePropertyInfo info = this.info.getEffectiveInfo(style);
		style.set(info, this.value, important);
	}

	public String toString() {
		return this.info.getName() + ": " + this.value + (this.important ? " !" : "") + " [uri=" + this.uri + "]";
	}
}