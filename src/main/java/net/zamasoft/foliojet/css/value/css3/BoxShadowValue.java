package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;

/**
 * The specified value of box-shadow (CSS Backgrounds 3 §7, 2026-08-29).
 *
 * <p>
 * Retains lengths in relative units such as em. {@code BoxShadow.get(style)} resolves
 * them to used values (the same approach as text-shadow).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class BoxShadowValue implements Value {
	public static final BoxShadowValue NONE = new BoxShadowValue(new Shadow[0]);

	public static final class Shadow {
		public final LengthValue x, y;

		/** Blur radius and spread. Null when omitted (=0). */
		public final LengthValue blur, spread;

		/** Null when omitted (=currentColor). */
		public final ColorValue color;

		public final boolean inset;

		public Shadow(LengthValue x, LengthValue y, LengthValue blur, LengthValue spread, ColorValue color,
				boolean inset) {
			this.x = x;
			this.y = y;
			this.blur = blur;
			this.spread = spread;
			this.color = color;
			this.inset = inset;
		}
	}

	public static BoxShadowValue create(Shadow[] shadows) {
		if (shadows == null || shadows.length == 0) {
			return NONE;
		}
		return new BoxShadowValue(shadows);
	}

	private final Shadow[] shadows;

	protected BoxShadowValue(Shadow[] shadows) {
		this.shadows = shadows;
	}

	public Shadow[] getShadows() {
		return this.shadows;
	}
}
