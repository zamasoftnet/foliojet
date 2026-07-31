package net.zamasoft.foliojet.layout.text;

import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * {@code leader()}の行内埋め物です(css-content-3、
 * consult-codex-2026-07-31-leader.txt L1)。
 *
 * <p>
 * shape済みのパターン1周期({@link #runs})を最小幅として行の分割
 * 判断に参加し、行の確定時({@code TextBuilder.drawLine}の割り付け)に
 * 残余幅の配分を受けて{@link #advance}が確定する。同一行に複数ある
 * 場合は残余を等分する(FolioJetの実用規則——草案は複数leaderの配分を
 * 規定していない)。描画はパターンをグリフ列として実体化せず、行末を
 * 原点とする固定グリッドへ反復描画する(位相揃え——複数行のドットが
 * 縦に揃う)。論理テキスト({@code getText})へは単一の空白のみ与え、
 * ドット列を混入させない。
 * </p>
 *
 * <p>
 * 幅は行ごとに割り付け直すため、割り付けは必ず{@link #advance}を
 * 最小幅へ戻してから行う(TwoPassの記録再生で同一インスタンスが
 * 再駆動されても前回の割り付けが漏れないように)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class LeaderQuad extends Quad {
	/** shape済みのパターン1周期です(不変として扱う)。 */
	public final Text[] runs;

	/** 最小幅(=shape済みパターン1周期分)です。 */
	public final double minAdvance;

	/** 行確定時に割り付けられる幅です(初期値は最小幅)。 */
	public double advance;

	/**
	 * 行末位相揃えの原点までの距離です(leader終端から行内容の終端まで。
	 * 割り付け時に設定)。
	 */
	public double endOffset;

	public LeaderQuad(final Text[] runs) {
		assert runs.length > 0;
		this.runs = runs;
		double a = 0;
		for (final Text run : runs) {
			a += run.getAdvance();
		}
		this.minAdvance = a;
		this.advance = a;
	}

	public double getAdvance() {
		return this.advance;
	}

	public String getString() {
		// leaderと後続内容(ページ番号)の間では改行しない
		return CONTINUE_BEFORE;
	}

	public String toString() {
		return "[LEADER]";
	}
}
