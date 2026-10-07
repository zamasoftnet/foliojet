package net.zamasoft.foliojet.css.value;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.font.FontFeatureValues;
import net.zamasoft.foliojet.css.font.FontFeatureValues.Type;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;

/**
 * A {@code font-variant-alternates} value.
 *
 * <p>
 * {@code historical-forms} maps to OpenType {@code hist} and affects rendering for fonts
 * that implement it as a single substitution. Named functions refer to the
 * {@code @font-feature-values} for the element's first font family and map
 * to OpenType feature tags and values.
 * </p>
 */
public final class FontVariantAlternatesValue implements Value {
	public record Alternate(String function, List<String> names) {
		public Alternate {
			names = List.copyOf(names);
		}
	}

	public static final FontVariantAlternatesValue NORMAL_VALUE =
			new FontVariantAlternatesValue(false, List.of());

	private final boolean historicalForms;
	private final List<Alternate> alternates;

	private FontVariantAlternatesValue(final boolean historicalForms, final List<Alternate> alternates) {
		this.historicalForms = historicalForms;
		this.alternates = List.copyOf(alternates);
	}

	public static FontVariantAlternatesValue create(final boolean historicalForms,
			final List<Alternate> alternates) {
		return !historicalForms && alternates.isEmpty() ? NORMAL_VALUE
				: new FontVariantAlternatesValue(historicalForms, alternates);
	}

	public boolean isNormal() {
		return this == NORMAL_VALUE;
	}

	public boolean hasHistoricalForms() {
		return this.historicalForms;
	}

	public List<Alternate> getAlternates() {
		return this.alternates;
	}

	/** Returns only {@code hist}, as before, for documents without a name table. */
	public FontFeatureSet featureSet() {
		if (!this.historicalForms) {
			return FontFeatureSet.EMPTY;
		}
		return FontFeatureSet.of(new int[] { FontFeatureSet.packTag("hist") }, new int[] { 1 });
	}

	/**
	 * Resolves the name table for the first font family and returns the OpenType feature sequence.
	 * Ignores only functions containing undefined names, preserving the other functions
	 * and {@code historical-forms}.
	 */
	public FontFeatureSet featureSet(final FontFeatureValues definitions, final String familyName) {
		final List<Integer> tags = new ArrayList<>();
		final List<Integer> values = new ArrayList<>();
		if (this.historicalForms) {
			add(tags, values, "hist", 1);
		}
		for (final Alternate alternate : this.alternates) {
			final Type type = Type.fromCssName(alternate.function());
			if (type == null) {
				continue;
			}
			final List<int[]> resolved = new ArrayList<>(alternate.names().size());
			boolean defined = true;
			for (final String name : alternate.names()) {
				final int[] indexes = definitions.lookup(familyName, type, name);
				if (indexes == null) {
					defined = false;
					break;
				}
				resolved.add(indexes);
			}
			if (!defined) {
				continue;
			}
			for (final int[] indexes : resolved) {
				switch (type) {
				case STYLESET:
					for (final int index : indexes) {
						if (index >= 1 && index <= 20) {
							add(tags, values, String.format(java.util.Locale.ROOT, "ss%02d", index), 1);
						}
					}
					break;
				case CHARACTER_VARIANT:
					if (indexes[0] >= 1 && indexes[0] <= 99) {
						add(tags, values, String.format(java.util.Locale.ROOT, "cv%02d", indexes[0]),
								indexes.length == 2 ? indexes[1] : 1);
					}
					break;
				case STYLISTIC:
					add(tags, values, "salt", indexes[0]);
					break;
				case SWASH:
					add(tags, values, "swsh", indexes[0]);
					break;
				case ORNAMENTS:
					add(tags, values, "ornm", indexes[0]);
					break;
				case ANNOTATION:
					add(tags, values, "nalt", indexes[0]);
					break;
				}
			}
		}
		if (tags.isEmpty()) {
			return FontFeatureSet.EMPTY;
		}
		final int[] packedTags = new int[tags.size()];
		final int[] featureValues = new int[values.size()];
		for (int i = 0; i < tags.size(); ++i) {
			packedTags[i] = tags.get(i);
			featureValues[i] = values.get(i);
		}
		return FontFeatureSet.of(packedTags, featureValues);
	}

	private static void add(final List<Integer> tags, final List<Integer> values, final String tag,
			final int value) {
		tags.add(FontFeatureSet.packTag(tag));
		values.add(value);
	}

	@Override
	public String toString() {
		if (this.isNormal()) {
			return "normal";
		}
		final StringBuilder out = new StringBuilder();
		if (this.historicalForms) {
			out.append("historical-forms");
		}
		for (final Alternate alternate : this.alternates) {
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(alternate.function()).append('(')
					.append(String.join(", ", alternate.names())).append(')');
		}
		return out.toString();
	}
}
