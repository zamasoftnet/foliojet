package net.zamasoft.foliojet.ua.impl.svg;

import org.apache.batik.bridge.AbstractSVGGradientElementBridge;
import org.apache.batik.bridge.BridgeContext;
import org.w3c.dom.Element;

/**
 * Bridge for gradient {@code <stop>} elements.
 * <p>
 * Batik's SVG 1.1 implementation (especially the bundled batik-all 1.14) requires
 * the {@code offset} attribute and throws BridgeException when it is absent,
 * while actual browsers (and SVG 2) render a missing value as 0.
 * Real sites commonly omit it (discovered on 2026-08-07 in yahoo.co.jp's AI assistant icon,
 * {@code <stop stop-color="#FF598E"/>}; failure to resolve the gradient made the entire icon disappear),
 * so supply 0 when absent before delegating to Batik.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class MySVGStopElementBridge extends AbstractSVGGradientElementBridge.SVGStopElementBridge {

	@Override
	public AbstractSVGGradientElementBridge.Stop createStop(BridgeContext ctx, Element gradientElement,
			Element stopElement, float opacity) {
		if (stopElement.getAttributeNS(null, "offset").length() == 0) {
			stopElement.setAttributeNS(null, "offset", "0");
		}
		return super.createStop(ctx, gradientElement, stopElement, opacity);
	}
}
