package net.zamasoft.foliojet.ua.props;

import java.util.Locale;

/**
 * A choice for a code-valued property. By default, property value identifiers
 * use the enum constant name in lowercase kebab case.
 */
public interface PropCode {
	public default String ident() {
		return ((Enum<?>) this).name().toLowerCase(Locale.ROOT).replace('_', '-');
	}
}
