package net.zamasoft.foliojet.ua.impl;

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.net.URI;

import net.zamasoft.foliojet.css.StructureElement;
import net.zamasoft.foliojet.ua.UserAgent;

public class NopVisitor extends AbstractVisitor {
	public NopVisitor(UserAgent ua) {
		super(ua);
	}

	protected void addFragment(String id, Point2D location) {
	}

	protected void addLink(Shape rect, URI uri, StructureElement ce, String contents) {
	}

	protected void endBookmark() {
	}

	protected void startBookmark(String title, Point2D location) {
	}
}
