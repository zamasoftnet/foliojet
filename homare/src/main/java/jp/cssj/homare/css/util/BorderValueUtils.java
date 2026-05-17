package jp.cssj.homare.css.util;

import jp.cssj.homare.css.value.BorderStyleValue;
import jp.cssj.homare.css.value.LengthValue;
import jp.cssj.homare.css.value.css3.BorderRadiusValue;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderValueUtils.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public final class BorderValueUtils {
	private BorderValueUtils() {
		// unused
	}

	/**
	 * &lt;border-width&gt; を値に変換します。
	 * 
	 * @param ua
	 * @param lu
	 * @return
	 */
	public static LengthValue toBorderWidth(UserAgent ua, LexicalUnit lu) {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("thin")) {
				return ua.getBorderWidth(UserAgent.BORDER_WIDTH_THIN);
			} else if (ident.equals("medium")) {
				return ua.getBorderWidth(UserAgent.BORDER_WIDTH_MEDIUM);
			} else if (ident.equals("thick")) {
				return ua.getBorderWidth(UserAgent.BORDER_WIDTH_THICK);
			}
			break;

		default:
			LengthValue length = ValueUtils.toLength(ua, lu);
			if (length != null && length.isNegative()) {
				break;
			}
			return length;
		}
		return null;
	}

	/**
	 * &lt;border-style&gt; を値に変換します。
	 * 
	 * @param lu
	 * @return
	 */
	public static BorderStyleValue toBorderStyle(LexicalUnit lu) {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("none")) {
				return BorderStyleValue.NONE_VALUE;
			} else if (ident.equals("hidden")) {
				return BorderStyleValue.HIDDEN_VALUE;
			} else if (ident.equals("dotted")) {
				return BorderStyleValue.DOTTED_VALUE;
			} else if (ident.equals("dashed")) {
				return BorderStyleValue.DASHED_VALUE;
			} else if (ident.equals("solid")) {
				return BorderStyleValue.SOLID_VALUE;
			} else if (ident.equals("double")) {
				return BorderStyleValue.DOUBLE_VALUE;
			} else if (ident.equals("groove")) {
				return BorderStyleValue.GROOVE_VALUE;
			} else if (ident.equals("ridge")) {
				return BorderStyleValue.RIDGE_VALUE;
			} else if (ident.equals("inset")) {
				return BorderStyleValue.INSET_VALUE;
			} else if (ident.equals("outset")) {
				return BorderStyleValue.OUTSET_VALUE;
			}
		}
		return null;
	}

	public static BorderRadiusValue toBorderRadius(UserAgent ua, LexicalUnit lu) {
		LengthValue hr = ValueUtils.toLength(ua, lu);
		if (hr == null) {
			return null;
		}
		lu = lu.getNextLexicalUnit();
		LengthValue vr;
		if (lu != null) {
			if (lu.getNextLexicalUnit() != null) {
				return null;
			}
			vr = ValueUtils.toLength(ua, lu);
			if (vr == null) {
				return null;
			}
		} else {
			vr = hr;
		}
		return BorderRadiusValue.create(hr, vr);
	}
}