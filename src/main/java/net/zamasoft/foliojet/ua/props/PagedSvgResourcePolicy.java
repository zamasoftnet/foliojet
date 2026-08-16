package net.zamasoft.foliojet.ua.props;

/**
 * Paged SVGの共有資源(フォントサブセット・画像)を出力するかどうかです。
 * <p>
 * 同じ本を文字サイズや画面サイズだけ変えて組み直す場合、フォントサブセットと画像は
 * 前回と同じものになります。<code>OMIT</code>を指定すると、ページSVGからの参照と
 * manifestの記載はそのままに、実体のバイト列だけを出力しません。受け手は前回の
 * 出力から同じURIの資源を再利用します。
 */
public enum PagedSvgResourcePolicy implements PropCode {
	/**
	 * 資源の実体を出力します。
	 */
	EMIT,

	/**
	 * 資源の実体を出力しません。参照とmanifestの記載は残ります。
	 */
	OMIT;
}
