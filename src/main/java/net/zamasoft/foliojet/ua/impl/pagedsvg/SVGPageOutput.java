package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.io.IOException;
import java.io.Writer;

/**
 * ページ1枚のSVGを、<b>溜めずに</b>書き出します。
 *
 * <p>
 * 順番は「ルート要素 → 本体 → {@code defs} → 閉じ」です。クリップ経路・
 * グラデーション・{@code @font-face}は描画の途中で判明しますが、
 * <b>SVG 1.1では{@code defs}は文書のどこに置いてもよく、それより前に現れる
 * 要素からも参照できます</b>。だから末尾に置いて構いません。
 * </p>
 *
 * <p>
 * <b>フラグメント出力を使って{@code defs}を先頭へ回すことは検討したうえで
 * 採らなかった。</b> PDFが相互参照表を後から埋めるのは、オブジェクトの
 * <b>バイト位置</b>を書く必要があるからで、SVGにはその必要がない。しかも
 * 先頭へ回すと、最終バイト列が「後から確定する先頭 + 先に流れた本体」に
 * なるため、<b>manifestの{@code svgSha256}を流しながら計算できなくなる</b>
 * ——ページ全体を抱え直すことになり、溜めない利点を打ち消す。
 * 規格が置き場所を自由にしてくれている以上、末尾でよい。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
final class SVGPageOutput implements AutoCloseable {
	private final Writer out;

	private final SVGWriter writer;

	SVGPageOutput(final Writer out, final double width, final double height) throws IOException {
		this.out = out;
		this.writer = new SVGWriter(out);
		out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		out.write("<svg xmlns=\"" + SVGWriter.SVG_NS + "\" xmlns:xlink=\"" + SVGWriter.XLINK_NS
				+ "\" version=\"1.1\"");
		out.write(" width=\"" + SVGWriter.number(width) + "\"");
		out.write(" height=\"" + SVGWriter.number(height) + "\"");
		out.write(" viewBox=\"0 0 " + SVGWriter.number(width) + ' ' + SVGWriter.number(height) + "\">");
	}

	SVGWriter writer() {
		return this.writer;
	}

	@Override
	public void close() throws IOException {
		this.writer.writeDefs(this.out);
		this.out.write("</svg>");
		this.out.flush();
	}
}
