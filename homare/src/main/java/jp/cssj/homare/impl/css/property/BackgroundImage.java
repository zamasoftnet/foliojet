package jp.cssj.homare.impl.css.property;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.ValueUtils;
import jp.cssj.homare.css.value.NoneValue;
import jp.cssj.homare.css.value.URIValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.message.MessageCodes;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BackgroundImage.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BackgroundImage extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BackgroundImage();
	private static final Logger LOG = Logger.getLogger(BackgroundImage.class.getName());

	public static Image get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value.getValueType() == Value.TYPE_NONE) {
			return null;
		}
		UserAgent ua = style.getUserAgent();
		URIValue uriValue = (URIValue) value;
		URI uri = uriValue.getURI();
		try {
			Source source = ua.resolve(uri);
			try {
				return ua.getImage(source);
			} finally {
				ua.release(source);
			}
		} catch (Exception e) {
			LOG.log(Level.FINE, "Missing image", e);
			ua.message(MessageCodes.WARN_MISSING_IMAGE, uri.toString());
			return null;
		}
	}

	protected BackgroundImage() {
		super("background-image");
	}

	public Value getDefault(CSSStyle style) {
		return NoneValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (ValueUtils.isNone(lu)) {
			return NoneValue.NONE_VALUE;
		}
		try {
			final URIValue value = ValueUtils.toURI(ua, uri, lu);
			if (value != null) {
				return value;
			}
		} catch (URISyntaxException e) {
			ua.message(MessageCodes.WARN_BAD_LINK_URI, lu.getStringValue());
		}
		throw new PropertyException();
	}

}