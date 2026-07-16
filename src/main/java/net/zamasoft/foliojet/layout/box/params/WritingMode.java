package net.zamasoft.foliojet.layout.box.params;

/**
 * ブロック進行方向(writing-mode / -cssj-block-flow)です。
 * TB=横書き、RL=縦書き(右→左)、LR=縦書き(左→右)。
 *
 * @author MIYABE Tatsuhiko
 */
public enum WritingMode {
	/** 横書き(ブロックは上から下へ進行)。 */
	TB,
	/** 縦書き(ブロックは右から左へ進行)。 */
	RL,
	/** 縦書き(ブロックは左から右へ進行)。 */
	LR;

	/**
	 * 縦書きであればtrueを返します。
	 *
	 * @return 縦書きであればtrue
	 */
	public boolean isVertical() {
		return this != TB;
	}
}
