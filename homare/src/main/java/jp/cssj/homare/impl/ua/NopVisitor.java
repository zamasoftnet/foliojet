package jp.cssj.homare.impl.ua;

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.net.URI;

import jp.cssj.homare.css.CSSElement;
import jp.cssj.homare.ua.UserAgent;

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
