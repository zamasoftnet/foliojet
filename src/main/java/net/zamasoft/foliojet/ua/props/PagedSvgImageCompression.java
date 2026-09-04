package net.zamasoft.foliojet.ua.props;

/**
 * ページ分割SVGの共有画像の圧縮方針です(2026-09-03、cti.li の要望)。
 *
 * <p>
 * 既定の {@code none} は取ってきた画像をそのまま出します(JPEG は JPEG のまま、
 * それ以外は PNG)。{@code jpeg} は透明部分の無いラスタ画像を JPEG(品質 0.8)に
 * 再圧縮します。小さい画像({@code output.paged-svg.image.compression.lossless}
 * の閾値以下)と透明部分のある画像は可逆(PNG)のままです。SVG 画像は
 * ベクタのままなので対象外です。
 * </p>
 */
public enum PagedSvgImageCompression implements PropCode {
	/** そのまま出します。 */
	NONE,
	/** 透明部分の無い大きなラスタ画像を JPEG に再圧縮します。 */
	JPEG;
}
