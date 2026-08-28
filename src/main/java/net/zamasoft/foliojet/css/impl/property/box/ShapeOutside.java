package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BasicShapes;
import net.zamasoft.foliojet.css.util.BasicShapes.ShapeSpec;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.ClipPathShape;
import net.zamasoft.foliojet.layout.box.params.ShapeOutsideParams;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.WrappedImage;
import net.zamasoft.zstream.resolver.Source;

/**
 * {@code shape-outside}です(css-shapes-1 §4.1、2026-08-29新設)。
 *
 * <p>
 * {@code none | [<basic-shape> || <shape-box>] | <shape-box> | <image>}。
 * basic-shapeの解析は{@code clip-path}と共通の{@link BasicShapes}。
 * basic-shapeだけを書いた場合の参照ボックスは仕様どおりmargin-box
 * ({@code clip-path}のborder-boxとは既定が異なる)。{@code <image>}は
 * {@code url()}のみ(グラデーション等は未対応=宣言ごと無視)。
 * </p>
 *
 * <p>
 * 浮動体(float:left/right)以外に指定しても効果はない(仕様どおり)。
 * レイアウトへの反映は{@code BoxStyleMapper.setupFloatPos}が
 * {@link #toParams}で{@code FloatPos.shapeOutside}へ載せ、行の配置
 * ({@code TextBuilder.locateLine}→{@code ExclusionSpace.scanLineBand})
 * だけがそれを見る——他の浮動体やBFCを作るブロックの回避は仕様どおり
 * マージンボックスのまま(§4.1「float positioning and stacking are not
 * affected」)。
 * </p>
 */
public class ShapeOutside extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ShapeOutside();
	private static final Logger LOG = Logger.getLogger(ShapeOutside.class.getName());

	/**
	 * パース済みの値です。{@code image}が非nullなら画像指定で、
	 * {@code shape}/{@code box}は使わない。
	 *
	 * @param shape 形状(nullなら参照ボックスのみ)
	 * @param box   参照ボックス(basic-shapeのみの指定はmargin-box)
	 * @param image {@code url()}画像
	 */
	public record ShapeOutsideValue(ShapeSpec shape, ClipPathShape.ReferenceBox box, URIValue image)
			implements Value {
	}

	public static Value get(final CSSStyle style) {
		return style.get(INFO);
	}

	/**
	 * computed valueから浮動体パラメータを作ります(noneはnull)。
	 * {@code shape-margin}・{@code shape-image-threshold}もここで束ねる。
	 *
	 * <p>
	 * 画像指定は、この時点でUAから画素が得られる場合だけ画像形状になる。
	 * 計測パス・構造走査パスは寸法だけのスタブ画像を返す
	 * ({@code AbstractUserAgent.loadImage})ので、そこでは画素が無く
	 * margin-boxへ退避する——計測パスと本レイアウトで行の折返しが
	 * 変わりうるが、pass-count≧2の文書でも本レイアウトの結果が最終出力
	 * なので実害は計測値のずれに留まる(既知の制限、マニュアル参照)。
	 * </p>
	 */
	public static ShapeOutsideParams toParams(final CSSStyle style) {
		final Value value = style.get(INFO);
		if (!(value instanceof ShapeOutsideValue v)) {
			return null;
		}
		final net.zamasoft.foliojet.layout.box.params.Length margin = ShapeMargin.get(style);
		if (v.image() != null) {
			final ShapeOutsideParams.ShapeImage image = loadShapeImage(style, v.image(),
					ShapeImageThreshold.get(style));
			if (image != null) {
				return new ShapeOutsideParams(null, image, margin);
			}
			return new ShapeOutsideParams(new ClipPathShape.BoxOnly(ClipPathShape.ReferenceBox.MARGIN_BOX), null,
					margin);
		}
		return new ShapeOutsideParams(BasicShapes.toShape(v.shape(), v.box()), null, margin);
	}

	/** 画像を読み、閾値で輪郭範囲を抽出します。画素が得られなければnull。 */
	private static ShapeOutsideParams.ShapeImage loadShapeImage(final CSSStyle style, final URIValue uriValue,
			final double threshold) {
		final UserAgent ua = style.getUserAgent();
		final URI uri = uriValue.getURI();
		final Image image;
		try {
			final Source source = ua.resolve(uri);
			try {
				image = ua.getImage(uri, source);
			} finally {
				ua.release(source);
			}
		} catch (Exception e) {
			LOG.log(Level.FINE, "Missing shape image", e);
			ua.message(MessageCodes.WARN_MISSING_IMAGE, uri.toString());
			return null;
		}
		Image original = image;
		while (original instanceof WrappedImage wrapped) {
			original = wrapped.getImage();
		}
		// SVG等のラスタでない画像・寸法だけのスタブは画素を持たない
		if (!(original instanceof net.zamasoft.pdfg2d.g2d.image.RasterImage raster)) {
			return null;
		}
		final java.awt.image.BufferedImage pixels = raster.getImage();
		if (pixels == null || pixels.getWidth() <= 0 || pixels.getHeight() <= 0) {
			return null;
		}
		return ShapeOutsideParams.ShapeImage.extract(pixels, threshold);
	}

	protected ShapeOutside() {
		super("shape-outside");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		if (!(value instanceof ShapeOutsideValue v) || v.shape() == null) {
			return value;
		}
		return new ShapeOutsideValue(BasicShapes.absolutize(v.shape(), style), v.box(), null);
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		ShapeSpec shape = null;
		ClipPathShape.ReferenceBox box = null;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (lu instanceof CssToken.Ident ident) {
				if (ident.is("none")) {
					if (shape != null || box != null || tokens.hasNext()) {
						throw new PropertyException();
					}
					return KeywordValue.NONE;
				}
				final ClipPathShape.ReferenceBox rb = BasicShapes.toReferenceBox(ident);
				if (rb == null || box != null) {
					throw new PropertyException();
				}
				box = rb;
				continue;
			}
			if (lu instanceof CssToken.Uri) {
				if (shape != null || box != null || tokens.hasNext()) {
					throw new PropertyException();
				}
				try {
					final URIValue image = ValueUtils.toURI(ua, uri, lu);
					if (image == null) {
						throw new PropertyException();
					}
					return new ShapeOutsideValue(null, null, image);
				} catch (URISyntaxException e) {
					ua.message(MessageCodes.WARN_BAD_LINK_URI, ((CssToken.Uri) lu).uri());
					throw new PropertyException();
				}
			}
			if (!(lu instanceof CssToken.Func func) || shape != null) {
				throw new PropertyException();
			}
			shape = BasicShapes.parseFunction(func, ua);
		}
		if (shape == null && box == null) {
			throw new PropertyException();
		}
		return new ShapeOutsideValue(shape, box == null ? ClipPathShape.ReferenceBox.MARGIN_BOX : box, null);
	}
}
