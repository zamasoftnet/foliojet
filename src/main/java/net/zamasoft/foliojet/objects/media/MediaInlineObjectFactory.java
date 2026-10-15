package net.zamasoft.foliojet.objects.media;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.InlineObject;
import net.zamasoft.foliojet.css.InlineObjectFactory;
import net.zamasoft.foliojet.css.html.HTMLCodes;

/**
 * HTML {@code <audio>} and {@code <video>} as replaced elements (2026-10-15, TECH-20261015-005).
 */
public class MediaInlineObjectFactory implements InlineObjectFactory {
	@Override
	public boolean match(final CSSElement key) {
		final short code = HTMLCodes.code(key);
		return code == HTMLCodes.AUDIO || code == HTMLCodes.VIDEO;
	}

	@Override
	public InlineObject createInlineObject() {
		return new MediaInlineObject();
	}
}
