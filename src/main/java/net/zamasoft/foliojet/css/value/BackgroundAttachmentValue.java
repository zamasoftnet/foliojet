package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.BackgroundImage;

/**
 * 背景の繰り返し方法です。
 * 
 * @author MIYABE Tatsuhiko
 *          $
 */
public enum BackgroundAttachmentValue implements Value {
	SCROLL_VALUE(
			BackgroundImage.ATTACHMENT_SCROLL),

	FIXED_VALUE(
			BackgroundImage.ATTACHMENT_FIXED);

	private final byte backgroundAttachment;

	private BackgroundAttachmentValue(byte backgroundAttachment) {
		this.backgroundAttachment = backgroundAttachment;
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