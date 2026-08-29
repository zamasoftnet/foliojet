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

	/**
	 * ページで共有する定義(2026-08-29)。層(グループ画像)の中身は別の
	 * バッファへ書く{@link SVGWriter}が受け持つが、{@code defs}・id・
	 * {@code @font-face}はページで1つなので、ここにまとめて親子で共有する。
	 */
	private static final class Shared {
		/** 先頭へ回す定義。ページを閉じるときにまとめて書く。 */
		final List<String> defs = new ArrayList<>();
		/** 内容が同じ定義(フィルタ)のid。同じ効果を何度も定義しない。 */
		final java.util.Map<String, String> defIds = new java.util.HashMap<>();
		/** {@code @font-face}の並び。共有WOFF2を参照する。 */
		final java.util.Map<String, String> fontFaces = new java.util.LinkedHashMap<>();
		int nextId = 0;
	}

	private final Shared shared;

	SVGWriter(final Writer out) {
		this.out = out;
		this.shared = new Shared();
	}

	/** 定義・idを{@code parent}と共有する、別の出力先への書き手(層の中身用)。 */
	SVGWriter(final Writer out, final SVGWriter parent) {
		this.out = out;
		this.shared = parent.shared;
	}

	/** 定義を1つ登録し、参照用のidを返します。 */
	String addDef(final String element) {
		this.shared.defs.add(element);
		return null;
	}

	/**
	 * 内容で重複を除いて定義を登録し、そのidを返します(2026-08-29)。
	 * {@code content}はid属性を除いた要素の中身・属性で、同じ内容なら
	 * 既存のidを返す。
	 *
	 * @param prefix  idの接頭辞
	 * @param name    要素名
	 * @param content {@code <name id=".."}の後ろに続ける文字列(属性と中身、閉じタグは含めない)
	 */
	String defId(final String prefix, final String name, final String content) {
		final String key = name + '\u0000' + content;
		String id = this.shared.defIds.get(key);
		if (id == null) {
			id = this.nextId(prefix);
			this.shared.defIds.put(key, id);
			this.addDef("<" + name + " id=\"" + id + "\"" + content + "</" + name + ">");
		}
		return id;
	}

	String nextId(final String prefix) {
		return prefix + (++this.shared.nextId);
	}

	void addFontFace(final String family, final String uri) {
		this.shared.fontFaces.put(family, uri);
	}

	/**
	 * 集めた定義を書き出します。{@code defs}は文書のどこに置いてもよく、
	 * それより前の要素からも参照できるので、末尾で構いません。
	 */
	void writeDefs(final Writer target) throws IOException {
		if (this.shared.defs.isEmpty() && this.shared.fontFaces.isEmpty()) {
			return;
		}
		target.write("<defs>");
		if (!this.shared.fontFaces.isEmpty()) {
			target.write("<style type=\"text/css\">");
			final StringBuilder css = new StringBuilder();
			for (final java.util.Map.Entry<String, String> face : this.shared.fontFaces.entrySet()) {
				css.append("@font-face{font-family:'").append(face.getKey()).append("';src:url('../")
						.append(face.getValue()).append("') format('woff2');font-display:block;}");
			}
			escapeText(target, css.toString());
			target.write("</style>");
		}
		for (final String def : this.shared.defs) {
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

	/** defsを組み立てるときのように、文字列へ直接積むための同じ処理です。 */
	static void escapeAttribute(final StringBuilder out, final String value) {
		for (int i = 0; i < value.length(); ++i) {
			final char ch = value.charAt(i);
			switch (ch) {
			case '&' -> out.append("&amp;");
			case '<' -> out.append("&lt;");
			case '>' -> out.append("&gt;");
			case '"' -> out.append("&quot;");
			case '\n' -> out.append("&#10;");
			case '\r' -> out.append("&#13;");
			case '\t' -> out.append("&#9;");
			default -> out.append(ch);
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
