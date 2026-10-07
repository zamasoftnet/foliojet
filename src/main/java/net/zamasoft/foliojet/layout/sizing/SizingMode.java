package net.zamasoft.foliojet.layout.sizing;

/**
 * Sizing mode.
 *
 * @author MIYABE Tatsuhiko
 */
public enum SizingMode {
	/** Layout at a definite size (bind/replay pass). */
	DEFINITE,
	/** Actual min-content measurement. */
	MIN_CONTENT,
	/** Actual max-content measurement. */
	MAX_CONTENT,
	/** Sizing with fit-content (shrink-to-fit). */
	FIT_CONTENT;
}
