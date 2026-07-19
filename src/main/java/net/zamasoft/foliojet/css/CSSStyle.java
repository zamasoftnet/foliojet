package net.zamasoft.foliojet.css;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.impl.property.font.CSSFontFamily;
import net.zamasoft.foliojet.css.impl.property.font.CSSFontStyle;
import net.zamasoft.foliojet.css.impl.property.font.FontSize;
import net.zamasoft.foliojet.css.impl.property.font.FontWeight;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJFontPolicy;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * CSSスタイルです。
 * 
 * @author MIYABE Tatsuhiko
 */
public class CSSStyle {
	public static final byte MODE_NORMAL = 0;
	public static final byte MODE_WEAK = -1;
	public static final byte MODE_IMPORTANT = 1;

	/**
	 * 匿名ボックスのスタイルです。
	 * 
	 * @author MIYABE Tatsuhiko
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

	/**
	 * カスタムプロパティ(--name)の宣言値(生トークン列、var()は未解決のまま)。
	 * 通常のプロパティのvalues[]/computedValues[](ElementPropertySet.CODESで
	 * 採番された固定コード空間)とは別枠で管理する。カスタムプロパティ名は
	 * 文書ごとに任意・無制限で、CODESはJVM全体で共有される静的な登録表のため、
	 * 同時実行中の別文書のCSSStyleとコード空間がずれる恐れがあり、動的採番は
	 * 採用しなかった(docs/PLAN.md参照)。
	 */
	private Map<String, List<CssToken>> customProperties = null;
	private Set<String> importantCustomProperties = null;

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
		short code = ElementPropertySet.getCode(info);
		if (code == -1) {
			return info.getDefault(this);
		}
		// 継承の親方向探索は元々 this.parentStyle.get(info) の再帰だったが、
		// 深くネストした文書(例: 条項番号が千段以上入れ子になる法令HTML)で
		// StackOverflowError になる実クラッシュが発生したため、スタック深さに
		// 依存しない反復に書き換える(2026-07-18)。意味論は再帰版と同一:
		// 各階層で values[code] を読み出し次第クリアし、最終的に確定した値を
		// 子階層へ向けて getComputedValue で1階層ずつ変換しながら
		// computedValues[code] にキャッシュする。
		java.util.List<CSSStyle> chain = new java.util.ArrayList<CSSStyle>();
		CSSStyle style = this;
		Value resolved;
		for (;;) {
			Value cached = style.computedValues != null ? style.computedValues[code] : null;
			if (cached != null) {
				resolved = cached;
				break;
			}
			Value raw = style.values != null ? style.values[code] : null;
			if (raw != null) {
				// 継承(読み出し次第クリア)
				style.values[code] = null;
			}
			if (raw == KeywordValue.UNSET) {
				// unset: 継承特性ならinherit相当、非継承特性ならinitial相当
				// (CSS Cascading and Inheritance)
				raw = info.isInherited() ? KeywordValue.INHERIT : KeywordValue.INITIAL;
			}
			if (raw == KeywordValue.INITIAL) {
				// initial: 継承せず、常にプロパティの初期値を使う
				resolved = info.getDefault(style);
				chain.add(style);
				break;
			}
			boolean needsParent = raw != null ? raw == KeywordValue.INHERIT
					: (style.parentStyle != null && info.isInherited());
			if (!needsParent || style.parentStyle == null) {
				// デフォルトの場合は継承するか、デフォルト値を使う
				resolved = (raw != null && !needsParent) ? raw : info.getDefault(style);
				chain.add(style);
				break;
			}
			chain.add(style);
			style = style.parentStyle;
		}
		for (int i = chain.size() - 1; i >= 0; --i) {
			CSSStyle level = chain.get(i);
			// 計算値
			resolved = info.getComputedValue(resolved, level);
			if (level.computedValues == null) {
				level.computedValues = new Value[ElementPropertySet.getCodeSize()];
			}
			level.computedValues[code] = resolved;
		}
		return resolved;
	}

	/**
	 * このスタイルの直下(継承元をたどらず)でプロパティが明示的に宣言されて
	 * いるか(cascade適用によりvalues[]にまだ書き込まれたままか)を返します。
	 * 論理プロパティ(margin-inline-start等)と対応する物理プロパティ
	 * (margin-top等)が同じ辺を指す場合にどちらを優先するかの判定に使います
	 * (物理側が明示指定されていれば物理を優先し、無ければ論理側を見る)。
	 * <p>
	 * <b>注意</b>: {@link #get}はこのスタイル階層のvalues[code]を読み出し
	 * 次第クリアします(継承解決の一部)。そのため、この判定は対応する
	 * {@link #get}呼び出しより前に行う必要があります——先にgetしてしまうと
	 * 明示指定の有無にかかわらずfalseになります。
	 * </p>
	 */
	public boolean isDeclared(PrimitivePropertyInfo info) {
		short code = ElementPropertySet.getCode(info);
		if (code == -1) {
			return false;
		}
		return this.values != null && this.values[code] != null;
	}

	/**
	 * カスタムプロパティ(--name)の宣言を記録します。値は型検証を行わず
	 * 生トークン列のまま保持します(var()の実際の解決は使用時=
	 * {@link net.zamasoft.foliojet.css.property.DeferredProperty#applyProperty}
	 * まで遅延するため)。!importantの優先度は通常の{@link #set}と同じ
	 * 「一度importantになったら以後のNORMALは無視」規則に従う。
	 */
	public void setCustomProperty(String name, List<CssToken> tokens, byte mode) {
		if (mode == MODE_IMPORTANT) {
			if (this.importantCustomProperties == null) {
				this.importantCustomProperties = new HashSet<String>();
			}
			this.importantCustomProperties.add(name);
		} else if (this.importantCustomProperties != null && this.importantCustomProperties.contains(name)) {
			return;
		}
		if (this.customProperties == null) {
			this.customProperties = new HashMap<String, List<CssToken>>();
		}
		this.customProperties.put(name, tokens);
	}

	/**
	 * カスタムプロパティの値を、祖先方向の継承を考慮して解決します。
	 * 見つからなければnull。通常の{@link #get}と異なり読み出し後も
	 * クリアしません(同じ祖先の値を複数の子孫が独立して繰り返し参照
	 * しうるため)。
	 */
	public List<CssToken> getCustomProperty(String name) {
		for (CSSStyle style = this; style != null; style = style.parentStyle) {
			if (style.customProperties != null) {
				List<CssToken> tokens = style.customProperties.get(name);
				if (tokens != null) {
					return tokens;
				}
			}
		}
		return null;
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
		Direction direction = net.zamasoft.foliojet.css.impl.property.text.Direction.getFontDirection(this);
		FontPolicyList policy = CSSJFontPolicy.get(this);

		this.fontStyle = new FontStyleImpl(family, size, style, weight, direction, policy);
		return this.fontStyle;
	}

	public String toString() {
		StringBuilder buff = new StringBuilder(super.toString());
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
		// StringBuilder disp = new StringBuilder(this.ce.toString());
		StringBuilder disp = new StringBuilder(String.valueOf(this.ce.lName));
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
