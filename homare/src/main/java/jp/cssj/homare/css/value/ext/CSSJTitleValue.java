package jp.cssj.homare.css.value.ext;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJHeadingValue.java 1034 2013-10-23 05:51:57Z miyabe $
 */
public class CSSJTitleValue implements ExtValue {
	public static final CSSJTitleValue CSSJ_TITLE_VALUE = new CSSJTitleValue();

	private CSSJTitleValue() {
		// singleton
	}

	public short getValueType() {
		return TYPE_CSSJ_TITLE;
	}

	public String toString() {
		return "-cssj-title";
	}
}