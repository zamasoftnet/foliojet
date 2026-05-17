package jp.cssj.homare.impl.ua.svg;

import java.awt.geom.Rectangle2D;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.batik.bridge.Bridge;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.SVGImageElementBridge;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.gvt.ShapeNode;
import org.apache.batik.util.ParsedURL;
import org.w3c.dom.Element;

import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.pdfg2d.gc.image.Image;

class MySVGImageElementBridge extends SVGImageElementBridge {
	private static final Logger LOG = Logger.getLogger(MySVGImageElementBridge.class.getName());
	protected final UserAgent ua;

	public MySVGImageElementBridge(UserAgent ua) {
		this.ua = ua;
	}

	public Bridge getInstance() {
		return new MySVGImageElementBridge(this.ua);
	}

	protected GraphicsNode createImageGraphicsNode(BridgeContext ctx, Element e, ParsedURL purl) {
		Rectangle2D bounds = getImageBounds(ctx, e);
		if ((bounds.getWidth() == 0) || (bounds.getHeight() == 0)) {
			ShapeNode sn = new ShapeNode();
			sn.setShape(bounds);
			return sn;
		}

		String purlStr = purl.toString();
		try {
			URI uri = URIHelper.create("UTF-8", purlStr);
			Source source = this.ua.resolve(uri);
			try {
				Image image = this.ua.getImage(source);
				MyImageNode node = new MyImageNode(image);
				Rectangle2D imgBounds = node.getPrimitiveBounds();
				float[] vb = new float[4];
				vb[0] = 0f;
				vb[1] = 0f;
				vb[2] = (float) imgBounds.getWidth();
				vb[3] = (float) imgBounds.getHeight();

				initializeViewport(ctx, e, node, vb, bounds);
				return node;
			} finally {
				this.ua.release(source);
			}
		} catch (Exception ex) {
			LOG.log(Level.FINE, "画像を読み込めません:" + purlStr, ex);
			ShapeNode sn = new ShapeNode();
			sn.setShape(bounds);
			return sn;
		}
	}
}
