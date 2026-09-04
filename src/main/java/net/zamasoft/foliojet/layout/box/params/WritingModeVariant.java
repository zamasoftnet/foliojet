package net.zamasoft.foliojet.layout.box.params;

/**
 * 書字方向の字形回転種別です。
 *
 * <p>
 * ブロック進行方向({@link WritingMode})および文字方向({@link AbstractTextParams#direction})とは
 * 独立して保持します。sideways の描画への反映は可視化段階で行います。
 * </p>
 */
public enum WritingModeVariant {
	NORMAL,
	/** 字形列を時計回りに回転します。 */
	SIDEWAYS_CW,
	/** 字形列を反時計回りに回転します。 */
	SIDEWAYS_CCW
}
