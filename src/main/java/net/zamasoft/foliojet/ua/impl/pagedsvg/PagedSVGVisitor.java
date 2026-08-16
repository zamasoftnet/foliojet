package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.net.URI;

import net.zamasoft.foliojet.css.StructureElement;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.AbstractVisitor;
import net.zamasoft.pdfg2d.gc.GC;

/** Collects page navigation and reading metadata beside the SVG display list. */
final class PagedSVGVisitor extends AbstractVisitor {
	private final PagedSVGResources resources;
	private PagedSVGResources.PageData page;
	private AffineTransform pageTransform;

	PagedSVGVisitor(final UserAgent ua, final PagedSVGResources resources) {
		super(ua);
		this.resources = resources;
		this.setHyperlinks(true);
		this.setFragments(true);
		this.setBookmarks(true);
	}

	void nextPage(final GC gc, final PagedSVGResources.PageData page) {
		super.nextPage();
		this.page = page;
		final AffineTransform transform = gc.getTransform();
		this.pageTransform = transform == null ? null : new AffineTransform(transform);
	}

	private Point2D pagePoint(final Point2D location) {
		final Point2D point = new Point2D.Double(location.getX(), location.getY());
		return this.pageTransform == null ? point : this.pageTransform.transform(point, point);
	}

	@Override
	protected void addFragment(final String id, final Point2D location) {
		this.resources.addFragment(this.page, id, this.pagePoint(location));
	}

	@Override
	protected void addLink(Shape shape, final URI uri, final StructureElement ce, final String contents) {
		if (this.pageTransform != null) {
			shape = this.pageTransform.createTransformedShape(shape);
		}
		this.resources.addLink(this.page, shape, uri.toString(), contents);
	}

	@Override
	protected void startBookmark(final String title, final Point2D location) {
		this.resources.startOutline(this.page, title, this.pagePoint(location));
	}

	@Override
	protected void endBookmark() {
		this.resources.endOutline();
	}
}
