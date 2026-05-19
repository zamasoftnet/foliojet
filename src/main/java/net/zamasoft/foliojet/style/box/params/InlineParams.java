package net.zamasoft.foliojet.style.box.params;

public class InlineParams extends AbstractTextParams {
	public RectFrame frame = RectFrame.NULL_FRAME;

	public byte getType() {
		return TYPE_INLINE;
	}

	public String toString() {
		return super.toString() + "[frame=" + this.frame + "]";
	}
}
