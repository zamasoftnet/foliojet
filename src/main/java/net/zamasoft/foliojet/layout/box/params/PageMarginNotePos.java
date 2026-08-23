package net.zamasoft.foliojet.layout.box.params;

/**
 * JLREQ 4.2.7の並列注（横組の傍注・縦組の頭注／脚注）を、版面の
 * 論理行頭側または行末側の余白へ置く配置です。
 *
 * <p>CSSに標準の並列注指定がないため、Copper拡張の
 * {@code float: -cssj-note-start | -cssj-note-end}から生成します。</p>
 */
public final class PageMarginNotePos extends FloatPos {
	/** 論理行頭側へ置くか（falseは論理行末側）。 */
	public final boolean start;

	public PageMarginNotePos(final boolean start) {
		this.start = start;
	}
}
