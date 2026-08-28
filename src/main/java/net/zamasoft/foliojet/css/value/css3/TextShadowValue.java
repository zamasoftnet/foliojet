package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;

/**
 * transform です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class TextShadowValue implements Value {
	public static final TextShadowValue EMPTY_TEXT_SHADOW = new TextShadowValue(new Shadow[0]);

	public static class Shadow {
		public final LengthValue x;

		public final LengthValue y;

		public final ColorValue color;

		/** ぼかし半径(なければnull。2026-08-29)。 */
		public final LengthValue blur;

		public Shadow(LengthValue x, LengthValue y, ColorValue color) {
			this(x, y, null, color);
		}

		public Shadow(LengthValue x, LengthValue y, LengthValue blur, ColorValue color) {
			this.x = x;
			this.y = y;
			this.blur = blur;
			this.color = color;
		}
	}

	public static final TextShadowValue create(Shadow[] shadows) {
		if (shadows == null || shadows.length == 0) {
			return EMPTY_TEXT_SHADOW;
		}
		return new TextShadowValue(shadows);
	}

	private final Shadow[] shadows;

	protected TextShadowValue(Shadow[] shadows) {
		this.shadows = shadows;
	}

	public Shadow[] getShadows() {
		return this.shadows;
	}

}