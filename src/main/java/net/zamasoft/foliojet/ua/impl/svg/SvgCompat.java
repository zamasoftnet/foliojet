package net.zamasoft.foliojet.ua.impl.svg;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Rewrites, before the graphics tree is built, what SVG 2 and CSS Color 3/4 allow and Batik 1.19 (SVG 1.1) refuses
 * (2026-11-26, 9,903 local SVG files drawn by COPPER 4 and Chrome; copperpdf4 docs/history/2026-11-25-realweb-test.md
 * section 8):
 * <ul>
 * <li>{@code href} without {@code xlink:} becomes {@code xlink:href}: Batik reads only the latter and failed the whole
 * SVG at a {@code <use href>}, {@code <image href>} or a gradient's {@code href}. As in SVG 2, {@code href} wins when
 * both are written (an empty one is left alone: Batik would fail the whole SVG on it).</li>
 * <li>A colour Batik does not read in {@code fill}, {@code stroke}, {@code stop-color}, {@code flood-color},
 * {@code lighting-color} and {@code color} ({@code transparent}, {@code rgba()}, {@code hsl()}, {@code hsla()},
 * {@code rgb(r g b / a)}, {@code #RRGGBBAA}, {@code #RGBA}, {@code color()} of an RGB space taken as sRGB, and
 * {@code light-dark()}'s light colour, as on paper) becomes {@code rgb(r,g,b)} and its alpha goes to the
 * matching {@code *-opacity}, multiplied with the opacity the element has or inherits; {@code transparent} paint
 * becomes {@code none}. Batik painted such a value black (Graphviz draws its background polygon with
 * {@code stroke="transparent"}: a black frame round every graph), and one such value in a {@code <style>} made it
 * drop the whole sheet. Attributes, {@code style} attributes and {@code <style>} elements are rewritten; a value
 * Batik already reads is left as written.</li>
 * </ul>
 * Approximations: the opacity is inherited where the colour is, so a descendant that sets its own colour by attribute
 * or {@code style} gets the opacity it had before back (one set by a style sheet rule does not); {@code color} has no
 * opacity property, so its alpha is dropped ({@code color: transparent} too, to keep the sheet). Running it twice
 * changes nothing more (a document can be built again, running elements).
 */
public final class SvgCompat {
	private static final String XLINK_NS = "http://www.w3.org/1999/xlink";

	private static final String[] COLOR_PROPERTIES = { "fill", "stroke", "stop-color", "flood-color",
			"lighting-color", "color" };

	/** The inherited opacities fill and stroke would have without this rewrite, and with it. */
	private record Inherited(double fill, double effectiveFill, double stroke, double effectiveStroke) {
		static final Inherited NONE = new Inherited(1, 1, 1, 1);
	}

	private SvgCompat() {
	}

	public static void apply(final Document doc) {
		final Element root = doc.getDocumentElement();
		if (root == null) {
			return;
		}
		// iterative: deep SVG (generated maps) must not exhaust the stack
		final ArrayDeque<Element> elements = new ArrayDeque<Element>();
		final ArrayDeque<Inherited> inherited = new ArrayDeque<Inherited>();
		elements.push(root);
		inherited.push(Inherited.NONE);
		while (!elements.isEmpty()) {
			final Element e = elements.pop();
			final Inherited own = fixElement(e, inherited.pop());
			for (Node child = e.getLastChild(); child != null; child = child.getPreviousSibling()) {
				if (child instanceof Element c) {
					elements.push(c);
					inherited.push(own);
				}
			}
		}
	}

	/** Rewrites one element; returns what its children inherit. */
	private static Inherited fixElement(final Element e, final Inherited from) {
		final Attr href = plainAttr(e, "href");
		if (href != null && !href.getValue().isEmpty() && !href.getValue().equals(e.getAttributeNS(XLINK_NS, "href"))) {
			e.setAttributeNS(XLINK_NS, "xlink:href", href.getValue());
		}
		final Attr style = plainAttr(e, "style");
		final Map<String, String> styleDecls = style == null ? Map.of() : declarations(style.getValue());
		// the opacities as written (an attribute, or the style attribute that overrides it), before any rewrite
		final double fillWritten = opacity(e, styleDecls, "fill-opacity"),
				strokeWritten = opacity(e, styleDecls, "stroke-opacity");
		final double fill = Double.isNaN(fillWritten) ? from.fill() : fillWritten;
		final double stroke = Double.isNaN(strokeWritten) ? from.stroke() : strokeWritten;
		double effectiveFill = Double.isNaN(fillWritten) ? from.effectiveFill() : fillWritten;
		double effectiveStroke = Double.isNaN(strokeWritten) ? from.effectiveStroke() : strokeWritten;
		// A colour set here with no opacity of its own: the opacity inherited is what it had before, whatever an
		// ancestor's alpha made of it
		final boolean ownFill = plainAttr(e, "fill") != null || styleDecls.containsKey("fill");
		final boolean ownStroke = plainAttr(e, "stroke") != null || styleDecls.containsKey("stroke");
		if (ownFill && Double.isNaN(fillWritten) && effectiveFill != fill) {
			e.setAttributeNS(null, "fill-opacity", number(fill));
			effectiveFill = fill;
		}
		if (ownStroke && Double.isNaN(strokeWritten) && effectiveStroke != stroke) {
			e.setAttributeNS(null, "stroke-opacity", number(stroke));
			effectiveStroke = stroke;
		}
		for (final String property : COLOR_PROPERTIES) {
			final Attr attr = plainAttr(e, property);
			if (attr == null) {
				continue;
			}
			final Color color = convert(property, attr.getValue());
			if (color == null) {
				continue;
			}
			if (color.value() == null) {
				e.removeAttributeNode(attr);
				continue;
			}
			attr.setValue(color.value());
			final String opacity = opacityProperty(property);
			if (color.alpha() < 1 && opacity != null) {
				final double base = switch (property) {
				case "fill" -> fill;
				case "stroke" -> stroke;
				default -> {
					final Attr written = plainAttr(e, opacity);
					yield written == null ? 1 : parseOpacity(written.getValue(), 1);
				}
				};
				final double value = base * color.alpha();
				e.setAttributeNS(null, opacity, number(value));
				if (property.equals("fill")) {
					effectiveFill = value;
				} else if (property.equals("stroke")) {
					effectiveStroke = value;
				}
			}
		}
		if (style != null) {
			// the alpha multiplies the opacity the element has or inherits, as for an attribute
			final String rewritten = rewriteDeclarations(style.getValue(), fill, stroke);
			if (!rewritten.equals(style.getValue())) {
				style.setValue(rewritten);
				// an alpha in the style attribute sets the opacity there, over the attribute
				final Map<String, String> after = declarations(rewritten);
				effectiveFill = parseOpacity(after.get("fill-opacity"), effectiveFill);
				effectiveStroke = parseOpacity(after.get("stroke-opacity"), effectiveStroke);
			}
		}
		if ("style".equals(localName(e))) {
			final String sheet = e.getTextContent();
			if (sheet != null) {
				final String rewritten = rewriteSheet(sheet);
				if (!rewritten.equals(sheet)) {
					while (e.getFirstChild() != null) {
						e.removeChild(e.getFirstChild());
					}
					e.appendChild(e.getOwnerDocument().createCDATASection(rewritten));
				}
			}
		}
		return new Inherited(fill, effectiveFill, stroke, effectiveStroke);
	}

	/** An opacity written on the element (the style attribute over the attribute), NaN if none. */
	private static double opacity(final Element e, final Map<String, String> style, final String property) {
		final String inStyle = style.get(property);
		if (inStyle != null) {
			return parseOpacity(inStyle, Double.NaN);
		}
		final Attr a = plainAttr(e, property);
		return a == null ? Double.NaN : parseOpacity(a.getValue(), Double.NaN);
	}

	private static double parseOpacity(final String value, final double otherwise) {
		if (value == null) {
			return otherwise;
		}
		String v = value.trim();
		final boolean percent = v.endsWith("%");
		if (percent) {
			v = v.substring(0, v.length() - 1);
		}
		try {
			final double d = Double.parseDouble(v) / (percent ? 100 : 1);
			return Double.isNaN(d) ? otherwise : Math.max(0, Math.min(1, d));
		} catch (final NumberFormatException ex) {
			return otherwise;
		}
	}

	/**
	 * An attribute in no namespace, made with or without namespace support (the HTML side). Looked up by name: Batik's
	 * attribute list walks from its start for every index.
	 */
	private static Attr plainAttr(final Element e, final String name) {
		Attr a = e.getAttributeNodeNS(null, name);
		if (a == null) {
			a = e.getAttributeNode(name);
			if (a != null && a.getNamespaceURI() != null) {
				a = null;
			}
		}
		return a;
	}

	private static String localName(final Node n) {
		return n.getLocalName() != null ? n.getLocalName() : n.getNodeName();
	}

	private static String opacityProperty(final String property) {
		return switch (property) {
		case "fill" -> "fill-opacity";
		case "stroke" -> "stroke-opacity";
		case "stop-color" -> "stop-opacity";
		case "flood-color" -> "flood-opacity";
		default -> null;
		};
	}

	/**
	 * A colour as Batik reads it (null: drop the declaration) and the alpha that goes to the opacity property.
	 */
	record Color(String value, double alpha) {
	}

	private static final Pattern FUNCTION = Pattern.compile("(rgba?|hsla?)\\s*\\((.*)\\)",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	/** The colour spaces of color() whose components are red, green and blue. */
	private static final java.util.Set<String> RGB_SPACES = java.util.Set.of("srgb", "srgb-linear", "display-p3",
			"a98-rgb", "prophoto-rgb", "rec2020");

	private static final Pattern WRAPPED = Pattern.compile("(light-dark|color)\\s*\\((.*)\\)",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	private static final Pattern HEX = Pattern.compile("#([0-9a-fA-F]{4}|[0-9a-fA-F]{8})");

	/** The first comma outside parentheses, or -1. */
	private static int topLevelComma(final String args) {
		int depth = 0;
		for (int i = 0; i < args.length(); ++i) {
			final char c = args.charAt(i);
			if (c == '(') {
				++depth;
			} else if (c == ')') {
				--depth;
			} else if (c == ',' && depth == 0) {
				return i;
			}
		}
		return -1;
	}
	private static final Pattern NUMBER = Pattern
			.compile("([+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)(%|deg|rad|grad|turn)?", Pattern.CASE_INSENSITIVE);

	/**
	 * The colour Batik reads for a value it refuses, or null when the value is to stay as written (Batik reads it, or
	 * it is not a colour this knows).
	 */
	static Color convert(final String property, final String value) {
		final String v = value.trim();
		final Matcher wrapped = WRAPPED.matcher(v);
		if (wrapped.matches()) {
			final String name = wrapped.group(1).toLowerCase(Locale.ROOT);
			final String args = wrapped.group(2);
			if (name.equals("light-dark")) {
				// light-dark(light, dark) (CSS Color 5; draw.io writes it in every style attribute): the light one, as on
				// paper. Batik dropped the whole style attribute, and the shapes were black
				final int comma = topLevelComma(args);
				if (comma < 0) {
					return null;
				}
				final String light = args.substring(0, comma).trim();
				final Color c = convert(property, light);
				if (c != null) {
					return c;
				}
				return light.regionMatches(true, 0, "var(", 0, 4) ? null : new Color(light, 1);
			}
			// color(srgb | display-p3 | ... r g b [/ a]): the components as sRGB (bulma's logo writes display-p3 after
			// a fallback, and Batik dropped the whole style attribute)
			final String[] parts = args.trim().split("\\s*/\\s*|\\s+");
			if (parts.length < 4 || parts.length > 5 || !RGB_SPACES.contains(parts[0].toLowerCase(Locale.ROOT))) {
				return null;
			}
			final double[] n = new double[parts.length - 1];
			for (int i = 1; i < parts.length; ++i) {
				final Matcher p = NUMBER.matcher(parts[i]);
				if (!p.matches() || p.group(2) != null && !p.group(2).equals("%")) {
					return null;
				}
				n[i - 1] = Double.parseDouble(p.group(1)) / (p.group(2) != null ? 100 : 1);
			}
			return new Color(rgb(n[0] * 255, n[1] * 255, n[2] * 255), n.length == 4 ? Math.max(0, Math.min(1, n[3])) : 1);
		}
		if (v.equalsIgnoreCase("transparent")) {
			return switch (property) {
			case "fill", "stroke" -> new Color("none", 1);
			case "color" -> new Color(null, 1);
			default -> new Color("black", 0);
			};
		}
		Matcher m = HEX.matcher(v);
		if (m.matches()) {
			final String h = m.group(1);
			final boolean shortForm = h.length() == 4;
			final int[] c = new int[4];
			for (int i = 0; i < 4; ++i) {
				c[i] = shortForm ? Integer.parseInt(h.substring(i, i + 1), 16) * 17
						: Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
			}
			return new Color(rgb(c[0], c[1], c[2]), c[3] / 255d);
		}
		m = FUNCTION.matcher(v);
		if (!m.matches()) {
			return null;
		}
		final String name = m.group(1).toLowerCase(Locale.ROOT);
		final String args = m.group(2).trim();
		final boolean commas = args.indexOf(',') >= 0;
		final String[] parts = args.split("\\s*[,/]\\s*|\\s+");
		if (parts.length < 3 || parts.length > 4) {
			return null;
		}
		final double[] n = new double[parts.length];
		final String[] units = new String[parts.length];
		for (int i = 0; i < parts.length; ++i) {
			final Matcher p = NUMBER.matcher(parts[i]);
			if (!p.matches()) {
				return null; // var(), none, relative colours: not this
			}
			n[i] = Double.parseDouble(p.group(1));
			units[i] = p.group(2) == null ? "" : p.group(2).toLowerCase(Locale.ROOT);
		}
		double alpha = 1;
		if (parts.length == 4) {
			alpha = units[3].equals("%") ? n[3] / 100 : n[3];
			alpha = Math.max(0, Math.min(1, alpha));
		}
		if (name.startsWith("rgb")) {
			if (name.equals("rgb") && parts.length == 3 && commas) {
				return null; // rgb(r,g,b): Batik reads it
			}
			final double[] c = new double[3];
			for (int i = 0; i < 3; ++i) {
				c[i] = units[i].equals("%") ? n[i] * 255 / 100 : n[i];
			}
			return new Color(rgb(c[0], c[1], c[2]), alpha);
		}
		double hue = switch (units[0]) {
		case "rad" -> Math.toDegrees(n[0]);
		case "grad" -> n[0] * 0.9;
		case "turn" -> n[0] * 360;
		default -> n[0];
		};
		hue = ((hue % 360) + 360) % 360;
		final double s = Math.max(0, Math.min(1, n[1] / 100)), l = Math.max(0, Math.min(1, n[2] / 100));
		// CSS Color 4, 7.1 (hsl to rgb)
		final double a = s * Math.min(l, 1 - l);
		final double[] c = new double[3];
		final int[] k0 = { 0, 8, 4 };
		for (int i = 0; i < 3; ++i) {
			final double k = (k0[i] + hue / 30) % 12;
			c[i] = 255 * (l - a * Math.max(-1, Math.min(Math.min(k - 3, 9 - k), 1)));
		}
		return new Color(rgb(c[0], c[1], c[2]), alpha);
	}

	private static String rgb(final double r, final double g, final double b) {
		return "rgb(" + octet(r) + "," + octet(g) + "," + octet(b) + ")";
	}

	private static long octet(final double v) {
		return Math.round(Math.max(0, Math.min(255, v)));
	}

	private static String number(final double v) {
		return new BigDecimal(v).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
	}

	/**
	 * The declaration blocks of a style sheet rewritten: the innermost blocks (rules inside {@code @media} too), found
	 * outside strings and comments.
	 */
	static String rewriteSheet(final String sheet) {
		StringBuilder out = null;
		int copied = 0;
		final ArrayDeque<int[]> open = new ArrayDeque<int[]>(); // {start of the block's text, has a nested block}
		final int len = sheet.length();
		for (int i = 0; i < len; ++i) {
			final char c = sheet.charAt(i);
			if (c == '/' && i + 1 < len && sheet.charAt(i + 1) == '*') {
				final int end = sheet.indexOf("*/", i + 2);
				i = end < 0 ? len : end + 1;
			} else if (c == '"' || c == '\'') {
				i = stringEnd(sheet, i);
			} else if (c == '{') {
				if (!open.isEmpty()) {
					open.peek()[1] = 1;
				}
				open.push(new int[] { i + 1, 0 });
			} else if (c == '}' && !open.isEmpty()) {
				final int[] block = open.pop();
				if (block[1] == 0) {
					final String text = sheet.substring(block[0], i);
					final String rewritten = rewriteDeclarations(text);
					if (!rewritten.equals(text)) {
						if (out == null) {
							out = new StringBuilder(len + 32);
						}
						out.append(sheet, copied, block[0]).append(rewritten);
						copied = i;
					}
				}
			}
		}
		if (out == null) {
			return sheet;
		}
		return out.append(sheet, copied, len).toString();
	}

	/** The index of the quote closing the string that starts at {@code start} (the last index if none). */
	private static int stringEnd(final String text, final int start) {
		final char quote = text.charAt(start);
		for (int i = start + 1; i < text.length(); ++i) {
			final char c = text.charAt(i);
			if (c == '\\') {
				++i;
			} else if (c == quote || c == '\n') {
				return i;
			}
		}
		return text.length() - 1;
	}

	private static final Pattern IMPORTANT = Pattern.compile("(.*?)\\s*!\\s*important\\s*",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

	/** The declarations of a block as written (comments taken out), the later one of a property winning. */
	private static Map<String, String> declarations(final String text) {
		final Map<String, String> out = new HashMap<String, String>();
		for (final String decl : split(text)) {
			final String d = COMMENT.matcher(decl).replaceAll(" ");
			final int colon = d.indexOf(':');
			if (colon > 0) {
				out.put(d.substring(0, colon).trim().toLowerCase(Locale.ROOT), d.substring(colon + 1).trim());
			}
		}
		return out;
	}

	/**
	 * Declarations ({@code a:b;c:d}) with the colours Batik refuses rewritten; the text as written otherwise. An alpha
	 * goes to the opacity property after the colour, multiplied with the opacity the block itself sets (which then
	 * gets the product too, wherever it is).
	 */
	static String rewriteDeclarations(final String text) {
		return rewriteDeclarations(text, 1, 1);
	}

	/** As {@link #rewriteDeclarations(String)}, with the fill and stroke opacities the declarations multiply when they set none. */
	private static String rewriteDeclarations(final String text, final double fillBase, final double strokeBase) {
		final List<String> decls = split(text);
		final Map<String, String> written = declarations(text);
		final Map<String, Double> products = new HashMap<String, Double>();
		final String[] rewritten = new String[decls.size()];
		boolean changed = false;
		for (int i = 0; i < decls.size(); ++i) {
			final String d = COMMENT.matcher(decls.get(i)).replaceAll(" ");
			final int colon = d.indexOf(':');
			if (colon < 0) {
				continue;
			}
			final String property = d.substring(0, colon).trim().toLowerCase(Locale.ROOT);
			if (opacityProperty(property) == null && !property.equals("lighting-color") && !property.equals("color")) {
				continue;
			}
			String value = d.substring(colon + 1);
			final Matcher important = IMPORTANT.matcher(value);
			final boolean isImportant = important.matches();
			if (isImportant) {
				value = important.group(1);
			}
			final Color color = convert(property, value);
			if (color == null) {
				continue;
			}
			changed = true;
			if (color.value() == null) {
				rewritten[i] = "";
				continue;
			}
			final String suffix = isImportant ? " !important" : "";
			final StringBuilder out = new StringBuilder().append(property).append(':').append(color.value())
					.append(suffix);
			final String opacity = opacityProperty(property);
			if (color.alpha() < 1 && opacity != null) {
				final double base = opacity.equals("fill-opacity") ? fillBase
						: opacity.equals("stroke-opacity") ? strokeBase : 1;
				final double product = parseOpacity(written.get(opacity), base) * color.alpha();
				products.put(opacity, product);
				out.append(';').append(opacity).append(':').append(number(product)).append(suffix);
			}
			rewritten[i] = out.toString();
		}
		if (!changed) {
			return text;
		}
		final StringBuilder out = new StringBuilder(text.length() + 32);
		for (int i = 0; i < decls.size(); ++i) {
			String decl = rewritten[i] != null ? rewritten[i] : decls.get(i);
			if (rewritten[i] == null && !products.isEmpty()) {
				final String d = COMMENT.matcher(decl).replaceAll(" ");
				final int colon = d.indexOf(':');
				final Double product = colon < 0 ? null
						: products.get(d.substring(0, colon).trim().toLowerCase(Locale.ROOT));
				if (product != null) {
					final Matcher important = IMPORTANT.matcher(d.substring(colon + 1));
					decl = d.substring(0, colon).trim() + ':' + number(product)
							+ (important.matches() ? " !important" : "");
				}
			}
			if (i > 0) {
				out.append(';');
			}
			out.append(decl);
		}
		return out.toString();
	}

	/** The declarations of a block, split at the semicolons outside parentheses, strings and comments. */
	private static List<String> split(final String text) {
		final List<String> out = new ArrayList<String>();
		int depth = 0, start = 0;
		final int len = text.length();
		for (int i = 0; i < len; ++i) {
			final char c = text.charAt(i);
			if (c == '/' && i + 1 < len && text.charAt(i + 1) == '*') {
				final int end = text.indexOf("*/", i + 2);
				i = end < 0 ? len : end + 1;
			} else if (c == '"' || c == '\'') {
				i = stringEnd(text, i);
			} else if (c == '(') {
				++depth;
			} else if (c == ')') {
				depth = Math.max(0, depth - 1);
			} else if (c == ';' && depth == 0) {
				out.add(text.substring(start, i));
				start = i + 1;
			}
		}
		out.add(text.substring(Math.min(start, len)));
		return out;
	}
}
