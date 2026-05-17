package jp.cssj.homare.css.value.css3;

import jp.cssj.homare.css.value.ColorValue;
import jp.cssj.homare.css.value.LengthValue;

/**
 * transform です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TextShadowValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TextShadowValue implements CSS3Value {
	public static final TextShadowValue EMPTY_TEXT_SHADOW = new TextShadowValue(new Shadow[0]);

	public static class Shadow {
		public final LengthValue x;

		public final LengthValue y;

		public final ColorValue color;

		public Shadow(LengthValue x, LengthValue y, ColorValue color) {
			this.x = x;
			this.y = y;
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

	public short getValueType() {
		return TYPE_TEXT_SHADOW;
	}
}