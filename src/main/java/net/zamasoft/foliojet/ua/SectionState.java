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

	/**
	 * Document-order keys of the elements whose sections are open, innermost last (2026-10-08). A box split across pages
	 * is visited once per fragment; a fragment of an element whose section is still open must not start it again.
	 */
	public final java.util.ArrayDeque<Long> openElements = new java.util.ArrayDeque<>();

	/** Whether the section of the element with this key is open (pseudo and anonymous elements have no key). */
	public boolean isOpen(final long elementKey) {
		return elementKey >= 0 && this.openElements.contains(elementKey);
	}
}
