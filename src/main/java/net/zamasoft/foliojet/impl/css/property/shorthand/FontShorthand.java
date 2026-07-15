package net.zamasoft.foliojet.impl.css.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.FontValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.FontFamilyValue;
import net.zamasoft.foliojet.css.value.FontStyleValue;
import net.zamasoft.foliojet.css.value.FontVariantValue;
import net.zamasoft.foliojet.css.value.FontWeightValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.font.CSSFontFamily;
import net.zamasoft.foliojet.impl.css.property.font.CSSFontStyle;
import net.zamasoft.foliojet.impl.css.property.font.FontSize;
import net.zamasoft.foliojet.impl.css.property.font.FontVariant;
import net.zamasoft.foliojet.impl.css.property.font.FontWeight;
import net.zamasoft.foliojet.impl.css.property.font.LineHeight;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.ua.AbsoluteFontSize;

/**
 * @author MIYABE Tatsuhiko
 */
public class FontShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new FontShorthand();

	protected FontShorthand() {
		super("font");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (tokens.isInherit()) {
			primitives.set(CSSFontStyle.INFO, KeywordValue.INHERIT);
			primitives.set(FontVariant.INFO, KeywordValue.INHERIT);
			primitives.set(FontWeight.INFO, KeywordValue.INHERIT);
			primitives.set(FontSize.INFO, KeywordValue.INHERIT);
			primitives.set(LineHeight.INFO, KeywordValue.INHERIT);
			primitives.set(CSSFontFamily.INFO, KeywordValue.INHERIT);
			return;
		} else if (tokens.peek() instanceof CssToken.Ident systemFont) {
			String ident = systemFont.lower();
			if (ident.equals("caption") || ident.equals("icon") || ident.equals("menu") || ident.equals("message-box")
					|| ident.equals("small-caption") || ident.equals("status-bar")) {
				// あまり重要ではない
				final FontFamilyValue defaultFamily = ua.getDefaultFontFamily();
				primitives.set(CSSFontStyle.INFO, FontStyleValue.NORMAL_VALUE);
				primitives.set(FontVariant.INFO, FontVariantValue.NORMAL_VALUE);
				primitives.set(FontWeight.INFO, FontWeightValue.NORMAL_VALUE);
				primitives.set(FontSize.INFO,
						AbsoluteLengthValue.create(ua, ua.getFontSize(AbsoluteFontSize.MEDIUM)));
				primitives.set(LineHeight.INFO, KeywordValue.NORMAL);
				primitives.set(CSSFontFamily.INFO, defaultFamily);
				return;
			}
		}

		FontStyleValue fontStyle = null;
		FontVariantValue fontVariant = null;
		FontWeightValue fontWeight = null;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.peek();
			if (fontStyle == null) {
				fontStyle = FontValueUtils.toFontStyle(lu);
				if (fontStyle != null) {
					tokens.next();
					continue;
				}
			}
			if (fontVariant == null) {
				fontVariant = FontValueUtils.toFontVariant(lu);
				if (fontVariant != null) {
					tokens.next();
					continue;
				}
			}
			if (fontWeight == null) {
				fontWeight = FontValueUtils.toFontWeight(lu);
				if (fontWeight != null) {
					tokens.next();
					continue;
				}
			}
			break;
		}

		final Value fontSize;
		if (!tokens.hasNext() || (fontSize = FontValueUtils.toFontSize(ua, tokens.next())) == null) {
			throw new PropertyException("フォントサイズが未指定です");
		}

		if (!tokens.hasNext()) {
			throw new PropertyException("フォントファミリが未指定です");
		}

		Value lineHeight = null;
		if (tokens.eatSlash()) {
			if (!tokens.hasNext() || (lineHeight = BoxValueUtils.toLineHeight(ua, tokens.next())) == null) {
				throw new PropertyException("行高さが不正です");
			}
		}

		final FontFamilyValue fontFamily;
		if (!tokens.hasNext() || (fontFamily = FontValueUtils.toFontFamily(ua, tokens)) == null) {
			throw new PropertyException("フォントファミリが未指定です");
		}

		primitives.set(FontSize.INFO, fontSize);
		if (lineHeight == null) {
			lineHeight = LineHeight.INFO.getDefault(null);
		}
		primitives.set(LineHeight.INFO, lineHeight);
		if (fontStyle == null) {
			fontStyle = (FontStyleValue) CSSFontStyle.INFO.getDefault(null);
		}
		primitives.set(CSSFontStyle.INFO, fontStyle);
		if (fontVariant == null) {
			fontVariant = (FontVariantValue) FontVariant.INFO.getDefault(null);
		}
		primitives.set(FontVariant.INFO, fontVariant);
		if (fontWeight == null) {
			fontWeight = (FontWeightValue) FontWeight.INFO.getDefault(null);
		}
		primitives.set(FontWeight.INFO, fontWeight);
		primitives.set(CSSFontFamily.INFO, fontFamily);
	}

}