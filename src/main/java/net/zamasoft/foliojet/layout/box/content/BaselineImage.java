package net.zamasoft.foliojet.layout.box.content;

/**
 * 自分の基準線を持つ画像です(2026-10-04)。
 *
 * <p>
 * 行の中の置換要素は、ふつう<b>下端(マージンの下端)を基準線</b>に置く。
 * 数式(MathML)は字と同じく基準線の下へ下がる部分(添字・括弧・y の
 * 下の出)を持つので、下端を置くと式全体が浮く。この印を持つ画像は
 * {@link #getDescent()}のぶん下げて置く(横書きのとき。縦書きの行では
 * 従来どおり中央に置く)。
 * </p>
 */
public interface BaselineImage {
	/**
	 * 画像の下端から基準線までの距離(画像の単位、{@code getHeight()}と同じ)。
	 * 0 なら下端が基準線。
	 */
	public double getDescent();
}
