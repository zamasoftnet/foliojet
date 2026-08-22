package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.ClipPathShape;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code clip-path}です(css-shapes-1/css-masking-1、2026-08-22新設)。
 *
 * <p>
 * {@code none | [<basic-shape> || <geometry-box>]}。basic-shapeは
 * {@code inset()}(round・角半径x=y)・{@code circle()}・{@code ellipse()}・
 * {@code polygon()}に対応する。{@code path()}と{@code url()}参照は未対応
 * (宣言ごと無視)。描画は{@code AbstractContainerBox.clip()}が参照
 * ボックスの実寸で形状を解決し、既存のクリップ伝播(overflow:hidden・
 * mask-image近似と同じ経路)へ流す。
 * </p>
 */
public class ClipPath extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ClipPath();

	/**
	 * パース済みの形状指定です。長さは{@link LengthValue}のまま保持し、
	 * computed value段階でem等を絶対長へ確定する。
	 *
	 * @param shape    形状(nullなら参照ボックスのみの指定)
	 * @param box      参照ボックス(未指定はborder-box)
	 */
	public record ClipPathValue(ShapeSpec shape, ClipPathShape.ReferenceBox box) implements Value {
	}

	/** 形状の種類ごとのパース結果。 */
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
	}

	public static Value get(final CSSStyle style) {
		return style.get(INFO);
	}

	/** computed valueからレイアウト用の形状を作ります(noneはnull)。 */
	public static ClipPathShape toShape(final Value value) {
		if (!(value instanceof ClipPathValue v)) {
			return null;
		}
		final ClipPathShape.ReferenceBox box = v.box();
		if (v.shape() == null) {
			return new ClipPathShape.BoxOnly(box);
		}
		return switch (v.shape()) {
		case ShapeSpec.Inset i -> new ClipPathShape.Inset(box, len(i.top()), len(i.right()), len(i.bottom()),
				len(i.left()), lens(i.radii()));
		case ShapeSpec.Circle c -> new ClipPathShape.Circle(box, c.radius() == null ? null : len(c.radius()),
				c.farthestSide(), len(c.cx()), len(c.cy()));
		case ShapeSpec.Ellipse e -> new ClipPathShape.Ellipse(box, e.rx() == null ? null : len(e.rx()),
				e.rxFarthest(), e.ry() == null ? null : len(e.ry()), e.ryFarthest(), len(e.cx()), len(e.cy()));
		case ShapeSpec.Polygon pg -> {
			final net.zamasoft.foliojet.layout.box.params.Length[] pts = new net.zamasoft.foliojet.layout.box.params.Length[pg
					.points().size()];
			for (int i = 0; i < pts.length; ++i) {
				pts[i] = len(pg.points().get(i));
			}
			yield new ClipPathShape.Polygon(box, pg.evenOdd(), pts);
		}
		};
	}

	private static net.zamasoft.foliojet.layout.box.params.Length len(final QuantityValue q) {
		return net.zamasoft.foliojet.css.util.BoxValueUtils.toLength(q);
	}

	private static net.zamasoft.foliojet.layout.box.params.Length[] lens(final QuantityValue[] qs) {
		if (qs == null) {
			return null;
		}
		final net.zamasoft.foliojet.layout.box.params.Length[] out = new net.zamasoft.foliojet.layout.box.params.Length[qs.length];
		for (int i = 0; i < qs.length; ++i) {
			out[i] = len(qs[i]);
		}
		return out;
	}

	protected ClipPath() {
		super("clip-path");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		if (!(value instanceof ClipPathValue v)) {
			return value;
		}
		// em等のフォント相対長をここで絶対化する(%はそのまま)
		if (v.shape() == null) {
			return v;
		}
		return new ClipPathValue(switch (v.shape()) {
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
		}, v.box());
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
				final ClipPathShape.ReferenceBox rb = switch (ident.lower()) {
				case "border-box" -> ClipPathShape.ReferenceBox.BORDER_BOX;
				case "padding-box" -> ClipPathShape.ReferenceBox.PADDING_BOX;
				case "content-box" -> ClipPathShape.ReferenceBox.CONTENT_BOX;
				case "margin-box" -> ClipPathShape.ReferenceBox.MARGIN_BOX;
				default -> null;
				};
				if (rb == null || box != null) {
					throw new PropertyException();
				}
				box = rb;
				continue;
			}
			if (!(lu instanceof CssToken.Func func) || shape != null) {
				throw new PropertyException();
			}
			final TokenStream args = func.argStream();
			shape = switch (func.name().toLowerCase()) {
			case "inset" -> parseInset(args, ua);
			case "circle" -> parseCircle(args, ua, false);
			case "ellipse" -> parseEllipse(args, ua);
			case "polygon" -> parsePolygon(args, ua);
			default -> throw new PropertyException();
			};
		}
		if (shape == null && box == null) {
			throw new PropertyException();
		}
		return new ClipPathValue(shape, box == null ? ClipPathShape.ReferenceBox.BORDER_BOX : box);
	}

	private static QuantityValue length(final UserAgent ua, final CssToken token) throws PropertyException {
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
					rs.add(length(ua, args.next()));
				}
				if (rs.isEmpty() || rs.size() > 4) {
					throw new PropertyException();
				}
				// border-radius式の1〜4値展開(TL, TR, BR, BL)
				radii = new QuantityValue[] { rs.get(0), rs.get(rs.size() > 1 ? 1 : 0),
						rs.get(rs.size() > 2 ? 2 : 0), rs.get(rs.size() > 3 ? 3 : rs.size() > 1 ? 1 : 0) };
				break;
			}
			edges.add(length(ua, t));
		}
		if (edges.isEmpty() || edges.size() > 4) {
			throw new PropertyException();
		}
		// margin式の1〜4値展開(top, right, bottom, left)
		final QuantityValue top = edges.get(0);
		final QuantityValue right = edges.get(edges.size() > 1 ? 1 : 0);
		final QuantityValue bottom = edges.get(edges.size() > 2 ? 2 : 0);
		final QuantityValue left = edges.get(edges.size() > 3 ? 3 : edges.size() > 1 ? 1 : 0);
		return new ShapeSpec.Inset(top, right, bottom, left, radii);
	}

	/** 半径1つ+at位置。ellipse用にはparseEllipseを使う。 */
	private static ShapeSpec parseCircle(final TokenStream args, final UserAgent ua, final boolean unused)
			throws PropertyException {
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
				radius = length(ua, t);
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
				rs.add(length(ua, t));
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
	 * {@code at}の後の位置(1〜2値)。キーワード(center/left/right/top/
	 * bottom)と長さ・%を受け、[x, y]で返す。
	 */
	private static QuantityValue[] parsePosition(final TokenStream args, final UserAgent ua)
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
				v = length(ua, t);
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
			points.add(length(ua, t));
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
