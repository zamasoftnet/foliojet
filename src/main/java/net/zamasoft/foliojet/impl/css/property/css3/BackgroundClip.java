package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.BackgroundClipValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ColumnFill.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BackgroundClip extends AbstractPrimitivePropertyInfo {

	public static final PrimitivePropertyInfo INFO = new BackgroundClip();

	public static byte get(CSSStyle style) {
		BackgroundClipValue value = (BackgroundClipValue) style.get(INFO);
		return value.getBackgroundClip();
	}

	protected BackgroundClip() {
		super("background-clip");
	}

	public Value getDefault(CSSStyle style) {
		return BackgroundClipValue.BORDER_BOX_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		BackgroundClipValue value = ColorValueUtils.toBackgroundClip(lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

}