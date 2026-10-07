package net.zamasoft.foliojet.ua;

import java.io.IOException;

import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;

/**
 * Output interface for page results.
 */
public interface PageOutput {
	/**
	 * Returns the graphics context for the next page.
	 *
	 * @param width  width in points.
	 * @param height height in points.
	 */
	public GC nextPage(double width, double height);

	/**
	 * Completes drawing on the page.
	 */
	public void closePage(GC gc) throws IOException;

	public Visitor getVisitor(GC gc);

	/**
	 * Builds the result document.
	 */
	public void finish() throws BrokenResultException, IOException;

	/**
	 * Disposes of the UA and releases resources.
	 */
	public void dispose();

	/**
	 * Sets document metadata.
	 */
	public void meta(String name, String content);

	public void setBoundSide(BoundSide boundSide);

	public BoundSide getBoundSide();
}
