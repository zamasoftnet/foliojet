package net.zamasoft.foliojet.css.impl.property.background;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.BackgroundAttachmentValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * <a href=
 * "http://www.w3.org/TR/CSS21/colors.html#propdef-background-attachment">
 * backgropund-attachment 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
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

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value value = ColorValueUtils.toBackgroundAttachment(lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

}