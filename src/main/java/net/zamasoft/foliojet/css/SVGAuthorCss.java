package net.zamasoft.foliojet.css;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.VarSubstitution;

/**
 * インラインSVGへ持ち込む著者CSSのSVG向け部分集合です(2026-08-07)。
 *
 * <p>
 * インラインSVGは独立文書としてBatikに渡され、HTML文書のスタイルシートが
 * 届かない——CSSクラスでfill/strokeを指定するアイコンがSVG既定の
 * fill=blackで黒く塗り潰れる(qiitaのいいねボタンで発覚)。そこで
 * スタイルシート解析時にSVGプレゼンテーション系の宣言を含む規則だけを
 * ここへ集め、SVG文書へ&lt;style&gt;として注入してBatik側で
 * カスケードさせる({@code CSSStyleSheetBuilder.collectSVGStyleRule}が
 * 収集、{@code SVGInlineObject.getImage}が注入)。
 * </p>
 *
 * <p>
 * 宣言値は生トークン列のまま保持する。モダンサイトのアイコン色は
 * ほぼ例外なく {@code var(--color-*)} 経由で指定される(qiitaの実測)ため、
 * var()を含む宣言を捨てると本命が全部落ちる——解決は注入時まで遅延し、
 * そのSVG要素のスタイル({@code CSSStyle})の文脈でカスタムプロパティを
 * 引く({@link VarSubstitution})。重複追加はキーで吸収する(多重パスで
 * 同じスタイルシートが再解析されるため)。
 * </p>
 */
public final class SVGAuthorCss {

	/** 1宣言。tokensはvar()を含み得る生トークン列。 */
	public record Decl(String property, List<CssToken> tokens, boolean important) {
	}

	/** 1規則(Batik安全形へ濾過済みのセレクタ+宣言群)。 */
	public record Rule(String selectors, List<Decl> declarations) {
	}

	private final Map<String, Rule> rules = new LinkedHashMap<String, Rule>();

	public synchronized void addRule(Rule rule) {
		final StringBuilder key = new StringBuilder(rule.selectors());
		for (final Decl d : rule.declarations()) {
			key.append('{').append(d.property()).append(':').append(d.tokens()).append(d.important());
		}
		this.rules.putIfAbsent(key.toString(), rule);
	}

	public synchronized boolean isEmpty() {
		return this.rules.isEmpty();
	}

	/**
	 * Batikへ渡すCSSテキストを組み立てます。var()を含む宣言は
	 * {@code varContext}(注入先SVG要素のスタイル)の文脈で解決し、
	 * 解決できないもの(未定義かつフォールバックなし等)は捨てます。
	 */
	public synchronized String toCssText(CSSStyle varContext) {
		if (this.rules.isEmpty()) {
			return "";
		}
		final StringBuilder buff = new StringBuilder();
		for (final Rule rule : this.rules.values()) {
			StringBuilder decls = null;
			for (final Decl d : rule.declarations()) {
				List<CssToken> tokens = d.tokens();
				if (VarSubstitution.containsVarReference(tokens)) {
					if (varContext == null) {
						continue;
					}
					tokens = VarSubstitution.substitute(tokens, varContext);
					if (tokens == null) {
						continue;
					}
				}
				final String value = serialize(tokens);
				if (value == null || value.isEmpty() || value.indexOf('<') >= 0) {
					// Batikが読めない値・空値・CDATA/要素境界を壊し得る値は
					// 宣言ごと捨てる。**1つでも不正な値があるとBatikは
					// スタイルシート全体を無効にする**(2026-08-07に実測——
					// rgb(0 0 255)の空白区切りで全滅した)ので、ここの防御が
					// 注入の成立条件そのもの。加えて注入側は規則ごとに
					// <style>を分け、万一の不正が1規則に閉じるようにしている
					continue;
				}
				if ("display".equals(d.property())
						&& !BATIK_DISPLAY_VALUES.contains(value.toLowerCase(java.util.Locale.ROOT))) {
					// BatikのCSS2世代のdisplay検証はflex/grid等を拒否し、
					// シート全体を無効化する(qiitaのdisplay:flexで実測)
					continue;
				}
				if (decls == null) {
					decls = new StringBuilder();
				}
				decls.append(d.property()).append(':').append(value);
				if (d.important()) {
					decls.append(" !important");
				}
				decls.append(';');
				// rgba()/rgb(r g b / a)のアルファはSVG 1.1の色に表現がない。
				// 捨てると濃くなりすぎる(qiitaの輪郭はrgb(0 0 0 / 12%))ので、
				// fill/stroke/stop-colorに限り対応する*-opacityへ移す
				final Double alpha = extractAlpha(tokens);
				final String opacityProp = opacityPropertyFor(d.property());
				if (alpha != null && opacityProp != null) {
					decls.append(opacityProp).append(':').append(alpha);
					if (d.important()) {
						decls.append(" !important");
					}
					decls.append(';');
				}
			}
			if (decls != null) {
				buff.append(rule.selectors()).append('{').append(decls).append("}\n");
			}
		}
		return buff.toString();
	}

	/**
	 * BatikのCSS2世代パーサが読める形へ値を直列化します。読めるか確信の
	 * 持てないトークンを含む宣言はnull(=宣言ごと破棄)にする——Batikは
	 * 1つの不正値でスタイルシート全体を無効にするため、通す側を狭く取る。
	 */
	private static String serialize(List<CssToken> tokens) {
		final List<String> parts = new ArrayList<String>(tokens.size());
		for (final CssToken token : tokens) {
			final String part = serializeToken(token);
			if (part == null) {
				return null;
			}
			parts.add(part);
		}
		return String.join(" ", parts).trim();
	}

	private static String serializeToken(CssToken token) {
		if (token instanceof CssToken.Num || token instanceof CssToken.Percent || token instanceof CssToken.Dim
				|| token instanceof CssToken.Str) {
			return token.toString();
		}
		if (token instanceof CssToken.Ident ident) {
			return ident.name();
		}
		if (token instanceof CssToken.Uri uri) {
			// url(#id)等。SVG文書内のグラデーション参照に要る
			return "url(" + uri.uri() + ")";
		}
		if (token == CssToken.Op.COMMA) {
			return ",";
		}
		if (token == CssToken.Op.SLASH) {
			return "/";
		}
		if (token instanceof CssToken.Func func) {
			// 色関数だけ通す。#hexはトークン化の段階でrgb()関数になるが、
			// 素朴に空白区切りへ直列化するとBatikが読めない——カンマ区切りの
			// rgb(r,g,b)へ組み直す。rgba()のアルファはBatik(SVG 1.1の色)に
			// 表現がないため落とす(近似)。それ以外の関数(calc等)は
			// Batikが解釈できないので宣言ごと破棄
			if (func.is("rgb") || func.is("rgba")) {
				final List<Integer> components = new ArrayList<Integer>(4);
				for (final CssToken arg : func.args()) {
					if (arg instanceof CssToken.Num num) {
						components.add((int) Math.round(num.value()));
					} else if (arg instanceof CssToken.Percent percent) {
						components.add((int) Math.round(percent.value() * 2.55));
					}
				}
				if (components.size() < 3) {
					return null;
				}
				return "rgb(" + clamp255(components.get(0)) + "," + clamp255(components.get(1)) + ","
						+ clamp255(components.get(2)) + ")";
			}
			return null;
		}
		// Keyword(inherit等)・unicode-range等は対象外
		return null;
	}

	private static int clamp255(int v) {
		return Math.max(0, Math.min(255, v));
	}

	/** 値が単一のrgba()等でアルファを持つならそれを返します(0..1)。 */
	private static Double extractAlpha(List<CssToken> tokens) {
		if (tokens.size() != 1 || !(tokens.get(0) instanceof CssToken.Func func)
				|| !(func.is("rgb") || func.is("rgba"))) {
			return null;
		}
		final List<Double> nums = new ArrayList<Double>(4);
		final List<Boolean> pcts = new ArrayList<Boolean>(4);
		for (final CssToken arg : func.args()) {
			if (arg instanceof CssToken.Num num) {
				nums.add(num.value());
				pcts.add(Boolean.FALSE);
			} else if (arg instanceof CssToken.Percent percent) {
				nums.add(percent.value());
				pcts.add(Boolean.TRUE);
			}
		}
		if (nums.size() < 4) {
			return null;
		}
		double a = nums.get(3).doubleValue();
		if (pcts.get(3).booleanValue()) {
			a /= 100d;
		}
		return Double.valueOf(Math.max(0d, Math.min(1d, a)));
	}

	/** BatikのCSS2世代のdisplay検証が受理する値。 */
	private static final java.util.Set<String> BATIK_DISPLAY_VALUES = java.util.Set.of( //
			"none", "inline", "block", "list-item", "run-in", "compact", "marker", //
			"table", "inline-table", "table-row-group", "table-header-group", "table-footer-group", //
			"table-row", "table-column-group", "table-column", "table-cell", "table-caption", "inherit");

	private static String opacityPropertyFor(String property) {
		switch (property) {
		case "fill":
			return "fill-opacity";
		case "stroke":
			return "stroke-opacity";
		case "stop-color":
			return "stop-opacity";
		default:
			return null;
		}
	}
}
