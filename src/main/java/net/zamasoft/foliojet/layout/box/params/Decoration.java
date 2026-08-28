package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * 継承・伝播する文字装飾線の描画属性です。
 *
 * <p>
 * 2026-08-29から色だけでなく線種・太さ・下線位置も線ごとに運ぶ
 * ({@link Line})。CSSの装飾線は指定した要素が「所有」し、子孫の
 * テキストへ伝播しても線種・太さ・色は所有要素のものを使う
 * (css-text-decoration-3 §2.2)ので、フラグを立てた要素のparamsから
 * 作った値を{@code AbstractTextBox.setDecoration}で子へ渡していく。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: Decoration.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Decoration {
	/**
	 * 1本の装飾線の描画属性。
	 *
	 * @param color     線の色
	 * @param style     線種({@code AbstractTextParams.DECORATION_STYLE_*})
	 * @param thickness 太さの絶対長(0なら自動=フォントサイズ×decorationThickness)
	 * @param offset    下線のずらし(NaNなら自動。下線以外は無視)
	 * @param position  下線の位置({@code AbstractTextParams.UNDERLINE_POSITION_*}。
	 *                  下線以外は無視)
	 */
	public record Line(Color color, byte style, double thickness, double offset, byte position) {
		/** 所有要素のparamsから線の属性を作る。 */
		public static Line of(final Color color, final AbstractTextParams params) {
			return new Line(color, params.decorationStyle, params.decorationThicknessLength, params.underlineOffset,
					params.underlinePosition);
		}
	}

	public final Line underline;

	public final Line overline;

	public final Line lineThrough;

	public Decoration(Line underline, Line overline, Line lineThrough) {
		this.underline = underline;
		this.overline = overline;
		this.lineThrough = lineThrough;
	}
}
