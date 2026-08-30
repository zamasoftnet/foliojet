package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.font.FontPaletteValues.Definition;

/**
 * {@code font-palette}の値です。名前付き値は{@code @font-palette-values}
 * の定義まで解決しますが、FolioJet/pdfg2dにはカラーフォントのパレット選択
 * 機構がないため、<b>解決した定義を描画には反映しません</b>。
 */
public final class FontPaletteValue implements Value {
	public enum Kind {
		NORMAL, LIGHT, DARK, IDENTIFIER
	}

	public static final FontPaletteValue NORMAL_VALUE = new FontPaletteValue(Kind.NORMAL, null, null);
	public static final FontPaletteValue LIGHT_VALUE = new FontPaletteValue(Kind.LIGHT, null, null);
	public static final FontPaletteValue DARK_VALUE = new FontPaletteValue(Kind.DARK, null, null);

	private final Kind kind;
	private final String identifier;
	private final Definition definition;

	private FontPaletteValue(final Kind kind, final String identifier, final Definition definition) {
		this.kind = kind;
		this.identifier = identifier;
		this.definition = definition;
	}

	public static FontPaletteValue identifier(final String identifier) {
		return new FontPaletteValue(Kind.IDENTIFIER, identifier, null);
	}

	public FontPaletteValue resolve(final Definition definition) {
		return definition == null || this.kind != Kind.IDENTIFIER || this.definition == definition ? this
				: new FontPaletteValue(this.kind, this.identifier, definition);
	}

	public Kind getKind() {
		return this.kind;
	}

	public String getIdentifier() {
		return this.identifier;
	}

	/**
	 * 解決した名前付きパレット定義を返します。未定義またはキーワード値なら
	 * {@code null}です。この値は解析結果の参照用であり、描画には使われません。
	 */
	public Definition getDefinition() {
		return this.definition;
	}

	@Override
	public String toString() {
		return switch (this.kind) {
		case NORMAL -> "normal";
		case LIGHT -> "light";
		case DARK -> "dark";
		case IDENTIFIER -> this.identifier;
		};
	}
}
