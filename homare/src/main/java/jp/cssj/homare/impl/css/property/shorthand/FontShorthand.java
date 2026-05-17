package jp.cssj.homare.impl.css.property.shorthand;

import java.net.URI;

import jp.cssj.homare.css.property.AbstractShorthandPropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.property.ShorthandPropertyInfo;
import jp.cssj.homare.css.util.BoxValueUtils;
import jp.cssj.homare.css.util.FontValueUtils;
import jp.cssj.homare.css.value.AbsoluteLengthValue;
import jp.cssj.homare.css.value.FontFamilyValue;
import jp.cssj.homare.css.value.FontStyleValue;
import jp.cssj.homare.css.value.FontVariantValue;
import jp.cssj.homare.css.value.FontWeightValue;
import jp.cssj.homare.css.value.InheritValue;
import jp.cssj.homare.css.value.NormalValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.impl.css.property.CSSFontFamily;
import jp.cssj.homare.impl.css.property.CSSFontStyle;
import jp.cssj.homare.impl.css.property.FontSize;
import jp.cssj.homare.impl.css.property.FontVariant;
import jp.cssj.homare.impl.css.property.FontWeight;
import jp.cssj.homare.impl.css.property.LineHeight;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: FontShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FontShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new FontShorthand();

	protected FontShorthand() {
		super("font");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
			primitives.set(CSSFontStyle.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(FontVariant.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(FontWeight.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(FontSize.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(LineHeight.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(CSSFontFamily.INFO, InheritValue.INHERIT_VALUE);
			return;
		} else if (lu.getLexicalUnitType() == LexicalUnit.SAC_IDENT) {
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("caption") || ident.equals("icon") || ident.equals("menu") || ident.equals("message-box")
					|| ident.equals("small-caption") || ident.equals("status-bar")) {
				// あまり重要ではない
				final FontFamilyValue defaultFamily = ua.getDefaultFontFamily();
				primitives.set(CSSFontStyle.INFO, FontStyleValue.NORMAL_VALUE);
				primitives.set(FontVariant.INFO, FontVariantValue.NORMAL_VALUE);
				primitives.set(FontWeight.INFO, FontWeightValue.NORMAL_VALUE);
				primitives.set(FontSize.INFO,
						AbsoluteLengthValue.create(ua, ua.getFontSize(UserAgent.FONT_SIZE_MEDIUM)));
				primitives.set(LineHeight.INFO, NormalValue.NORMAL_VALUE);
				primitives.set(CSSFontFamily.INFO, defaultFamily);
				return;
			}
		}

		FontStyleValue fontStyle = null;
		FontVariantValue fontVariant = null;
		FontWeightValue fontWeight = null;
		do {
			if (fontStyle == null) {
				fontStyle = FontValueUtils.toFontStyle(lu);
				if (fontStyle != null) {
					lu = lu.getNextLexicalUnit();
					continue;
				}
			}
			if (fontVariant == null) {
				fontVariant = FontValueUtils.toFontVariant(lu);
				if (fontVariant != null) {
					lu = lu.getNextLexicalUnit();
					continue;
				}
			}
			if (fontWeight == null) {
				fontWeight = FontValueUtils.toFontWeight(lu);
				if (fontWeight != null) {
					lu = lu.getNextLexicalUnit();
					continue;
				}
			}
			break;
		} while (lu != null);

		final Value fontSize;
		if (lu == null || (fontSize = FontValueUtils.toFontSize(ua, lu)) == null) {
			throw new PropertyException("フォントサイズが未指定です");
		}
		lu = lu.getNextLexicalUnit();

		if (lu == null) {
			throw new PropertyException("フォントファミリが未指定です");
		}

		Value lineHeight = null;
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_OPERATOR_SLASH) {
			lu = lu.getNextLexicalUnit();
			if (lu == null || (lineHeight = BoxValueUtils.toLineHeight(ua, lu)) == null) {
				throw new PropertyException("行高さが不正です");
			}
			lu = lu.getNextLexicalUnit();
		}

		final FontFamilyValue fontFamily;
		if (lu == null || (fontFamily = FontValueUtils.toFontFamily(ua, lu)) == null) {
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