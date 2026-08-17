package net.zamasoft.foliojet.ua.props;

/**
 * ページごとの読み取り用データ(ページJSON)を返すかどうかです。
 *
 * <p>
 * ページJSONには、そのページの文字列と位置、リンク、アンカーが入ります。
 * 見た目はページSVGが持つので、<b>読み取り用の面が要らない使い方なら
 * 止められます</b>。印刷や画像化のように、検索も選択もしない用途がこれに
 * あたります。
 * </p>
 *
 * <p>
 * 止めると、ページSVGの{@code data-copper-text}と{@code aria-label}に残る
 * 文字列だけになります。ただしSVG上の文字は共有サブセットに合わせた私用領域の
 * 符号位置で書かれているため、<b>文字ノードをそのまま読んでも元の文字列には
 * なりません</b>。検索・選択・コピー・リンク処理を実装するなら、ページJSONを
 * 出したままにしてください。
 * </p>
 */
public enum PagedSvgPageData implements PropCode {
	/**
	 * 返します。
	 */
	EMIT,

	/**
	 * 返しません。{@code manifest.json}の{@code data}と{@code dataSha256}も落ちます。
	 */
	OMIT;
}
