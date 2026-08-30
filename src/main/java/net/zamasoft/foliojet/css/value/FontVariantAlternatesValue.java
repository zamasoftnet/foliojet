package net.zamasoft.foliojet.css.value;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.font.FontFeatureValues;
import net.zamasoft.foliojet.css.font.FontFeatureValues.Type;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;

/**
 * {@code font-variant-alternates}の値です。
 *
 * <p>
 * {@code historical-forms}はOpenType {@code hist}へ変換し、単一置換として
 * 実装されているフォントでは描画まで反映します。名前付き関数は要素の
 * 第一フォントファミリに対応する{@code @font-feature-values}を参照し、
 * OpenType機能タグと値へ変換します。
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

	/** 名前表が無い文書で、従来どおり{@code hist}だけを返します。 */
	public FontFeatureSet featureSet() {
		if (!this.historicalForms) {
			return FontFeatureSet.EMPTY;
		}
		return FontFeatureSet.of(new int[] { FontFeatureSet.packTag("hist") }, new int[] { 1 });
	}

	/**
	 * 第一フォントファミリの名前表を解決してOpenType機能列を返します。
	 * 未定義の名前を含む関数だけを無視し、他の関数と
	 * {@code historical-forms}は維持します。
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
