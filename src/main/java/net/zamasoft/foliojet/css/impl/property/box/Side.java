package net.zamasoft.foliojet.css.impl.property.box;

/**
 * The four sides of a box.
 *
 * <p>
 * 2026-07-20: Abolished the "physical property rotation" mechanism provided by the proprietary
 * {@code -cssj-direction-mode} extension (real-world CSS/browsers have no such behavior;
 * vertical writing support was consolidated into standard logical properties ({@link LogicalSide})).
 * Removed {@code resolve}, which had remained as an identity mapping, on 2026-10-04.
 * </p>
 */
public enum Side {
	TOP("top"), RIGHT("right"), BOTTOM("bottom"), LEFT("left");

	private final String text;

	private Side(String text) {
		this.text = text;
	}

	public String text() {
		return this.text;
	}
}
