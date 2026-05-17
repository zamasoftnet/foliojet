package jp.cssj.homare.impl.css.property.internal;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.value.NoneValue;
import jp.cssj.homare.css.value.StringValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.css.value.internal.CSSJImageValue;
import jp.cssj.homare.css.value.internal.InternalValue;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * 画像(置換ボックス)の内部特性です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJInternalImage.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJInternalImage extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJInternalImage();

	public static Image getImage(CSSStyle style) {
		Value value = style.get(INFO);
		if (value.getValueType() != InternalValue.TYPE_CSSJ_IMAGE) {
			return null;
		}
		return ((CSSJImageValue) value).getImage();
	}

	public static String getText(CSSStyle style) {
		Value value = style.get(INFO);
		if (value.getValueType() != Value.TYPE_STRING) {
			return null;
		}
		return ((StringValue) value).getString();
	}

	public static void setImage(CSSStyle style, Image image) {
		style.set(INFO, new CSSJImageValue(image));
	}

	public static void setText(CSSStyle style, String text) {
		style.set(INFO, new StringValue(text));
	}

	public CSSJInternalImage() {
		super("-cssj-internal-image");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return NoneValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		throw new UnsupportedOperationException();
	}
}