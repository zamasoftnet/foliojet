package net.zamasoft.foliojet.ua.props;

/**
 * Paged SVGのページSVGの書き出し方式です。
 *
 * <p>
 * 既定は{@link #DIRECT}です。出力の中身は{@link #BATIK}と同じで、
 * 速く小さくなります。{@code BATIK}は退避先として残してあります。
 * </p>
 */
public enum PagedSvgWriter implements PropCode {
	/**
	 * Batikの{@code SVGGraphics2D}でDOMを組み、最後に直列化します。
	 *
	 * <p>
	 * SVG 1.0のDTDを指す{@code DOCTYPE}が付くので、DTDを読む処理系は
	 * ページを開くたびw3.orgへ問い合わせに行きます。
	 * </p>
	 */
	BATIK,

	/**
	 * DOMを作らず、描画が来た順にSVGを書き出します。既定。
	 */
	DIRECT;
}
