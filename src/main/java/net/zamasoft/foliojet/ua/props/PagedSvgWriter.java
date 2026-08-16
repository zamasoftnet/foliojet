package net.zamasoft.foliojet.ua.props;

/**
 * Paged SVGのページSVGの書き出し方式です。
 */
public enum PagedSvgWriter implements PropCode {
	/**
	 * Batikの{@code SVGGraphics2D}でDOMを組み、最後に直列化します。
	 */
	BATIK,

	/**
	 * DOMを作らず、描画が来た順にSVGを書き出します。
	 */
	DIRECT;
}
