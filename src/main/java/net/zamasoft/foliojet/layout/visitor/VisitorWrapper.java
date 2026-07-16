package net.zamasoft.foliojet.layout.visitor;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.draw.Drawer;

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

	public void visitBox(AffineTransform transform, IBox box, Drawer drawer, double x, double y) {
		if (this.visitor == null) {
			return;
		}
		this.visitor.visitBox(transform, box, drawer, x, y);
	}
}
