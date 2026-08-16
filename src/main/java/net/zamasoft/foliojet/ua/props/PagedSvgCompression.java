package net.zamasoft.foliojet.ua.props;

/**
 * Paged SVGのページSVGの圧縮方法です。
 */
public enum PagedSvgCompression implements PropCode {
	/**
	 * 圧縮しません。ページは<code>pages/NNNN.svg</code>になります。
	 */
	NONE,

	/**
	 * gzip圧縮します。ページは<code>pages/NNNN.svgz</code>になります。
	 */
	GZIP;
}
