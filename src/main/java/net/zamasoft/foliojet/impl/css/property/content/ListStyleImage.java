package net.zamasoft.foliojet.impl.css.property.content;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class ListStyleImage extends AbstractPrimitivePropertyInfo {
	private static final Logger LOG = Logger.getLogger(ListStyleImage.class.getName());

	public static final PrimitivePropertyInfo INFO = new ListStyleImage();

	public static Image get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.NONE) {
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

	protected ListStyleImage() {
		super("list-style-image");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ValueUtils.isNone(lu)) {
			return KeywordValue.NONE;
		}
		URIValue value;
		try {
			value = ValueUtils.toURI(ua, uri, lu);
			if (value != null) {
				return value;
			}
		} catch (URISyntaxException e) {
			ua.message(MessageCodes.WARN_BAD_LINK_URI, ((CssToken.Uri) lu).uri());
		}
		throw new PropertyException();
	}

}