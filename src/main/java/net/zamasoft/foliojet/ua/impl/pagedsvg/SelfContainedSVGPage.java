package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.font.FontManager;

/**
 * <b>1枚で完結するSVG</b>を直接書き出す口です(B-1、2026-08-29の利用者要望)。
 *
 * <p>
 * 中身はページ分割SVGとまったく同じ書き手({@link DirectPagedSVGGC}・
 * {@link SVGWriter}・{@link WebFontSubset})です。違うのは<b>資源の届け方</b>
 * だけで、ここではフォントも画像も{@code data:}でSVGの中へ入れます。
 * 単一SVGは1枚で持ち歩くためのものなので、外部参照を残せません。
 * </p>
 *
 * <p>
 * このクラスだけを公開にしてあります。書き手一式は実装の詳細なので
 * パッケージの外へは出しません。
 * </p>
 *
 * <p>
 * <b>制限</b>: 字形はGIDをそのまま出すため私用領域(PUA)の符号で書かれます。
 * 見た目は正確ですが、複写すると私用領域の文字になります。元の文字列は
 * {@code aria-label}と{@code data-copper-text}に載っているので、
 * 読み上げと検索はそちらで効きます。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class SelfContainedSVGPage implements AutoCloseable {

	private final PagedSVGResources resources;

	private final SVGPageOutput page;

	private final DirectPagedSVGGC gc;

	public SelfContainedSVGPage(final Writer out, final double width, final double height,
			final FontManager fonts) throws IOException {
		// 資源は結果として出さない。埋め込みではemitterは呼ばれないが、
		// 呼ばれたら設計の誤りなので黙って捨てずに落とす
		this.resources = new PagedSVGResources((uri, mimeType, bytes) -> {
			throw new IllegalStateException("自己完結SVGは資源を別に出しません: " + uri);
		});
		this.resources.setResourceMode(net.zamasoft.foliojet.ua.props.PagedSvgResourceMode.EMBED);
		this.page = new SVGPageOutput(out, width, height);
		this.gc = new DirectPagedSVGGC(this.page.writer(), fonts, this.resources,
				new PagedSVGResources.PageData(1, width, height));
	}

	public GC gc() {
		return this.gc;
	}

	/**
	 * サブセットを組み立てて{@code @font-face}へ差し込み、SVGを閉じます。
	 *
	 * <p>
	 * 組み立てられるのは<b>この時点</b>——ページに出た字形が出そろってから
	 * です。{@code defs}を末尾に置いてあるので、ここで確定した{@code src}が
	 * そのまま書けます({@link SVGPageOutput}参照)。
	 * </p>
	 */
	@Override
	public void close() throws IOException {
		final Map<String, String> sources = this.resources.inlineFontSources();
		this.page.writer().setFontSrc(uri -> sources.getOrDefault(uri, uri));
		this.page.close();
	}
}
