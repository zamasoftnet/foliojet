package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.font.FontSize;
import net.zamasoft.foliojet.css.impl.property.font.LineHeight;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.CSSFloatValue;
import net.zamasoft.foliojet.css.value.InitialLetterValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code initial-letter}です(css-inline-3、2026-08-20新設)。
 *
 * <p>
 * {@code ::first-letter}(または先頭のインラインボックス)に指定された
 * ドロップキャップの行数と沈み。適用は{@code StyleEventMachine}の
 * first-letter分岐が{@link #desugar}で行う——占有行数から文字寸法を
 * 計算し、既存のfloat機構(float:left+回り込み)へ脱糖する。Firefoxが
 * 未実装のまま(非Baseline)の印刷差別化機能で、Prince/AH/WebKitが対応。
 * </p>
 *
 * <p>
 * <b>寸法の近似</b>: cap高整列は「大文字の高さ=フォント寸法の0.7倍」の
 * 慣用近似で行う(実フォントのOS/2 sCapHeightはスタイル計算段階では
 * 参照できない)。目標cap高 = (N-1)×行送り + 親のcap高。
 * </p>
 */
public class InitialLetter extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new InitialLetter();

	/** 慣用のcap-height比(cap高/フォント寸法)。 */
	private static final double CAP_RATIO = 0.7;

	public static InitialLetterValue get(final CSSStyle style) {
		final Value value = style.get(InitialLetter.INFO);
		return value instanceof InitialLetterValue v ? v : null;
	}

	/**
	 * first-letterスタイルへの脱糖です。{@code initial-letter}が指定されて
	 * いれば、文字寸法・行の高さ・floatを設定して既存機構に載せる。
	 *
	 * @param firstLetterStyle first-letter擬似要素のスタイル(適用済み)
	 * @param parentStyle 親(段落)のスタイル
	 */
	public static void desugar(final CSSStyle firstLetterStyle, final CSSStyle parentStyle) {
		final InitialLetterValue v = get(firstLetterStyle);
		if (v == null) {
			return;
		}
		final UserAgent ua = firstLetterStyle.getUserAgent();
		final double parentFontSize = FontSize.get(parentStyle);
		final double lineHeight = LineHeight.get(parentStyle);
		// 実フォントのcap高比を使う(2026-08-20、利用者了承のうえ厳密化)。
		// OS/2 sCapHeight由来(units/em=1000規約)。異常値(全欠損の0や
		// em超え)は慣用近似0.7へフォールバック
		double capRatio = CAP_RATIO;
		try {
			// 親スタイルの確定済みFontStyleで解決する——firstLetterStyle側は
			// この後font-size等をsetするため、ここでgetFontStyle()を呼ぶと
			// 未確定状態が固定される(実測でドロップキャップが親サイズ相当に
			// 潰れた)。first-letterでのfont-family上書きは稀なので親で足りる
			final short cap = ua.getFontManager().getFontListMetrics(parentStyle.getFontStyle())
					.getFontMetrics(0).getFontSource().getCapHeight();
			if (cap > 200 && cap <= 1000) {
				capRatio = cap / 1000.0;
			}
		} catch (final RuntimeException e) {
			// フォント解決に失敗しても近似で続行
		}
		// 目標cap高: (N-1)行ぶんの行送り+親のcap高
		final double targetCap = (v.lines() - 1) * lineHeight + parentFontSize * capRatio;
		final double size = targetCap / capRatio;
		firstLetterStyle.set(FontSize.INFO, AbsoluteLengthValue.create(ua, size));
		// floatの箱高がsink行数ちょうどになるよう行の高さを絶対値で固定する
		// (文字寸法のまま=RealValue.ONEだと箱がsink行を超え、回り込みが
		// 1行余分に及ぶ——Chrome対照で確認)
		firstLetterStyle.set(LineHeight.INFO, AbsoluteLengthValue.create(ua, v.sink() * lineHeight));
		if (v.sink() >= v.lines()) {
			// 通常のドロップキャップ: floatで回り込み
			firstLetterStyle.set(net.zamasoft.foliojet.css.impl.property.box.CSSFloat.INFO,
					CSSFloatValue.LEFT_VALUE);
		}
		// sink < lines(raised cap)はインラインの拡大のみ(floatなし)——
		// ベースラインに立つ形が仕様の近似になる
	}

	protected InitialLetter() {
		super("initial-letter");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.NORMAL;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident) {
			if (ident.is("normal")) {
				return KeywordValue.NORMAL;
			}
			throw new PropertyException();
		}
		final double lines;
		if (lu instanceof CssToken.Num num && num.value() >= 1) {
			lines = num.value();
		} else {
			throw new PropertyException();
		}
		int sink = (int) Math.floor(lines);
		if (tokens.hasNext()) {
			final CssToken second = tokens.next();
			if (second instanceof CssToken.Num num2 && num2.integer() && num2.value() >= 1) {
				sink = (int) num2.value();
			} else if (second instanceof CssToken.Ident id2 && id2.is("drop")) {
				sink = (int) Math.floor(lines);
			} else if (second instanceof CssToken.Ident id3 && id3.is("raise")) {
				sink = 1;
			} else {
				throw new PropertyException();
			}
		}
		return new InitialLetterValue(lines, sink);
	}
}
