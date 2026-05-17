package jp.cssj.homare.impl.ua.svg;

import java.awt.Shape;
import java.net.URI;

import org.apache.batik.anim.dom.SVGOMAElement;
import org.apache.batik.bridge.Bridge;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.SVGAElementBridge;
import org.apache.batik.gvt.GraphicsNode;
import org.w3c.dom.Element;

import jp.cssj.homare.ua.UserAgent;

public class MySVGAElementBridge extends SVGAElementBridge {
	// private static final Logger LOG = Logger.getLogger(MySVGAElementBridge.class.getName());
	protected final UserAgent ua;

	public MySVGAElementBridge(UserAgent ua) {
		this.ua = ua;
	}

	public Bridge getInstance() {
		return new MySVGAElementBridge(this.ua);
	}

	public void buildGraphicsNode(BridgeContext ctx, Element e, GraphicsNode node) {
		super.buildGraphicsNode(ctx, e, node);
		MyBridgeContext mctx = (MyBridgeContext)ctx;
		SVGOMAElement a = (SVGOMAElement) e;
		Shape s = node.getOutline();
		s = node.getGlobalTransform().createTransformedShape(s);
		mctx.addLink(s, URI.create(a.getHref().getBaseVal()));
	}

}
