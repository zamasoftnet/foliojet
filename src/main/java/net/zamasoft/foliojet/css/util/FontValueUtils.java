package net.zamasoft.foliojet.css.util;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.FontFamilyValue;
import net.zamasoft.foliojet.css.value.FontStyleValue;
import net.zamasoft.foliojet.css.value.FontVariantValue;
import net.zamasoft.foliojet.css.value.FontWeightValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.RelativeSizeValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJFontPolicyValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontFamily;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList.FontPolicy;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.ua.AbsoluteFontSize;

/**
 * @author MIYABE Tatsuhiko
 */
public final class FontValueUtils {
	private FontValueUtils() {
		// unused
	}

	/**
	 * font-familyを値に変換します。SPEC CSS2.1 15.3
	 * 
	 * @param lu
	 * @return
	 */
	public static FontFamilyValue toFontFamily(UserAgent ua, TokenStream tokens) {
		List<FontFamily> list = new ArrayList<FontFamily>();
		while (tokens.hasNext()) {
			CssToken token = tokens.next();
			if (token instanceof CssToken.Ident ident) {
				switch (ident.lower()) {
				case "cursive":
					list.add(FontFamily.CURSIVE_VALUE);
					break;
				case "fantasy":
					list.add(FontFamily.FANTASY_VALUE);
					break;
				case "monospace":
					list.add(FontFamily.MONOSPACE_VALUE);
					break;
				case "sans-serif":
					list.add(FontFamily.SANS_SERIF_VALUE);
					break;
				case "serif":
					list.add(FontFamily.SERIF_VALUE);
					break;
				default:
					// 一般のファミリ名
					list.add(new FontFamily(ident.name()));
					break;
				}
			} else if (token instanceof CssToken.Str str) {
				list.add(new FontFamily(str.value()));
			}
			// コンマ区切り・空白区切りのどちらも許容する
			while (tokens.eatComma()) {
				// do nothing
			}
		}
		if (list.isEmpty()) {
			return null;
		}
		FontFamilyValue defaultFamily = ua.getDefaultFontFamily();
		for (int i = 0; i < defaultFamily.getLength(); ++i) {
			list.add(defaultFamily.get(i));
		}
		return new FontFamilyValue((FontFamily[]) list.toArray(new FontFamily[list.size()]));
	}

	/**
	 * font-styleを値に変換します。SPEC CSS2.1 15.4
	 * 
	 * @param lu
	 * @return
	 */
	public static FontStyleValue toFontStyle(CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			switch (ident.lower()) {
			case "normal":
				return FontStyleValue.NORMAL_VALUE;
			case "italic":
				return FontStyleValue.ITALIC_VALUE;
			case "oblique":
				return FontStyleValue.OBLIQUE_VALUE;
			}
		}
		return null;
	}

	/**
	 * -cssj-font-policyを値に変換します。
	 * 
	 * @param lu
	 * @return
	 */
	public static CSSJFontPolicyValue toFontPolicy(TokenStream tokens) {
		String first = tokens.ident();
		if (first == null) {
			return null;
		}
		if (!tokens.hasNext()) {
			switch (first.toLowerCase()) {
			case "generic":
			case "cid-keyed":
				return CSSJFontPolicyValue.CORE_CID_KEYED_VALUE;
			case "external":
			case "cid-identity":
				return CSSJFontPolicyValue.CORE_CID_IDENTITY_VALUE;
			case "embed":
			case "embedded":
				return CSSJFontPolicyValue.CORE_EMBEDDED_VALUE;
			case "outlines":
				return CSSJFontPolicyValue.OUTLINES_VALUE;
			default:
				return null;
			}
		}
		List<FontPolicy> codes = new ArrayList<FontPolicy>();
		codes.add(FontPolicy.CORE);
		String ident = first;
		for (;;) {
			switch (ident.toLowerCase()) {
			case "generic":
			case "cid-keyed":
				codes.add(FontPolicy.CID_KEYED);
				break;
			case "external":
			case "cid-identity":
				codes.add(FontPolicy.CID_IDENTITY);
				break;
			case "embed":
			case "embedded":
				codes.add(FontPolicy.EMBEDDED);
				break;
			case "-core":
				codes.remove(FontPolicy.CORE);
				break;
			case "core":
				codes.add(FontPolicy.CORE);
				break;
			case "outlines":
				codes.add(FontPolicy.OUTLINES);
				break;
			default:
				return null;
			}
			if (!tokens.hasNext()) {
				break;
			}
			ident = tokens.ident();
			if (ident == null) {
				return null;
			}
		}
		return new CSSJFontPolicyValue(codes.toArray(new FontPolicy[codes.size()]));
	}

	public static CSSJFontPolicyValue toFontPolicy(String str) {
		String[] types = str.split("[\\s]+");
		if (types.length == 1) {
			String ident = types[0].toLowerCase();
			if (ident.equals("generic") || ident.equals("cid-keyed")) {
				return CSSJFontPolicyValue.CORE_CID_KEYED_VALUE;
			} else if (ident.equals("external") || ident.equals("cid-identity")) {
				return CSSJFontPolicyValue.CORE_CID_IDENTITY_VALUE;
			} else if (ident.equals("embed") || ident.equals("embedded")) {
				return CSSJFontPolicyValue.CORE_EMBEDDED_VALUE;
			} else if (ident.equals("outlines")) {
				return CSSJFontPolicyValue.OUTLINES_VALUE;
			} else {
				return null;
			}
		}
		List<FontPolicy> codes = new ArrayList<FontPolicy>();
		codes.add(FontPolicy.CORE);
		for (int i = 0; i < types.length; ++i) {
			String ident = types[i].toLowerCase();
			if (ident.equals("generic") || ident.equals("cid-keyed")) {
				codes.add(FontPolicy.CID_KEYED);
			} else if (ident.equals("external") || ident.equals("cid-identity")) {
				codes.add(FontPolicy.CID_IDENTITY);
			} else if (ident.equals("embed") || ident.equals("embedded")) {
				codes.add(FontPolicy.EMBEDDED);
			} else if (ident.equals("-core")) {
				codes.remove(FontPolicy.CORE);
			} else if (ident.equals("core")) {
				codes.add(FontPolicy.CORE);
			} else if (ident.equals("outlines")) {
				codes.add(FontPolicy.OUTLINES);
			} else {
				return null;
			}
		}
		CSSJFontPolicyValue result = new CSSJFontPolicyValue(codes.toArray(new FontPolicy[codes.size()]));
		// System.err.println(str+"/"+result);
		return result;
	}

	public static CSSJFontPolicyValue toFontPolicyA1(String str) {
		String[] types = str.split("[\\s]+");
		if (types.length == 1) {
			String ident = types[0].toLowerCase();
			if (ident.equals("embed") || ident.equals("embedded")) {
				return CSSJFontPolicyValue.PDFA1_VALUE;
			} else if (ident.equals("outlines")) {
				return CSSJFontPolicyValue.OUTLINES_VALUE;
			} else {
				return null;
			}
		}
		List<FontPolicy> codes = new ArrayList<FontPolicy>();
		codes.add(FontPolicy.CORE);
		for (int i = 0; i < types.length; ++i) {
			String ident = types[i].toLowerCase();
			if (ident.equals("embed") || ident.equals("embedded")) {
				codes.add(FontPolicy.EMBEDDED);
			} else if (ident.equals("outlines")) {
				codes.add(FontPolicy.OUTLINES);
			} else {
				return null;
			}
		}
		CSSJFontPolicyValue result = new CSSJFontPolicyValue(codes.toArray(new FontPolicy[codes.size()]));
		// System.err.println(str+"/"+result);
		return result;
	}

	/**
	 * font-variantを値に変換します。SPEC CSS2.1 15.5
	 * 
	 * @param lu
	 * @return
	 */
	public static FontVariantValue toFontVariant(CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			switch (ident.lower()) {
			case "normal":
				return FontVariantValue.NORMAL_VALUE;
			case "small-caps":
				return FontVariantValue.SMALL_CAPS_VALUE;
			}
		}
		return null;
	}

	/**
	 * font-weightを値に変換します。SPEC CSS2.1 15.6
	 * 
	 * @param lu
	 * @return
	 */
	public static FontWeightValue toFontWeight(CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			switch (ident.lower()) {
			case "normal":
				return FontWeightValue.NORMAL_VALUE;
			case "bold":
				return FontWeightValue.BOLD_VALUE;
			case "bolder":
				return FontWeightValue.BOLDER_VALUE;
			case "lighter":
				return FontWeightValue.LIGHTER_VALUE;
			}
			return null;
		}
		if (token instanceof CssToken.Num num && num.integer()) {
			try {
				return FontWeightValue.create(num.intValue());
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
		return null;
	}

	/**
	 * フォントサイズ値に変換します。
	 * 
	 * @param lu
	 * @return
	 */
	public static Value toFontSize(UserAgent ua, CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			switch (ident.lower()) {
			case "larger":
				return RelativeSizeValue.LARGER_VALUE;
			case "smaller":
				return RelativeSizeValue.SMALLER_VALUE;
			case "xx-small":
				return AbsoluteLengthValue.create(ua, ua.getFontSize(AbsoluteFontSize.XX_SMALL));
			case "x-small":
				return AbsoluteLengthValue.create(ua, ua.getFontSize(AbsoluteFontSize.X_SMALL));
			case "small":
				return AbsoluteLengthValue.create(ua, ua.getFontSize(AbsoluteFontSize.SMALL));
			case "medium":
				return AbsoluteLengthValue.create(ua, ua.getFontSize(AbsoluteFontSize.MEDIUM));
			case "large":
				return AbsoluteLengthValue.create(ua, ua.getFontSize(AbsoluteFontSize.LARGE));
			case "x-large":
				return AbsoluteLengthValue.create(ua, ua.getFontSize(AbsoluteFontSize.X_LARGE));
			case "xx-large":
				return AbsoluteLengthValue.create(ua, ua.getFontSize(AbsoluteFontSize.XX_LARGE));
			}
			return null;
		}
		if (token instanceof CssToken.Percent) {
			PercentageValue per = ValueUtils.toPercentage(token);
			if (per != null && per.isNegative()) {
				return null;
			}
			return per;
		}
		if (token instanceof CssToken.Dim dim) {
			switch (dim.unit()) {
			case EM:
				return RelativeLengthValue.em(dim.value());
			case EX:
				return RelativeLengthValue.ex(dim.value());
			case REM:
				return RelativeLengthValue.rem(dim.value());
			case CH:
				return RelativeLengthValue.ch(dim.value());
			default:
				// 絶対長さはフォント倍率を適用する
				return ValueUtils.toAbsoluteLength(ua,
						new CssToken.Dim(dim.value() * ua.getFontMagnification(), dim.unit(), dim.unitText()));
			}
		}
		if (token instanceof CssToken.Num num && num.value() == 0) {
			return AbsoluteLengthValue.create(ua, 0, Unit.PX);
		}
		return null;
	}

	/**
	 * font-familyを値に変換します。
	 * 
	 * @param str
	 * @return
	 */
	public static FontFamilyValue toFontFamily(String str) {
		List<FontFamily> list = new ArrayList<FontFamily>();
		int state = 0;
		StringBuilder buff = new StringBuilder();
		for (int i = 0; i <= str.length(); ++i) {
			String ident;
			if (i < str.length()) {
				char c = str.charAt(i);
				switch (state) {
				case 0:
					if (Character.isWhitespace(c)) {
						continue;
					}
					if (c == '\'') {
						state = 1;
						continue;
					}
					if (c == '\"') {
						state = 2;
						continue;
					}
					buff.append(c);
					state = 3;
					continue;
				case 1:
					if (c == '\'') {
						ident = buff.toString().trim();
						buff = new StringBuilder();
						state = 0;
						break;
					}
					buff.append(c);
					continue;
				case 2:
					if (c == '\"') {
						ident = buff.toString().trim();
						buff = new StringBuilder();
						state = 0;
						break;
					}
					buff.append(c);
					continue;
				case 3:
					if (Character.isWhitespace(c)) {
						ident = buff.toString().trim();
						buff = new StringBuilder();
						state = 0;
						break;
					}
					buff.append(c);
					continue;
				default:
					throw new IllegalStateException();
				}
			} else {
				ident = buff.toString().trim();
			}
			if (ident.length() > 0) {
				FontFamily family = FontFamily.create(ident);
				list.add(family);
			}
		}
		FontFamily[] families = (FontFamily[]) list.toArray(new FontFamily[list.size()]);
		return new FontFamilyValue(families);
	}
}
