package jp.cssj.homare.css.util;

import java.net.URI;
import java.net.URISyntaxException;

import jp.cssj.homare.css.value.URIValue;
import net.zamasoft.zstream.resolver.util.URIHelper;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: URIUtils.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public final class URIUtils {
	public static URIValue createURIValue(String encoding, URI baseURI, String href) throws URISyntaxException {
		URI uri = URIHelper.resolve(encoding, baseURI, href);
		return URIValue.create(uri);
	}
}