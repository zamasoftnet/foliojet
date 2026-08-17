package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.Encoder;

import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.XRef;
import net.zamasoft.pdfg2d.pdf.font.PDFEmbeddedFont;
import net.zamasoft.pdfg2d.pdf.font.type2.CFFGenerator;

/** A deterministic glyph subset wrapped as an OpenType/CFF WOFF2 font. */
final class WebFontSubset {
	private static final int FSTYPE_RESTRICTED = 0x0002;
	private static final int FSTYPE_NO_SUBSETTING = 0x0100;
	private static final int FSTYPE_BITMAP_ONLY = 0x0200;
	/** XML 1.0-safe BMP private-use range. Batik escapes supplementary surrogate halves separately. */
	static final int FIRST_PUA = 0xE000;
	static final int LAST_PUA = 0xF8FF;
	private static final int MAX_MAPPED_GLYPHS = LAST_PUA - FIRST_PUA + 1;

	enum Mode {
		HORIZONTAL, VERTICAL_UPRIGHT, VERTICAL_SIDEWAYS
	}

	private final int id;
	private final FontSource source;
	private final ShapedFont font;
	private final Mode mode;
	private final boolean syntheticOblique;
	private final String family;
	private final String uri;
	private final Map<Integer, Integer> sourceGidToSubset = new LinkedHashMap<>();
	private final List<Shape> shapes = new ArrayList<>();
	private final List<Short> widths = new ArrayList<>();

	WebFontSubset(final int id, final FontSource source, final ShapedFont font, final Mode mode,
			final boolean syntheticOblique) {
		this.id = id;
		this.source = source;
		this.font = font;
		this.mode = mode;
		this.syntheticOblique = syntheticOblique;
		this.family = String.format("CopperSubset%04d", id);
		this.uri = String.format("assets/fonts/font-%04d.woff2", id);
		// GID 0 is .notdef. Display characters start at subset GID 1.
		this.shapes.add(null);
		this.widths.add((short) FontSource.DEFAULT_UNITS_PER_EM);
	}

	String family() {
		return this.family;
	}

	String uri() {
		return this.uri;
	}

	String sourceName() {
		return this.source.getFontName();
	}

	int glyphCount() {
		return this.shapes.size();
	}

	int embeddingLicenseFlags() {
		return this.source.getEmbeddingLicenseFlags() & 0xFFFF;
	}

	static boolean allowsEmbedding(final short fsType) {
		return ((fsType & 0xFFFF) & (FSTYPE_RESTRICTED | FSTYPE_NO_SUBSETTING | FSTYPE_BITMAP_ONLY)) == 0;
	}

	boolean canMap(final int[] sourceGids, final int count) {
		int remaining = MAX_MAPPED_GLYPHS - this.sourceGidToSubset.size();
		if (remaining < 0) {
			return false;
		}
		final Set<Integer> newGids = new HashSet<>();
		for (int i = 0; i < count; ++i) {
			final int gid = sourceGids[i];
			if (!this.sourceGidToSubset.containsKey(gid) && newGids.add(gid) && --remaining < 0) {
				return false;
			}
		}
		return true;
	}

	int codePointFor(final int sourceGid) {
		Integer subsetGid = this.sourceGidToSubset.get(sourceGid);
		if (subsetGid == null) {
			subsetGid = this.shapes.size();
			if (subsetGid > MAX_MAPPED_GLYPHS) {
				throw new IllegalStateException("A single XML-safe webfont subset cannot exceed "
						+ MAX_MAPPED_GLYPHS + " glyphs");
			}
			this.sourceGidToSubset.put(sourceGid, subsetGid);
			this.shapes.add(this.transform(this.font.getShapeByGID(sourceGid), sourceGid));
			this.widths.add(this.font.getWidth(sourceGid));
		}
		return FIRST_PUA + subsetGid - 1;
	}

	private Shape transform(Shape shape, final int gid) {
		if (shape == null) {
			return null;
		}
		if (this.syntheticOblique) {
			shape = (this.mode == Mode.VERTICAL_UPRIGHT
					? AffineTransform.getShearInstance(0, 0.25)
					: AffineTransform.getShearInstance(-0.25, 0)).createTransformedShape(shape);
		}
		if (this.mode == Mode.VERTICAL_UPRIGHT) {
			final double width = this.font.getWidth(gid);
			final double dx = -500.0 + (FontSource.DEFAULT_UNITS_PER_EM - width) / 2.0;
			return AffineTransform.getTranslateInstance(dx, 880).createTransformedShape(shape);
		}
		if (this.mode == Mode.VERTICAL_SIDEWAYS) {
			final BBox bbox = this.source.getBBox();
			final double dy = (bbox.lly() + bbox.ury()) / 2.0;
			final AffineTransform at = AffineTransform.getRotateInstance(Math.PI / 2.0);
			at.translate(0, dy);
			return at.createTransformedShape(shape);
		}
		return shape;
	}

	/**
	 * WOFF2を組み立てます。
	 *
	 * @param quality Brotliの品質(1〜11)。11は極端に遅い割に小さくならない
	 *                ({@code output.paged-svg.font-compression}参照)
	 */
	byte[] build(final int quality) throws IOException {
		final SubsetFont subset = new SubsetFont();
		final BBox bbox = CFFGenerator.calculateSubsetBBox(subset);
		final ByteArrayOutputStream cffBytes = new ByteArrayOutputStream(1 << 14);
		final CFFGenerator cff = new CFFGenerator();
		cff.setSubsetName(this.family);
		cff.setEmbedableFont(subset);
		cff.setBBox(bbox);
		cff.writeTo(cffBytes);

		final List<TableData> tables = new ArrayList<>();
		tables.add(new TableData("CFF ", cffBytes.toByteArray()));
		tables.add(new TableData("OS/2", os2Table()));
		tables.add(new TableData("cmap", cmapTable()));
		tables.add(new TableData("head", headTable(bbox)));
		tables.add(new TableData("hhea", hheaTable(bbox)));
		tables.add(new TableData("hmtx", hmtxTable()));
		tables.add(new TableData("maxp", maxpTable()));
		tables.add(new TableData("name", nameTable()));
		tables.add(new TableData("post", postTable()));
		tables.sort(Comparator.comparing(TableData::tag));
		return Woff2.wrap(tables, quality);
	}

	private byte[] cmapTable() throws IOException {
		final int count = this.shapes.size() - 1;
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream(40);
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeShort(0); // version
			out.writeShort(1); // encoding records
			out.writeShort(3); // Microsoft
			out.writeShort(10); // Unicode full repertoire
			out.writeInt(12);
			out.writeShort(12);
			out.writeShort(0);
			out.writeInt(count == 0 ? 16 : 28);
			out.writeInt(0); // language
			out.writeInt(count == 0 ? 0 : 1);
			if (count != 0) {
				out.writeInt(FIRST_PUA);
				out.writeInt(FIRST_PUA + count - 1);
				out.writeInt(1);
			}
		}
		return bytes.toByteArray();
	}

	private byte[] headTable(final BBox bbox) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream(54);
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeInt(0x00010000);
			out.writeInt(0x00010000);
			out.writeInt(0); // checkSumAdjustment is patched in reconstructed sfnt
			out.writeInt(0x5F0F3CF5);
			// WOFF2 5.0 requires bit 11 for a font recreated by a lossless
			// modifying transform. The remaining bits retain the generated CFF
			// subset's baseline-at-zero, LSB and integer-PPEM flags.
			out.writeShort(0x080B);
			out.writeShort(FontSource.DEFAULT_UNITS_PER_EM);
			out.writeLong(0);
			out.writeLong(0);
			out.writeShort(bbox.llx());
			out.writeShort(bbox.lly());
			out.writeShort(bbox.urx());
			out.writeShort(bbox.ury());
			int macStyle = this.source.isItalic() ? 2 : 0;
			if (this.source.getWeight().w >= 700) {
				macStyle |= 1;
			}
			out.writeShort(macStyle);
			out.writeShort(8);
			out.writeShort(2);
			out.writeShort(0);
			out.writeShort(0);
		}
		return bytes.toByteArray();
	}

	private byte[] hheaTable(final BBox bbox) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream(36);
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeInt(0x00010000);
			out.writeShort(this.source.getAscent());
			out.writeShort(-this.source.getDescent());
			out.writeShort(0);
			out.writeShort(FontSource.DEFAULT_UNITS_PER_EM);
			out.writeShort(bbox.llx());
			out.writeShort(0);
			out.writeShort(bbox.urx());
			out.writeShort(1);
			out.writeShort(0); // caretSlopeRun
			out.writeShort(0); // caretOffset
			out.writeShort(0); // reserved 1
			out.writeShort(0); // reserved 2
			out.writeShort(0); // reserved 3
			out.writeShort(0); // reserved 4
			out.writeShort(0); // metricDataFormat
			out.writeShort(this.shapes.size());
		}
		return bytes.toByteArray();
	}

	private byte[] hmtxTable() throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream(this.shapes.size() * 4);
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			for (int i = 0; i < this.shapes.size(); ++i) {
				out.writeShort(FontSource.DEFAULT_UNITS_PER_EM);
				out.writeShort(0);
			}
		}
		return bytes.toByteArray();
	}

	private byte[] maxpTable() throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream(6);
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeInt(0x00005000); // CFF maxp 0.5
			out.writeShort(this.shapes.size());
		}
		return bytes.toByteArray();
	}

	private byte[] nameTable() throws IOException {
		final int[] ids = { 1, 2, 3, 4, 5, 6 };
		final String[] values = { this.family, "Regular", this.family + ";1.0", this.family, "Version 1.0",
				this.family };
		final List<byte[]> strings = new ArrayList<>(values.length);
		int length = 0;
		for (final String value : values) {
			final byte[] string = value.getBytes(StandardCharsets.UTF_16BE);
			strings.add(string);
			length += string.length;
		}
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream(6 + ids.length * 12 + length);
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeShort(0);
			out.writeShort(ids.length);
			out.writeShort(6 + ids.length * 12);
			int offset = 0;
			for (int i = 0; i < ids.length; ++i) {
				out.writeShort(3);
				out.writeShort(1);
				out.writeShort(0x0409);
				out.writeShort(ids[i]);
				out.writeShort(strings.get(i).length);
				out.writeShort(offset);
				offset += strings.get(i).length;
			}
			for (final byte[] string : strings) {
				out.write(string);
			}
		}
		return bytes.toByteArray();
	}

	byte[] os2Table() throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream(96);
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeShort(4);
			out.writeShort(FontSource.DEFAULT_UNITS_PER_EM);
			out.writeShort(this.source.getWeight().w);
			out.writeShort(5);
			// サブセットを別ライセンスへ見せない。restricted/no-subsetting/
			// bitmap-onlyは生成前に拒否されるが、preview&print/editable等の
			// 許可ビットは元フォントの値をそのまま保持する。
			out.writeShort(this.embeddingLicenseFlags());
			for (int i = 0; i < 11; ++i) {
				out.writeShort(0);
			}
			out.write(new byte[10]); // PANOSE
			for (int i = 0; i < 4; ++i) {
				out.writeInt(0);
			}
			out.writeInt(0x434F5052); // COPR
			int selection = this.source.isItalic() ? 1 : 0;
			selection |= this.source.getWeight().w >= 700 ? 0x20 : 0x40;
			out.writeShort(selection);
			out.writeShort(0xFFFF);
			out.writeShort(0xFFFF);
			out.writeShort(this.source.getAscent());
			out.writeShort(-this.source.getDescent());
			out.writeShort(0);
			out.writeShort(Math.max(0, this.source.getBBox().ury()));
			out.writeShort(Math.max(0, -this.source.getBBox().lly()));
			out.writeInt(0);
			out.writeInt(0);
			out.writeShort(this.source.getXHeight());
			out.writeShort(this.source.getCapHeight());
			out.writeShort(0);
			out.writeShort(0);
			out.writeShort(1);
		}
		return bytes.toByteArray();
	}

	private static byte[] postTable() throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream(32);
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeInt(0x00030000);
			out.writeInt(0);
			out.writeShort(-75);
			out.writeShort(50);
			for (int i = 0; i < 5; ++i) {
				out.writeInt(0);
			}
		}
		return bytes.toByteArray();
	}

	private final class SubsetFont implements PDFEmbeddedFont, ShapedFont {
		@Override public String getPSName() { return family; }
		@Override public String getName() { return family; }
		@Override public BBox getBBox() { return source.getBBox(); }
		@Override public String getRegistry() { return "Adobe"; }
		@Override public String getOrdering() { return "Identity"; }
		@Override public int getSupplement() { return 0; }
		@Override public Shape getShape(final int i) { return shapes.get(i); }
		@Override public Shape getShapeByGID(final int i) { return shapes.get(i); }
		@Override public byte[] getCharString(final int i) { return null; }
		@Override public int getGlyphCount() { return shapes.size(); }
		@Override public int getCharCount() { return shapes.size(); }
		@Override public FontSource getFontSource() { return source; }
		@Override public int toGID(final int c) { return c >= FIRST_PUA ? c - FIRST_PUA + 1 : 0; }
		@Override public short getAdvance(final int gid) { return FontSource.DEFAULT_UNITS_PER_EM; }
		@Override public short getWidth(final int gid) { return widths.get(gid); }
		@Override public short getKerning(final int sgid, final int gid) { return 0; }
		@Override public int getLigature(final int gid, final int cid) { return -1; }
		@Override public void drawTo(final GC gc, final Text text) throws IOException, GraphicsException {
			throw new UnsupportedOperationException();
		}
		@Override public void writeTo(final PDFFragmentOutput out, final XRef xref) throws IOException {
			throw new UnsupportedOperationException();
		}
	}

	private record TableData(String tag, byte[] data) {
		int checksum() {
			long sum = 0;
			for (int i = 0; i < this.data.length; i += 4) {
				long word = 0;
				for (int j = 0; j < 4; ++j) {
					word = (word << 8) | (i + j < this.data.length ? this.data[i + j] & 0xFFL : 0);
				}
				sum = (sum + word) & 0xFFFFFFFFL;
			}
			return (int) sum;
		}
	}

	private static final class Woff2 {
		private static final String[] KNOWN_TAGS = { "cmap", "head", "hhea", "hmtx", "maxp", "name", "OS/2",
				"post", "cvt ", "fpgm", "glyf", "loca", "prep", "CFF ", "VORG", "EBDT", "EBLC", "gasp",
				"hdmx", "kern", "LTSH", "PCLT", "VDMX", "vhea", "vmtx", "BASE", "GDEF", "GPOS", "GSUB",
				"EBSC", "JSTF", "MATH", "CBDT", "CBLC", "COLR", "CPAL", "SVG ", "sbix", "acnt", "avar",
				"bdat", "bloc", "bsln", "cvar", "fdsc", "feat", "fmtx", "fvar", "gvar", "hsty", "just",
				"lcar", "mort", "morx", "opbd", "prop", "trak", "Zapf", "Silf", "Glat", "Gloc", "Feat",
				"Sill" };

		static byte[] wrap(final List<TableData> tables, final int quality) throws IOException {
			final ByteArrayOutputStream directoryBytes = new ByteArrayOutputStream();
			final ByteArrayOutputStream plainBytes = new ByteArrayOutputStream();
			for (final TableData table : tables) {
				final int index = knownIndex(table.tag);
				directoryBytes.write(index < 0 ? 0x3F : index);
				if (index < 0) {
					directoryBytes.write(table.tag.getBytes(StandardCharsets.ISO_8859_1));
				}
				writeBase128(directoryBytes, table.data.length);
				plainBytes.write(table.data);
			}
			final byte[] compressed = brotliCompress(plainBytes.toByteArray(), quality);
			final int totalSfntSize = 12 + tables.size() * 16
					+ tables.stream().mapToInt(t -> (t.data.length + 3) & ~3).sum();
			final int contentLength = 48 + directoryBytes.size() + compressed.length;
			// The reference WOFF2 decoder (and Chromium's converter derived from it)
			// rounds the compressed-data end to a four-byte boundary even when no
			// optional metadata/private block follows. Keep the file itself aligned;
			// totalCompressedSize still excludes these trailing zero pad bytes.
			final int length = (contentLength + 3) & ~3;
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(length);
			try (DataOutputStream out = new DataOutputStream(bytes)) {
				out.writeInt(0x774F4632); // wOF2
				out.writeInt(0x4F54544F); // OTTO
				out.writeInt(length);
				out.writeShort(tables.size());
				out.writeShort(0);
				out.writeInt(totalSfntSize);
				out.writeInt(compressed.length);
				out.writeShort(1);
				out.writeShort(0);
				for (int i = 0; i < 5; ++i) {
					out.writeInt(0);
				}
				out.write(directoryBytes.toByteArray());
				out.write(compressed);
				for (int i = contentLength; i < length; ++i) {
					out.writeByte(0);
				}
			}
			return bytes.toByteArray();
		}

		private static int knownIndex(final String tag) {
			for (int i = 0; i < KNOWN_TAGS.length; ++i) {
				if (KNOWN_TAGS[i].equals(tag)) {
					return i;
				}
			}
			return -1;
		}

		private static void writeBase128(final ByteArrayOutputStream out, final int value) {
			int bits = 28;
			while (bits > 0 && ((value >>> bits) & 0x7F) == 0) {
				bits -= 7;
			}
			for (; bits > 0; bits -= 7) {
				out.write(((value >>> bits) & 0x7F) | 0x80);
			}
			out.write(value & 0x7F);
		}

		/**
		 * RFC 7932 deterministic no-compression stream. This follows Google's
		 * {@code MakeUncompressedStream}: a minimal window header, an empty metadata
		 * block, one or more uncompressed blocks, then the final empty block.
		 */
		private static byte[] brotliStore(final byte[] input) {
			if (input.length == 0) {
				return new byte[] { 6 };
			}
			final ByteArrayOutputStream out = new ByteArrayOutputStream(input.length + input.length / (1 << 24) * 4 + 7);
			out.write(0x21); // window bits = 10, ISLAST = false
			out.write(0x03); // empty metadata block and byte padding
			int offset = 0;
			while (offset < input.length) {
				final int chunkSize = Math.min(1 << 24, input.length - offset);
				final int nibbles = chunkSize > (1 << 16) ? chunkSize > (1 << 20) ? 2 : 1 : 0;
				final long bits = ((long) nibbles << 1) | ((long) (chunkSize - 1) << 3)
						| (1L << (19 + 4 * nibbles));
				out.write((int) bits);
				out.write((int) (bits >>> 8));
				out.write((int) (bits >>> 16));
				if (nibbles == 2) {
					out.write((int) (bits >>> 24));
				}
				out.write(input, offset, chunkSize);
				offset += chunkSize;
			}
			out.write(3); // final empty meta-block
			return out.toByteArray();
		}

		/**
		 * WOFF2用のFONT modeで実圧縮する。未知のOS/CPUでは正しい非圧縮
		 * RFC 7932 streamへ落とし、Paged SVG出力そのものは失わせない。
		 */
		private static byte[] brotliCompress(final byte[] input, final int quality) throws IOException {
			try {
				Brotli4jLoader.ensureAvailability();
				return Encoder.compress(input, Encoder.Parameters.create(quality, 22, Encoder.Mode.FONT));
			} catch (final UnsatisfiedLinkError | ExceptionInInitializerError e) {
				return brotliStore(input);
			}
		}
	}
}
