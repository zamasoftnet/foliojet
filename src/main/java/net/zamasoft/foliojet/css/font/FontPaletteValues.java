package net.zamasoft.foliojet.css.font;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.css.value.ColorValue;

/**
 * Registry of {@code @font-palette-values} definitions in the document.
 *
 * <p><b>FolioJet/pdfg2d has no mechanism for selecting color font palettes,
 * so this registry only parses rules and resolves names from {@code font-palette};
 * it does not apply the definitions to rendering.</b></p>
 */
public final class FontPaletteValues {
	public enum BasePaletteKind {
		INDEX, LIGHT, DARK
	}

	/** Base palette. Uses index only for {@code INDEX}. */
	public record BasePalette(BasePaletteKind kind, int index) {
		public BasePalette {
			if (kind == null || index < 0) {
				throw new IllegalArgumentException();
			}
		}

		public static BasePalette index(final int index) {
			return new BasePalette(BasePaletteKind.INDEX, index);
		}

		public static BasePalette light() {
			return new BasePalette(BasePaletteKind.LIGHT, 0);
		}

		public static BasePalette dark() {
			return new BasePalette(BasePaletteKind.DARK, 0);
		}
	}

	/** A named palette definition. */
	public record Definition(List<String> fontFamilies, BasePalette basePalette,
			Map<Integer, ColorValue> overrideColors) {
		public Definition {
			fontFamilies = List.copyOf(fontFamilies);
			if (fontFamilies.isEmpty() || basePalette == null) {
				throw new IllegalArgumentException();
			}
			overrideColors = Collections.unmodifiableMap(new LinkedHashMap<>(overrideColors));
		}
	}

	private final Map<String, Definition> definitions = new HashMap<>();

	/** Replaces a rule with the same name with the last valid rule in document order. */
	public void define(final String name, final Definition definition) {
		this.definitions.put(name, definition);
	}

	public Definition lookup(final String name) {
		return this.definitions.get(name);
	}

	public boolean isEmpty() {
		return this.definitions.isEmpty();
	}
}
