package net.zamasoft.foliojet.css.font;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.zamasoft.pdfg2d.gc.font.FontFamily;

/**
 * Registry of OpenType feature value names by font family, defined by
 * {@code @font-feature-values} in the document.
 *
 * <p>If the same (family, inner rule, name) is redefined, the later definition
 * replaces it as CSS Fonts specifies. {@code UAContext} holds the registry
 * so that multiple layout passes can share it.</p>
 */
public final class FontFeatureValues {
	/** Inner rules corresponding to the named functions of {@code font-variant-alternates}. */
	public enum Type {
		STYLISTIC, STYLESET, CHARACTER_VARIANT, SWASH, ORNAMENTS, ANNOTATION;

		public static Type fromCssName(final String name) {
			return switch (name.toLowerCase(Locale.ROOT)) {
			case "stylistic" -> STYLISTIC;
			case "styleset" -> STYLESET;
			case "character-variant" -> CHARACTER_VARIANT;
			case "swash" -> SWASH;
			case "ornaments" -> ORNAMENTS;
			case "annotation" -> ANNOTATION;
			default -> null;
			};
		}
	}

	private final Map<FontFamily, EnumMap<Type, Map<String, int[]>>> families = new HashMap<>();

	/** Registers a definition for each family. */
	public void define(final List<String> familyNames, final Type type, final String name, final int[] values) {
		for (final String familyName : familyNames) {
			final FontFamily family = new FontFamily(familyName);
			this.families.computeIfAbsent(family, key -> new EnumMap<>(Type.class))
					.computeIfAbsent(type, key -> new HashMap<>())
					.put(name, values.clone());
		}
	}

	/**
	 * Returns the sequence of numbers for the name, or {@code null} if it is undefined.
	 * Returns a copy so that the caller cannot modify the stored value.
	 */
	public int[] lookup(final String familyName, final Type type, final String name) {
		if (familyName == null) {
			return null;
		}
		final EnumMap<Type, Map<String, int[]>> byType = this.families.get(new FontFamily(familyName));
		if (byType == null) {
			return null;
		}
		final Map<String, int[]> byName = byType.get(type);
		if (byName == null) {
			return null;
		}
		final int[] values = byName.get(name);
		return values == null ? null : values.clone();
	}

	public boolean isEmpty() {
		return this.families.isEmpty();
	}
}
