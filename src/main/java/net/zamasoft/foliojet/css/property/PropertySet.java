package net.zamasoft.foliojet.css.property;

import java.net.URI;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: PropertySet.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class PropertySet {
	private static final Logger LOG = Logger.getLogger(PropertySet.class.getName());

	protected abstract PropertyInfo getPropertyParser(String name);

	public final Property parseDeclaration(String name, List<CssToken> value, UserAgent ua, URI uri,
			boolean important) {
		PropertyInfo ph = this.getPropertyParser(name.toLowerCase());
		if (ph != null) {
			TokenStream tokens = new TokenStream(value);
			try {
				return ph.parse(tokens, ua, uri, important);
			} catch (PropertyException e) {
				String m = name + ":" + tokens + ":" + e.getMessage();
				LOG.log(Level.FINE, m, e);
				ua.message(MessageCodes.WARN_BAD_CSS_ARGMENTS, name, tokens.toString(), e.getMessage());
				return null;
			}
		}
		ua.message(MessageCodes.WARN_UNSUPPORTED_CSS_PROPERTY, name);
		return null;
	}
}
