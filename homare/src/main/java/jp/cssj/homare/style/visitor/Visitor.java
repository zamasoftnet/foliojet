package jp.cssj.homare.style.visitor;

import java.awt.geom.AffineTransform;

import jp.cssj.homare.style.box.IBox;

/**
 * 描画可能なオブジェクトを描画します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Visitor.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface Visitor {
	public void startPage();

	public void visitBox(AffineTransform transform, IBox box, double x, double y);

	public void endPage();
}
