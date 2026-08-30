package net.zamasoft.foliojet.css.font;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.css.value.ColorValue;

/**
 * 文書中の{@code @font-palette-values}定義の登録簿です。
 *
 * <p><b>FolioJet/pdfg2dにはカラーフォントのパレット選択機構がないため、
 * この登録簿は規則の解析と{@code font-palette}からの名前解決だけを行い、
 * 定義を描画には反映しません。</b></p>
 */
public final class FontPaletteValues {
	public enum BasePaletteKind {
		INDEX, LIGHT, DARK
	}

	/** 基底パレットです。{@code INDEX}のときだけindexを使います。 */
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

	/** 1つの名前付きパレット定義です。 */
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

	/** 同名規則は文書順で最後の有効な規則に置き換えます。 */
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
