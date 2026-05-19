package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.BackgroundImage;

/**
 * 背景の繰り返し方法です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: BackgroundAttachmentValue.java 3806 2012-07-10 07:03:19Z miyabe
 *          $
 */
public class BackgroundAttachmentValue implements Value {
	public static final BackgroundAttachmentValue SCROLL_VALUE = new BackgroundAttachmentValue(
			BackgroundImage.ATTACHMENT_SCROLL);

	public static final BackgroundAttachmentValue FIXED_VALUE = new BackgroundAttachmentValue(
			BackgroundImage.ATTACHMENT_FIXED);

	private final byte backgroundAttachment;

	private BackgroundAttachmentValue(byte backgroundAttachment) {
		this.backgroundAttachment = backgroundAttachment;
	}

	public short getValueType() {
		return TYPE_BACKGROUND_ATTACHMENT;
	}

	public byte getBackgroundAttachment() {
		return this.backgroundAttachment;
	}

	public String toString() {
		switch (this.backgroundAttachment) {
		case BackgroundImage.ATTACHMENT_SCROLL:
			return "scroll";

		case BackgroundImage.ATTACHMENT_FIXED:
			return "fixed";

		default:
			throw new IllegalStateException();
		}
	}
}