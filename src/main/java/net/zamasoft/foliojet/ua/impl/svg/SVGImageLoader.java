package net.zamasoft.foliojet.ua.impl.svg;

import java.awt.geom.Dimension2D;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import org.apache.batik.anim.dom.AbstractSVGAnimatedLength;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGOMDocument;
import org.apache.batik.anim.dom.SVGOMSVGElement;
import org.apache.batik.bridge.BridgeException;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.ParsedURL;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.svg.SVGRect;

import net.zamasoft.foliojet.ua.ImageLoader;
import net.zamasoft.foliojet.ua.ImageMap;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.foliojet.ua.impl.svg.Dimension2DImpl;
import net.zamasoft.pdfg2d.svg.PDFGVTBuilder;
import net.zamasoft.pdfg2d.svg.SVGImage;

public class SVGImageLoader implements ImageLoader {
	private static final Logger LOG = Logger.getLogger(SVGImageLoader.class.getName());
	protected static final String SVG_MIME_TYPE = "image/svg+xml";

	static {
		ParsedURL.registerHandler(MyParsedURLDefaultProtocolHandler.INSTANCE);
	}

	public boolean match(Source key) {
		Source source = (Source) key;
		String mimeType;
		try {
			mimeType = source.getMimeType();
		} catch (IOException e) {
			LOG.log(Level.WARNING, "MIME型を取得できませんでした。", e);
			return false;
		}
		URI uri = source.getURI();
		String path = uri.getPath();
		if (!SVG_MIME_TYPE.equalsIgnoreCase(mimeType)) {
			if (path == null || path.length() == 0) {
				path = uri.getSchemeSpecificPart();
			}
			if (path == null) {
				return false;
			}
			path = path.toLowerCase();
			if (!path.endsWith(".svgz") && !path.endsWith(".svg")) {
				return false;
			}
		}
		return true;
	}

	/** 合成URI({@link #toBatikDocURI})の一意性を保つための連番。 */
	private static final java.util.concurrent.atomic.AtomicLong INLINE_SEQ = new java.util.concurrent.atomic.AtomicLong();

	/**
	 * Batikに渡す文書URIを返します。
	 * <p>
	 * data:等のopaque URIを文書URIにするとBatik内部の各所が壊れる——
	 * 相対解決(java.net.URI#resolveはopaque基底に適用できない)、
	 * 同一文書判定(url(#id)のクリップ・グラデーション参照が「別文書」と
	 * 誤認され、参照文字列自体の再パースや空クリップに至る)など、
	 * 応急処置(ParsedURLハンドラのfragment再構成、clip-path剥がし再試行等)を
	 * 重ねても穴が残った(2026-08-06〜07、yahoo.co.jpのアイコンで発覚)。
	 * そこでopaque URIの文書には一意な合成階層URIを与え、問題の土壌ごと
	 * 取り除く。opaque URI内からの相対参照はもともと解決不能なので、
	 * 差し替えで失われる情報はない。
	 * </p>
	 */
	private static String toBatikDocURI(URI uri) {
		if (!uri.isOpaque()) {
			return uri.toString();
		}
		// http形式にするのはBatik標準のhttpプロトコルハンドラに処理させる
		// ため(独自スキームはMyParsedURLDefaultProtocolHandlerの不完全な
		// ParsedURLDataで処理され、CSS経由の参照解決が壊れる)。ホストは
		// RFC 2606予約の.invalidで、実在せず衝突もフェッチ成功もしない
		return "http://svg-inline.invalid/" + INLINE_SEQ.incrementAndGet() + ".svg";
	}

	public Image loadImage(final UserAgent ua, Source source) throws IOException {
		SVGOMDocument doc = (SVGOMDocument) this.loadDocument(source);
		// loadDocumentがopaque URIを合成URIへ差し替えるため、文書自身が
		// 持つURL(=createDocumentへ渡した値)を使う。source.getURI()を
		// 使うと文書URLと食い違い、同一文書判定が再び壊れる
		return getImage(doc.getURL(), doc, ua);
	}

	/**
	 * 外部SVGのルートへCSSの{@code currentColor}を焼き込んで読み込みます。
	 * {@code mask-image:url(...)}を単色SVGとして描く近似で使用します。
	 */
	public Image loadImage(final UserAgent ua, final Source source, final Color color) throws IOException {
		final SVGOMDocument doc = (SVGOMDocument) this.loadDocument(source);
		final Element root = doc.getDocumentElement();
		root.setAttribute("color", "rgb(" + Math.round(color.getRed() * 255f) + ","
				+ Math.round(color.getGreen() * 255f) + "," + Math.round(color.getBlue() * 255f) + ")");
		if (color.getAlpha() < 1f) {
			root.setAttribute("opacity", Float.toString(color.getAlpha()));
		}
		return getImage(doc.getURL(), doc, ua);
	}

	public Document loadDocument(Source source) throws IOException {
		final URI uri = source.getURI();
		// **拡張子の判定にだけ小文字化した複製を使う**(2026-08-28)。
		// 以前は path 自体を小文字化して使い回しており、その値が下の
		// xml:base にも入っていた。大文字を含むファイル名のSVG
		// (実例: asahi.comの logo_globePlus.svg)では、xml:base 経由の
		// 相対解決が小文字URLを作り、大文字小文字を区別するサーバーへ
		// 存在しないURLを要求していた(実測で403)
		final String path = uri.getPath();
		boolean gzip = path != null && path.toLowerCase(java.util.Locale.ROOT).endsWith(".svgz");

		// SAXSVGDocumentFactoryはスレッドセーフではないことに注意
		SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName());
		final String uriStr = toBatikDocURI(uri);

		SVGOMDocument doc;
		if (!gzip && source.isReader()) {
			try (Reader in = new BufferedReader(source.getReader())) {
				doc = (SVGOMDocument) factory.createDocument(uriStr, in);
			}
		} else {
			InputStream in = new BufferedInputStream(source.getInputStream());
			try {
				if (!gzip) {
					in.mark(2);
					if (in.read() == 0x1f && in.read() == 0x8b) {
						gzip = true;
					}
					in.reset();
				}
				if (gzip) {
					in = new GZIPInputStream(in);
				}
				doc = (SVGOMDocument) factory.createDocument(uriStr, in);
			} finally {
				in.close();
			}

		}

		if (path != null) {
			final int slash = path.lastIndexOf('/');
			final String fileName = slash == -1 ? path : path.substring(slash + 1);
			doc.getDocumentElement().setAttributeNS("http://www.w3.org/XML/1998/namespace", "base", fileName);
		}
		// pathがnull(data:等のopaque URI)のままxml:baseを設定してはならない。
		// 空値のxml:baseは基底結合時にjava.net.URI#resolve("")の非RFC挙動で
		// 文書URIの末尾セグメントを落とし(inline-svg:/1.svg → inline-svg:/)、
		// CSS経由のurl(#id)絶対化が文書URLと食い違って同一文書判定に失敗、
		// クリップ・グラデーション参照が静かに空になる(2026-08-07に特定)
		return doc;
	}

	private static final Dimension2D VIEWPORT = new Dimension2DImpl(400, 400);

	public Image getImage(String docURI, final Document doc, final UserAgent ua) throws IOException {
		try {
			SVGOMSVGElement root = (SVGOMSVGElement) doc.getDocumentElement();
			Dimension2D viewport = VIEWPORT;
			double vbWidth = 0;
			double vbHeight = 0;
			try {
				SVGRect r = root.getViewBox().getBaseVal();
				vbWidth = r.getWidth();
				vbHeight = r.getHeight();
				if (vbWidth > 0 && vbHeight > 0) {
					viewport = new Dimension2DImpl(vbWidth, vbHeight);
				}
			} catch (Exception e) {
				// viewBoxなし・不正は下の固有サイズ解決へ委ねる
			}
			MyBridgeContext ctx = new MyBridgeContext(docURI, ua, viewport, this);
			// かつてここにBridgeException時にclip-path属性を剥がして再試行する
			// 迂回路があった(2026-08-06)。原因だったdata:基底での同一文書内
			// 断片参照(url(#id))の解決失敗はMyURIResolver.getNode()の
			// フラグメント常時同一文書解決で根治したため撤去(2026-08-07)。
			GVTBuilder gvt = new PDFGVTBuilder();
			GraphicsNode gvtRoot = gvt.build(ctx, doc);

			String width = root.getAttribute("width");
			String height = root.getAttribute("height");
			// 固有寸法の判定は**生の属性**で行う(2026-08-27)。widthの既定は
			// 100%で、viewBoxがあると下のgetCheckedValue()がviewBox寸法へ
			// 解決してしまうため、解決後の値では「属性で寸法が決まっている」
			// SVGと区別できない。パーセントは固有寸法にならない(SVG2/CSS)
			final boolean attrWidthFixed = width != null && width.length() > 0 && !width.trim().endsWith("%");
			final boolean attrHeightFixed = height != null && height.length() > 0 && !height.trim().endsWith("%");
			if ((width == null || width.length() == 0) && (height != null && height.length() > 0)) {
				root.setAttribute("width", height);
			} else if ((height == null || height.length() == 0) && (width != null && width.length() > 0)) {
				root.setAttribute("height", width);
			}
			// 'width' attribute - default is 100%
			AbstractSVGAnimatedLength _width = (AbstractSVGAnimatedLength) root.getWidth();
			double w = _width.getCheckedValue();

			// 'height' attribute - default is 100%
			AbstractSVGAnimatedLength _height = (AbstractSVGAnimatedLength) root.getHeight();
			double h = _height.getCheckedValue();

			// GVTのジオメトリ(バウンズ・クリップ形状等)をこの場で確定させる。
			// Batikはクリップ(clip-path)等を遅延評価し、その評価は
			// BridgeContextが弱参照で保持するDOM・CSSエンジンに依存する。
			// FolioJetは構築した GraphicsNode を display list 経由で後から
			// 描画するため、その間にGCが走ると遅延評価が静かに失敗し、
			// クリップ付きSVGの描画だけが空になる(GCタイミング依存で、
			// 2026-08-07にyahoo.co.jpのアイコン消失として発覚。単発の
			// 小文書では再現せず、大きな文書ほど発生した)。getBounds()は
			// ジオメトリ全体を再帰的に確定・キャッシュする
			gvtRoot.getBounds();

			// width/height属性が無い(またはパーセントが0に解決される)SVGの
			// 固有サイズを補完する。viewBoxがあればその寸法・アスペクト比を
			// 使い、無ければCSSの置換要素既定サイズ300x150にする。0のまま
			// 返すと背景描画のPattern生成(BufferedImage)が
			// "Width (0) and height (0) cannot be <= 0"で変換ごと中断する
			// (2026-08-07、yahoo.co.jpのviewBoxのみのアイコンで発覚)。
			// 固有寸法の種別(Image.Intrinsic参照、2026-08-27)。属性で寸法が
			// 決まればSIZE(片側+viewBox比率で導出できる場合も含む)。
			// viewBoxの比率だけならRATIO、どちらも無ければNONE。背景描画の
			// background-size:autoがこれで既定サイズ規則を分岐する——従来は
			// 常に下の代用値を原寸として扱い、viewBoxのみのロゴSVGが背景で
			// 原寸(数百px)のまま描かれて箱からはみ出していた
			// (asahi.comフッターのRe:Ronロゴで発覚)
			net.zamasoft.pdfg2d.gc.image.Image.Intrinsic intrinsic;
			if (attrWidthFixed && attrHeightFixed) {
				intrinsic = net.zamasoft.pdfg2d.gc.image.Image.Intrinsic.SIZE;
			} else if (vbWidth > 0 && vbHeight > 0) {
				// 片側の固定属性+viewBox比率で寸法が決まる場合もSIZE
				intrinsic = (attrWidthFixed || attrHeightFixed)
						? net.zamasoft.pdfg2d.gc.image.Image.Intrinsic.SIZE
						: net.zamasoft.pdfg2d.gc.image.Image.Intrinsic.RATIO;
			} else if (attrWidthFixed || attrHeightFixed) {
				// 片側のみ(viewBoxなし): 上の属性補完で正方形として扱う
				intrinsic = net.zamasoft.pdfg2d.gc.image.Image.Intrinsic.SIZE;
			} else {
				intrinsic = net.zamasoft.pdfg2d.gc.image.Image.Intrinsic.NONE;
			}
			if (w <= 0 || h <= 0) {
				if (vbWidth > 0 && vbHeight > 0) {
					if (w > 0) {
						h = w * vbHeight / vbWidth;
					} else if (h > 0) {
						w = h * vbWidth / vbHeight;
					} else {
						w = vbWidth;
						h = vbHeight;
					}
				} else {
					if (w <= 0) {
						w = 300;
					}
					if (h <= 0) {
						h = 150;
					}
				}
			}

			ImageMap imageMap = ctx.imageMap;
			Image image = new SVGImage(gvtRoot, w, h, intrinsic);
			ua.getUAContext().getImageMaps().put(image, imageMap);
			return image;
		} catch (BridgeException e) {
			// 原因メッセージを含める(「読み込めませんでした」だけでは
			// どの参照・属性で死んだのか分からず、診断のたびに
			// スタックトレース仕込みが要る)
			IOException ioe = new IOException("SVGを読み込めませんでした: " + e.getMessage());
			ioe.initCause(e);
			throw ioe;
		}
	}

}
