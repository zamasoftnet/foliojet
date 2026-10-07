package net.zamasoft.foliojet.css.util;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.ClipPathShape;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Parses css-shapes-1 {@code <basic-shape>} ({@code inset()}, {@code circle()},
 * {@code ellipse()}, {@code polygon()}), resolves absolute values, and creates layout
 * shapes (added 2026-08-29).
 *
 * <p>
 * Moved here from code private to {@code clip-path} ({@code css.impl.property.box.ClipPath})
 * to share with {@code shape-outside}. Both properties accept the same {@code <basic-shape>}
 * grammar; separate parsers could diverge in the future (css-shapes-1 §3.1 is identical
 * for both). {@code ClipPath} keeps only the default reference box (border-box) and
 * value type, delegating all shape details here.
 * </p>
 */
public final class BasicShapes {
	private BasicShapes() {
	}

	/**
	 * Parsed shape specification. Retains lengths as {@link LengthValue}; resolves em, etc.
	 * to absolute lengths at the computed-value stage ({@link #absolutize}).
	 */
	public sealed interface ShapeSpec {
		record Inset(QuantityValue top, QuantityValue right, QuantityValue bottom, QuantityValue left,
				QuantityValue[] radii) implements ShapeSpec {
		}

		record Circle(QuantityValue radius, boolean farthestSide, QuantityValue cx, QuantityValue cy)
				implements ShapeSpec {
		}

		record Ellipse(QuantityValue rx, boolean rxFarthest, QuantityValue ry, boolean ryFarthest, QuantityValue cx,
				QuantityValue cy) implements ShapeSpec {
		}

		record Polygon(boolean evenOdd, List<QuantityValue> points) implements ShapeSpec {
		}

		/**
		 * {@code rect(<top> <right> <bottom> <left>)} (css-shapes-1, 2026-08-30).
		 *
		 * <p>
		 * Unlike {@code inset()}, <b>all four values are coordinates with the reference box's
		 * top-left corner as origin</b> (right/bottom are distances from the origin, not insets
		 * from the right/bottom edges). {@code auto} aligns that side with the reference box's
		 * side. {@link #toShape} folds this into the equivalent {@code inset()}.
		 */
		record Rect(QuantityValue top, QuantityValue right, QuantityValue bottom, QuantityValue left,
				QuantityValue[] radii) implements ShapeSpec {
		}

		/**
		 * {@code xywh(<x> <y> <width> <height>)} (css-shapes-1, 2026-08-30).
		 * Position and size relative to the top-left origin. {@link #toShape} folds this
		 * into the equivalent {@code inset()}.
		 */
		record Xywh(QuantityValue x, QuantityValue y, QuantityValue width, QuantityValue height,
				QuantityValue[] radii) implements ShapeSpec {
		}

		/**
		 * {@code path([fill-rule,] "svg path data")} (2026-08-29). Coordinates are in px,
		 * so attach a pt conversion factor at parse time (no length values, so no processing
		 * is needed at the computed-value stage).
		 */
		record Path(boolean evenOdd, java.awt.geom.Path2D.Double path, double pxToPt) implements ShapeSpec {
		}
	}

	/** Converts a {@code <shape-box>} keyword to a reference box (null if none matches). */
	public static ClipPathShape.ReferenceBox toReferenceBox(final CssToken.Ident ident) {
		return switch (ident.lower()) {
		case "border-box" -> ClipPathShape.ReferenceBox.BORDER_BOX;
		case "padding-box" -> ClipPathShape.ReferenceBox.PADDING_BOX;
		case "content-box" -> ClipPathShape.ReferenceBox.CONTENT_BOX;
		case "margin-box" -> ClipPathShape.ReferenceBox.MARGIN_BOX;
		default -> null;
		};
	}

	/**
	 * Parses a {@code <basic-shape>} function. Unsupported function names throw
	 * {@link PropertyException} (the caller ignores the entire declaration).
	 */
	public static ShapeSpec parseFunction(final CssToken.Func func, final UserAgent ua) throws PropertyException {
		final TokenStream args = func.argStream();
		return switch (func.name().toLowerCase()) {
		case "inset" -> parseInset(args, ua);
		case "rect" -> parseRect(args, ua);
		case "xywh" -> parseXywh(args, ua);
		case "circle" -> parseCircle(args, ua);
		case "ellipse" -> parseEllipse(args, ua);
		case "polygon" -> parsePolygon(args, ua);
		case "path" -> parsePath(args, ua);
		default -> throw new PropertyException();
		};
	}

	private static ShapeSpec parsePath(final TokenStream args, final UserAgent ua) throws PropertyException {
		boolean evenOdd = false;
		CssToken t = args.hasNext() ? args.next() : null;
		if (t instanceof CssToken.Ident ident) {
			if (ident.is("evenodd")) {
				evenOdd = true;
			} else if (!ident.is("nonzero")) {
				throw new PropertyException();
			}
			if (!args.eatComma()) {
				throw new PropertyException();
			}
			t = args.hasNext() ? args.next() : null;
		}
		if (!(t instanceof CssToken.Str str) || args.hasNext()) {
			throw new PropertyException();
		}
		final java.awt.geom.Path2D.Double path;
		try {
			path = SvgPathData.parse(str.value());
		} catch (final IllegalArgumentException e) {
			throw new PropertyException();
		}
		// Convert px to pt with the UA resolution (normally 96 dpi→0.75). Without a UA (unit tests), use the default ratio.
		final double pxToPt = ua == null ? 0.75
				: LengthUtils.convert(ua, 1, net.zamasoft.foliojet.css.token.Unit.PX,
						net.zamasoft.foliojet.css.token.Unit.PT);
		return new ShapeSpec.Path(evenOdd, path, pxToPt);
	}

	/** Converts to a computed value: resolves font-relative lengths such as em to absolute values (leaves % unchanged). */
	public static ShapeSpec absolutize(final ShapeSpec shape, final CSSStyle style) {
		if (shape == null) {
			return null;
		}
		return switch (shape) {
		case ShapeSpec.Inset i -> new ShapeSpec.Inset(abs(i.top(), style), abs(i.right(), style),
				abs(i.bottom(), style), abs(i.left(), style), absAll(i.radii(), style));
		case ShapeSpec.Circle c -> new ShapeSpec.Circle(abs(c.radius(), style), c.farthestSide(), abs(c.cx(), style),
				abs(c.cy(), style));
		case ShapeSpec.Ellipse e -> new ShapeSpec.Ellipse(abs(e.rx(), style), e.rxFarthest(), abs(e.ry(), style),
				e.ryFarthest(), abs(e.cx(), style), abs(e.cy(), style));
		case ShapeSpec.Polygon p -> {
			final List<QuantityValue> pts = new ArrayList<>(p.points().size());
			for (final QuantityValue q : p.points()) {
				pts.add(abs(q, style));
			}
			yield new ShapeSpec.Polygon(p.evenOdd(), pts);
		}
		case ShapeSpec.Rect r -> new ShapeSpec.Rect(abs(r.top(), style), abs(r.right(), style),
				abs(r.bottom(), style), abs(r.left(), style), absAll(r.radii(), style));
		case ShapeSpec.Xywh x -> new ShapeSpec.Xywh(abs(x.x(), style), abs(x.y(), style), abs(x.width(), style),
				abs(x.height(), style), absAll(x.radii(), style));
		case ShapeSpec.Path p -> p;
		};
	}

	/** Creates a layout shape from a computed value (shape==null means reference box only). */
	public static ClipPathShape toShape(final ShapeSpec shape, final ClipPathShape.ReferenceBox box) {
		if (shape == null) {
			return new ClipPathShape.BoxOnly(box);
		}
		return switch (shape) {
		case ShapeSpec.Inset i -> new ClipPathShape.Inset(box, len(i.top()), len(i.right()), len(i.bottom()),
				len(i.left()), lens(i.radii()));
		case ShapeSpec.Circle c -> new ClipPathShape.Circle(box, c.radius() == null ? null : len(c.radius()),
				c.farthestSide(), len(c.cx()), len(c.cy()));
		case ShapeSpec.Ellipse e -> new ClipPathShape.Ellipse(box, e.rx() == null ? null : len(e.rx()),
				e.rxFarthest(), e.ry() == null ? null : len(e.ry()), e.ryFarthest(), len(e.cx()), len(e.cy()));
		case ShapeSpec.Polygon pg -> {
			final Length[] pts = new Length[pg.points().size()];
			for (int i = 0; i < pts.length; ++i) {
				pts[i] = len(pg.points().get(i));
			}
			yield new ClipPathShape.Polygon(box, pg.evenOdd(), pts);
		}
		case ShapeSpec.Rect r -> new ClipPathShape.Inset(box, edge(r.top(), false), edge(r.right(), true),
				edge(r.bottom(), true), edge(r.left(), false), lens(r.radii()));
		case ShapeSpec.Xywh x -> new ClipPathShape.Inset(box, edge(x.y(), false),
				fullMinus(sum(x.x(), x.width())), fullMinus(sum(x.y(), x.height())), edge(x.x(), false),
				lens(x.radii()));
		case ShapeSpec.Path p -> new ClipPathShape.Path(box, p.evenOdd(), p.path(), p.pxToPt());
		};
	}

	/**
	 * Converts one {@code rect()}/{@code xywh()} coordinate to an {@code inset()} amount.
	 * If {@code fromFar} is true, converts a distance from the origin to an inset from the
	 * opposite edge ({@code 100% - value}). {@code auto} (null) means zero inset.
	 */
	private static Length edge(final QuantityValue q, final boolean fromFar) {
		if (q == null) {
			return Length.ZERO_LENGTH;
		}
		return fromFar ? fullMinus(len(q)) : len(q);
	}

	/** Returns {@code 100% - length}. */
	private static Length fullMinus(final Length l) {
		return Length.createMixed(-absoluteOf(l), 1 - ratioOf(l));
	}

	/** Returns the sum of two {@code <length-percentage>} values as a length (null means 0). */
	private static Length sum(final QuantityValue a, final QuantityValue b) {
		final Length la = a == null ? Length.ZERO_LENGTH : len(a);
		final Length lb = b == null ? Length.ZERO_LENGTH : len(b);
		return Length.createMixed(absoluteOf(la) + absoluteOf(lb), ratioOf(la) + ratioOf(lb));
	}

	private static double absoluteOf(final Length l) {
		return l.getType() == LengthType.RELATIVE ? 0 : l.getLength();
	}

	private static double ratioOf(final Length l) {
		return switch (l.getType()) {
		case RELATIVE -> l.getLength();
		case MIXED -> l.getRatio();
		default -> 0;
		};
	}

	private static Length len(final QuantityValue q) {
		return BoxValueUtils.toLength(q);
	}

	private static Length[] lens(final QuantityValue[] qs) {
		if (qs == null) {
			return null;
		}
		final Length[] out = new Length[qs.length];
		for (int i = 0; i < qs.length; ++i) {
			out[i] = len(qs[i]);
		}
		return out;
	}

	private static QuantityValue abs(final QuantityValue q, final CSSStyle style) {
		if (q instanceof LengthValue length && !(q instanceof AbsoluteLengthValue)) {
			return length.toAbsoluteLength(style);
		}
		return q;
	}

	private static QuantityValue[] absAll(final QuantityValue[] qs, final CSSStyle style) {
		if (qs == null) {
			return null;
		}
		final QuantityValue[] out = new QuantityValue[qs.length];
		for (int i = 0; i < qs.length; ++i) {
			out[i] = abs(qs[i], style);
		}
		return out;
	}

	/** Reads a {@code <length-percentage>} (throws otherwise). */
	public static QuantityValue lengthOrPercentage(final UserAgent ua, final CssToken token)
			throws PropertyException {
		final Value pct = ValueUtils.toPercentage(token);
		if (pct instanceof QuantityValue q) {
			return q;
		}
		final Value v = ValueUtils.toLength(ua, token);
		if (v instanceof QuantityValue q) {
			return q;
		}
		throw new PropertyException();
	}

	private static ShapeSpec parseInset(final TokenStream args, final UserAgent ua) throws PropertyException {
		final List<QuantityValue> edges = new ArrayList<>(4);
		QuantityValue[] radii = null;
		while (args.hasNext()) {
			final CssToken t = args.next();
			if (t instanceof CssToken.Ident ident && ident.is("round")) {
				final List<QuantityValue> rs = new ArrayList<>(4);
				while (args.hasNext()) {
					rs.add(lengthOrPercentage(ua, args.next()));
				}
				if (rs.isEmpty() || rs.size() > 4) {
					throw new PropertyException();
				}
				// Expand 1–4 values using border-radius rules (TL, TR, BR, BL)
				radii = new QuantityValue[] { rs.get(0), rs.get(rs.size() > 1 ? 1 : 0),
						rs.get(rs.size() > 2 ? 2 : 0), rs.get(rs.size() > 3 ? 3 : rs.size() > 1 ? 1 : 0) };
				break;
			}
			edges.add(lengthOrPercentage(ua, t));
		}
		if (edges.isEmpty() || edges.size() > 4) {
			throw new PropertyException();
		}
		// Expand 1–4 values using margin rules (top, right, bottom, left)
		final QuantityValue top = edges.get(0);
		final QuantityValue right = edges.get(edges.size() > 1 ? 1 : 0);
		final QuantityValue bottom = edges.get(edges.size() > 2 ? 2 : 0);
		final QuantityValue left = edges.get(edges.size() > 3 ? 3 : edges.size() > 1 ? 1 : 0);
		return new ShapeSpec.Inset(top, right, bottom, left, radii);
	}

	/**
	 * Parses {@code rect(<top> <right> <bottom> <left> [round <radii>])}
	 * (css-shapes-1, 2026-08-30). Each value may be {@code auto}.
	 */
	private static ShapeSpec parseRect(final TokenStream args, final UserAgent ua) throws PropertyException {
		final Coordinates c = parseFourCoordinates(args, ua);
		return new ShapeSpec.Rect(c.values()[0], c.values()[1], c.values()[2], c.values()[3], c.radii());
	}

	/**
	 * Parses {@code xywh(<x> <y> <width> <height> [round <radii>])}
	 * (css-shapes-1, 2026-08-30). Unlike {@code rect()}, does not accept {@code auto};
	 * width and height cannot be negative.
	 */
	private static ShapeSpec parseXywh(final TokenStream args, final UserAgent ua) throws PropertyException {
		final Coordinates c = parseFourCoordinates(args, ua);
		final QuantityValue[] v = c.values();
		for (final QuantityValue q : v) {
			if (q == null) {
				// xywh() has no auto
				throw new PropertyException();
			}
		}
		if (v[2].isNegative() || v[3].isNegative()) {
			throw new PropertyException();
		}
		return new ShapeSpec.Xywh(v[0], v[1], v[2], v[3], c.radii());
	}

	/** Four {@code rect()}/{@code xywh()} values and optional {@code round <radii>}. */
	private record Coordinates(QuantityValue[] values, QuantityValue[] radii) {
	}

	/**
	 * Reads four {@code rect()}/{@code xywh()} values and optional {@code round <radii>}.
	 * Returns null for {@code auto}.
	 */
	private static Coordinates parseFourCoordinates(final TokenStream args, final UserAgent ua)
			throws PropertyException {
		final List<QuantityValue> values = new ArrayList<>(4);
		QuantityValue[] radii = null;
		while (args.hasNext()) {
			final CssToken t = args.next();
			if (t instanceof CssToken.Ident ident && ident.is("round")) {
				radii = parseRadii(args, ua);
				break;
			}
			if (t instanceof CssToken.Ident ident && ident.is("auto")) {
				values.add(null);
				continue;
			}
			values.add(lengthOrPercentage(ua, t));
		}
		if (values.size() != 4) {
			throw new PropertyException();
		}
		return new Coordinates(values.toArray(new QuantityValue[4]), radii);
	}

	/** Expands the 1–4 radii following {@code round} using border-radius rules. */
	private static QuantityValue[] parseRadii(final TokenStream args, final UserAgent ua) throws PropertyException {
		final List<QuantityValue> rs = new ArrayList<>(4);
		while (args.hasNext()) {
			rs.add(lengthOrPercentage(ua, args.next()));
		}
		if (rs.isEmpty() || rs.size() > 4) {
			throw new PropertyException();
		}
		return new QuantityValue[] { rs.get(0), rs.get(rs.size() > 1 ? 1 : 0), rs.get(rs.size() > 2 ? 2 : 0),
				rs.get(rs.size() > 3 ? 3 : rs.size() > 1 ? 1 : 0) };
	}

	/** One radius + at position. */
	private static ShapeSpec parseCircle(final TokenStream args, final UserAgent ua) throws PropertyException {
		QuantityValue radius = null;
		boolean farthest = false;
		boolean radiusSeen = false;
		QuantityValue[] at = null;
		while (args.hasNext()) {
			final CssToken t = args.next();
			if (t instanceof CssToken.Ident ident && ident.is("at")) {
				at = parsePosition(args, ua);
				break;
			}
			if (radiusSeen) {
				throw new PropertyException();
			}
			radiusSeen = true;
			if (t instanceof CssToken.Ident ident) {
				if (ident.is("closest-side")) {
					farthest = false;
				} else if (ident.is("farthest-side")) {
					farthest = true;
				} else {
					throw new PropertyException();
				}
			} else {
				radius = lengthOrPercentage(ua, t);
			}
		}
		final QuantityValue cx = at != null ? at[0] : PercentageValue.HALF;
		final QuantityValue cy = at != null ? at[1] : PercentageValue.HALF;
		return new ShapeSpec.Circle(radius, farthest, cx, cy);
	}

	private static ShapeSpec parseEllipse(final TokenStream args, final UserAgent ua) throws PropertyException {
		final List<QuantityValue> rs = new ArrayList<>(2);
		final boolean[] far = new boolean[2];
		int i = 0;
		QuantityValue[] at = null;
		while (args.hasNext()) {
			final CssToken t = args.next();
			if (t instanceof CssToken.Ident ident && ident.is("at")) {
				at = parsePosition(args, ua);
				break;
			}
			if (i >= 2) {
				throw new PropertyException();
			}
			if (t instanceof CssToken.Ident ident) {
				if (ident.is("closest-side")) {
					rs.add(null);
					far[i] = false;
				} else if (ident.is("farthest-side")) {
					rs.add(null);
					far[i] = true;
				} else {
					throw new PropertyException();
				}
			} else {
				rs.add(lengthOrPercentage(ua, t));
			}
			++i;
		}
		if (rs.size() == 1) {
			throw new PropertyException();
		}
		final QuantityValue rx = rs.isEmpty() ? null : rs.get(0);
		final QuantityValue ry = rs.isEmpty() ? null : rs.get(1);
		final QuantityValue cx = at != null ? at[0] : PercentageValue.HALF;
		final QuantityValue cy = at != null ? at[1] : PercentageValue.HALF;
		return new ShapeSpec.Ellipse(rx, rs.isEmpty() ? false : far[0], ry, rs.isEmpty() ? false : far[1], cx, cy);
	}

	/**
	 * Position after {@code at} (1–2 values). Accepts keywords (center/left/right/top/bottom),
	 * lengths, and percentages; returns [x, y].
	 */
	static QuantityValue[] parsePosition(final TokenStream args, final UserAgent ua)
			throws PropertyException {
		QuantityValue x = null, y = null;
		final List<CssToken> ts = new ArrayList<>(2);
		while (args.hasNext()) {
			ts.add(args.next());
		}
		if (ts.isEmpty() || ts.size() > 2) {
			throw new PropertyException();
		}
		for (int i = 0; i < ts.size(); ++i) {
			final CssToken t = ts.get(i);
			QuantityValue v;
			boolean isX = i == 0;
			if (t instanceof CssToken.Ident ident) {
				switch (ident.lower()) {
				case "center" -> v = PercentageValue.HALF;
				case "left" -> {
					v = PercentageValue.ZERO;
					isX = true;
				}
				case "right" -> {
					v = PercentageValue.FULL;
					isX = true;
				}
				case "top" -> {
					v = PercentageValue.ZERO;
					isX = false;
				}
				case "bottom" -> {
					v = PercentageValue.FULL;
					isX = false;
				}
				default -> throw new PropertyException();
				}
			} else {
				v = lengthOrPercentage(ua, t);
			}
			if (isX && x == null) {
				x = v;
			} else if (!isX && y == null) {
				y = v;
			} else if (x == null) {
				x = v;
			} else if (y == null) {
				y = v;
			} else {
				throw new PropertyException();
			}
		}
		if (x == null) {
			x = PercentageValue.HALF;
		}
		if (y == null) {
			y = PercentageValue.HALF;
		}
		return new QuantityValue[] { x, y };
	}

	private static ShapeSpec parsePolygon(final TokenStream args, final UserAgent ua) throws PropertyException {
		boolean evenOdd = false;
		final List<QuantityValue> points = new ArrayList<>();
		boolean first = true;
		while (args.hasNext()) {
			final CssToken t = args.next();
			if (first && t instanceof CssToken.Ident ident) {
				if (ident.is("evenodd")) {
					evenOdd = true;
				} else if (!ident.is("nonzero")) {
					throw new PropertyException();
				}
				first = false;
				if (!args.eatComma()) {
					throw new PropertyException();
				}
				continue;
			}
			first = false;
			points.add(lengthOrPercentage(ua, t));
			if (points.size() % 2 == 0 && args.hasNext() && !args.eatComma()) {
				throw new PropertyException();
			}
		}
		if (points.size() < 6 || points.size() % 2 != 0) {
			throw new PropertyException();
		}
		return new ShapeSpec.Polygon(evenOdd, points);
	}
}
