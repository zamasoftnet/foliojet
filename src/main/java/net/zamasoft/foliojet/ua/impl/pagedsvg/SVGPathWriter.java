package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;

/**
 * {@link Shape}をSVGの{@code d}属性へ直します。
 *
 * <p>
 * SVGのパス文法では<b>同じコマンドが続くならコマンド文字を省ける</b>ため、
 * 直前と同じ種類なら文字を出しません。区切りのカンマも省き、負数の前は
 * 符号が区切りを兼ねるので空白も省きます。長い文書ではこの差が効きます。
 * </p>
 *
 * <p>
 * <b>区切りが要るかは、直前に実際に書いた文字だけで決めること。</b>
 * 「コマンド文字を書いたはず」という前提で判断すると、コマンドを省いた回に
 * 区切りが落ちて{@code L300 0300.4}のように数が繋がり、<b>黙って別の図形に
 * なる</b>。XMLとしては妥当なままなので、整形式の検査では捕まらない。
 * </p>
 *
 * <p>
 * 数値は指数表記を使いません。SVGの文法上は許されますが、読めない実装が
 * あるためです({@link SVGWriter#number}と同じ方針)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
final class SVGPathWriter {
	private SVGPathWriter() {
		// ユーティリティ
	}

	/** 塗り規則。{@code d}とは別に{@code fill-rule}属性で指定します。 */
	static String fillRule(final Shape shape) {
		return shape.getPathIterator(null).getWindingRule() == PathIterator.WIND_EVEN_ODD ? "evenodd" : null;
	}

	static String toPathData(final Shape shape, final AffineTransform at) {
		final StringBuilder d = new StringBuilder(128);
		final double[] c = new double[6];
		char last = 0;
		for (final PathIterator i = shape.getPathIterator(at); !i.isDone(); i.next()) {
			switch (i.currentSegment(c)) {
			case PathIterator.SEG_MOVETO -> {
				d.append('M');
				// moveto に続く座標対は暗黙に lineto。以降の L は省ける
				last = 'L';
				number(d, c[0]);
				number(d, c[1]);
			}
			case PathIterator.SEG_LINETO -> {
				last = command(d, last, 'L');
				number(d, c[0]);
				number(d, c[1]);
			}
			case PathIterator.SEG_QUADTO -> {
				last = command(d, last, 'Q');
				number(d, c[0]);
				number(d, c[1]);
				number(d, c[2]);
				number(d, c[3]);
			}
			case PathIterator.SEG_CUBICTO -> {
				last = command(d, last, 'C');
				number(d, c[0]);
				number(d, c[1]);
				number(d, c[2]);
				number(d, c[3]);
				number(d, c[4]);
				number(d, c[5]);
			}
			case PathIterator.SEG_CLOSE -> {
				d.append('Z');
				// closepath の後に暗黙の継続は無い。次はコマンドから書く
				last = 0;
			}
			default -> throw new IllegalStateException("unknown path segment");
			}
		}
		return d.toString();
	}

	/** 直前と違うコマンドなら文字を書き、同じなら省きます。 */
	private static char command(final StringBuilder d, final char last, final char wanted) {
		if (last != wanted) {
			d.append(wanted);
		}
		return wanted;
	}

	/**
	 * 数を1つ書きます。区切りが要るのは「直前に書いた文字が数字か小数点で、
	 * この数が負でない」ときだけです。
	 */
	private static void number(final StringBuilder d, final double value) {
		final String text = SVGWriter.number(value);
		if (d.length() > 0 && text.charAt(0) != '-') {
			final char prev = d.charAt(d.length() - 1);
			if ((prev >= '0' && prev <= '9') || prev == '.') {
				d.append(' ');
			}
		}
		d.append(text);
	}
}
