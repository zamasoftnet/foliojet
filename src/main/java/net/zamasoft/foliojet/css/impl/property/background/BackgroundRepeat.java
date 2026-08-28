package net.zamasoft.foliojet.css.impl.property.background;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.BackgroundRepeatValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * 2026-07-20: {@code -cssj-direction-mode}廃止に伴い、縦書き時のX/Y入れ替え
 * (実世界のCSS/ブラウザには存在しない挙動)を削除した。background-repeatは
 * 常に物理軸のまま扱う。
 *
 * @author MIYABE Tatsuhiko
 */
public class BackgroundRepeat extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BackgroundRepeat();

	public static byte get(CSSStyle style) {
		return ((BackgroundRepeatValue) style.get(INFO)).getBackgroundRepeat();
	}

	protected BackgroundRepeat() {
		this("background-repeat");
	}

	/** mask-repeat等、同じ文法を使う特性のための派生用(2026-08-29)。 */
	protected BackgroundRepeat(String name) {
		super(name);
	}

	public Value getDefault(CSSStyle style) {
		return BackgroundRepeatValue.REPEAT_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value value = ColorValueUtils.toBackgroundRepeat(lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

}