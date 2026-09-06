package net.zamasoft.foliojet.ua;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

/** 文書に一つの脚注領域です。ページ名・ページ擬似クラスには依存しません。 */
public final class FootnoteArea {
	public enum Position {
		BLOCK_END, BOTTOM
	}

	public static final FootnoteArea DEFAULT = new FootnoteArea(Position.BLOCK_END, null);

	public final Position position;

	/** nullならページの書字方向に従います。 */
	public final WritingMode flow;

	private FootnoteArea(final Position position, final WritingMode flow) {
		this.position = java.util.Objects.requireNonNull(position);
		this.flow = flow;
	}

	public FootnoteArea withPosition(final Position position) {
		return this.position == position ? this : new FootnoteArea(position, this.flow);
	}

	public FootnoteArea withFlow(final WritingMode flow) {
		return this.flow == flow ? this : new FootnoteArea(this.position, flow);
	}
}
