package net.zamasoft.foliojet.css.property;

import java.net.URI;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.VarSubstitution;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * ある文脈(要素・@page・@font-face)で解釈可能なプロパティの集合です。
 *
 * @author MIYABE Tatsuhiko
 */
public abstract class PropertySet {
	private static final Logger LOG = Logger.getLogger(PropertySet.class.getName());

	private final Map<String, PropertyInfo> nameToInfo = new HashMap<String, PropertyInfo>();

	/**
	 * プロパティを登録します。
	 */
	protected final void put(PropertyInfo... infos) {
		for (PropertyInfo info : infos) {
			this.nameToInfo.put(info.getName(), info);
		}
	}

	/**
	 * 別名(ベンダープレフィックス等)でプロパティを登録します。
	 */
	protected final void alias(String name, PropertyInfo info) {
		this.nameToInfo.put(name, info);
	}

	protected PropertyInfo getPropertyParser(String name) {
		return this.nameToInfo.get(name);
	}

	/**
	 * 登録済みプロパティの列挙です(登録整合性テスト用——
	 * {@code PropertyCodeRegistryTest}が「全ての解釈可能プロパティは
	 * カスケード用コードを持つ」を静的に検査する。@page sizeで実際に
	 * 踏んだ「名前登録だけしてコード未割当→set()が黙って落ちる」罠の
	 * 再発防止、2026-08-01)。
	 */
	public final java.util.Collection<PropertyInfo> registeredInfos() {
		return java.util.Collections.unmodifiableCollection(this.nameToInfo.values());
	}

	public final Property parseDeclaration(String name, List<CssToken> value, UserAgent ua, URI uri,
			boolean important) {
		if (isCustomPropertyName(name)) {
			// カスタムプロパティ(--name)は型検証を行わず生トークン列のまま
			// 保持する(名前は大文字小文字を区別するため小文字化しない)。
			return new CustomProperty(name, value, uri, important);
		}
		PropertyInfo ph = this.getPropertyParser(name.toLowerCase());
		if (ph != null) {
			if (VarSubstitution.containsEnvReference(value)) {
				// env()は要素に依存しないので解析時に置換する(2026-08-29)。
				// 未知の名前でフォールバックも無ければ宣言全体が無効(仕様)
				final List<CssToken> substituted = VarSubstitution.substituteEnv(value);
				if (substituted == null) {
					ua.message(MessageCodes.WARN_BAD_CSS_ARGMENTS, name, new TokenStream(value).toString(),
							"env()");
					return null;
				}
				value = substituted;
			}
			if (VarSubstitution.containsVarReference(value)) {
				// var()の実際の値はカスケード適用時(要素ごと)に異なりうるため、
				// ここ(スタイルシート解析時、文書全体で1回)では解析を確定
				// できない。要素ごとの適用時まで遅延する(DeferredProperty参照)。
				return new DeferredProperty(name, ph, value, ua, uri, important);
			}
			if (isRevert(value)) {
				// revert/revert-layer(css-cascade-4/5、2026-08-29)。宣言を
				// 無かったことにするのが最も近い——revert-layerは前の層の
				// 値へ、revertはUA/ユーザー起源の値へ戻す指定で、どちらも
				// 「この宣言が無い場合のカスケード結果」に一致するか近い
				// (同じ層・同じ起源に別の宣言がある場合だけ差が出る)。
				// 以前は不正値として警告していたが、結果は同じだった
				return null;
			}
			TokenStream tokens = new TokenStream(value);
			try {
				return ph.parse(tokens, ua, uri, important);
			} catch (PropertyException e) {
				String m = name + ":" + tokens + ":" + e.getMessage();
				LOG.log(Level.FINE, m, e);
				ua.message(MessageCodes.WARN_BAD_CSS_ARGMENTS, name, tokens.toString(), e.getMessage());
				return null;
			}
		}
		if (SVG_PRESENTATION_PROPERTIES.contains(name.toLowerCase(java.util.Locale.ROOT))) {
			// SVGのプレゼンテーション属性(fill/stroke等)は、HTML側の箱には
			// 意味がないが、インラインSVGへは規則ごとBatikへ持ち込まれて
			// 効いている(CSSStyleSheetBuilder.collectSVGStyleRule)。ここで
			// 「未対応」と警告すると、実サイト50件中31件でfillが最頻の
			// 誤警告になっていた(2026-08-29)。黙って受ける
			return null;
		}
		ua.message(isIgnored(name) ? MessageCodes.WARN_IGNORED_CSS_PROPERTY
				: MessageCodes.WARN_UNSUPPORTED_CSS_PROPERTY, name);
		return null;
	}

	/**
	 * SVGのプレゼンテーション属性のうち、HTMLの箱には無くインラインSVGへ
	 * 転送されるもの(2026-08-29)。{@code opacity}/{@code clip-path}/{@code mask}/
	 * {@code filter}はHTML側の特性でもあるのでここには含めない。
	 */
	private static final Set<String> SVG_PRESENTATION_PROPERTIES = Set.of("fill", "fill-opacity", "fill-rule",
			"stroke", "stroke-width", "stroke-opacity", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit",
			"stroke-dasharray", "stroke-dashoffset", "stop-color", "stop-opacity", "marker-start", "marker-mid",
			"marker-end", "marker", "text-anchor", "dominant-baseline", "baseline-shift", "alignment-baseline",
			"vector-effect", "paint-order", "shape-rendering", "color-interpolation", "color-interpolation-filters",
			"flood-color", "flood-opacity", "lighting-color", "clip-rule", "glyph-orientation-vertical",
			"glyph-orientation-horizontal", "enable-background", "color-rendering");

	/** 値が単独の{@code revert}/{@code revert-layer}か。 */
	private static boolean isRevert(final List<CssToken> value) {
		return value.size() == 1 && value.get(0) instanceof CssToken.Ident ident
				&& (ident.is("revert") || ident.is("revert-layer"));
	}

	/**
	 * 静的な組版に意味がないので<b>意図して対応しない</b>プロパティ
	 * (2026-08-28)。
	 *
	 * <p>
	 * 画面上の操作・時間変化・入力機器にしか関わらないものを挙げます。
	 * 「まだ実装していない」ものと同じ警告にすると、実サイトの警告を数えて
	 * 実装候補を選ぶときに混ざる——実測では1記事の未対応警告126件のうち
	 * 45件がこの類だった。接頭辞({@code -webkit-}・{@code -moz-}・
	 * {@code -ms-}・{@code -o-})は外して判定します。
	 * </p>
	 */
	private static final Set<String> IGNORED_PROPERTIES = Set.of(
			// 入力機器・操作
			"cursor", "pointer-events", "user-select", "touch-action", "caret-color",
			"resize", "appearance", "tap-highlight-color", "user-drag", "user-modify",
			"overscroll-behavior", "overscroll-behavior-x", "overscroll-behavior-y",
			"scroll-behavior", "scrollbar-color", "scrollbar-width", "scroll-snap-type",
			"scroll-snap-align", "scroll-margin", "scroll-padding",
			// 時間変化
			"transition", "transition-property", "transition-duration",
			"transition-timing-function", "transition-delay",
			"animation", "animation-name", "animation-duration", "animation-timing-function",
			"animation-delay", "animation-iteration-count", "animation-direction",
			"animation-fill-mode", "animation-play-state", "will-change",
			// 2026-08-29、50サイトの実測で加えた分。画面のレンダリング・
			// スクロール・GPU合成・入力機器の制御で、紙面には現れない
			"text-size-adjust", "font-smoothing", "osx-font-smoothing", "overflow-scrolling",
			"backface-visibility", "overflow-style", "touch-callout", "text-rendering",
			"color-scheme", "transform-style", "perspective", "perspective-origin",
			"backdrop-filter", "interpolation-mode", "text-decoration-skip",
			"text-decoration-skip-ink", "scrollbar-gutter", "khtml-user-select", "speak",
			"contain-intrinsic-size", "contain", "ms-filter", "print-color-adjust",
			"scroll-snap-stop", "scroll-margin-top", "scroll-margin-bottom", "scroll-margin-left",
			"scroll-margin-right", "scroll-padding-top", "scroll-padding-bottom",
			"scroll-padding-left", "scroll-padding-right", "scroll-margin-block",
			"scroll-margin-inline", "scroll-padding-block", "scroll-padding-inline",
			"scroll-timeline", "view-transition-name", "accent-color", "field-sizing",
			"box-orient", "box-direction", "box-pack", "box-align", "box-flex",
			"box-ordinal-group", "box-lines", "font-optical-sizing",
			// zoom(描画時拡大)とtext-underline-positionは2026-08-29に実装し、無視リストから外した
			"image-rendering", "ime-mode", "font-smooth", "line-clamp-fallback");

	/** 接頭辞を外した名前が{@link #IGNORED_PROPERTIES}にあるか。 */
	static boolean isIgnored(final String name) {
		if (name == null) {
			return false;
		}
		String bare = name.toLowerCase(java.util.Locale.ROOT);
		for (final String prefix : new String[] { "-webkit-", "-moz-", "-ms-", "-o-", "-khtml-" }) {
			if (bare.startsWith(prefix)) {
				bare = bare.substring(prefix.length());
				break;
			}
		}
		return IGNORED_PROPERTIES.contains(bare);
	}

	private static boolean isCustomPropertyName(String name) {
		return name.length() > 2 && name.charAt(0) == '-' && name.charAt(1) == '-';
	}

	/**
	 * @supports (name: value) の判定用。プロパティ名が登録されており、かつ
	 * 与えられた値をそのプロパティとして解析できるかを試すだけで、実際の値は
	 * 破棄します(通常の{@link #parseDeclaration}と違い、失敗しても警告を
	 * 出しません——@supports は「対応していない」ことを調べるための構文であり、
	 * 未対応であること自体が正常な結果のため)。
	 */
	public final boolean supports(String name, List<CssToken> value, UserAgent ua, URI uri) {
		if (isCustomPropertyName(name)) {
			// カスタムプロパティの宣言文法は常に妥当(CSS仕様: -- で始まる
			// プロパティは任意のトークン列を受理する)
			return true;
		}
		PropertyInfo ph = this.getPropertyParser(name.toLowerCase());
		if (ph == null) {
			return false;
		}
		if (VarSubstitution.containsEnvReference(value)) {
			value = VarSubstitution.substituteEnv(value);
			if (value == null) {
				return false;
			}
		}
		if (VarSubstitution.containsVarReference(value)) {
			// var()の実際の値は要素ごとに異なりうるため、ここでは評価せず
			// 常にtrueとする(ブラウザの挙動と同じ: var()を含む宣言は
			// @supportsの判定では無条件にサポートありとみなす)
			return true;
		}
		try {
			return ph.parse(new TokenStream(value), ua, uri, false) != null;
		} catch (PropertyException e) {
			return false;
		}
	}
}
