package net.zamasoft.foliojet.layout.box.params;

/**
 * 浮動体の配置パラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: FloatPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FloatPos extends AbstractNormalFlowPos implements Pos {
	public FloatSide floating = FloatSide.START;

	/**
	 * {@code shape-outside}・{@code shape-margin}・
	 * {@code shape-image-threshold}(css-shapes-1、2026-08-29)。
	 * nullは{@code none}(マージンボックス矩形)。{@code floating}と同じく
	 * 構築時にだけ書く(排除域スナップショットの前提——
	 * {@code BlockBuilder.floatingsGeneration}の説明を参照)。
	 */
	public ShapeOutsideParams shapeOutside = null;

	public PosType getType() {
		return PosType.FLOAT;
	}

	public String toString() {
		return super.toString() + "[floating=" + this.floating
				+ (this.shapeOutside == null ? "" : ",shapeOutside=" + this.shapeOutside) + "]";
	}
}
