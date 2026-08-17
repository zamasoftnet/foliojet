package net.zamasoft.foliojet.ua.props;

/**
 * ページSVGとページJSONをgzipで縮めて返すかどうかです。
 *
 * <p>
 * 縮むのは文字で書かれた結果だけです。共有WOFF2とPNG/JPEGは既に圧縮済みで、
 * gzipをかけても縮みません(実測でWOFF2は0.1%増、PNGは1.7%減)。だから
 * これらには一切かけません。
 * </p>
 *
 * <p>
 * 314ページの縦組み書籍の実測では、ページSVGが78.2%、ページJSONが79.1%縮み、
 * 出力全体では14.96MBから6.35MBへ<b>57.5%減</b>になります。圧縮そのものの
 * 手間は628件で0.1秒ほどです。
 * </p>
 *
 * <p>
 * 速い回線では往復時間がほとんど変わりません(613Mbpsの経路で転送は0.2秒程度)。
 * 効くのは、遅い回線・従量課金の回線・受け取ったまま保管する場合です。
 * </p>
 */
public enum PagedSvgCompression implements PropCode {
	/**
	 * そのまま返します。
	 */
	NONE,

	/**
	 * ページSVGを{@code .svgz}、ページJSONを{@code .json.gz}として
	 * gzipで縮めて返します。{@code manifest.json}は読み口なので縮めません。
	 */
	GZIP;
}
