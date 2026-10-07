package net.zamasoft.foliojet.objects.mathml;

import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sourceforge.jeuclid.font.FontFactory;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.FontSourceWrapper;
import net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource;
import net.zamasoft.pdfg2d.gc.font.FontFamily;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/**
 * Maps CSS font-family to JEuclid font candidates (2026-10-04).
 *
 * <p>
 * JEuclid measures character widths with AWT fonts and draws outlines. Passing CSS font names
 * directly did not use fonts unknown to AWT (fonts available only in Copper's font configuration
 * or font pack); layout used JEuclid defaults instead. For each name, this retrieves the
 * OpenType file from Copper's font manager, registers it in JEuclid's font repository, and
 * prepends its AWT name to the candidates. Registers both upright and italic
 * (JEuclid uses "italic" in the font name to classify a font as italic).
 * Generic families (such as serif) use JEuclid's default candidates.
 * </p>
 *
 * <p>
 * Registration uses JEuclid's JVM-wide repository, so each file is registered only once.
 * AWT {@code Font.createFont} cannot select the second or later font in a TTC;
 * these are not registered, and only their names are passed.
 * </p>
 */
final class MathFonts {
	private static final Logger LOG = Logger.getLogger(MathFonts.class.getName());

	/** File → AWT font name (empty string if registration failed). */
	private static final Map<String, String> REGISTERED = new ConcurrentHashMap<String, String>();

	/** Font policy for file lookup. Prioritizes fonts backed by files. */
	private static final FontPolicyList FILE_POLICY = new FontPolicyList(new FontPolicyList.FontPolicy[] {
			FontPolicyList.FontPolicy.EMBEDDED, FontPolicyList.FontPolicy.CID_IDENTITY,
			FontPolicyList.FontPolicy.CID_KEYED });

	private MathFonts() {
		// static only
	}

	/**
	 * Returns the CSS font sequence mapped to JEuclid candidates (AWT font names)
	 * and prepended to {@code defaults}.
	 */
	static List<String> families(final FontManager fontManager, final FontStyle base, final List<String> defaults) {
		final List<String> list = new ArrayList<String>();
		final FontFamilyList families = base.getFamily();
		for (int i = 0; i < families.getLength(); ++i) {
			final FontFamily family = families.get(i);
			if (family.isGenericFamily()) {
				continue;
			}
			final String name = family.getName();
			final String awtName = fontManager == null ? null : register(fontManager, base, name);
			final String candidate = awtName != null ? awtName : name;
			if (!list.contains(candidate)) {
				list.add(candidate);
			}
		}
		for (final String name : defaults) {
			if (!list.contains(name)) {
				list.add(name);
			}
		}
		return list;
	}

	/** Registers upright and italic fonts and returns the AWT font name. Null if unavailable. */
	private static String register(final FontManager fontManager, final FontStyle base, final String name) {
		String awtName = null;
		for (final FontStyle.Style style : new FontStyle.Style[] { FontStyle.Style.NORMAL, FontStyle.Style.ITALIC }) {
			final OpenTypeFontSource source = lookup(fontManager, base, name, style);
			if (source == null) {
				continue;
			}
			final String registered = registerFile(source.getFile(), source.getIndex());
			if (awtName == null && registered != null && !registered.isEmpty()) {
				awtName = registered;
			}
		}
		return awtName;
	}

	private static OpenTypeFontSource lookup(final FontManager fontManager, final FontStyle base, final String name,
			final FontStyle.Style style) {
		final FontStyle fs = new FontStyleImpl(new FontFamilyList(new FontFamily(name)), base.getSize(), style,
				base.getWeight(), FontStyle.Direction.LTR, FILE_POLICY, null, false, false, null,
				FontSource.NORMAL_WIDTH_CLASS, null);
		final FontListMetrics flm = fontManager.getFontListMetrics(fs);
		if (flm == null || flm.getLength() == 0) {
			return null;
		}
		// Aliases from font configuration may be on a wrapper (FontSourceWrapper),
		// so a name match at any wrapper level is sufficient
		FontSource source = flm.getFontMetrics(0).getFontSource();
		boolean named = matches(source, name);
		while (source instanceof FontSourceWrapper wrapper) {
			source = wrapper.getSource();
			named |= matches(source, name);
		}
		if (!named || !(source instanceof OpenTypeFontSource ots)) {
			// Name mismatch = no font with that name exists and a substitute was selected
			return null;
		}
		// If requesting italic returns upright (no italic face), registering the upright font is enough
		return style == FontStyle.Style.NORMAL || ots.isItalic() ? ots : null;
	}

	private static boolean matches(final FontSource source, final String name) {
		if (name.equalsIgnoreCase(source.getFontName())) {
			return true;
		}
		final String[] aliases = source.getAliases();
		if (aliases != null) {
			for (final String alias : aliases) {
				if (name.equalsIgnoreCase(alias)) {
					return true;
				}
			}
		}
		return false;
	}

	private static String registerFile(final File file, final int index) {
		if (file == null || index != 0) {
			return null;
		}
		final String key = file.getAbsolutePath().toLowerCase(Locale.ROOT);
		return REGISTERED.computeIfAbsent(key, k -> {
			try {
				// Registering while the repository is empty prevents JEuclid from loading AWT fonts
				// on its first lookup (it loads only when empty). Trigger that load first
				FontFactory.getInstance().getFont(Font.SERIF, Font.PLAIN, 12f);
				final Font font = FontFactory.getInstance().registerFont(Font.TRUETYPE_FONT, file);
				// For a math font (with a MATH table), use it for subscript positioning and italic correction (⑮⑯)
				net.sourceforge.jeuclid.font.MathTable.register(font.getFamily(),
						net.sourceforge.jeuclid.font.MathTable.read(file));
				return font.getFamily();
			} catch (final Exception e) {
				LOG.log(Level.FINE, "MathML: " + file, e);
				return "";
			}
		});
	}
}
