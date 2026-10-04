package net.zamasoft.foliojet.layout.box.impl;

import java.awt.geom.Rectangle2D;
import java.net.URI;

import net.zamasoft.foliojet.css.counterstyle.CounterStyles;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.content.BaselineImage;
import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.layout.util.ApproximationGC;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.PageRef;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;

/**
 * 1パスのPDFで組む{@code target-counter()}の番号の欄です(2026-10-04、
 * docs/design/one-pass-target-counter-design.md)。
 *
 * <p>
 * 欄の幅は「桁数×数字の最大の送り」で、番号の値に依存しない。だから目次は参照先の
 * 頁が決まる前に組める。描くときに値が分かっていれば(前の頁への参照)その場で
 * 描き、まだなら(後ろの頁への参照)PDFの部品を頁から参照だけしておき、中身は
 * 文書を閉じるときに書く({@link PDFGC#drawDeferredForm})。番号は欄の中で
 * 右揃えで、溢れたら左へはみ出す。
 * </p>
 *
 * <p>
 * 前例は脚注番号の固定欄({@link FootnoteLabelImage})。あちらは頁を確定した
 * ときに埋まるが、こちらは別の(後ろの)頁で決まるので、描画を後へ送る。
 * 画像は不変の仕様だけを持ち、描くたびに部品を作る(表の見出しの繰り返しや
 * 複製で共有しない)。
 * </p>
 */
public final class TargetCounterSlotImage
		implements net.zamasoft.pdfg2d.gc.image.Image, ReplacedBoxImage, BaselineImage {

	private final UserAgent ua;

	private final URI uri;

	private final String counter;

	private final short numberStyleType;

	private final FontStyle fontStyle;

	private final FontManager fontManager;

	private final Color color;

	private final int digits;

	/** 数字1桁の欄幅(0〜9の最大advance)。 */
	private final double digitAdvance;

	private final double ascent, descent;

	/**
	 * 1パスのPDFで、{@code target-counter()}を欄として組めるか。組めなければ
	 * 今までどおり(2パス以上なら前のパスの値を文字で組む)。
	 * {@code output.pdf.bidi.actual-text}では行の論理文字列で欄がU+FFFCになり
	 * 番号が抜けるので組まない。
	 */
	public static boolean available(final UserAgent ua) {
		return ua.paintsPageNumbersLater() && UAProps.PROCESSING_PASS_COUNT.getInteger(ua) == 1
				&& !UAProps.OUTPUT_PDF_BIDI_ACTUAL_TEXT.getBoolean(ua);
	}

	public TargetCounterSlotImage(final UserAgent ua, final URI uri, final String counter,
			final short numberStyleType, final FontStyle fontStyle, final Color color) {
		this.ua = ua;
		this.uri = uri;
		this.counter = counter;
		this.numberStyleType = numberStyleType;
		this.fontStyle = fontStyle;
		this.fontManager = ua.getFontManager();
		this.color = color;
		this.digits = Math.max(1, Math.min(9, UAProps.PROCESSING_TARGET_COUNTER_DIGITS.getInteger(ua)));
		final FontMetrics fm = this.fontManager.getFontListMetrics(fontStyle).getFontMetrics(0);
		this.ascent = fm.getAscent();
		this.descent = fm.getDescent();
		double digit = 0;
		for (char c = '0'; c <= '9'; ++c) {
			digit = Math.max(digit, this.measure(String.valueOf(c)));
		}
		this.digitAdvance = digit;
	}

	@Override
	public double getWidth() {
		return this.digitAdvance * this.digits;
	}

	@Override
	public double getHeight() {
		return this.ascent + this.descent;
	}

	/** 番号は字なので、字の基準線を行の基準線に合わせる。 */
	@Override
	public double getDescent() {
		return this.descent;
	}

	/** 参照先のURI(表示リスト用)。 */
	public URI getURI() {
		return this.uri;
	}

	@Override
	public String getAltString() {
		final String text = this.resolve();
		return text == null ? "" : text;
	}

	@Override
	public void drawTo(final GC gc) throws GraphicsException {
		try (final var state = gc.begin()) {
			gc.setFillPaint(this.color);
			final String text = this.resolve();
			if (text != null) {
				this.paint(gc, text);
				return;
			}
			if (unwrapApproximation(gc) instanceof PDFGC pdf) {
				pdf.drawDeferredForm(this.getWidth(), this.getHeight(), form -> {
					final String later = this.resolve();
					if (later == null) {
						// 参照先が無い。2パスのときと同じく空にする
						return null;
					}
					form.setFillPaint(this.color);
					return this.paint(form, later);
				});
				return;
			}
			// ラスタ化するfilterの中など、後から書けない描画先
			this.report("2822.target-counter-unresolved");
		}
	}

	/**
	 * 近似を知らせる包み紙だけを剥がす。filter等の包み紙はその内側へ描く必要が
	 * あるので剥がさない(その中では後から書けない)。
	 */
	private static GC unwrapApproximation(GC gc) {
		while (gc instanceof ApproximationGC a) {
			gc = a.delegate();
		}
		return gc;
	}

	/**
	 * 参照先の値を、このパスで登録されたものだけ読む。前のパス(継続変換の中間パス)の
	 * 値は確定していないので使わない。
	 */
	private String resolve() {
		final PageRef pageRef = this.ua.getUAContext().getPageRef();
		final PageRef.Fragment fragment = pageRef.getFragment(this.uri);
		if (fragment == null || fragment.generation != pageRef.getGeneration()) {
			return null;
		}
		return CounterStyles.of(this.ua).format(fragment.getCounterValue(this.counter), this.numberStyleType);
	}

	/** 欄の右端に揃えて描き、描いた範囲を返す。 */
	private Rectangle2D paint(final GC gc, final String text) throws GraphicsException {
		final TextImpl[] runs = this.shape(text);
		double advance = 0;
		for (final TextImpl run : runs) {
			advance += run.getAdvance();
		}
		final double width = this.getWidth();
		if (advance > width + 0.01) {
			this.report("2822.target-counter-digits");
		}
		double x = width - advance;
		final double left = x;
		for (final TextImpl run : runs) {
			gc.drawText(run, x, this.ascent);
			x += run.getAdvance();
		}
		// 字形の張り出しを切らないよう、上下左右に字の高さぶんの余裕を取る
		final double height = this.getHeight();
		return new Rectangle2D.Double(Math.min(0, left) - height, -height, Math.max(width, advance) + height * 2,
				height * 3);
	}

	private void report(final String detailKey) {
		if (this.ua.getUAContext().getReportedApproximations().add("target-counter() " + detailKey)) {
			this.ua.message(MessageCodes.WARN_APPROXIMATED_RENDERING, "target-counter()",
					UAProps.OUTPUT_TYPE.getString(this.ua),
					net.zamasoft.foliojet.message.MessageCodeUtils.detail(detailKey));
		}
	}

	@Override
	public void setReplacedBox(final AbstractReplacedBox box, final double width, final double height) {
		// back-referenceは不要(サイズは固定欄)
	}

	/** 不変なので複製は自分自身でよい。 */
	@Override
	public net.zamasoft.pdfg2d.gc.image.Image duplicate() {
		return this;
	}

	private double measure(final String text) {
		double advance = 0;
		for (final TextImpl run : this.shape(text)) {
			advance += run.getAdvance();
		}
		return advance;
	}

	private TextImpl[] shape(final String text) {
		return net.zamasoft.foliojet.layout.text.spacing.TrimmedRuns.shape(this.fontManager, this.fontStyle, text, -1,
				false);
	}
}
