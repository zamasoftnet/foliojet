package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.FontVariantEastAsianValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code font-variant-east-asian}(css-fonts-3)です。
 * {@code normal | [ <east-asian-variant-values> || <east-asian-width-values> || ruby ]}。
 * 異体字系(jis78/jis83/jis90/jis04/simplified/traditional)と
 * 字幅系(full-width/proportional-width)は各カテゴリ最大1つ、順序任意。
 * 内部ではOpenType featureタグへ正規化して搬送します
 * ({@link FontVariantEastAsianValue#featureSet()})。
 *
 * @author MIYABE Tatsuhiko
 */
public class FontVariantEastAsian extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontVariantEastAsian();

	public static FontVariantEastAsianValue get(CSSStyle style) {
		return (FontVariantEastAsianValue) style.get(INFO);
	}

	protected FontVariantEastAsian() {
		super("font-variant-east-asian");
	}

	public Value getDefault(CSSStyle style) {
		return FontVariantEastAsianValue.NORMAL_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.size() == 1 && tokens.eat("normal")) {
			return FontVariantEastAsianValue.NORMAL_VALUE;
		}
		String variant = null;
		String width = null;
		boolean ruby = false;
		while (tokens.hasNext()) {
			final String ident = tokens.ident();
			if (ident == null) {
				throw new PropertyException();
			}
			final String variantTag = switch (ident.toLowerCase(java.util.Locale.ROOT)) {
			case "jis78" -> "jp78";
			case "jis83" -> "jp83";
			case "jis90" -> "jp90";
			case "jis04" -> "jp04";
			case "simplified" -> "smpl";
			case "traditional" -> "trad";
			default -> null;
			};
			if (variantTag != null) {
				if (variant != null) {
					throw new PropertyException();
				}
				variant = variantTag;
				continue;
			}
			final String widthTag = switch (ident.toLowerCase(java.util.Locale.ROOT)) {
			case "full-width" -> "fwid";
			case "proportional-width" -> "pwid";
			default -> null;
			};
			if (widthTag != null) {
				if (width != null) {
					throw new PropertyException();
				}
				width = widthTag;
				continue;
			}
			if (ident.equalsIgnoreCase("ruby")) {
				if (ruby) {
					throw new PropertyException();
				}
				ruby = true;
				continue;
			}
			throw new PropertyException();
		}
		if (variant == null && width == null && !ruby) {
			throw new PropertyException();
		}
		return FontVariantEastAsianValue.create(variant, width, ruby);
	}
}
