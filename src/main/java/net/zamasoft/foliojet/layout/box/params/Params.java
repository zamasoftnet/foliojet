package net.zamasoft.foliojet.layout.box.params;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.CSSElement;

/**
 * 内容のパラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Params.java 1587 2019-06-10 01:42:25Z miyabe $
 */
public abstract class Params {

	public static final byte Z_INDEX_AUTO = 0;
	public static final byte Z_INDEX_SPECIFIED = 1;

	/**
	 * 対応するCSSStyleです。
	 */
	public CSSElement element = null;

	/**
	 * 本流セグメント窓(css.style.Segment)内での、この内容を生んだ
	 * Start イベントの位置です(M6b)。セグメント再駆動時の再開位置の
	 * 対応付けに使います。本流以外(ページ内容・run-in 一次バッファ等)や
	 * 匿名内容では -1 のままです。窓の刈り込み後は旧値は無効になります。
	 */
	public int sourceIndex = -1;

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

	public abstract ParamsType getType();

	public String toString() {
		return super.toString() + "[element=" + this.element + ",zIndex=" + this.zIndexValue + ",opacity="
				+ this.opacity + "]";
	}
}
