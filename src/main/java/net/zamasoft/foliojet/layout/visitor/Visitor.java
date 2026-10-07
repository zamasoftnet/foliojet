package net.zamasoft.foliojet.layout.visitor;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.draw.Drawer;

/**
 * Draws drawable objects.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: Visitor.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface Visitor {
	/** Visits a non-drawing assignment anchor committed by the page builder. */
	public default void visitAssignment(
			net.zamasoft.foliojet.css.style.running.RunningRegistry.Placement placement) {
	}

	public void startPage();

	/**
	 * Visits a box.
	 *
	 * @param transform transformation matrix
	 * @param box       box
	 * @param drawer    drawer for this box's contents (used to insert interactive objects
	 *                   emitted at paint time in document order)
	 * @param x         X coordinate
	 * @param y         Y coordinate
	 */
	public void visitBox(AffineTransform transform, IBox box, Drawer drawer, double x, double y);

	public void endPage();
}
