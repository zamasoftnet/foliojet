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
 * css-shapes-1の{@code <basic-shape>}({@code inset()}・{@code circle()}・
 * {@code ellipse()}・{@code polygon()})の解析・絶対化・レイアウト形状化
 * です(2026-08-29新設)。
 *
 * <p>
 * もとは{@code clip-path}({@code css.impl.property.box.ClipPath})が
 * 私有していた処理を、{@code shape-outside}と共有するためにここへ移した。
 * 両プロパティは同じ{@code <basic-shape>}文法を受けるので、解析器を
 * 二重に持つと文法の解釈が将来ずれる(css-shapes-1 §3.1は両者で同一)。
 * {@code ClipPath}側は参照ボックスの既定(border-box)と値型だけを持ち、
 * 形状の中身はすべてここへ委譲する。
 * </p>
 */
public final class BasicShapes {
	private BasicShapes() {
	}

	/**
	 * パース済みの形状指定です。長さは{@link LengthValue}のまま保持し、
	 * computed value段階({@link #absolutize})でem等を絶対長へ確定する。
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
		 * {@code rect(<top> <right> <bottom> <left>)}(css-shapes-1、2026-08-30)。
		 *
		 * <p>
		 * {@code inset()}と違い<b>4値すべてが参照ボックスの左上を原点とする座標</b>
		 * である(右辺・下辺は「右端からの差し込み」ではなく原点からの距離)。
		 * {@code auto}はその辺が参照ボックスの辺に一致することを表す。
		 * {@link #toShape}で{@code inset()}相当へ畳む。
		 */
		record Rect(QuantityValue top, QuantityValue right, QuantityValue bottom, QuantityValue left,
				QuantityValue[] radii) implements ShapeSpec {
		}

		/**
		 * {@code xywh(<x> <y> <width> <height>)}(css-shapes-1、2026-08-30)。
		 * 左上を原点とする位置と大きさ。{@link #toShape}で{@code inset()}相当へ畳む。
		 */
		record Xywh(QuantityValue x, QuantityValue y, QuantityValue width, QuantityValue height,
				QuantityValue[] radii) implements ShapeSpec {
		}

		/**
		 * {@code path([fill-rule,] "svg path data")}(2026-08-29)。座標はpx
		 * なので解析時にpt換算係数を添えておく(長さ値を持たないため
		 * computed value段階の処理は不要)。
		 */
		record Path(boolean evenOdd, java.awt.geom.Path2D.Double path, double pxToPt) implements ShapeSpec {
		}
	}

	/** {@code <shape-box>}キーワードを参照ボックスへ変換します(該当なしはnull)。 */
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
	 * {@code <basic-shape>}関数を解析します。対応外の関数名は
	 * {@link PropertyException}(呼び出し側で宣言ごと無視される)。
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
		// pxはUAの解像度でptへ(通常96dpi→0.75)。uaなし(単体テスト)は既定比
		final double pxToPt = ua == null ? 0.75
				: LengthUtils.convert(ua, 1, net.zamasoft.foliojet.css.token.Unit.PX,
						net.zamasoft.foliojet.css.token.Unit.PT);
		return new ShapeSpec.Path(evenOdd, path, pxToPt);
	}

	/** computed value化: em等のフォント相対長を絶対化する(%はそのまま)。 */
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

	/** computed valueからレイアウト用の形状を作ります(shape==nullは参照ボックスのみ)。 */
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
	 * {@code rect()}/{@code xywh()}の座標1つを{@code inset()}の差し込み量へ
	 * 変換します。{@code fromFar}が真なら「原点からの距離」を反対側の辺からの
	 * 差し込み({@code 100% - 値})へ反転する。{@code auto}(null)は差し込み0。
	 */
	private static Length edge(final QuantityValue q, final boolean fromFar) {
		if (q == null) {
			return Length.ZERO_LENGTH;
		}
		return fromFar ? fullMinus(len(q)) : len(q);
	}

	/** {@code 100% - 長さ}を返します。 */
	private static Length fullMinus(final Length l) {
		return Length.createMixed(-absoluteOf(l), 1 - ratioOf(l));
	}

	/** 2つの{@code <length-percentage>}の和を長さとして返します(nullは0)。 */
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

	/** {@code <length-percentage>}を読みます(それ以外は例外)。 */
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
				// border-radius式の1〜4値展開(TL, TR, BR, BL)
				radii = new QuantityValue[] { rs.get(0), rs.get(rs.size() > 1 ? 1 : 0),
						rs.get(rs.size() > 2 ? 2 : 0), rs.get(rs.size() > 3 ? 3 : rs.size() > 1 ? 1 : 0) };
				break;
			}
			edges.add(lengthOrPercentage(ua, t));
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

	/**
	 * {@code rect(<top> <right> <bottom> <left> [round <radii>])}を解析します
	 * (css-shapes-1、2026-08-30)。各値は{@code auto}を取れる。
	 */
	private static ShapeSpec parseRect(final TokenStream args, final UserAgent ua) throws PropertyException {
		final Coordinates c = parseFourCoordinates(args, ua);
		return new ShapeSpec.Rect(c.values()[0], c.values()[1], c.values()[2], c.values()[3], c.radii());
	}

	/**
	 * {@code xywh(<x> <y> <width> <height> [round <radii>])}を解析します
	 * (css-shapes-1、2026-08-30)。{@code rect()}と違い{@code auto}は取れず、
	 * 幅・高さは負にできない。
	 */
	private static ShapeSpec parseXywh(final TokenStream args, final UserAgent ua) throws PropertyException {
		final Coordinates c = parseFourCoordinates(args, ua);
		final QuantityValue[] v = c.values();
		for (final QuantityValue q : v) {
			if (q == null) {
				// xywh()にautoは無い
				throw new PropertyException();
			}
		}
		if (v[2].isNegative() || v[3].isNegative()) {
			throw new PropertyException();
		}
		return new ShapeSpec.Xywh(v[0], v[1], v[2], v[3], c.radii());
	}

	/** {@code rect()}/{@code xywh()}の4値と、任意の{@code round <radii>}です。 */
	private record Coordinates(QuantityValue[] values, QuantityValue[] radii) {
	}

	/**
	 * {@code rect()}/{@code xywh()}の4値と、任意の{@code round <radii>}を
	 * 読みます。{@code auto}はnullで返します。
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

	/** {@code round}に続く1〜4個の半径をborder-radius式に展開します。 */
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

	/** 半径1つ+at位置。 */
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
	 * {@code at}の後の位置(1〜2値)。キーワード(center/left/right/top/
	 * bottom)と長さ・%を受け、[x, y]で返す。
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
