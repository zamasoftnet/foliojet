package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.SrcValue;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class Src extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Src();

	public static URI[] get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.NONE) {
			return null;
		}
		SrcValue srcValue = (SrcValue) value;
		return srcValue.getURIs();
	}

	protected Src() {
		super("src");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		List<URI> list = new ArrayList<URI>();
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (lu instanceof CssToken.Uri uriToken) {
				try {
					final URI uriv = URIHelper.resolve(ua.getDocumentContext().getEncoding(), uri, uriToken.uri());
					list.add(uriv);
				} catch (URISyntaxException e) {
					ua.message(MessageCodes.WARN_BAD_LINK_URI, uriToken.uri());
				}
			} else if (lu instanceof CssToken.Func func && func.is("local")) {
				final TokenStream params = func.argStream();
				while (params.hasNext()) {
					final CssToken param = params.next();
					final String name;
					if (param instanceof CssToken.Str str) {
						name = str.value();
					} else if (param instanceof CssToken.Ident ident) {
						name = ident.name();
					} else {
						continue;
					}
					try {
						list.add(URIHelper.create("UTF-8", "local-font:" + name));
					} catch (URISyntaxException e) {
						throw new PropertyException();
					}
				}
			}
			// その他のトークン(コンマ等)は無視
		}
		return new SrcValue((URI[]) list.toArray(new URI[list.size()]));
	}

}