package net.zamasoft.foliojet.layout.box.params;

/**
 * The {@code contain}/{@code cover} keyword forms of background-size (2026-08-06).
 *
 * <p>
 * These require comparing the box's actual size (padding box) with the image's aspect ratio, so they
 * cannot use {@link Dimension} for {@code <length>|<percentage>|auto}. LengthType has exactly four
 * values packed into two bits, and the existing framework shared with Length/Insets/Offset must
 * remain intact. Carry only the type until draw time (the {@code Background} painting operation),
 * when the box's actual size is known, and calculate the actual image size there.
 * </p>
 */
public enum BackgroundFit {
	NONE, CONTAIN, COVER
}
