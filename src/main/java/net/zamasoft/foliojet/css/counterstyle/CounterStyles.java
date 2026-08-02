package net.zamasoft.foliojet.css.counterstyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.css.util.GeneratedValueUtils;
import net.zamasoft.foliojet.css.value.CounterStyleValue;
import net.zamasoft.foliojet.css.value.ListStyleTypeSource;
import net.zamasoft.foliojet.css.value.ListStyleTypeValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 文書ごとの著者定義カウンタスタイル({@code @counter-style})の登録簿
 * であり、<b>カウンタ整形の入口</b>です(2026-08-02——PLAN §2の5位)。
 *
 * <p>
 * カウンタスタイルはコード({@code short})で持ち回る既存設計を保つ。
 * 組み込みは{@link ListStyleTypeValue}の定数、著者定義は
 * {@link ListStyleTypeValue#FIRST_CUSTOM}以降を名前ごとに割り当てる。
 * 名前→コードの割り当ては{@code @counter-style}規則より先に
 * {@code list-style-type: foo}を読んでも成立する(定義は後から埋まる)
 * ——CSSに規則の出現順の制約はないため。
 * </p>
 *
 * <p>
 * 整形はここが窓口になり、組み込みコードは{@link GeneratedValueUtils}へ
 * 委譲する。依存を一方向(counterstyle → util)に保つための配置で、
 * {@code fallback}/{@code extends}が組み込みスタイルを指す場合も
 * 同じ窓口で解決できる。
 * </p>
 */
public final class CounterStyles {

	/**
	 * カウンタスタイル名を値へ解決します(組み込みならその定数、
	 * それ以外は著者定義として文書ごとのコードを割り当てる)。
	 */
	public static ListStyleTypeSource styleValue(final UserAgent ua, final String name) {
		final ListStyleTypeValue builtin = GeneratedValueUtils.toListStyleType(name);
		if (builtin != null) {
			return builtin;
		}
		return new CounterStyleValue(ua.getUAContext().getCounterStyles().code(name));
	}

	/** カウンタスタイル名をコードへ解決します。 */
	public static short styleCode(final UserAgent ua, final String name) {
		return styleValue(ua, name).getListStyleType();
	}

	/** 文書のカウンタスタイル登録簿です。 */
	public static CounterStyles of(final UserAgent ua) {
		return ua.getUAContext().getCounterStyles();
	}

	private final Map<String, Short> nameToCode = new HashMap<>();

	private final List<CounterStyleDef> defs = new ArrayList<>();

	/**
	 * 名前に対応するコードを返します(未知の名前にも割り当てる——
	 * 定義がなければ整形時に{@code decimal}へ落ちる、仕様どおりの扱い)。
	 */
	public synchronized short code(final String name) {
		final String key = name.toLowerCase();
		final Short existing = this.nameToCode.get(key);
		if (existing != null) {
			return existing;
		}
		final short code = (short) (ListStyleTypeValue.FIRST_CUSTOM + this.defs.size());
		if (code < ListStyleTypeValue.FIRST_CUSTOM) {
			// 割り当て枯渇(現実には起きない)——decimalへ縮退する
			return ListStyleTypeValue.DECIMAL;
		}
		this.nameToCode.put(key, code);
		this.defs.add(null);
		return code;
	}

	/** 定義を登録します(同名の再定義は後勝ち——CSSの規則どおり)。 */
	public synchronized void define(final String name, final CounterStyleDef def) {
		final short code = this.code(name);
		if (code >= ListStyleTypeValue.FIRST_CUSTOM) {
			this.defs.set(code - ListStyleTypeValue.FIRST_CUSTOM, def);
		}
	}

	/** コードに対応する定義です(未定義ならnull)。 */
	public synchronized CounterStyleDef def(final short code) {
		final int index = code - ListStyleTypeValue.FIRST_CUSTOM;
		return index >= 0 && index < this.defs.size() ? this.defs.get(index) : null;
	}

	/**
	 * カウンタを整形します。組み込みスタイルは{@link GeneratedValueUtils}
	 * へ委譲し、著者定義は定義に従います。記号マーカー(disc等、文字列で
	 * 表せないもの)はnullを返します。
	 */
	public String format(final int number, final short style) {
		return this.format(number, style, 0);
	}

	private String format(final int number, final short style, final int depth) {
		if (style < ListStyleTypeValue.FIRST_CUSTOM) {
			return GeneratedValueUtils.format(number, style);
		}
		final CounterStyleDef def = this.def(style);
		if (def == null) {
			// 定義のない名前は decimal(§CSS Counter Styles 3 §7)
			return String.valueOf(number);
		}
		final String str = def.format(number, this, depth);
		return str != null ? str : String.valueOf(number);
	}

	/** {@code fallback}/{@code extends}の解決です(名前で辿る)。 */
	String formatByName(final String name, final int number, final int depth) {
		if (depth > 8) {
			return String.valueOf(number);
		}
		final ListStyleTypeValue builtin = GeneratedValueUtils.toListStyleType(name);
		if (builtin != null) {
			return GeneratedValueUtils.format(number, builtin.getListStyleType());
		}
		return this.format(number, this.code(name), depth);
	}

	/** マーカーの前置文字列です(組み込みは空)。 */
	public String prefix(final short style) {
		final CounterStyleDef def = style >= ListStyleTypeValue.FIRST_CUSTOM ? this.def(style) : null;
		return def == null ? "" : def.prefix;
	}

	/** マーカーの後置文字列です(組み込みは{@code "."}等)。 */
	public String suffix(final short style) {
		if (style < ListStyleTypeValue.FIRST_CUSTOM) {
			return GeneratedValueUtils.period(style);
		}
		final CounterStyleDef def = this.def(style);
		return def == null ? "." : def.suffix;
	}
}
