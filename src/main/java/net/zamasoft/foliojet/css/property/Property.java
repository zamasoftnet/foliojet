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

	/**
	 * Whether applying this declaration can set the given property. False only when it certainly cannot; a caller
	 * uses it to skip work that matters only if the property is declared (a pseudo-element without {@code content}).
	 */
	public default boolean mayApply(final PrimitivePropertyInfo info) {
		return true;
	}
}
