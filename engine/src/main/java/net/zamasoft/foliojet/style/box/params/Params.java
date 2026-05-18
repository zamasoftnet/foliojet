package net.zamasoft.foliojet.style.box.params;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.CSSElement;

/**
 * 内容のパラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Params.java 1587 2019-06-10 01:42:25Z miyabe $
 */
public abstract class Params {
	public static final byte TYPE_FIRST_LINE = 1;
	public static final byte TYPE_INLINE = 2;
	public static final byte TYPE_BLOCK = 3;
	public static final byte TYPE_REPLACED = 4;
	public static final byte TYPE_TABLE = 5;
	public static final byte TYPE_INNER_TABLE = 6;

	public static final byte Z_INDEX_AUTO = 0;
	public static final byte Z_INDEX_SPECIFIED = 1;

	/**
	 * 対応するCSSStyleです。
	 */
	public CSSElement element = null;

	/**
	 * ボックスの奥行きです。
	 */
	public int zIndexValue = 0;

	public byte zIndexType = Z_INDEX_AUTO;

	/**
	 * ボックスの可視性です。
	 */
	public float opacity = 1f;

	private static final AffineTransform IDENTITY_TRANSFORM = new AffineTransform();
	public AffineTransform transform = IDENTITY_TRANSFORM;
	public Offset transformOrigin = Offset.HALF_OFFSET;

	public abstract byte getType();

	public String toString() {
		return super.toString() + "[element=" + this.element + ",zIndex=" + this.zIndexValue + ",opacity="
				+ this.opacity + "]";
	}
}
