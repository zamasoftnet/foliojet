package jp.cssj.homare.style.draw;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * 描画可能なオブジェクトです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Drawable.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface Drawable {
	/**
	 * オブジェクトを描画します。
	 * 
	 * @param gc
	 * @param x
	 * @param y
	 * @throws GraphicsException
	 *             TODO
	 */
	public void draw(GC gc, double x, double y) throws GraphicsException;
}
