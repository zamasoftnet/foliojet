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
	/** {@link #get}が消費(クリア)した宣言の記録({@link #isDeclared}用)。 */
	private java.util.BitSet consumedDeclared = null;
	private boolean[] importants = null;
	private FontStyle fontStyle = null;

	/**
	 * 脚注の論理識別子です(脚注F4、CSS非公開のengine-owned側路——
	 * {@code Params.footnoteId}参照)。StyleEventMachineが脚注元要素の
	 * styleと::footnote-call擬似styleへ同じIDを設定し、
	 * {@code BoxStyleMapper.setupParams}がbox paramsへ写す。既定-1。
	 */
	public long footnoteId = -1;

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
			// fail-loud(2026-08-01): setと同じ登録漏れ検出(本番は既定値で続行)
			assert false : "カスケード用コード未割当のプロパティがgetされました(登録漏れ): " + info.getName();
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
				// 継承(読み出し次第クリア)。クリア後もisDeclaredが宣言有無を
				// 答えられるよう、消費済みビットに記録する
				style.values[code] = null;
				if (style.consumedDeclared == null) {
					style.consumedDeclared = new java.util.BitSet(ElementPropertySet.getCodeSize());
				}
				style.consumedDeclared.set(code);
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
	 * いるか返します。論理プロパティ(margin-inline-start等)と対応する物理
	 * プロパティ(margin-top等)が同じ辺を指す場合にどちらを優先するかの判定や、
	 * Flexの自動最小サイズ(§4.5、min宣言有無)の判定に使います。
	 * <p>
	 * {@link #get}はこのスタイル階層のvalues[code]を読み出し次第クリアする
	 * (継承解決の一部)が、クリア時に消費済みビットへ記録するため、この判定は
	 * getの前後どちらで呼んでも同じ結果を返す。かつては「getより前に呼ぶ」
	 * という順序制約があり、BlockParams構築(MinWidth.get)後にFlexItemSpecを
	 * 構築する経路でmin-width宣言が常に「なし」と誤判定され、フロー内容が
	 * 空のflexアイテムがmin-widthを無視して幅0になっていた(2026-08-07、
	 * yahoo.co.jpの順位バッジ消失として発覚)。
	 * </p>
	 */
	public boolean isDeclared(PrimitivePropertyInfo info) {
		short code = ElementPropertySet.getCode(info);
		if (code == -1) {
			return false;
		}
		if (this.values != null && this.values[code] != null) {
			return true;
		}
		return this.consumedDeclared != null && this.consumedDeclared.get(code);
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
		final CSSStyle owner = this.getCustomPropertyOwner(name);
		return owner == null ? null : owner.customProperties.get(name);
	}

	/**
	 * そのカスタムプロパティを<b>宣言している</b>スタイルを返します
	 * (2026-08-03新設)。見つからなければnull。
	 *
	 * <p>
	 * 値の中の{@code var()}は、<b>宣言した要素の文脈</b>で解決しなければ
	 * なりません(CSS Variables 1: カスタムプロパティの計算値は
	 * 「{@code var()}を置換した後のトークン列」であり、<b>継承より前に</b>
	 * 計算される)。祖先で{@code --y: calc(var(--x) + 1px)}と書き、子で
	 * {@code --x}だけ変えても、継承した{@code --y}は<b>祖先の</b>
	 * {@code --x}で計算された値のままです。Chrome・Firefox・Safariとも
	 * 仕様どおり(2026-08-03、独立相談で確認)。
	 * </p>
	 */
	public CSSStyle getCustomPropertyOwner(String name) {
		for (CSSStyle style = this; style != null; style = style.parentStyle) {
			if (style.customProperties != null && style.customProperties.get(name) != null) {
				return style;
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
			// fail-loud(2026-08-01): 解釈可能なプロパティのコード未割当は
			// 登録漏れ(ElementPropertySetのreg/regCode)であり、開発・テスト
			// (-ea)では即座に落とす。@page sizeで「黙って捨てられて長い
			// デバッグになった」実害の再発防止。本番(-eaなし)は従来どおり
			// WARNで続行(クラッシュ排除)。静的な網羅検査は
			// PropertyCodeRegistryTestが行う
			assert false : "カスケード用コード未割当のプロパティがsetされました(登録漏れ): " + info.getName();
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
		final var alternates = net.zamasoft.foliojet.css.impl.property.font.FontVariantAlternates.get(this);
		final var featureValues = this.ua.getUAContext().getFontFeatureValues();
		// @font-feature-valuesの無い文書は従来と同じメソッドを通し、既定経路を変えない。
		final var alternateFeatures = featureValues.isEmpty() ? alternates.featureSet()
				: alternates.featureSet(featureValues, family.get(0).getName());
		// font-variant-*由来のタグをfont-feature-settingsの明示タグで
		// 上書きしてOpenType feature列へ正規化する(css-fonts-3の優先順)
		final var features = net.zamasoft.foliojet.css.impl.property.font.FontVariantCaps.get(this).featureSet()
				.override(net.zamasoft.foliojet.css.impl.property.font.FontVariantLigatures.get(this).featureSet())
				.override(alternateFeatures)
				.override(net.zamasoft.foliojet.css.impl.property.font.FontVariantEastAsian.get(this).featureSet())
				.override(net.zamasoft.foliojet.css.impl.property.font.FontVariantNumeric.get(this).featureSet())
				// font-kerning:noneはkern明示off(2026-08-29)。font-feature-settingsが優先
				.override(net.zamasoft.foliojet.css.impl.property.font.FontKerning.featureSet(this))
				.override(net.zamasoft.foliojet.css.impl.property.font.FontFeatureSettings.get(this));

		// font-stretch(2026-08-29)は幅級(usWidthClass 1..9)としてFontStyleに
		// 載せ、pdfg2dの書体選択がitalic/weight同点の中から幅級の近い面を選ぶ
		this.fontStyle = new FontStyleImpl(family, size, style, weight, direction, policy, features,
				net.zamasoft.foliojet.css.impl.property.font.FontSynthesisWeight.get(this),
				net.zamasoft.foliojet.css.impl.property.font.FontSynthesisStyle.get(this),
				net.zamasoft.foliojet.layout.box.params.TypesettingMode.usedTextOrientation(
						net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant.get(this),
						net.zamasoft.foliojet.css.impl.property.text.TextOrientation.get(this)),
				net.zamasoft.foliojet.css.impl.property.font.FontStretch.getWidthClass(this),
				// 内容の言語(2026-08-31)。汎用ファミリの連鎖を言語別に選ぶために
				// 運ぶ。既定の連鎖は日本語向けなので、これが無いと中国語に
				// 日本語の字形が、韓国語のsans-serifに明朝が出る
				this.ce == null ? null : this.ce.lang);
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
