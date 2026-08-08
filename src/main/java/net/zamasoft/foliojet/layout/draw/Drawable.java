package net.zamasoft.foliojet.layout.draw;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * 描画可能なオブジェクトです。
 *
 * @author MIYABE Tatsuhiko
 */
public interface Drawable {
	/**
	 * オブジェクトを描画します。
	 */
	public void draw(GC gc, double x, double y) throws GraphicsException;

	/**
	 * 表示リストダンプ用の1行表現を返します。
	 * 回帰検証(golden比較)に使うため、内容を特定できる決定的な文字列を返してください。
	 */
	public default String describe() {
		String name = this.getClass().getSimpleName();
		return name.isEmpty() ? this.getClass().getName() : name;
	}

	/**
	 * 表示リストダンプ用に、描画時クリップの1行表現を返します(2026-08-09)。
	 * クリップ無しは空文字列。ダンプの座標はクリップ前の値のため、クリップを
	 * 含む回帰はこれが無いとgoldenに一切現れない({@code tf=}と同じ穴)。
	 */
	public default String describeClip() {
		return "";
	}
}
