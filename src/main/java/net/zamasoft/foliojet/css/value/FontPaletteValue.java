package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.font.FontPaletteValues.Definition;

/**
 * A {@code font-palette} value. Named values resolve to their {@code @font-palette-values}
 * definitions, but FolioJet/pdfg2d has no mechanism for selecting color-font palettes,
 * so <b>the resolved definitions do not affect rendering</b>.
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
