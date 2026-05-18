package net.zamasoft.foliojet.impl.ua;

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.net.URI;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.ua.UserAgent;

public class NopVisitor extends AbstractVisitor {
	public NopVisitor(UserAgent ua) {
		super(ua);
	}

	protected void addFragment(String id, Point2D location) {
	}

	protected void addLink(Shape rect, URI uri, CSSElement ce) {
	}

	protected void endBookmark() {
	}

	protected void startBookmark(String title, Point2D location) {
	}
}
