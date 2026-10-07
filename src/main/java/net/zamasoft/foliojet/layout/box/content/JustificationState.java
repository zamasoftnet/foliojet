package net.zamasoft.foliojet.layout.box.content;

public class JustificationState {
	/** The last code point of the preceding cluster. -1 means no boundary. */
	public int prevCodePoint = -1;
	public double prevFontSize = 0;

	/** If preceded by a Latin word space, its actual width and the character immediately before it. */
	public double wordSpaceAdvance = -1;
	public int beforeWordSpaceCodePoint = -1;
	public double beforeWordSpaceFontSize = 0;
}
