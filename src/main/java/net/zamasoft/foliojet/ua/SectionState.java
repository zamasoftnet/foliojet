package net.zamasoft.foliojet.ua;

public class SectionState {
	/**
	 * Level of the section being processed (numeric part of H1~H6).
	 */
	public int sectionLevel = 1;
	/**
	 * Depth of the section being processed (e.g., H3 under H1 has depth 2).
	 */
	public int sectionDepth = 0;

	/**
	 * Number of sections processed.
	 */
	public int sectionCount = 0;
}
