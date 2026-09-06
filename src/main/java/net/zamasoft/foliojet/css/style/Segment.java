package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.css.CSSStyle;

/**
 * 本流のスタイル窓の件数・深さ・世代です。
 *
 * <p>
 * 再レイアウトは凍結済みの {@code LayoutSource/BoxRecipe} を使います。
 * この旧M6a窓に閉じた要素のスタイルや文字を読む消費者はありません。
 * ページ境界までStart/Endを残すと、auto表ではPass B開始まで全td/trの
 * {@code CSSStyle.values/computedValues}を保持してしまいます。
 * </p>
 *
 * <p>
 * <b>保持の不変条件</b>: スタイルと文字への参照を持たず、数値だけを更新します。
 * CSSStyle自体の計算値は変更しないので、
 * ::afterの評価・匿名箱の終了処理など、呼び出し側の残りの処理は影響を受けません。
 * 再スタイルは入力の再走査、running/page-contentは独立したStyleSnapshotを
 * 使い、この窓へ閉じたスタイルを残す必要はありません。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class Segment {
	private int eventCount = 0;

	protected int depth = 0;

	/**
	 * ページ境界ごとに進む窓の世代です。再生アンカーはLayoutSourceが所有します。
	 */
	protected int epoch = 0;

	/**
	 * 窓の世代を返します。
	 */
	public int getEpoch() {
		return this.epoch;
	}

	public int getDepth() {
		return this.depth;
	}

	/**
	 * 現在の窓のイベント数を返します。イベント本体は保持しません。
	 */
	public int size() {
		return this.eventCount;
	}

	public void startStyle(CSSStyle style) {
		++this.eventCount;
		++this.depth;
	}

	public void characters(int offset, char[] ch, int off, int len) {
		// 文字の唯一の再生元はRecordingLayoutSinkが記録するLayoutSource。
		++this.eventCount;
	}

	public void endStyle(CSSStyle style) {
		++this.eventCount;
		--this.depth;
	}

	/**
	 * ページ境界で窓を開要素のStart件数だけに戻し、世代を更新します。
	 * depthは変化しません。
	 */
	public void trimToOpenElements() {
		this.eventCount = this.depth;
		++this.epoch;
	}
}
