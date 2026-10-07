package net.zamasoft.foliojet.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * A property that is accepted but does nothing (2026-08-29).
 *
 * <p>
 * Used for descriptors such as {@code @font-face} {@code font-display} and
 * {@code size-adjust}, whose values warrant neither validation nor warnings.
 * Succeeds for any value and expands to an empty result.
 * </p>
 */
public final class IgnoredPropertyInfo extends AbstractPropertyInfo {
	public IgnoredPropertyInfo(final String name) {
		super(name);
	}

	@Override
	public Property parse(final TokenStream tokens, final UserAgent ua, final URI uri, final boolean important)
			throws PropertyException {
		return new CompositeProperty(this.getName(), new CompositeProperty.Entry[0], uri, important);
	}
}
