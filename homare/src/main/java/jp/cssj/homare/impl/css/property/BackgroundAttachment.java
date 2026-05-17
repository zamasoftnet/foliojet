package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.ColorValueUtils;
import jp.cssj.homare.css.value.BackgroundAttachmentValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * <a href=
 * "http://www.w3.org/TR/CSS21/colors.html#propdef-background-attachment">
 * backgropund-attachment 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: BackgroundAttachment.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BackgroundAttachment extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BackgroundAttachment();

	public static byte get(CSSStyle style) {
		return ((BackgroundAttachmentValue) style.get(INFO)).getBackgroundAttachment();
	}

	protected BackgroundAttachment() {
		super("background-attachment");
	}

	public Value getDefault(CSSStyle style) {
		return BackgroundAttachmentValue.SCROLL_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		final Value value = ColorValueUtils.toBackgroundAttachment(lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

}