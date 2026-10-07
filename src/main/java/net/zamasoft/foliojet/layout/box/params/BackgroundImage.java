package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * Background image.
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: BackgroundImage.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BackgroundImage implements Background.Layer {
	/**
	 * Does not repeat the background.
	 */
	public static final byte REPEAT_NO = 0;

	/**
	 * Repeats the background horizontally.
	 */
	public static final byte REPEAT_X = 1;

	/**
	 * Repeats the background vertically.
	 */
	public static final byte REPEAT_Y = 2;

	/**
	 * Repeats the background as tiles.
	 */
	public static final byte REPEAT = 3;

	/**
	 * Aligns the background phase with the page.
	 */
	public static final byte ATTACHMENT_SCROLL = 0;

	/**
	 * Aligns the background phase with the box.
	 */
	public static final byte ATTACHMENT_FIXED = ATTACHMENT_SCROLL + 1;

	/**
	 * Background image.
	 */
	public final Image image;

	/**
	 * Background-image repetition mode.
	 * <p>
	 * Use a REPEAT_X constant.
	 * </p>
	 */
	public final byte repeat;

	/**
	 * Background-image attachment mode.
	 * <p>
	 * Use an ATTACHMENT_X constant.
	 * </p>
	 */
	public final byte attachment;

	/**
	 * Background-image position.
	 */
	public final Offset position;

	/**
	 * Background-image size. Ignored when {@link #fit} is not {@code NONE}.
	 */
	public final Dimension size;

	/**
	 * The {@code contain}/{@code cover} keyword forms of background-size.
	 * {@code NONE} uses ordinary resolution with {@link #size}.
	 */
	public final BackgroundFit fit;

	public static BackgroundImage create(Image image, byte repeat, byte attachment, Offset position, Dimension size,
			BackgroundFit fit) {
		return new BackgroundImage(image, repeat, attachment, position, size, fit);
	}

	private BackgroundImage(Image image, byte repeat, byte attachment, Offset position, Dimension size,
			BackgroundFit fit) {
		assert image != null && position != null && size != null && fit != null;
		this.image = image;
		this.repeat = repeat;
		this.attachment = attachment;
		this.position = position;
		this.size = size;
		this.fit = fit;
	}

	public String toString() {
		return super.toString() + "[image=" + this.image + ",repeat=" + this.repeat + ",attachment=" + this.attachment
				+ ",position=" + this.position + ",size=" + this.size + ",fit=" + this.fit + "]";
	}
}
