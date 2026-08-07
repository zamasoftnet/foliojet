package net.zamasoft.foliojet.ua.impl.svg;

import org.apache.batik.bridge.AbstractSVGGradientElementBridge;
import org.apache.batik.bridge.BridgeContext;
import org.w3c.dom.Element;

/**
 * グラデーションの{@code <stop>}要素のブリッジです。
 * <p>
 * SVG 1.1のBatik(特に製品同梱のbatik-all 1.14)は{@code offset}属性を必須と
 * してBridgeExceptionを投げるが、実ブラウザ(およびSVG 2)は省略時0として
 * 描画する。実サイトには省略したSVGが普通に存在する(2026-08-07、
 * yahoo.co.jpのAIアシスタントアイコン{@code <stop stop-color="#FF598E"/>}で
 * 発覚——グラデーション解決の失敗はアイコン全体の消失になる)ため、
 * 省略時は0を補ってからBatikへ委譲する。
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
