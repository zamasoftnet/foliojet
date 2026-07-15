package net.zamasoft.foliojet.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;

/**
 * @author MIYABE Tatsuhiko
 */
public interface Property {
	public boolean isImportant();

	public String getName();

	public URI getURI();

	public void applyProperty(CSSStyle style);
}
