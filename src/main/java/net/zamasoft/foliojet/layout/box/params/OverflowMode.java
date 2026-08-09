package net.zamasoft.foliojet.layout.box.params;

/**
 * overflowプロパティの値です。
 *
 * @author MIYABE Tatsuhiko
 */
public enum OverflowMode {
	VISIBLE, HIDDEN, SCROLL, AUTO;

	/**
	 * はみ出した描画をボックスでクリップするか(2026-08-09)。CSSの
	 * スクロールコンテナ(scroll/auto)は画面ではスクロールバーで中身に
	 * 到達できるが、印刷ではブラウザ同様「見えている範囲」で切り取る
	 * (オーナー裁定——従来ははみ出しをそのまま描いており、絶対配置の
	 * タブ見出し等が全展開の中身と重なっていた。asahi.comの速報ニュース欄)。
	 * BFC成立・float封じ込めなどのレイアウト効果は従来どおりHIDDENのみ。
	 */
	public boolean clipsPaint() {
		return this != VISIBLE;
	}
}
