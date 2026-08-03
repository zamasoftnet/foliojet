package net.zamasoft.foliojet.layout.box.params;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.StructureElement;

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
	 * 対応するソース要素です。live構築では{@code CSSElement}、ソース
	 * 再生(BoxRecipeのmaterialize)では{@code StructureToken}が入る
	 * (E-6増分3b-4——読み手が必要とする契約は{@link StructureElement}の
	 * javadoc参照)。
	 */
	public StructureElement element = null;

	/**
	 * 脚注の論理識別子です(脚注F4、2026-07-31——
	 * consult-codex-2026-07-31-footnote-f4.txt)。engine-ownedの単調な
	 * 通し番号で、CSSには非公開。脚注元要素の本文ボックスと
	 * {@code ::footnote-call}擬似要素のインラインボックスが同じIDを持ち、
	 * ページ確定時の「呼び出しがこのページに残ったか」の集合判定に使う。
	 * 表示上の番号(counter "footnote")とは独立(ページ毎再採番=F5に備える)。
	 * 脚注と無関係のボックスは-1。
	 */
	public long footnoteId = -1;

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

	/**
	 * {@code translate()}の割合成分(2026-08-03新設)。描画時に箱の幅・高さを
	 * 掛けて平行移動に足す——割合の基準がその要素自身の境界箱なので、
	 * 解析時には行列へ畳めない。
	 */
	public double transformTxRatio = 0, transformTyRatio = 0;
	public Offset transformOrigin = Offset.HALF_OFFSET;

	public abstract ParamsType getType();

	public String toString() {
		return super.toString() + "[element=" + this.element + ",zIndex=" + this.zIndexValue + ",opacity="
				+ this.opacity + "]";
	}
}
