package net.zamasoft.foliojet.css.util;

import java.net.URI;
import java.net.URISyntaxException;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.util.NumberUtils;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.foliojet.css.value.CalcFontRelativeValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.TypedAttrValue;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;
import net.zamasoft.foliojet.css.token.Unit;

/**
 * @author MIYABE Tatsuhiko
 */
public final class ValueUtils {
	private ValueUtils() {
		// unused
	}

	/**
	 * Returns true for an identifier keyword (case-insensitive).
	 */
	public static boolean isKeyword(CssToken token, String keyword) {
		return token instanceof CssToken.Ident ident && ident.is(keyword);
	}

	/**
	 * Returns true for auto.
	 */
	public static boolean isAuto(CssToken token) {
		return isKeyword(token, "auto");
	}

	/**
	 * Returns true for none.
	 */
	public static boolean isNone(CssToken token) {
		return isKeyword(token, "none");
	}

	/**
	 * Returns true for normal.
	 */
	public static boolean isNormal(CssToken token) {
		return isKeyword(token, "normal");
	}

	/**
	 * Converts &lt;length&gt; to a value.
	 */
	public static LengthValue toLength(UserAgent ua, CssToken token) {
		if (token instanceof CssToken.Dim dim) {
			switch (dim.unit()) {
			case EM:
				return RelativeLengthValue.em(dim.value());
			case EX:
				return RelativeLengthValue.ex(dim.value());
			case REM:
				return RelativeLengthValue.rem(dim.value());
			case CH:
				return RelativeLengthValue.ch(dim.value());
			case LH:
				return RelativeLengthValue.lh(dim.value());
			case CAP:
				return RelativeLengthValue.cap(dim.value());
			case RLH:
				return RelativeLengthValue.rlh(dim.value());
			case CQW:
			case CQI:
				// Container query units (stage 6, 2026-08-15). Like RelativeLengthValue,
				// do not resolve at parse time; resolve during used-value computation
				// in emExToAbsoluteLength.
				return net.zamasoft.foliojet.css.value.ContainerRelativeLengthValue.of(dim.unit(), dim.value());
			default:
				break;
			}
		}
		return toAbsoluteLength(ua, token);
	}

	/**
	 * Converts a string representation to a length.
	 */
	public static LengthValue toLength(UserAgent ua, boolean legacy, String s) {
		try {
			s = s.toLowerCase(java.util.Locale.ROOT).trim();
			// Check rem before em (until 2026-10-04, it came later: the em branch read "2r" from "2rem" as a number and rejected it)
			if (s.endsWith("rem")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 3));
				return RelativeLengthValue.rem(len);
			} else if (s.endsWith("em")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return RelativeLengthValue.em(len);
			} else if (s.endsWith("ex")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return RelativeLengthValue.ex(len);
			} else if (s.endsWith("ch")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return RelativeLengthValue.ch(len);
			} else if (s.endsWith("lh")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return RelativeLengthValue.lh(len);
			} else {
				return toAbsoluteLength(ua, legacy, s);
			}
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Converts a string representation to a length.
	 */
	public static AbsoluteLengthValue toAbsoluteLength(UserAgent ua, boolean legacy, String s) {
		if (s == null) {
			return null;
		}
		s = s.toLowerCase().trim();
		try {
			if (s.endsWith("q")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 1));
				return AbsoluteLengthValue.create(ua, len, Unit.Q);
			} else if (s.endsWith("mm")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, Unit.MM);
			} else if (s.endsWith("cm")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, Unit.CM);
			} else if (s.endsWith("pt")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, Unit.PT);
			} else if (s.endsWith("px")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, Unit.PX);
			} else if (s.endsWith("pc")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, Unit.PC);
			} else if (s.endsWith("in")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, Unit.IN);
			} else {
				double len = NumberUtils.parseDouble(s);
				if (len == 0) {
					return AbsoluteLengthValue.ZERO;
				}
				if (legacy) {
					return AbsoluteLengthValue.create(ua, len, Unit.PX);
				}
				return null;
			}
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * If value is EM_LENGTH or EX_LENGTH, converts it to an absolute length using style's font information.
	 */
	public static Value emExToAbsoluteLength(Value value, CSSStyle style) {
		if (value instanceof RelativeLengthValue relative) {
			return relative.toAbsoluteLength(style);
		}
		// Container query units (stage 6, 2026-08-15). Same path as RelativeLengthValue
		if (value instanceof net.zamasoft.foliojet.css.value.ContainerRelativeLengthValue containerRelative) {
			return containerRelative.toAbsoluteLength(style);
		}
		// **Resolve font-relative units inside calc() here too** (2026-08-03).
		// The element's font-size is unavailable at parse time, so carry them here
		// separately from absolute and ratio components ({@link CalcFontRelativeValue}).
		if (value instanceof CalcFontRelativeValue calc) {
			return calc.resolve(style);
		}
		// Typed attr() (2026-08-03). Reads an attribute to produce a value. If resolution
		// fails with no fallback, return null; the caller (DeferredProperty) treats it as
		// invalid at computed-value time, falling back to the equivalent of unset.
		if (value instanceof TypedAttrValue attr) {
			final Value resolved = attr.resolve(style);
			return resolved == null ? KeywordValue.NONE : emExToAbsoluteLength(resolved, style);
		}
		// fit-content(<length-percentage>) argument (2026-08-29). Like calc() font-relative
		// components, resolve here where the element's font-size is known.
		if (value instanceof net.zamasoft.foliojet.css.value.FitContentValue fit) {
			final Value argument = emExToAbsoluteLength(fit.argument(), style);
			return argument == fit.argument() ? fit
					: new net.zamasoft.foliojet.css.value.FitContentValue(argument);
		}
		return value;
	}

	/**
	 * Converts &lt;length&gt; other than font-relative lengths to a value.
	 */
	public static AbsoluteLengthValue toAbsoluteLength(UserAgent ua, CssToken token) {
		if (token instanceof CssToken.Dim dim) {
			switch (dim.unit()) {
			case IN:
			case CM:
			case MM:
			case Q:
			case PT:
			case PC:
			case PX:
				return AbsoluteLengthValue.create(ua, dim.value(), dim.unit());
			case VW:
			case VH:
			case VMIN:
			case VMAX:
				// Viewport units (2026-08-29). In paged media, type area dimensions are known
				// at parse time (from UA properties), so unlike rem, etc.,
				// they can be resolved immediately to absolute lengths. Leaves in calc()/min()/max()
				// (CalcValueUtils.evaluateLeaf) also follow this path,
				// so values are identical inside expressions.
				return ViewportUnits.resolve(ua, dim.unit(), dim.value());
			default:
				return null;
			}
		}
		if (token instanceof CssToken.Num num && num.value() == 0) {
			return AbsoluteLengthValue.ZERO;
		}
		return null;
	}

	/**
	 * Converts &lt;percentage&gt; to a value.
	 */
	public static PercentageValue toPercentage(CssToken token) {
		if (token instanceof CssToken.Percent percent) {
			return PercentageValue.create(percent.value());
		}
		return null;
	}

	/**
	 * Converts &lt;number&gt; to a value.
	 */
	public static RealValue toReal(CssToken token) {
		if (token instanceof CssToken.Num num) {
			return RealValue.create(num.value());
		}
		return null;
	}

	/**
	 * Converts &lt;uri&gt; to a value.
	 */
	public static URIValue toURI(UserAgent ua, URI baseURI, CssToken token) throws URISyntaxException {
		if (token instanceof CssToken.Uri uri) {
			return createURIValue(ua.getDocumentContext().getEncoding(), baseURI, uri.uri());
		}
		return null;
	}

	/** Whether this is a {@code url()} or {@code image-set()} token (2026-08-29). */
	public static boolean isImage(CssToken token) {
		return token instanceof CssToken.Uri || token instanceof CssToken.Func func
				&& (func.is("image-set") || func.is("-webkit-image-set"));
	}

	/** Returns the token's URI string (the entire function for image-set()) for warning messages. */
	public static String uriText(CssToken token) {
		return token instanceof CssToken.Uri uri ? uri.uri() : String.valueOf(token);
	}

	/**
	 * Converts &lt;image&gt; ({@code url()} or {@code image-set()}) to an image URI
	 * (css-images-4 §4.1, 2026-08-29).
	 *
	 * <p>
	 * From {@code image-set(<image> <resolution>? type(<string>)?, ...)}
	 * (and prefixed {@code -webkit-image-set(url() 1x, url() 2x)}), selects the candidate
	 * closest to output resolution ({@code UAProps.OUTPUT_RESOLUTION}=
	 * {@link UserAgent#getPixelsPerInch()}; 1x=96 dpi, 2x=192 dpi): the highest resolution
	 * not exceeding output resolution, or if none, the lowest above it. Omitted resolution
	 * means 1x. Skips {@code image()} functions, gradients, and candidates whose
	 * {@code type()} specifies an unsupported MIME type (null if none remain, invalidating
	 * the declaration).
	 * </p>
	 */
	public static URIValue toImage(UserAgent ua, URI baseURI, CssToken token) throws URISyntaxException {
		if (token instanceof CssToken.Uri) {
			return toURI(ua, baseURI, token);
		}
		if (!isImage(token)) {
			return null;
		}
		final double target = ua.getPixelsPerInch();
		String bestBelow = null, bestAbove = null;
		double belowDpi = -1, aboveDpi = Double.MAX_VALUE;
		for (final TokenStream candidate : ((CssToken.Func) token).argStream().splitComma()) {
			final CssToken image = candidate.next();
			final String href;
			if (image instanceof CssToken.Uri uri) {
				href = uri.uri();
			} else if (image instanceof CssToken.Str str) {
				href = str.value();
			} else {
				continue; // image(), gradients, etc.
			}
			double dpi = 96;
			boolean supported = true;
			while (candidate.hasNext()) {
				final CssToken option = candidate.next();
				if (option instanceof CssToken.Dim dim) {
					final String unit = dim.unitText().toLowerCase(java.util.Locale.ROOT);
					switch (unit) {
					case "x", "dppx" -> dpi = dim.value() * 96;
					case "dpi" -> dpi = dim.value();
					case "dpcm" -> dpi = dim.value() * 2.54;
					default -> supported = false;
					}
				} else if (option instanceof CssToken.Func func && func.is("type")) {
					final String mime = func.argStream().string();
					supported &= mime != null && isSupportedImageType(mime);
				} else {
					supported = false;
				}
			}
			if (!supported || !(dpi > 0)) {
				continue;
			}
			if (dpi <= target + 1e-6) {
				if (dpi > belowDpi) {
					belowDpi = dpi;
					bestBelow = href;
				}
			} else if (dpi < aboveDpi) {
				aboveDpi = dpi;
				bestAbove = href;
			}
		}
		final String chosen = bestBelow != null ? bestBelow : bestAbove;
		if (chosen == null) {
			return null;
		}
		return createURIValue(ua.getDocumentContext().getEncoding(), baseURI, chosen);
	}

	/** Whether the {@code type()} MIME type is a renderable image format (skip unknown-format candidates). */
	private static boolean isSupportedImageType(final String mime) {
		switch (mime.trim().toLowerCase(java.util.Locale.ROOT)) {
		case "image/png", "image/jpeg", "image/jpg", "image/gif", "image/bmp", "image/svg+xml", "image/webp",
				"image/tiff":
			return true;
		default:
			return false;
		}
	}

	/**
	 * Resolves a reference string against the base URI to create a URI value.
	 */
	public static URIValue createURIValue(String encoding, URI baseURI, String href) throws URISyntaxException {
		URI uri;
		try {
			uri = URIHelper.resolve(encoding, baseURI, href);
		} catch (URISyntaxException e) {
			// **URIHelper.resolve() sanitizes invalid characters only when baseURI uses
			// http/https** (discovered on the actual yahoo.co.jp site, 2026-08-06).
			// Embedding icon SVGs in `url("data:image/svg+xml;charset=utf-8,
			// %3Csvg width='80' height='80'...")` with literal spaces is common
			// in browser CSS. Browsers always allow it, but here a file:// baseURI
			// (local HTML conversion or inline &lt;style&gt;) caused a URI syntax exception,
			// making the entire background image disappear.
			// To match browser tolerance regardless of the base URI scheme,
			// percent-encode only the minimum invalid characters on an exception
			// and retry once.
			String sanitized = sanitizeForURI(href);
			if (sanitized.equals(href)) {
				throw e;
			}
			uri = URIHelper.resolve(encoding, baseURI, sanitized);
		}
		return URIValue.create(uri);
	}

	/**
	 * Percent-encodes invalid (unescaped) URI characters.
	 *
	 * <p>
	 * Leaves valid RFC3986 sub-delims such as {@code '} unchanged: exceptions are actually
	 * caused by characters such as spaces that always need escaping. Passes existing
	 * {@code %XX} escapes through unchanged to avoid double encoding.
	 * </p>
	 */
	private static String sanitizeForURI(String href) {
		StringBuilder sb = null;
		for (int i = 0; i < href.length(); ++i) {
			char c = href.charAt(i);
			boolean illegal;
			switch (c) {
			case ' ':
			case '"':
			case '<':
			case '>':
			case '`':
			case '{':
			case '}':
			case '|':
			case '\\':
			case '^':
				illegal = true;
				break;
			default:
				illegal = c <= 0x20 || c == 0x7f;
			}
			if (illegal) {
				if (sb == null) {
					sb = new StringBuilder(href.length() + 16);
					sb.append(href, 0, i);
				}
				sb.append('%');
				sb.append(Character.forDigit((c >> 4) & 0xf, 16));
				sb.append(Character.forDigit(c & 0xf, 16));
			} else if (sb != null) {
				sb.append(c);
			}
		}
		return sb == null ? href : sb.toString();
	}
}
