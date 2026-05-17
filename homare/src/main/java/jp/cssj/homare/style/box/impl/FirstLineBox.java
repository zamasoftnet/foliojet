package jp.cssj.homare.style.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import jp.cssj.homare.style.box.AbstractLineBox;
import jp.cssj.homare.style.box.params.AbstractLineParams;
import jp.cssj.homare.style.box.params.AbstractTextParams;
import jp.cssj.homare.style.box.params.FirstLineParams;
import jp.cssj.homare.style.box.params.Params;
import jp.cssj.homare.style.draw.BackgroundDrawable;
import jp.cssj.homare.style.draw.Drawable;
import jp.cssj.homare.style.draw.Drawer;
import jp.cssj.homare.style.visitor.Visitor;

/**
 * 行ボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: FirstLineBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public class FirstLineBox extends AbstractLineBox {
	protected final FirstLineParams params;

	public FirstLineBox(FirstLineParams params) {
		this.params = params;
		this.setDecoration(null);
	}

	public final AbstractTextParams getTextParams() {
		return this.params;
	}

	public final AbstractLineParams getLineParams() {
		return this.params;
	}

	public final Params getParams() {
		return this.params;
	}

	public final boolean isContextBox() {
		return false;
	}

	public final void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		// 背景は最初の行だけ描画する
		if (this.params.opacity != 0 && this.params.background.isVisible()) {
			Drawable drawable = new BackgroundDrawable(pageBox, clip, this.params.opacity, transform,
					this.params.background, this.getWidth(), this.getHeight());
			drawer.visitDrawable(drawable, x, y);
		}
		super.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
	}

	public String toString() {
		return "[FirstLineBox]" + super.toString() + "[/FirstLineBox]";
	}
}
