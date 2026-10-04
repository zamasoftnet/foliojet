/*
 * Copper PDF (2026-10-04): an addition to JEuclid, not part of the upstream
 * sources. Licensed under the Apache License, Version 2.0, like JEuclid.
 */

package net.sourceforge.jeuclid.font;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sourceforge.jeuclid.LayoutContext;
import net.sourceforge.jeuclid.context.Parameter;

/**
 * The parts of an OpenType MATH table that the layout uses: the script
 * positioning constants and the italic corrections of glyphs.
 * <p>
 * Fonts are registered by family name ({@link #register}) when Copper PDF
 * registers the font file with JEuclid. Layout code finds the table of the
 * first math family of the context ({@link #find}); fonts without a MATH table
 * keep JEuclid's own layout.
 * </p>
 */
public final class MathTable {

    /** Constants of the MathConstants subtable (offsets of the MathValueRecords). */
    public enum Constant {
        /** Standard shift down applied to subscript elements. */
        SUBSCRIPT_SHIFT_DOWN(24),
        /** Maximum allowed height of the top of a subscript above the baseline. */
        SUBSCRIPT_TOP_MAX(28),
        /** Minimum drop of the subscript baseline below the bottom of a non-glyph base. */
        SUBSCRIPT_BASELINE_DROP_MIN(32),
        /** Standard shift up applied to superscript elements. */
        SUPERSCRIPT_SHIFT_UP(36),
        /** Minimum allowed height of the bottom of a superscript above the baseline. */
        SUPERSCRIPT_BOTTOM_MIN(44),
        /** Maximum drop of the superscript baseline below the top of a non-glyph base. */
        SUPERSCRIPT_BASELINE_DROP_MAX(48),
        /** Minimum gap between the superscript bottom and the subscript top. */
        SUB_SUPERSCRIPT_GAP_MIN(52),
        /** Maximum level the superscript bottom is raised to when closing the gap. */
        SUPERSCRIPT_BOTTOM_MAX_WITH_SUBSCRIPT(56),
        /** Extra space after a script. */
        SPACE_AFTER_SCRIPT(60);

        private final int offset;

        Constant(final int offset) {
            this.offset = offset;
        }
    }

    private static final int TAG_TTCF = 0x74746366;

    private static final int TAG_HEAD = 0x68656164;

    private static final int TAG_MATH = 0x4d415448;

    private static final Map<String, MathTable> BY_FAMILY = new ConcurrentHashMap<String, MathTable>();

    private final int unitsPerEm;

    private final short[] constants;

    /** Sorted glyph ids that have an italic correction. */
    private final int[] italicGlyphs;

    private final short[] italicCorrections;

    private MathTable(final int unitsPerEm, final short[] constants, final int[] italicGlyphs,
            final short[] italicCorrections) {
        this.unitsPerEm = unitsPerEm;
        this.constants = constants;
        this.italicGlyphs = italicGlyphs;
        this.italicCorrections = italicCorrections;
    }

    /**
     * Reads the MATH table of a font file (the first font of a collection).
     *
     * @param file
     *            an OpenType or TrueType font file
     * @return the table, or {@code null} when the font has none
     * @throws IOException
     *             if the file cannot be read
     */
    public static MathTable read(final File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                FileChannel channel = raf.getChannel()) {
            return MathTable.parse(channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()));
        }
    }

    /**
     * Reads the MATH table of a font.
     *
     * @param font
     *            the font data
     * @return the table, or {@code null} when the font has none or the data is
     *         not a font
     */
    public static MathTable parse(final ByteBuffer font) {
        try {
            int base = 0;
            if (font.getInt(0) == MathTable.TAG_TTCF) {
                base = font.getInt(12);
            }
            final int numTables = font.getShort(base + 4) & 0xFFFF;
            int head = -1, math = -1;
            for (int i = 0; i < numTables; ++i) {
                final int record = base + 12 + i * 16;
                final int tag = font.getInt(record);
                if (tag == MathTable.TAG_HEAD) {
                    head = font.getInt(record + 8);
                } else if (tag == MathTable.TAG_MATH) {
                    math = font.getInt(record + 8);
                }
            }
            if (head < 0 || math < 0) {
                return null;
            }
            final int unitsPerEm = font.getShort(head + 18) & 0xFFFF;
            final int constantsAt = math + (font.getShort(math + 4) & 0xFFFF);
            final Constant[] all = Constant.values();
            final short[] constants = new short[all.length];
            for (final Constant c : all) {
                constants[c.ordinal()] = font.getShort(constantsAt + c.offset);
            }
            int[] glyphs = new int[0];
            short[] corrections = new short[0];
            final int glyphInfoOffset = font.getShort(math + 6) & 0xFFFF;
            if (glyphInfoOffset != 0) {
                final int glyphInfo = math + glyphInfoOffset;
                final int italicsOffset = font.getShort(glyphInfo) & 0xFFFF;
                if (italicsOffset != 0) {
                    final int italics = glyphInfo + italicsOffset;
                    final int coverage = italics + (font.getShort(italics) & 0xFFFF);
                    final int count = font.getShort(italics + 2) & 0xFFFF;
                    corrections = new short[count];
                    for (int i = 0; i < count; ++i) {
                        corrections[i] = font.getShort(italics + 4 + i * 4);
                    }
                    glyphs = MathTable.coverage(font, coverage, count);
                }
            }
            return new MathTable(unitsPerEm == 0 ? 1000 : unitsPerEm, constants, glyphs, corrections);
        } catch (final IndexOutOfBoundsException e) {
            return null;
        }
    }

    /** The glyph ids of a coverage table, in coverage index order. */
    private static int[] coverage(final ByteBuffer font, final int at, final int count) {
        final int[] glyphs = new int[count];
        final int format = font.getShort(at) & 0xFFFF;
        if (format == 1) {
            final int n = Math.min(count, font.getShort(at + 2) & 0xFFFF);
            for (int i = 0; i < n; ++i) {
                glyphs[i] = font.getShort(at + 4 + i * 2) & 0xFFFF;
            }
        } else if (format == 2) {
            final int ranges = font.getShort(at + 2) & 0xFFFF;
            for (int r = 0; r < ranges; ++r) {
                final int record = at + 4 + r * 6;
                final int start = font.getShort(record) & 0xFFFF;
                final int end = font.getShort(record + 2) & 0xFFFF;
                final int index = font.getShort(record + 4) & 0xFFFF;
                for (int g = start; g <= end && index + g - start < count; ++g) {
                    glyphs[index + g - start] = g;
                }
            }
        }
        return glyphs;
    }

    /**
     * A constant scaled to a font size.
     *
     * @param constant
     *            the constant
     * @param fontSize
     *            the font size
     * @return the value in the units of the font size
     */
    public float get(final Constant constant, final float fontSize) {
        return this.constants[constant.ordinal()] * fontSize / this.unitsPerEm;
    }

    /**
     * The italic correction of a glyph scaled to a font size.
     *
     * @param glyph
     *            the glyph id
     * @param fontSize
     *            the font size
     * @return the italic correction, or 0 when the glyph has none
     */
    public float italicCorrection(final int glyph, final float fontSize) {
        // Coverage tables list glyphs in increasing order
        final int i = java.util.Arrays.binarySearch(this.italicGlyphs, glyph);
        return i < 0 ? 0 : this.italicCorrections[i] * fontSize / this.unitsPerEm;
    }

    /**
     * Registers the MATH table of a font family.
     *
     * @param family
     *            the AWT family name of the font
     * @param table
     *            the table, or {@code null} to do nothing
     */
    public static void register(final String family, final MathTable table) {
        if (family != null && table != null) {
            MathTable.BY_FAMILY.put(family.toLowerCase(Locale.ROOT), table);
        }
    }

    /**
     * Removes the table of a font family.
     *
     * @param family
     *            the AWT family name of the font
     */
    public static void unregister(final String family) {
        if (family != null) {
            MathTable.BY_FAMILY.remove(family.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * The registered table of a font.
     *
     * @param font
     *            the font
     * @return the table, or {@code null}
     */
    public static MathTable forFont(final Font font) {
        return font == null ? null : MathTable.BY_FAMILY.get(font.getFamily().toLowerCase(Locale.ROOT));
    }

    /**
     * The table of the first math family of a layout context that has one.
     *
     * @param context
     *            the layout context
     * @return the table, or {@code null}
     */
    public static MathTable find(final LayoutContext context) {
        final Object families = context.getParameter(Parameter.FONTS_SERIF);
        if (families instanceof List<?> list) {
            for (final Object family : list) {
                if (family instanceof String name) {
                    final MathTable table = MathTable.BY_FAMILY.get(name.toLowerCase(Locale.ROOT));
                    if (table != null) {
                        return table;
                    }
                }
            }
        }
        return null;
    }
}
