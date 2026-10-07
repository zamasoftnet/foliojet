package net.zamasoft.foliojet.layout.box.params;

/**
 * Block progression direction (writing-mode / -cssj-block-flow).
 * TB = horizontal writing, RL = vertical writing (right → left), LR = vertical writing (left → right).
 *
 * @author MIYABE Tatsuhiko
 */
public enum WritingMode {
	/** Horizontal writing (blocks progress from top to bottom). */
	TB,
	/** Vertical writing (blocks progress from right to left). */
	RL,
	/** Vertical writing (blocks progress from left to right). */
	LR;

	/**
	 * Returns true for vertical writing.
	 *
	 * @return true for vertical writing
	 */
	public boolean isVertical() {
		return this != TB;
	}
}
