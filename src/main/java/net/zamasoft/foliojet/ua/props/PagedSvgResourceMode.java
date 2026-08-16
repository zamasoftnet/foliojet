package net.zamasoft.foliojet.ua.props;

/**
 * Paged SVGの共有資源を、別ファイルとして参照するか、ページSVGへ埋め込むかです。
 *
 * <p>
 * ディレクトリへ出すなら参照が使えます。同じ画像が何度出てきても実体は1つで済み、
 * 受け手は変換のたびに変わらないものを取り直さずに済みます。
 * </p>
 *
 * <p>
 * 埋め込みは、ページSVG 1枚だけで完結させたいときに使います。相対URIを保てない
 * 送り方(1ファイルだけを別の場所へ渡す、参照を辿れないビューア)で要ります。
 * 同じ画像がページごとに複製されるので、全体の容量は増えます。
 * </p>
 */
public enum PagedSvgResourceMode implements PropCode {
	/**
	 * 別ファイルとして出し、相対URIで参照します。
	 */
	REFERENCE,

	/**
	 * ページSVGへ{@code data:}で埋め込みます。
	 */
	EMBED;
}
