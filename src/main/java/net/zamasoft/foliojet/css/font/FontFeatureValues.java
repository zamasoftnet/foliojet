package net.zamasoft.foliojet.css.font;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.zamasoft.pdfg2d.gc.font.FontFamily;

/**
 * 文書中の{@code @font-feature-values}で定義された、フォントファミリ別の
 * OpenType機能値名の登録簿です。
 *
 * <p>同じ(ファミリ、内側の規則、名前)が再定義された場合は、CSS Fontsの
 * 規定どおり後の定義で置き換えます。登録簿は複数の組版パスで共有するため
 * {@code UAContext}が保持します。</p>
 */
public final class FontFeatureValues {
	/** {@code font-variant-alternates}の名前付き関数に対応する内側の規則です。 */
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

	/** 定義を各ファミリへ登録します。 */
	public void define(final List<String> familyNames, final Type type, final String name, final int[] values) {
		for (final String familyName : familyNames) {
			final FontFamily family = new FontFamily(familyName);
			this.families.computeIfAbsent(family, key -> new EnumMap<>(Type.class))
					.computeIfAbsent(type, key -> new HashMap<>())
					.put(name, values.clone());
		}
	}

	/**
	 * 名前に対応する番号列を返します。未定義なら{@code null}です。
	 * 返却値は呼び出し側から変更できないよう複製します。
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
