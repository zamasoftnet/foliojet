package net.zamasoft.foliojet.layout.box.content;

public class JustificationState {
	/** 直前clusterの末尾code point。-1は境界なし。 */
	public int prevCodePoint = -1;
	public double prevFontSize = 0;

	/** 直前が欧文語間スペースなら、その実幅とスペース直前の文字。 */
	public double wordSpaceAdvance = -1;
	public int beforeWordSpaceCodePoint = -1;
	public double beforeWordSpaceFontSize = 0;
}
