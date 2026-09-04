package net.zamasoft.foliojet.layout.text.bidi;

import junit.framework.TestCase;

/**
 * {@link BidiMirroring}(BidiMirroring-17.0.0.txt から生成、428 entries)の試験
 * (2026-09-04、bidi-isolation-design.md §0-22、batch A-1c-1)。
 */
public class BidiMirroringTest extends TestCase {
	public void testMirroringTableSize() {
		assertEquals(428, BidiMirroring.size());
	}

	public void testRepresentativeMirroringPairs() {
		assertMirrorPair('(', ')');
		assertMirrorPair('[', ']');
		assertMirrorPair('{', '}');
		assertMirrorPair(0x00AB, 0x00BB); // « »
		assertMirrorPair(0x2039, 0x203A); // ‹ ›
		assertMirrorPair(0x27E8, 0x27E9); // ⟨ ⟩
		assertMirrorPair(0x2264, 0x2265); // ≤ ≥
		assertMirrorPair(0x3008, 0x3009); // 〈 〉
		assertMirrorPair(0xFF5F, 0xFF60); // ｟ ｠
	}

	public void testEveryMirroringEntryIsSymmetric() {
		int entries = 0;
		for (int codePoint = Character.MIN_CODE_POINT; codePoint <= Character.MAX_CODE_POINT; ++codePoint) {
			if (!BidiMirroring.hasMirror(codePoint)) {
				continue;
			}
			++entries;
			assertEquals("asymmetric mapping for U+" + Integer.toHexString(codePoint).toUpperCase(), codePoint,
					BidiMirroring.mirror(BidiMirroring.mirror(codePoint)));
		}
		assertEquals(BidiMirroring.size(), entries);
	}

	public void testMirroredCodePointWithoutMappingIsUnchanged() {
		final int codePoint = 0x221C; // ∜
		assertTrue(Character.isMirrored(codePoint));
		assertFalse(BidiMirroring.hasMirror(codePoint));
		assertEquals(codePoint, BidiMirroring.mirror(codePoint));
	}

	public void testNonMirroredLetterIsUnchanged() {
		assertFalse(Character.isMirrored('A'));
		assertFalse(BidiMirroring.hasMirror('A'));
		assertEquals('A', BidiMirroring.mirror('A'));
	}

	private static void assertMirrorPair(final int left, final int right) {
		assertTrue(BidiMirroring.hasMirror(left));
		assertTrue(BidiMirroring.hasMirror(right));
		assertEquals(right, BidiMirroring.mirror(left));
		assertEquals(left, BidiMirroring.mirror(right));
	}
}
