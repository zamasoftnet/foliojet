package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.FontValueUtils;
import net.zamasoft.foliojet.css.value.FontVariantValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code font-variant-caps}(CSS Fonts)です。
 *
 * <p>
 * 従来の{@code font-variant: small-caps}が保持していた
 * {@link FontVariantValue}へ直接つなぎ、OpenTypeの
 * {@code smcp/c2sc/pcap/c2pc/unic/titl}を描画段へ搬送します。
 * {@code all-small-caps}等も対応する大文字・小文字用featureを併用するため、
 * 近似ではなくフォントが持つ字形を使用します。featureの無いフォントに対する
 * small-caps合成は未実装です。
 * </p>
 */
public final class FontVariantCaps extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontVariantCaps();

	public static FontVariantValue get(final CSSStyle style) {
		return (FontVariantValue) style.get(INFO);
	}

	private FontVariantCaps() {
		super("font-variant-caps");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return FontVariantValue.NORMAL_VALUE;
	}

	@Override
	public boolean isInherited() {
		return true;
	}

	@Override
	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	@Override
	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final FontVariantValue value = FontValueUtils.toFontVariant(tokens.next());
		if (value == null || tokens.hasNext()) {
			throw new PropertyException();
		}
		return value;
	}
}
