package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.nio.ByteBuffer;

import junit.framework.TestCase;
import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;

public class WebFontSubsetTest extends TestCase {
	public void testEmbeddingPermissionPolicy() {
		assertTrue(WebFontSubset.allowsEmbedding((short) 0));
		assertTrue(WebFontSubset.allowsEmbedding((short) 0x0004));
		assertTrue(WebFontSubset.allowsEmbedding((short) 0x0008));
		assertFalse(WebFontSubset.allowsEmbedding((short) 0x0002));
		assertFalse(WebFontSubset.allowsEmbedding((short) 0x0100));
		assertFalse(WebFontSubset.allowsEmbedding((short) 0x0200));
	}

	public void testOs2RetainsOriginalFsType() throws Exception {
		final WebFontSubset subset = new WebFontSubset(1, new StubFontSource((short) 0x0004), null,
				WebFontSubset.Mode.HORIZONTAL, false);
		final ByteBuffer os2 = ByteBuffer.wrap(subset.os2Table());
		assertEquals(0x0004, os2.getShort(8) & 0xFFFF);
		assertEquals(0x0004, subset.embeddingLicenseFlags());
	}

	private static final class StubFontSource implements FontSource {
		private static final long serialVersionUID = 1L;
		private final short fsType;

		StubFontSource(final short fsType) {
			this.fsType = fsType;
		}

		@Override public String getFontName() { return "Stub"; }
		@Override public String[] getAliases() { return new String[0]; }
		@Override public Direction getDirection() { return Direction.LTR; }
		@Override public boolean isItalic() { return false; }
		@Override public Weight getWeight() { return Weight.W_400; }
		@Override public boolean canDisplay(final int c) { return true; }
		@Override public BBox getBBox() { return new BBox((short) 0, (short) -200, (short) 1000, (short) 800); }
		@Override public short getAscent() { return 800; }
		@Override public short getCapHeight() { return 700; }
		@Override public short getDescent() { return 200; }
		@Override public short getStemH() { return 0; }
		@Override public short getStemV() { return 0; }
		@Override public short getXHeight() { return 500; }
		@Override public short getSpaceAdvance() { return 500; }
		@Override public short getEmbeddingLicenseFlags() { return this.fsType; }
		@Override public Font createFont() { throw new UnsupportedOperationException(); }
	}
}
