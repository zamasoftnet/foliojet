package net.zamasoft.foliojet.layout.visitor;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.draw.Drawer;

/**
 * 描画可能なオブジェクトを描画します。
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: Visitor.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface Visitor {
	/** ページビルダーがcommitした、非描画の代入アンカーを訪問します。 */
	public default void visitAssignment(
			net.zamasoft.foliojet.css.style.running.RunningRegistry.Placement placement) {
	}

	public void startPage();

	/**
	 * ボックスを訪問します。
	 *
	 * @param transform 変換行列
	 * @param box       ボックス
	 * @param drawer    このボックスの内容を描画するドロワー（ペイント時に発行する
	 *                  対話オブジェクトを文書順に挿入するために使用）
	 * @param x         X座標
	 * @param y         Y座標
	 */
	public void visitBox(AffineTransform transform, IBox box, Drawer drawer, double x, double y);

	public void endPage();
}
