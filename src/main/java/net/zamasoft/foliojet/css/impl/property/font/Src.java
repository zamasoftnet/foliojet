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
	 * <b>Excludes unreadable {@code src} formats from the candidates</b> (2026-08-05).
	 *
	 * <p>
	 * Font loading is asynchronous (FutureTask), so even if {@code addFontFace} fails later,
	 * <b>the caller assumes success and does not try the next candidate</b>.
	 * Thus, with `src: url(a.woff2) format("woff2"), url(a.woff) format("woff")`,
	 * a WOFF2 failure led to the default font <b>without falling back to WOFF</b>.
	 * The real-world corpus had 1265 woff2 and 323 woff occurrences;
	 * most modern sites use this pattern.
	 * </p>
	 *
	 * <p>
	 * Supported formats are sfnt (truetype/opentype), WOFF, WOFF2, and
	 * TrueType Collection. <b>EOT and SVG fonts are unsupported</b>.
	 * </p>
	 */
	private static boolean unsupportedFormat(String format) {
		switch (format.toLowerCase(java.util.Locale.ROOT)) {
		// **WOFF2 support was added on 2026-08-06, so it was removed from here.**
		// Forgetting to remove it would leave the new implementation unused on most real sites,
		// which put `format("woff2")` first.
		case "svg":
		case "embedded-opentype":
			return true;
		default:
			return false;
		}
	}

	/** Fallback when no hint exists. Uses only the extension (strictly a supplementary check). */
	private static boolean unsupportedExtension(URI uriv) {
		final String path = uriv.getPath();
		if (path == null) {
			return false;
		}
		final String lower = path.toLowerCase(java.util.Locale.ROOT);
		return lower.endsWith(".eot") || lower.endsWith(".svg");
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		List<URI> list = new ArrayList<URI>();
		// Position of the URL just added (format() applies to that URL). -1=none.
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
			// Ignore other tokens (commas, etc.).
		}
		return new SrcValue((URI[]) list.toArray(new URI[list.size()]));
	}

}