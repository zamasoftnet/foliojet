package net.zamasoft.foliojet.css.impl.property.background;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.zstream.resolver.Source;
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
	private static final Logger LOG = Logger.getLogger(BackgroundImage.class.getName());

	public static Image get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.NONE || !(value instanceof URIValue)) {
			// グラデーション(PaintValue)は画像ではなく塗り——getPaint()
			return null;
		}
		UserAgent ua = style.getUserAgent();
		URIValue uriValue = (URIValue) value;
		URI uri = uriValue.getURI();
		try {
			// 記録済みの寸法があれば解決しない(2026-08-16)。解決が取得を伴う
			// 経路では、ここで止めないと使わない画像を転送してしまう
			Image known = ua.getImageMetrics(uri);
			if (known != null) {
				return known;
			}
			Source source = ua.resolve(uri);
			try {
				return ua.getImage(uri, source);
			} finally {
				ua.release(source);
			}
		} catch (Exception e) {
			LOG.log(Level.FINE, "Missing image", e);
			ua.message(MessageCodes.WARN_MISSING_IMAGE, uri.toString());
			return null;
		}
	}

	/**
	 * グラデーションの塗りを返します(2026-08-29)。{@code background-image}に
	 * グラデーション関数が書かれた場合は、画像(url())ではなく
	 * {@link PaintValue}として保持する——描画側(BoxStyleMapper.createBackground)
	 * が背景色の代わりに塗る。画像・none のときは null。
	 */
	public static PaintValue getPaint(CSSStyle style) {
		Value value = style.get(INFO);
		return value instanceof PaintValue paint ? paint : null;
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
		final CssToken lu = tokens.next();
		if (ValueUtils.isNone(lu)) {
			return KeywordValue.NONE;
		}
		try {
			final URIValue value = ValueUtils.toURI(ua, uri, lu);
			if (value != null) {
				return value;
			}
		} catch (URISyntaxException e) {
			ua.message(MessageCodes.WARN_BAD_LINK_URI, ((CssToken.Uri) lu).uri());
		}
		// グラデーション(2026-08-29)。複数レイヤ(コンマ区切り)は最初の
		// レイヤだけを採る(多層背景は未対応——記録済み)
		final PaintValue gradient = net.zamasoft.foliojet.css.util.ColorValueUtils.toGradient(ua, lu);
		if (gradient != null) {
			return gradient;
		}
		throw new PropertyException();
	}

}