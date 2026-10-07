package net.zamasoft.foliojet.css.impl.property.border;

/**
 * The four corners of a box.
 *
 * <p>
 * 2026-07-20: Abolished the "physical property rotation" mechanism provided by the proprietary
 * {@code -cssj-direction-mode} extension (real-world CSS/browsers have no such behavior;
 * vertical writing support was consolidated into standard logical properties).
 * Removed {@code resolve}, which had remained as an identity mapping, on 2026-10-04.
 * </p>
 */
public enum Corner {
	TOP_LEFT("top-left"), TOP_RIGHT("top-right"), BOTTOM_RIGHT("bottom-right"), BOTTOM_LEFT("bottom-left");

	private final String text;

	private Corner(String text) {
		this.text = text;
	}

	public String text() {
		return this.text;
	}
}
