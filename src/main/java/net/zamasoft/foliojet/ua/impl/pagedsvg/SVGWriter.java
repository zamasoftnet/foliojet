package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * SVGを<b>組み立てずに書き出す</b>ための最小の書き手です。
 *
 * <p>
 * Batikの{@code SVGGraphics2D}は描画のたびにDOMを作り、最後に直列化します。
 * ここではDOMを作らず、描画が来た順に文字列を出力へ流します。
 * </p>
 *
 * <p>
 * <b>defsの扱いがフラグメント出力の使いどころです。</b> クリップ経路・
 * グラデーション・{@code @font-face}は<b>描画の途中で判明する</b>のに、
 * 見る側にとっては先頭にあるほうが都合がよい(参照を解決してから本体を読める)。
 * そこで先頭に1つフラグメントを予約し、本体を第2フラグメントへ流しながら、
 * ページを閉じるときに予約したほうを埋めます。PDFの相互参照表を後から
 * 埋めるのと同じ形です。
 * </p>
 *
 * <p>
 * SVG 1.1では{@code defs}は文書のどこに置いてもよく、<b>それより前に現れる
 * 要素からも参照できます</b>。つまり末尾に置いても規格上は正しく、
 * フラグメントは「先頭に置くため」の手段です。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
final class SVGWriter {
	static final String SVG_NS = "http://www.w3.org/2000/svg";

	static final String XLINK_NS = "http://www.w3.org/1999/xlink";

	/** 座標の桁数。Batikの既定(6桁)に合わせる。 */
	private static final int PRECISION = 6;

	private final Writer out;

	/** 先頭へ回す定義。ページを閉じるときにまとめて書く。 */
	private final List<String> defs = new ArrayList<>();

	private int nextId = 0;

	SVGWriter(final Writer out) {
		this.out = out;
	}

	/** 定義を1つ登録し、参照用のidを返します。 */
	String addDef(final String element) {
		this.defs.add(element);
		return null;
	}

	String nextId(final String prefix) {
		return prefix + (++this.nextId);
	}

	/** {@code @font-face}の並び。共有WOFF2を参照する。 */
	private final java.util.Map<String, String> fontFaces = new java.util.LinkedHashMap<>();

	void addFontFace(final String family, final String uri) {
		this.fontFaces.put(family, uri);
	}

	/**
	 * 集めた定義を書き出します。{@code defs}は文書のどこに置いてもよく、
	 * それより前の要素からも参照できるので、末尾で構いません。
	 */
	void writeDefs(final Writer target) throws IOException {
		if (this.defs.isEmpty() && this.fontFaces.isEmpty()) {
			return;
		}
		target.write("<defs>");
		if (!this.fontFaces.isEmpty()) {
			target.write("<style type=\"text/css\">");
			final StringBuilder css = new StringBuilder();
			for (final java.util.Map.Entry<String, String> face : this.fontFaces.entrySet()) {
				css.append("@font-face{font-family:'").append(face.getKey()).append("';src:url('../")
						.append(face.getValue()).append("') format('woff2');font-display:block;}");
			}
			escapeText(target, css.toString());
			target.write("</style>");
		}
		for (final String def : this.defs) {
			target.write(def);
		}
		target.write("</defs>");
	}

	void raw(final String text) throws IOException {
		this.out.write(text);
	}

	/** 要素を開きます。属性は{@link #attr}で続けて書きます。 */
	void open(final String name) throws IOException {
		this.out.write('<');
		this.out.write(name);
	}

	void attr(final String name, final String value) throws IOException {
		this.out.write(' ');
		this.out.write(name);
		this.out.write("=\"");
		escapeAttribute(this.out, value);
		this.out.write('"');
	}

	void attr(final String name, final double value) throws IOException {
		this.attr(name, number(value));
	}

	/** 子を持たない要素を閉じます。 */
	void closeEmpty() throws IOException {
		this.out.write("/>");
	}

	/** 開始タグを閉じます(子が続く)。 */
	void closeStart() throws IOException {
		this.out.write('>');
	}

	void end(final String name) throws IOException {
		this.out.write("</");
		this.out.write(name);
		this.out.write('>');
	}

	void text(final String value) throws IOException {
		escapeText(this.out, value);
	}

	void flush() throws IOException {
		this.out.flush();
	}

	/**
	 * 数値を書きます。指数表記は使いません——SVGの文法では許されますが、
	 * 実装によっては読めないものがあるためです。整数はそのまま出し、
	 * 末尾の0は落とします。
	 */
	static String number(final double value) {
		if (value == Math.rint(value) && Math.abs(value) < 1e15) {
			return Long.toString((long) value);
		}
		java.math.BigDecimal d = new java.math.BigDecimal(value)
				.setScale(PRECISION, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
		return d.toPlainString();
	}

	static void escapeAttribute(final Writer out, final String value) throws IOException {
		for (int i = 0; i < value.length(); ++i) {
			final char ch = value.charAt(i);
			switch (ch) {
			case '&' -> out.write("&amp;");
			case '<' -> out.write("&lt;");
			case '>' -> out.write("&gt;");
			case '"' -> out.write("&quot;");
			case '\n' -> out.write("&#10;");
			case '\r' -> out.write("&#13;");
			case '\t' -> out.write("&#9;");
			default -> out.write(ch);
			}
		}
	}

	static void escapeText(final Writer out, final String value) throws IOException {
		for (int i = 0; i < value.length(); ++i) {
			final char ch = value.charAt(i);
			switch (ch) {
			case '&' -> out.write("&amp;");
			case '<' -> out.write("&lt;");
			case '>' -> out.write("&gt;");
			default -> out.write(ch);
			}
		}
	}
}
