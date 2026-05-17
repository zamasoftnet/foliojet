package jp.cssj.homare.style.visitor;

import java.awt.geom.AffineTransform;

import jp.cssj.homare.style.box.IBox;

/**
 * Visitorのラッパークラスです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: VisitorWrapper.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class VisitorWrapper implements Visitor {
	private final Visitor visitor;

	public VisitorWrapper(Visitor visitor) {
		this.visitor = visitor;
	}

	public void startPage() {
		this.visitor.startPage();
	}

	public void endPage() {
		this.visitor.endPage();
	}

	public void visitBox(AffineTransform transform, IBox box, double x, double y) {
		if (this.visitor == null) {
			return;
		}
		this.visitor.visitBox(transform, box, x, y);
	}
}
