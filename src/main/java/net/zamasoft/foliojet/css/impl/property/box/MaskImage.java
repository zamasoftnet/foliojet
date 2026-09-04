package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.PaintValue;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundColor;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.ImageLoadDiagnostics;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.svg.SVGImageLoader;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * <b>mask-imageのグラデーション形の近似</b>です(2026-08-09新設)。
 *
 * <p>
 * 実サイトは「本文の抜粋を{@code max-height}で固定し、
 * {@code mask-image: linear-gradient(#000 60%, transparent)}で下端を
 * フェードアウトさせる」抜粋イディオムを使う({@code overflow: hidden}は
 * 書かないことがある——マスクがはみ出しを透明にするため画面では困らない)。
 * マスクを丸ごと無視すると、はみ出した本文がそのまま描かれて後続の内容に
 * 重なる(5ch.ioのスレッド一覧で実測)。
 * </p>
 *
 * <p>
 * 完全なアルファマスク合成はPDF出力の大工事になるため、<b>グラデーションの
 * マスクをペイントのボックスクリップへ近似</b>する: 値にグラデーション関数を
 * 含むとき{@link KeywordValue#CLIP}を計算値とし、
 * {@code BlockParams.paintClip}経由で{@code overflow: hidden}と同じ
 * ペイントクリップだけを適用する(BFC成立などのレイアウト効果は持たない)。
 * ボックス内の描画はフェードなしで残る——印刷ではフェードよりも
 * 「はみ出しが見えない」ことが本質のため、この妥協を選ぶ。
 * {@code url()}のマスク(アイコン型抜き等)はクリップでは近似できないため
 * 従来どおり無視する({@link KeywordValue#NONE})。
 * </p>
 */
public class MaskImage extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MaskImage();

	/** グラデーションマスクの近似としてペイントをボックスへクリップするか。 */
	public static boolean isClip(CSSStyle style) {
		return style.get(INFO) == KeywordValue.CLIP;
	}

	/**
	 * URLマスクを、背景色で着色したSVG画像として返します。
	 *
	 * <p>PDFの任意画像アルファマスクではなく、透明背景の単色SVGを直接描く
	 * 近似です。MDN等のアイコン用マスクでは同じ見た目になり、従来の
	 * 「背景色の四角だけが残る」欠陥を避けます。</p>
	 */
	public static Image getImage(final CSSStyle style) {
		final Value value = style.get(INFO);
		if (!(value instanceof URIValue uriValue)) {
			return null;
		}
		final PaintValue backgroundPaint = BackgroundColor.get(style);
		if (backgroundPaint == null) {
			return null;
		}
		final Color color = backgroundPaint instanceof ColorValue colorValue
				? colorValue.getColor()
				: CSSColor.get(style);
		final URI uri = uriValue.getURI();
		final String key = uri + "#mask-color=" + Math.round(color.getRed() * 255f) + ","
				+ Math.round(color.getGreen() * 255f) + "," + Math.round(color.getBlue() * 255f) + ","
				+ Math.round(color.getAlpha() * 255f);
		final UserAgent ua = style.getUserAgent();
		Image image = ua.getDocumentContext().getMaskImage(key);
		if (image != null) {
			return image;
		}
		image = ImageLoadDiagnostics.load(ua, uri, (resolvedUri, source) -> {
			final SVGImageLoader loader = new SVGImageLoader();
			return loader.match(source) ? loader.loadImage(ua, source, color) : null;
		});
		if (image != null) {
			ua.getDocumentContext().putMaskImage(key, image);
		}
		return image;
	}

	private MaskImage() {
		super("mask-image");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken first = tokens.next();
		if (ValueUtils.isNone(first)) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return KeywordValue.NONE;
		}
		try {
			// url()とimage-set()(2026-08-29)
			final URIValue uriValue = ValueUtils.toImage(ua, uri, first);
			if (uriValue != null) {
				if (tokens.hasNext()) {
					throw new PropertyException();
				}
				return uriValue;
			}
			if (ValueUtils.isImage(first)) {
				throw new PropertyException();
			}
		} catch (URISyntaxException e) {
			ua.message(MessageCodes.WARN_BAD_LINK_URI, ValueUtils.uriText(first));
			throw new PropertyException();
		}

		// 複数レイヤー(カンマ区切り)を含め全トークンを読み、1つでも
		// グラデーション関数があればクリップ近似を適用する。
		boolean clip = false;
		if (first instanceof CssToken.Func func
				&& func.name().toLowerCase(Locale.ROOT).endsWith("gradient")) {
			clip = true;
		}
		while (tokens.hasNext()) {
			final CssToken token = tokens.next();
			if (token instanceof CssToken.Func func
					&& func.name().toLowerCase(Locale.ROOT).endsWith("gradient")) {
				clip = true;
			}
		}
		if (!clip) {
			throw new PropertyException();
		}
		return KeywordValue.CLIP;
	}

}
