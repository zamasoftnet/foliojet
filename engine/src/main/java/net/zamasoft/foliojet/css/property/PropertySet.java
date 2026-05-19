package net.zamasoft.foliojet.css.property;

import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: PropertySet.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class PropertySet {
	private static final Logger LOG = Logger.getLogger(PropertySet.class.getName());

	protected abstract PropertyInfo getPropertyParser(String name);

	public final Property parseDeclaration(String name, LexicalUnit lu, UserAgent ua, URI uri, boolean important) {
		PropertyInfo ph = this.getPropertyParser(name.toLowerCase());
		if (ph != null) {
			Property property;
			try {
				property = ph.parseProperty(lu, ua, uri, important);
			} catch (PropertyException e) {
				String m = name + ":" + lu + ":" + e.getMessage();
				LOG.log(Level.FINE, m, e);
				ua.message(MessageCodes.WARN_BAD_CSS_ARGMENTS, name, lu.toString(), e.getMessage());
				return null;
			}
			return property;
		}
		ua.message(MessageCodes.WARN_UNSUPPORTED_CSS_PROPERTY, name);
		return null;
	}
}