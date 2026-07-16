package net.zamasoft.foliojet.layout.box.params;

public class InlineParams extends AbstractTextParams {
	public RectFrame frame = RectFrame.NULL_FRAME;

	public ParamsType getType() {
		return ParamsType.INLINE;
	}

	public String toString() {
		return super.toString() + "[frame=" + this.frame + "]";
	}
}
