package net.zamasoft.foliojet.css.util;

import java.awt.geom.Path2D;

/**
 * SVG 1.1のパスデータ({@code d}属性の文法)を{@link Path2D.Double}へ
 * 変換します(2026-08-29新設、{@code clip-path: path()}用)。
 *
 * <p>
 * M/m L/l H/h V/v C/c S/s Q/q T/t A/a Z/z の全コマンドと暗黙の反復
 * (コマンド文字なしで座標が続く形。Mの反復はLとして扱う)に対応する。
 * 楕円弧は SVG 実装ノート F.6.5 の端点→中心パラメータ化で3次ベジェへ
 * 近似する(90°以下ずつ分割)。座標はそのまま(単位変換しない)。
 * 文法エラーは{@link IllegalArgumentException}で報告し、呼び出し側
 * (ClipPath)がPropertyExceptionへ読み替えて宣言ごと無視する。
 * </p>
 */
public final class SvgPathData {
	private final String s;
	private int pos;
	private final Path2D.Double path = new Path2D.Double();
	// 現在点・サブパス開始点・直前の制御点(S/Tの反射用)
	private double cx, cy, sx, sy, pcx, pcy;
	private char prevCmd = 0;

	private SvgPathData(final String s) {
		this.s = s;
	}

	/**
	 * パスデータを解析します。
	 *
	 * @param d パスデータ文字列
	 * @return 解析結果(空文字列なら空のパス)
	 * @throws IllegalArgumentException 文法エラー
	 */
	public static Path2D.Double parse(final String d) {
		final SvgPathData p = new SvgPathData(d == null ? "" : d);
		p.run();
		return p.path;
	}

	private void run() {
		this.skipWsp();
		boolean first = true;
		while (this.pos < this.s.length()) {
			final char c = this.s.charAt(this.pos);
			if (!isCommand(c)) {
				throw new IllegalArgumentException("SVG path: command expected at " + this.pos + ": " + this.s);
			}
			if (first && c != 'M' && c != 'm') {
				throw new IllegalArgumentException("SVG path: must start with moveto: " + this.s);
			}
			first = false;
			++this.pos;
			this.skipWsp();
			this.command(c);
			this.skipWsp();
		}
	}

	private static boolean isCommand(final char c) {
		return "MmLlHhVvCcSsQqTtAaZz".indexOf(c) >= 0;
	}

	private void command(final char cmd) {
		final boolean rel = Character.isLowerCase(cmd);
		switch (Character.toUpperCase(cmd)) {
		case 'Z' -> {
			this.path.closePath();
			this.cx = this.sx;
			this.cy = this.sy;
			this.pcx = this.cx;
			this.pcy = this.cy;
			this.prevCmd = 'Z';
		}
		case 'M' -> {
			boolean firstPair = true;
			do {
				double x = this.number(), y = this.number();
				if (rel) {
					x += this.cx;
					y += this.cy;
				}
				if (firstPair) {
					this.path.moveTo(x, y);
					this.sx = x;
					this.sy = y;
				} else {
					// 暗黙の反復はlineto
					this.path.lineTo(x, y);
				}
				firstPair = false;
				this.cx = x;
				this.cy = y;
			} while (this.moreArgs());
			this.pcx = this.cx;
			this.pcy = this.cy;
			this.prevCmd = 'M';
		}
		case 'L' -> {
			do {
				double x = this.number(), y = this.number();
				if (rel) {
					x += this.cx;
					y += this.cy;
				}
				this.lineTo(x, y);
			} while (this.moreArgs());
		}
		case 'H' -> {
			do {
				double x = this.number();
				if (rel) {
					x += this.cx;
				}
				this.lineTo(x, this.cy);
			} while (this.moreArgs());
		}
		case 'V' -> {
			do {
				double y = this.number();
				if (rel) {
					y += this.cy;
				}
				this.lineTo(this.cx, y);
			} while (this.moreArgs());
		}
		case 'C' -> {
			do {
				double x1 = this.number(), y1 = this.number(), x2 = this.number(), y2 = this.number(),
						x = this.number(), y = this.number();
				if (rel) {
					x1 += this.cx;
					y1 += this.cy;
					x2 += this.cx;
					y2 += this.cy;
					x += this.cx;
					y += this.cy;
				}
				this.cubicTo(x1, y1, x2, y2, x, y);
			} while (this.moreArgs());
		}
		case 'S' -> {
			do {
				double x2 = this.number(), y2 = this.number(), x = this.number(), y = this.number();
				if (rel) {
					x2 += this.cx;
					y2 += this.cy;
					x += this.cx;
					y += this.cy;
				}
				// 直前がC/Sならその第2制御点の反射、それ以外は現在点
				final boolean reflect = this.prevCmd == 'C' || this.prevCmd == 'S';
				final double x1 = reflect ? 2 * this.cx - this.pcx : this.cx;
				final double y1 = reflect ? 2 * this.cy - this.pcy : this.cy;
				this.cubicTo(x1, y1, x2, y2, x, y);
				this.prevCmd = 'S';
			} while (this.moreArgs());
		}
		case 'Q' -> {
			do {
				double x1 = this.number(), y1 = this.number(), x = this.number(), y = this.number();
				if (rel) {
					x1 += this.cx;
					y1 += this.cy;
					x += this.cx;
					y += this.cy;
				}
				this.quadTo(x1, y1, x, y);
			} while (this.moreArgs());
		}
		case 'T' -> {
			do {
				double x = this.number(), y = this.number();
				if (rel) {
					x += this.cx;
					y += this.cy;
				}
				final boolean reflect = this.prevCmd == 'Q' || this.prevCmd == 'T';
				final double x1 = reflect ? 2 * this.cx - this.pcx : this.cx;
				final double y1 = reflect ? 2 * this.cy - this.pcy : this.cy;
				this.quadTo(x1, y1, x, y);
				this.prevCmd = 'T';
			} while (this.moreArgs());
		}
		case 'A' -> {
			do {
				final double rx = this.number(), ry = this.number(), rot = this.number();
				final boolean large = this.flag(), sweep = this.flag();
				double x = this.number(), y = this.number();
				if (rel) {
					x += this.cx;
					y += this.cy;
				}
				this.arcTo(rx, ry, rot, large, sweep, x, y);
			} while (this.moreArgs());
		}
		default -> throw new IllegalStateException();
		}
	}

	private void lineTo(final double x, final double y) {
		this.path.lineTo(x, y);
		this.cx = x;
		this.cy = y;
		this.pcx = x;
		this.pcy = y;
		this.prevCmd = 'L';
	}

	private void cubicTo(final double x1, final double y1, final double x2, final double y2, final double x,
			final double y) {
		this.path.curveTo(x1, y1, x2, y2, x, y);
		this.pcx = x2;
		this.pcy = y2;
		this.cx = x;
		this.cy = y;
		this.prevCmd = 'C';
	}

	private void quadTo(final double x1, final double y1, final double x, final double y) {
		this.path.quadTo(x1, y1, x, y);
		this.pcx = x1;
		this.pcy = y1;
		this.cx = x;
		this.cy = y;
		this.prevCmd = 'Q';
	}

	/**
	 * 楕円弧(SVG 1.1 F.6.5/F.6.6)。端点パラメータを中心パラメータへ
	 * 変換し、90°以下の区間ごとに3次ベジェで近似する。
	 */
	private void arcTo(double rx, double ry, final double rotDeg, final boolean largeArc, final boolean sweep,
			final double x, final double y) {
		final double x0 = this.cx, y0 = this.cy;
		if (x0 == x && y0 == y) {
			return; // F.6.2: 端点が一致する弧は省略
		}
		rx = Math.abs(rx);
		ry = Math.abs(ry);
		if (rx == 0 || ry == 0) {
			this.lineTo(x, y); // F.6.2: 半径0は直線
			return;
		}
		final double phi = Math.toRadians(rotDeg);
		final double cosPhi = Math.cos(phi), sinPhi = Math.sin(phi);
		// F.6.5.1
		final double dx2 = (x0 - x) / 2, dy2 = (y0 - y) / 2;
		final double x1p = cosPhi * dx2 + sinPhi * dy2;
		final double y1p = -sinPhi * dx2 + cosPhi * dy2;
		// F.6.6.2: 半径が小さすぎれば拡大
		final double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
		if (lambda > 1) {
			final double k = Math.sqrt(lambda);
			rx *= k;
			ry *= k;
		}
		// F.6.5.2
		final double rx2 = rx * rx, ry2 = ry * ry;
		final double num = rx2 * ry2 - rx2 * y1p * y1p - ry2 * x1p * x1p;
		final double den = rx2 * y1p * y1p + ry2 * x1p * x1p;
		double coef = den == 0 ? 0 : Math.sqrt(Math.max(0, num / den));
		if (largeArc == sweep) {
			coef = -coef;
		}
		final double cxp = coef * (rx * y1p / ry);
		final double cyp = coef * -(ry * x1p / rx);
		// F.6.5.3
		final double cX = cosPhi * cxp - sinPhi * cyp + (x0 + x) / 2;
		final double cY = sinPhi * cxp + cosPhi * cyp + (y0 + y) / 2;
		// F.6.5.4〜6
		final double ux = (x1p - cxp) / rx, uy = (y1p - cyp) / ry;
		final double vx = (-x1p - cxp) / rx, vy = (-y1p - cyp) / ry;
		final double theta1 = Math.atan2(uy, ux);
		double dtheta = Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
		if (!sweep && dtheta > 0) {
			dtheta -= 2 * Math.PI;
		} else if (sweep && dtheta < 0) {
			dtheta += 2 * Math.PI;
		}
		// 90°以下の区間へ分割してベジェ近似
		final int segments = Math.max(1, (int) Math.ceil(Math.abs(dtheta) / (Math.PI / 2) - 1e-9));
		final double delta = dtheta / segments;
		final double t = 4.0 / 3.0 * Math.tan(delta / 4);
		double theta = theta1;
		for (int i = 0; i < segments; ++i) {
			final double cos1 = Math.cos(theta), sin1 = Math.sin(theta);
			final double theta2 = theta + delta;
			final double cos2 = Math.cos(theta2), sin2 = Math.sin(theta2);
			// 単位円上の制御点(回転前・楕円スケール前)
			final double e1x = cos1 - t * sin1, e1y = sin1 + t * cos1;
			final double e2x = cos2 + t * sin2, e2y = sin2 - t * cos2;
			final double[] p1 = ellipsePoint(cX, cY, rx, ry, cosPhi, sinPhi, e1x, e1y);
			final double[] p2 = ellipsePoint(cX, cY, rx, ry, cosPhi, sinPhi, e2x, e2y);
			final double[] p = i == segments - 1 ? new double[] { x, y }
					: ellipsePoint(cX, cY, rx, ry, cosPhi, sinPhi, cos2, sin2);
			this.path.curveTo(p1[0], p1[1], p2[0], p2[1], p[0], p[1]);
			theta = theta2;
		}
		this.cx = x;
		this.cy = y;
		this.pcx = x;
		this.pcy = y;
		this.prevCmd = 'A';
	}

	private static double[] ellipsePoint(final double cX, final double cY, final double rx, final double ry,
			final double cosPhi, final double sinPhi, final double ux, final double uy) {
		final double ex = rx * ux, ey = ry * uy;
		return new double[] { cX + cosPhi * ex - sinPhi * ey, cY + sinPhi * ex + cosPhi * ey };
	}

	// ---- 字句 ----

	private void skipWsp() {
		while (this.pos < this.s.length() && isWsp(this.s.charAt(this.pos))) {
			++this.pos;
		}
	}

	private static boolean isWsp(final char c) {
		return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
	}

	/** 引数区切り(空白・カンマ)を読み飛ばし、次に数値が続くならtrue。 */
	private boolean moreArgs() {
		this.skipWsp();
		int p = this.pos;
		if (p < this.s.length() && this.s.charAt(p) == ',') {
			++p;
			while (p < this.s.length() && isWsp(this.s.charAt(p))) {
				++p;
			}
			this.pos = p;
			return true; // カンマの後は必ず数値
		}
		return p < this.s.length() && startsNumber(this.s.charAt(p));
	}

	private static boolean startsNumber(final char c) {
		return (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.';
	}

	private void separator() {
		this.skipWsp();
		if (this.pos < this.s.length() && this.s.charAt(this.pos) == ',') {
			++this.pos;
			this.skipWsp();
		}
	}

	private double number() {
		this.separator();
		final int start = this.pos;
		int p = this.pos;
		final int n = this.s.length();
		if (p < n && (this.s.charAt(p) == '+' || this.s.charAt(p) == '-')) {
			++p;
		}
		boolean digits = false;
		while (p < n && Character.isDigit(this.s.charAt(p))) {
			++p;
			digits = true;
		}
		if (p < n && this.s.charAt(p) == '.') {
			++p;
			while (p < n && Character.isDigit(this.s.charAt(p))) {
				++p;
				digits = true;
			}
		}
		if (!digits) {
			throw new IllegalArgumentException("SVG path: number expected at " + start + ": " + this.s);
		}
		if (p < n && (this.s.charAt(p) == 'e' || this.s.charAt(p) == 'E')) {
			int q = p + 1;
			if (q < n && (this.s.charAt(q) == '+' || this.s.charAt(q) == '-')) {
				++q;
			}
			if (q < n && Character.isDigit(this.s.charAt(q))) {
				while (q < n && Character.isDigit(this.s.charAt(q))) {
					++q;
				}
				p = q;
			}
		}
		this.pos = p;
		return Double.parseDouble(this.s.substring(start, p));
	}

	/** 弧のフラグ(1文字の0/1。"a1 1 0 00 10"のように連結できる)。 */
	private boolean flag() {
		this.separator();
		if (this.pos >= this.s.length()) {
			throw new IllegalArgumentException("SVG path: flag expected: " + this.s);
		}
		final char c = this.s.charAt(this.pos);
		if (c != '0' && c != '1') {
			throw new IllegalArgumentException("SVG path: flag expected at " + this.pos + ": " + this.s);
		}
		++this.pos;
		return c == '1';
	}
}
