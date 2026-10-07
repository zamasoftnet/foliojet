package net.zamasoft.foliojet.css.style;

import junit.framework.TestCase;

/**
 * Tests for pruning Segment (a style-event window) (M6a).
 * Style references are irrelevant to the window mechanism, so substitute null.
 */
public class SegmentTest extends TestCase {
	public void testTrimKeepsOpenElements() {
		final Segment segment = new Segment();
		segment.startStyle(null); // html
		segment.startStyle(null); // body
		segment.startStyle(null); // p (closed)
		segment.characters(0, "hello".toCharArray(), 0, 5);
		segment.endStyle(null); // /p
		segment.startStyle(null); // div (left open)
		segment.characters(5, "world".toCharArray(), 0, 5);
		assertEquals(7, segment.size());
		assertEquals(3, segment.getDepth());

		segment.trimToOpenElements();
		// Only the Start events for html, body, and div remain.
		assertEquals(3, segment.size());
		assertEquals(3, segment.getDepth());
	}

	public void testTrimAcrossWindows() {
		final Segment segment = new Segment();
		segment.startStyle(null); // body
		segment.startStyle(null); // div
		segment.trimToOpenElements();
		assertEquals(2, segment.size());

		// Close an element in the next window that was opened in the previous window.
		segment.endStyle(null); // /div
		segment.characters(0, "x".toCharArray(), 0, 1);
		segment.trimToOpenElements();
		assertEquals(1, segment.size());
		assertEquals(1, segment.getDepth());
	}

	public void testTrimEmpty() {
		final Segment segment = new Segment();
		segment.trimToOpenElements();
		assertEquals(0, segment.size());
		assertEquals(0, segment.getDepth());
	}
}
