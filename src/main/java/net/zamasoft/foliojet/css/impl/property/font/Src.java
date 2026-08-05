package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.SrcValue;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class Src extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Src();

	public static URI[] get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.NONE) {
			return null;
		}
		SrcValue srcValue = (SrcValue) value;
		return srcValue.getURIs();
	}

	protected Src() {
		super("src");
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

	/**
	 * <b>読めない形式の{@code src}は候補から外します</b>(2026-08-05)。
	 *
	 * <p>
	 * フォントの読み込みは非同期(FutureTask)なので、{@code addFontFace}が
	 * 後から失敗しても<b>呼び出し側は成功したと思って次の候補へ進まない</b>。
	 * つまり `src: url(a.woff2) format("woff2"), url(a.woff) format("woff")` と
	 * 書かれていると、WOFF2で失敗したきり<b>WOFFへ落ちずに</b>既定フォントに
	 * なる。実地コーパスではwoff2が1265件・woffが323件で、現代のサイトの
	 * ほとんどがこの形。
	 * </p>
	 *
	 * <p>
	 * 対応しているのは sfnt(truetype/opentype)・WOFF・TrueType Collection。
	 * <b>WOFF2はBrotli伸長が要るため未対応</b>(pdfg2dの{@code FontFile}が
	 * {@code wOFF}と{@code ttcf}しか見ない)。EOTとSVGフォントも未対応。
	 * </p>
	 */
	private static boolean unsupportedFormat(String format) {
		switch (format.toLowerCase(java.util.Locale.ROOT)) {
		case "woff2":
		case "svg":
		case "embedded-opentype":
			return true;
		default:
			return false;
		}
	}

	/** ヒントが無いときの保険。拡張子だけで判断する(あくまで補助)。 */
	private static boolean unsupportedExtension(URI uriv) {
		final String path = uriv.getPath();
		if (path == null) {
			return false;
		}
		final String lower = path.toLowerCase(java.util.Locale.ROOT);
		return lower.endsWith(".woff2") || lower.endsWith(".eot") || lower.endsWith(".svg");
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		List<URI> list = new ArrayList<URI>();
		// 直前に足したURLの位置(format()はそのURLに掛かる)。-1=無し
		int lastUri = -1;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (lu instanceof CssToken.Uri uriToken) {
				try {
					final URI uriv = URIHelper.resolve(ua.getDocumentContext().getEncoding(), uri, uriToken.uri());
					if (unsupportedExtension(uriv)) {
						lastUri = -1;
						continue;
					}
					lastUri = list.size();
					list.add(uriv);
				} catch (URISyntaxException e) {
					ua.message(MessageCodes.WARN_BAD_LINK_URI, uriToken.uri());
				}
			} else if (lu instanceof CssToken.Func fmt && fmt.is("format")) {
				if (lastUri >= 0) {
					final TokenStream params = fmt.argStream();
					while (params.hasNext()) {
						final CssToken param = params.next();
						final String name;
						if (param instanceof CssToken.Str str) {
							name = str.value();
						} else if (param instanceof CssToken.Ident ident) {
							name = ident.name();
						} else {
							continue;
						}
						if (unsupportedFormat(name)) {
							list.remove(lastUri);
							lastUri = -1;
							break;
						}
					}
				}
			} else if (lu instanceof CssToken.Func func && func.is("local")) {
				lastUri = -1;
				final TokenStream params = func.argStream();
				while (params.hasNext()) {
					final CssToken param = params.next();
					final String name;
					if (param instanceof CssToken.Str str) {
						name = str.value();
					} else if (param instanceof CssToken.Ident ident) {
						name = ident.name();
					} else {
						continue;
					}
					try {
						list.add(URIHelper.create("UTF-8", "local-font:" + name));
					} catch (URISyntaxException e) {
						throw new PropertyException();
					}
				}
			}
			// その他のトークン(コンマ等)は無視
		}
		return new SrcValue((URI[]) list.toArray(new URI[list.size()]));
	}

}