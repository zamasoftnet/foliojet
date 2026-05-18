package net.zamasoft.foliojet.css;

import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.CSSFontFamily;
import net.zamasoft.foliojet.impl.css.property.CSSFontStyle;
import net.zamasoft.foliojet.impl.css.property.FontSize;
import net.zamasoft.foliojet.impl.css.property.FontWeight;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJFontPolicy;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;

/**
 * CSSスタイルです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSStyle.java 1587 2019-06-10 01:42:25Z miyabe $
 */
public class CSSStyle {
	public static final byte MODE_NORMAL = 0;
	public static final byte MODE_WEAK = -1;
	public static final byte MODE_IMPORTANT = 1;

	/**
	 * 匿名ボックスのスタイルです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: CSSStyle.java 1587 2019-06-10 01:42:25Z miyabe $
	 */
	static class AnonStyle extends CSSStyle {
		AnonStyle() {
			// empty
		}

		public boolean isAnonStyle() {
			return true;
		}

		public String toString() {
			return "ANON:" + super.toString();
		}
	}

	static class InsertedAnonStyle extends AnonStyle {
		public boolean isInsertedAnonStyle() {
			return true;
		}
	}

	/**
	 * 対応するマークアップ言語要素です。
	 */
	private CSSElement ce;

	/**
	 * ターゲットUAです。
	 */
	private UserAgent ua;

	/**
	 * 親のスタイルです。
	 */
	private CSSStyle parentStyle;

	private Value[] values = null;
	private Value[] computedValues = null;
	private boolean[] importants = null;
	private FontStyle fontStyle = null;

	public static CSSStyle getCSSStyle(UserAgent ua, CSSStyle parentStyle, CSSElement ce) {
		CSSStyle style = new CSSStyle();
		style.init(ce, ua, parentStyle);
		return style;
	}

	private static CSSStyle getAnonStyle(CSSElement anone, UserAgent ua, CSSStyle parentStyle, boolean inserted) {
		final AnonStyle style;
		if (inserted) {
			style = new InsertedAnonStyle();
		} else {
			style = new AnonStyle();
		}
		style.init(anone, ua, parentStyle);
		return style;
	}

	private CSSStyle() {
		// empty
	}

	protected void init(CSSElement ce, UserAgent ua, CSSStyle parentStyle) {
		this.ce = ce;
		this.ua = ua;
		this.parentStyle = parentStyle;
	}

	public CSSElement getCSSElement() {
		return this.ce;
	}

	public UserAgent getUserAgent() {
		return this.ua;
	}

	public CSSStyle getParentStyle() {
		return this.parentStyle;
	}

	public CSSStyle getRootStyle() {
		if (this.parentStyle == null) {
			return this;
		}
		return this.parentStyle.getRootStyle();
	}

	public CSSStyle getExplicitStyle() {
		if (!this.isAnonStyle()) {
			return this;
		}
		return this.parentStyle.getExplicitStyle();
	}

	/**
	 * 上位に匿名スタイルを挿入します。
	 * 
	 * @return
	 */
	public CSSStyle insertAnonStyle(CSSElement anone) {
		return this.parentStyle = CSSStyle.getAnonStyle(anone, this.ua, this.parentStyle, true);
	}

	/**
	 * 下位に匿名スタイルを挿入します。
	 * 
	 * @return
	 */
	public CSSStyle inheritAnonStyle(CSSElement anone) {
		return CSSStyle.getAnonStyle(anone, this.ua, this, false);
	}

	/**
	 * 上位の匿名スタイルを除去します。
	 */
	public void removeAnonStyle() {
		assert this.parentStyle.isAnonStyle();
		this.parentStyle = this.parentStyle.parentStyle;
	}

	public boolean isAnonStyle() {
		return false;
	}

	public boolean isInsertedAnonStyle() {
		return false;
	}

	public Value get(PrimitivePropertyInfo info) {
		Value value = null;
		short code = ElementPropertySet.getCode(info);
		if (code == -1) {
			return info.getDefault(this);
		}
		if (this.computedValues != null) {
			value = this.computedValues[code];
		}
		if (value != null) {
			return value;
		}
		if (this.values != null) {
			value = this.values[code];
		}
		if (value != null) {
			// 継承
			this.values[code] = null;
			if (value.getValueType() == Value.TYPE_INHERIT) {
				// 継承
				value = (this.parentStyle != null) ? this.parentStyle.get(info) : info.getDefault(this);
			}
		}
		if (value == null) {
			// デフォルトの場合は継承するか、デフォルト値を使う
			value = (this.parentStyle != null && info.isInherited()) ? this.parentStyle.get(info)
					: info.getDefault(this);
		}
		// 計算値
		value = info.getComputedValue(value, this);
		if (this.computedValues == null) {
			this.computedValues = new Value[ElementPropertySet.getCodeSize()];
		}
		this.computedValues[code] = value;
		return value;
	}

	public void set(PrimitivePropertyInfo info, Value value) {
		this.set(info, value, MODE_NORMAL);
	}

	public void set(PrimitivePropertyInfo info, Value value, byte mode) {
		short code = ElementPropertySet.getCode(info);
		if (code == -1) {
			this.ua.message(MessageCodes.WARN_UNSUPPORTED_CSS_PROPERTY, info.getName());
			return;
		}
		if (mode == MODE_IMPORTANT) {
			if (this.importants == null) {
				this.importants = new boolean[ElementPropertySet.getCodeSize()];
			}
			this.importants[code] = true;
		} else {
			if (this.importants != null && this.importants[code]) {
				return;
			}
		}
		if (this.values == null) {
			this.values = new Value[ElementPropertySet.getCodeSize()];
		}
		if (mode == MODE_WEAK) {
			if (this.values[code] != null) {
				return;
			}
		}
		this.values[code] = value;
		if (this.computedValues != null) {
			this.computedValues[code] = null;
		}
	}

	public FontStyle getFontStyle() {
		if (this.fontStyle != null) {
			return this.fontStyle;
		}
		FontFamilyList family = CSSFontFamily.get(this);
		double size = FontSize.get(this);
		Style style = CSSFontStyle.get(this);
		Weight weight = FontWeight.get(this);
		Direction direction = net.zamasoft.foliojet.impl.css.property.Direction.getFontDirection(this);
		FontPolicyList policy = CSSJFontPolicy.get(this);

		this.fontStyle = new FontStyleImpl(family, size, style, weight, direction, policy);
		return this.fontStyle;
	}

	public String toString() {
		StringBuffer buff = new StringBuffer(super.toString());
		buff.append("\n").append(this.ce).append("\n");
		if (this.values != null) {
			buff.append("values[");
			for (int i = 0; i < this.values.length; ++i) {
				Value value = this.values[i];
				if (value == null) {
					continue;
				}
				buff.append(value).append(";");
			}
			buff.deleteCharAt(buff.length() - 1);
			buff.append("]\n");
		}
		if (this.computedValues != null) {
			buff.append("computed values[");
			for (int i = 0; i < this.computedValues.length; ++i) {
				Value value = this.computedValues[i];
				if (value == null) {
					continue;
				}
				buff.append(value).append(";");
			}
			buff.deleteCharAt(buff.length() - 1);
			buff.append("]\n");
		}
		buff.deleteCharAt(buff.length() - 1);
		return buff.toString();
	}

	public String path() {
		// String disp = this.get(Display.INFO).toString();
		// StringBuffer disp = new StringBuffer(this.ce.toString());
		StringBuffer disp = new StringBuffer(String.valueOf(this.ce.lName));
		if (this.isAnonStyle()) {
			disp.insert(0, '(');
			disp.append(')');
		}
		if (this.parentStyle == null) {
			return disp.toString();
		}
		return this.parentStyle.path() + "/" + disp;
	}
}
