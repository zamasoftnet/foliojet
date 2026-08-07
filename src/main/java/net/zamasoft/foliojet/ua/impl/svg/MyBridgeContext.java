package net.zamasoft.foliojet.ua.impl.svg;

import java.awt.Shape;
import java.awt.geom.Dimension2D;
import java.net.URI;

import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.TextPainter;
import org.apache.batik.bridge.URIResolver;
import org.w3c.dom.svg.SVGDocument;

import net.zamasoft.foliojet.ua.ImageMap;
import net.zamasoft.foliojet.ua.UserAgent;

public class MyBridgeContext extends BridgeContext {
	protected final UserAgent ua;
	protected final SVGImageLoader loader;
	protected final ImageMap imageMap = new ImageMap();

	public MyBridgeContext(String docURI, UserAgent ua, Dimension2D viewport, SVGImageLoader loader) {
		super(new MyUserAgent(docURI, ua, viewport));
		this.ua = ua;
		this.loader = loader;
		this.setDynamic(false);
		TextPainter textPainer = new MyTextPainter(ua);
		this.setTextPainter(textPainer);
	}
	
	public ImageMap getImageMap() {
		return this.imageMap;
	}
	
	public void addLink(Shape shape, URI uri) {
		this.imageMap.add(new ImageMap.Area(shape, uri));
	}

	public void registerSVGBridges() {
		super.registerSVGBridges();
		this.putBridge(new MySVGTextElementBridge(this.ua));
		this.putBridge(new MySVGImageElementBridge(this.ua));
		this.putBridge(new MySVGAElementBridge(this.ua));
		this.putBridge(new MySVGStopElementBridge());
	}

	public URIResolver createURIResolver(SVGDocument doc, DocumentLoader dl) {
		return new MyURIResolver(doc, dl, this.ua, this.loader);
	}
}
