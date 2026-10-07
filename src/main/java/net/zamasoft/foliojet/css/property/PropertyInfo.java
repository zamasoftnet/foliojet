package net.zamasoft.foliojet.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * An object for property information and parsing.
 *
 * @author MIYABE Tatsuhiko
 */
public interface PropertyInfo {
	/**
	 * Returns the property name.
	 *
	 * @return
	 */
	public String getName();

	/**
	 * Parses the declaration's token sequence to create a property.
	 *
	 * @param tokens    declaration value
	 * @param ua        user agent
	 * @param uri       URI of the stylesheet containing the declaration
	 * @param important whether !important is specified
	 * @return the parsed property
	 * @throws PropertyException if the value cannot be interpreted
	 */
	public Property parse(TokenStream tokens, UserAgent ua, URI uri, boolean important) throws PropertyException;
}
