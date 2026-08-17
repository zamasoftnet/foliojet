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
 * <b>バイト位置</b>を書く必要があるからで、SVGにはその必要がない。規格が
 * 置き場所を自由にしてくれている以上、末尾でよい。<b>採らない理由はこれで足りる。</b>
 * </p>
 *
 * <p>
 * 先頭へ回せば、最終バイト列が「後から確定する先頭 + 先に流れた本体」に
 * なるので、ページ全体を一度抱えることになり、溜めない利点を打ち消す。
 * ついでにmanifestの{@code svgSha256}も流しながら取れなくなるが、
 * <b>こちらは副次的な話</b>——ページのハッシュはURIに使っておらず(URIは連番)、
 * いまのところ読む実装も無い。制約として扱うほどのものではない。
 * </p>
 *
 * <p>
 * 速さのために使う余地も無い。risuでの実測(build 19021、314ページ、1パス、
 * 7回中央値)では変換1,397msのうち約1,250msが組版で、形式ごとの書き出しの差は
 * PDFに対して155msしかない。フラグメントはその155msを削るものではなく、
 * 抱える量を増やす方向に働く。
 * </p>
 *
 * <p>
 * 意味があるとすれば順序のほうで、{@code manifest.json}を先頭で予約して
 * 最後に埋めれば、受け手が総ページ数・綴じ方向・目次を先に知ったうえで
 * 1ページ目から描き始められる。総時間ではなく、最初に使える情報が届くまでの
 * 時間の話である。
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
