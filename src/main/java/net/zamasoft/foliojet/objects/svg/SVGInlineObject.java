package net.zamasoft.foliojet.objects.svg;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.net.URI;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGOMDocument;
import org.apache.batik.util.ParsedURL;
import org.apache.batik.util.XMLResourceDescriptor;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import net.zamasoft.foliojet.css.InlineObject;
import net.zamasoft.foliojet.css.util.LengthUtils;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.ua.impl.svg.SVGImageLoader;
import net.zamasoft.foliojet.ua.ImageMap;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.xml.util.XMLParsers;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.util.TransformedImage;
import net.zamasoft.foliojet.css.token.Unit;

public class SVGInlineObject extends SAXSVGDocumentFactory
		implements net.zamasoft.foliojet.css.StyleAwareInlineObject {
	protected SVGImageLoader loader = null;

	/** ホスト文書側のsvg要素のスタイル(著者CSSのvar()解決の文脈)。 */
	private net.zamasoft.foliojet.css.CSSStyle hostStyle;
	private net.zamasoft.foliojet.css.value.internal.CSSJImageValue.SvgSource runningSource;

	public net.zamasoft.foliojet.css.value.internal.CSSJImageValue.SvgSource getRunningSource() {
		return this.runningSource;
	}

	private boolean isRunning() {
		for (var style = this.hostStyle; style != null; style = style.getParentStyle()) {
			if (style.get(net.zamasoft.foliojet.css.impl.property.box.CSSPosition.INFO)
					instanceof net.zamasoft.foliojet.css.value.RunningPositionValue) {
				return true;
			}
		}
		return false;
	}

	/** シリアライズ中から上限を守り、巨大SVGを一旦丸ごとコピーしない。 */
	private void snapshotRunningSource(final org.w3c.dom.Document document, final String baseURI) {
		final StringBuilder xml = new StringBuilder();
		final int limit = net.zamasoft.foliojet.css.style.running.RunningCapture.MAX_TEXT_BYTES / 2 - baseURI.length();
		try {
			final java.io.Writer writer = new java.io.Writer() {
				@Override
				public void write(final char[] chars, final int offset, final int length) throws IOException {
					if (length > limit - xml.length()) {
						throw new IOException("running SVG text bytes");
					}
					xml.append(chars, offset, length);
				}

				@Override
				public void flush() {
				}

				@Override
				public void close() {
				}
			};
			javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(
					new javax.xml.transform.dom.DOMSource(document), new javax.xml.transform.stream.StreamResult(writer));
			this.runningSource = new net.zamasoft.foliojet.css.value.internal.CSSJImageValue.SvgSource(xml.toString(), baseURI);
		} catch (final javax.xml.transform.TransformerException e) {
			// 通常の画像化は続行する。runningの捕捉側が警告して登録を拒否する。
			this.runningSource = new net.zamasoft.foliojet.css.value.internal.CSSJImageValue.SvgSource(null, baseURI);
		}
	}

	public void setHostStyle(net.zamasoft.foliojet.css.CSSStyle style) {
		this.hostStyle = style;
	}

	public SVGInlineObject() {
		super(XMLResourceDescriptor.getXMLParserClassName());
		synchronized (this) {
			if (this.loader == null) {
				this.loader = new SVGImageLoader();
			}
		}
		try {
			this.parser = XMLParsers.createXMLReader();
		} catch (Exception e) {
			// ignore
		}
		this.setValidating(false);
	}
	/**
	 * HTMLパーサーが小文字化したSVG要素名を正しいcamelCaseへ戻す表です
	 * (HTML Standard §13.2.6.5 "adjust SVG tag names"と同内容)。HTML内の
	 * インラインSVGはHTML構文で字句解析されるため要素名・属性名が小文字化
	 * されるが、SVG DOM(Batik)はcamelCaseでしか認識しない——たとえば
	 * {@code lineargradient}のままではグラデーションが無効になり、
	 * fill="url(#...)"のパスが描画されず絵ごと消える(2026-08-07、
	 * yahoo.co.jpのAIアシスタントアイコンで発覚)。
	 */
	private static final java.util.Map<String, String> SVG_TAG_ADJUST = buildAdjustMap(new String[] { "altGlyph",
			"altGlyphDef", "altGlyphItem", "animateColor", "animateMotion", "animateTransform", "clipPath", "feBlend",
			"feColorMatrix", "feComponentTransfer", "feComposite", "feConvolveMatrix", "feDiffuseLighting",
			"feDisplacementMap", "feDistantLight", "feDropShadow", "feFlood", "feFuncA", "feFuncB", "feFuncG",
			"feFuncR", "feGaussianBlur", "feImage", "feMerge", "feMergeNode", "feMorphology", "feOffset",
			"fePointLight", "feSpecularLighting", "feSpotLight", "feTile", "feTurbulence", "foreignObject", "glyphRef",
			"linearGradient", "radialGradient", "textPath" });

	/**
	 * HTMLパーサーが小文字化したSVG属性名を戻す表です(HTML Standard
	 * §13.2.6.5 "adjust SVG attributes"と同内容)。
	 */
	private static final java.util.Map<String, String> SVG_ATTR_ADJUST = buildAdjustMap(new String[] {
			"attributeName", "attributeType", "baseFrequency", "baseProfile", "calcMode", "clipPathUnits",
			"diffuseConstant", "edgeMode", "filterUnits", "glyphRef", "gradientTransform", "gradientUnits",
			"kernelMatrix", "kernelUnitLength", "keyPoints", "keySplines", "keyTimes", "lengthAdjust",
			"limitingConeAngle", "markerHeight", "markerUnits", "markerWidth", "maskContentUnits", "maskUnits",
			"numOctaves", "pathLength", "patternContentUnits", "patternTransform", "patternUnits", "pointsAtX",
			"pointsAtY", "pointsAtZ", "preserveAlpha", "preserveAspectRatio", "primitiveUnits", "refX", "refY",
			"repeatCount", "repeatDur", "requiredExtensions", "requiredFeatures", "specularConstant",
			"specularExponent", "spreadMethod", "startOffset", "stdDeviation", "stitchTiles", "surfaceScale",
			"systemLanguage", "tableValues", "targetX", "targetY", "textLength", "viewBox", "viewTarget",
			"xChannelSelector", "yChannelSelector", "zoomAndPan" });

	private static java.util.Map<String, String> buildAdjustMap(String[] names) {
		final java.util.Map<String, String> map = new java.util.HashMap<>();
		for (final String name : names) {
			map.put(name.toLowerCase(java.util.Locale.ROOT), name);
		}
		return map;
	}

	private static String adjustTag(String name) {
		final String adjusted = SVG_TAG_ADJUST.get(name);
		return adjusted != null ? adjusted : name;
	}

	@Override
	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		AttributesImpl attsi = null;
		for (int i = 0; i < atts.getLength(); ++i) {
			final String adjusted = SVG_ATTR_ADJUST.get(atts.getLocalName(i));
			if (adjusted != null) {
				if (attsi == null) {
					attsi = new AttributesImpl(atts);
					atts = attsi;
				}
				attsi.setLocalName(i, adjusted);
				attsi.setQName(i, adjusted);
			}
		}
		super.startElement(uri, adjustTag(lName), adjustTag(qName), atts);
	}

	@Override
	public void endElement(String uri, String lName, String qName) throws SAXException {
		super.endElement(uri, adjustTag(lName), adjustTag(qName));
	}


	public Image getImage(UserAgent ua) throws IOException {
		SVGOMDocument doc = (SVGOMDocument) this.document;
		this.document = null;
		this.currentNode = null;
		this.locator = null;

		URI uri = ua.getDocumentContext().getBaseURI();
		String path = uri.getPath();
		if (path != null) {
			int slash = path.lastIndexOf('/');
			if (slash != -1) {
				path = path.substring(slash + 1);
			}
		}
		doc.getDocumentElement().setAttributeNS("http://www.w3.org/XML/1998/namespace", "base", path);
		doc.setParsedURL(new ParsedURL(uri.toString()));

		// HTML文書の著者CSSのSVG向け部分集合を<style>として注入する
		// (2026-08-07)。インラインSVGは独立文書としてBatikに渡されるため、
		// これが無いとCSSクラスでfill/strokeを塗るアイコンがSVG既定の
		// fill=blackで黒く塗り潰れる(qiitaのいいねボタンで発覚)。
		// 収集と濾過はCSSStyleSheetBuilder.collectSVGStyleRule、var()の
		// 解決(hostStyleの文脈)はSVGAuthorCss.toCssText参照。
		// ルートの先頭へ入れるのは、SVG内の既存<style>や style属性が
		// あとから重なって勝てるようにするため(カスケードの出現順)
		final String svgAuthorCss = ua.getDocumentContext().getSVGAuthorCss().toCssText(this.hostStyle);
		final boolean running = this.isRunning();
		this.hostStyle = null;
		if (!svgAuthorCss.isEmpty()) {
			// **規則ごとに別々の<style>にする**(2026-08-07)。Batikは
			// スタイルシート内に1つでも読めない値があるとシート全体を
			// 無効にする(qiitaのdisplay:flexで全アイコンが素の黒に
			// 戻った)。SVGAuthorCss側の白リストで大半は防ぐが、想定外の
			// 値が1つ混ざっても被害がその規則に閉じるよう、失敗の単位を
			// 規則へ落とす
			final org.w3c.dom.Element root = doc.getDocumentElement();
			org.w3c.dom.Node anchor = root.getFirstChild();
			for (final String rule : svgAuthorCss.split("\n")) {
				if (rule.isEmpty()) {
					continue;
				}
				final org.w3c.dom.Element styleElement = doc.createElementNS("http://www.w3.org/2000/svg", "style");
				styleElement.setAttributeNS(null, "type", "text/css");
				styleElement.appendChild(doc.createCDATASection(rule));
				root.insertBefore(styleElement, anchor);
			}
		}

		if (running) {
			this.snapshotRunningSource(doc, uri.toString());
		}
		Image image = this.loader.getImage(uri.toString(), doc, ua);
		double scale = LengthUtils.convert(ua, 1.0, Unit.PX, Unit.PT);
		if (scale != 1) {
			ImageMap map = ua.getUAContext().getImageMaps().remove(image);
			AffineTransform at = AffineTransform.getScaleInstance(scale, scale);
			image = new TransformedImage(image, at);
			map = map.getTransformedImageMap(at);
			ua.getUAContext().getImageMaps().put(image, map);
		}
		return image;
	}

}
