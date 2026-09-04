package net.zamasoft.foliojet.css.impl.property.background;

import java.net.URI;
import java.net.URISyntaxException;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.ImageLoadDiagnostics;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.PaintValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class BackgroundImage extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BackgroundImage();

	/**
	 * 多層背景の値(2026-08-29)。各レイヤは{@link URIValue}、
	 * {@link PaintValue}(グラデーション)、または位置合わせ用のnoneで、先頭が最前面。単層は
	 * 値をそのまま持つのでこの型にならない。
	 */
	public record LayersValue(Value[] layers) implements Value {
		@Override
		public String toString() {
			return java.util.Arrays.toString(this.layers);
		}
	}

	/** 最前面の画像を返します(単層時代からの窓口)。 */
	public static Image get(CSSStyle style) {
		for (final Value layer : getLayers(style)) {
			if (layer instanceof URIValue uri) {
				return load(style, uri);
			}
		}
		return null;
	}

	/** 全レイヤ(先頭が最前面)。noneなら空。 */
	public static Value[] getLayers(CSSStyle style) {
		final Value value = style.get(INFO);
		if (value instanceof LayersValue layers) {
			return layers.layers();
		}
		if (value == KeywordValue.NONE || value == null) {
			return new Value[0];
		}
		return new Value[] { value };
	}

	/**
	 * レイヤの画像を読み込みます。読めなければnull。
	 *
	 * <p>
	 * 読み込んだ画像には{@code image-orientation}(2026-08-30)を適用する。
	 * 背景・マスク・{@code border-image}はいずれもこの入口を通る。
	 */
	public static Image load(CSSStyle style, URIValue uriValue) {
		return net.zamasoft.foliojet.css.impl.property.image.ImageOrientation.apply(style, loadRaw(style, uriValue));
	}

	private static Image loadRaw(CSSStyle style, URIValue uriValue) {
		UserAgent ua = style.getUserAgent();
		URI uri = uriValue.getURI();
		return ImageLoadDiagnostics.loadImage(ua, uri, true);
	}

	/**
	 * グラデーションの塗りを返します(2026-08-29)。{@code background-image}に
	 * グラデーション関数が書かれた場合は、画像(url())ではなく
	 * {@link PaintValue}として保持する——描画側(BoxStyleMapper.createBackground)
	 * が背景色の代わりに塗る。画像・none のときは null。
	 */
	public static PaintValue getPaint(CSSStyle style) {
		for (final Value layer : getLayers(style)) {
			if (layer instanceof PaintValue paint) {
				return paint;
			}
		}
		return null;
	}

	protected BackgroundImage() {
		super("background-image");
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
		// 多層(コンマ区切り、2026-08-29)。noneもレイヤ位置の対応を
		// 保つためリスト内には残す(全レイヤnoneのときだけ単一noneへ畳む)
		final java.util.List<Value> layers = new java.util.ArrayList<Value>();
		boolean hasImage = false;
		for (final TokenStream layer : tokens.splitComma()) {
			final CssToken lu = layer.next();
			if (lu == null || layer.hasNext()) {
				throw new PropertyException();
			}
			final Value value = parseLayer(ua, uri, lu);
			if (value == null) {
				throw new PropertyException();
			}
			layers.add(value);
			hasImage |= value != KeywordValue.NONE;
		}
		if (layers.isEmpty() || !hasImage) {
			return KeywordValue.NONE;
		}
		if (layers.size() == 1) {
			return layers.get(0);
		}
		return new LayersValue(layers.toArray(new Value[layers.size()]));
	}

	/** 1レイヤ({@code none}・url()・グラデーション)。読めなければnull。 */
	public static Value parseLayer(UserAgent ua, URI uri, CssToken lu) {
		if (ValueUtils.isNone(lu)) {
			return KeywordValue.NONE;
		}
		try {
			// url()とimage-set()(2026-08-29、出力解像度に最も近い候補)
			final URIValue value = ValueUtils.toImage(ua, uri, lu);
			if (value != null) {
				return value;
			}
			if (ValueUtils.isImage(lu)) {
				return null; // 採れる候補の無いimage-set()(呼び出し側で宣言無効)
			}
		} catch (URISyntaxException e) {
			ua.message(MessageCodes.WARN_BAD_LINK_URI, ValueUtils.uriText(lu));
		}
		return net.zamasoft.foliojet.css.util.ColorValueUtils.toGradient(ua, lu);
	}

}
