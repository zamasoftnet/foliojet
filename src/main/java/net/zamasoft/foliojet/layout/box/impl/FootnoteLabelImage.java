package net.zamasoft.foliojet.layout.box.impl;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.TextShaper;

/**
 * 脚注番号の未解決ラベルです(脚注F5、2026-07-31——
 * consult-codex-2026-07-31-footnote-f5.txt)。{@code ::footnote-call}/
 * {@code ::footnote-marker}の番号部分を文字として焼き込まず、
 * {@code footnoteId}付きのインライン置換原子(このImageを持つ
 * {@code InlineReplacedBox})として保持する。番号はページ確定時に
 * {@code RootBuilder}が「callが残ったページ」ごとに1から割り当てて
 * {@link #resolve}し、描画時に初めてグリフ化される。
 *
 * <p>
 * <b>固定欄</b>: レイアウト幅は番号の桁数に依存しない
 * (callは1桁欄、markerは2桁欄+右揃え。数字は0〜9の最大advance基準)。
 * 番号がレイアウト入力にならないため、widows/avoid・脚注予約との
 * 固定点計算が発生しない——CSSの厳密な番号幅再組版ではないことは
 * 意図的仕様逸脱として記録済み。
 * </p>
 *
 * <p>
 * {@link ReplacedBoxImage}として実装するのは可変状態(解決済み番号)を
 * 持つため——freeze経路({@code ReplacedParamsTemplate})が記録時に
 * {@link #duplicate}の独立複製を凍結し、ソース再生ごとにさらに複製を
 * 配るので、live・再生間で解決状態が共有されない。再生された複製は
 * 未解決に戻り、確定木の走査({@code RootBuilder})が改めて解決する。
 * </p>
 */
public final class FootnoteLabelImage implements net.zamasoft.pdfg2d.gc.image.Image, ReplacedBoxImage {

	private final long footnoteId;

	/** markerなら2桁欄+右揃え、callなら1桁欄+左詰め(超過は右へ張り出し)。 */
	private final boolean marker;

	private final String prefix, suffix;

	private final FontStyle fontStyle;

	private final FontManager fontManager;

	/** 数字1桁の欄幅(0〜9の最大advance)。 */
	private final double digitAdvance;

	private final double prefixAdvance, suffixAdvance;

	private final double ascent, descent;

	/** ページ確定時に割り当てられる番号。未解決は-1。 */
	private int resolvedNumber = -1;

	public FootnoteLabelImage(final long footnoteId, final boolean marker, final String prefix, final String suffix,
			final FontStyle fontStyle, final FontManager fontManager) {
		this.footnoteId = footnoteId;
		this.marker = marker;
		this.prefix = prefix;
		this.suffix = suffix;
		this.fontStyle = fontStyle;
		this.fontManager = fontManager;
		final FontListMetrics flm = fontManager.getFontListMetrics(fontStyle);
		final FontMetrics fm = flm.getFontMetrics(0);
		this.ascent = fm.getAscent();
		this.descent = fm.getDescent();
		double digit = 0;
		for (char c = '0'; c <= '9'; ++c) {
			digit = Math.max(digit, this.measure(String.valueOf(c)));
		}
		this.digitAdvance = digit;
		this.prefixAdvance = this.measure(prefix);
		this.suffixAdvance = this.measure(suffix);
	}

	private FootnoteLabelImage(final FootnoteLabelImage source) {
		this.footnoteId = source.footnoteId;
		this.marker = source.marker;
		this.prefix = source.prefix;
		this.suffix = source.suffix;
		this.fontStyle = source.fontStyle;
		this.fontManager = source.fontManager;
		this.digitAdvance = source.digitAdvance;
		this.prefixAdvance = source.prefixAdvance;
		this.suffixAdvance = source.suffixAdvance;
		this.ascent = source.ascent;
		this.descent = source.descent;
		// 解決状態は複製しない——再生された複製は確定木の走査が改めて解決する
	}

	public long getFootnoteId() {
		return this.footnoteId;
	}

	public boolean isMarker() {
		return this.marker;
	}

	/**
	 * ページ確定時の番号割り当てです({@code RootBuilder}から)。
	 *
	 * @param number ページローカルの脚注番号(1始まり)
	 */
	public void resolve(final int number) {
		this.resolvedNumber = number;
	}

	/** 欄幅: 数字欄(callは1桁、markerは2桁)+literalの前後。 */
	@Override
	public double getWidth() {
		return this.prefixAdvance + this.digitAdvance * (this.marker ? 2 : 1) + this.suffixAdvance;
	}

	@Override
	public double getHeight() {
		return this.ascent + this.descent;
	}

	@Override
	public String getAltString() {
		return this.resolvedNumber < 0 ? "" : this.prefix + this.resolvedNumber + this.suffix;
	}

	@Override
	public void drawTo(final GC gc) throws GraphicsException {
		if (this.resolvedNumber < 0) {
			// 未解決のまま描くと文書通番も0も黙って出せない——型付き失敗
			// (クラッシュ型の一貫性。解決漏れ=採番走査の欠落を隠さない)
			throw new IllegalStateException("unresolved footnote label: id=" + this.footnoteId);
		}
		final String text = this.prefix + this.resolvedNumber + this.suffix;
		final TextImpl[] runs = this.shape(text);
		double advance = 0;
		for (final TextImpl run : runs) {
			advance += run.getAdvance();
		}
		// markerは右揃え(1桁でも本文開始位置が安定)、callは左詰めで
		// 超過(10以上)はinline-end側へ張り出す
		double x = this.marker ? this.getWidth() - advance : 0;
		final double y = this.ascent;
		for (final TextImpl run : runs) {
			gc.drawText(run, x, y);
			x += run.getAdvance();
		}
	}

	@Override
	public void setReplacedBox(final AbstractReplacedBox box, final double width, final double height) {
		// back-referenceは不要(サイズは固定欄)
	}

	@Override
	public net.zamasoft.pdfg2d.gc.image.Image duplicate() {
		return new FootnoteLabelImage(this);
	}

	private double measure(final String text) {
		double advance = 0;
		for (final TextImpl run : this.shape(text)) {
			advance += run.getAdvance();
		}
		return advance;
	}

	/** RubyUnitBox.shapeと同型の自己完結整形(collector→TextImpl列)。 */
	private TextImpl[] shape(final String text) {
		if (text.isEmpty()) {
			return new TextImpl[0];
		}
		final List<TextImpl> runs = new ArrayList<>();
		final GlyphHandler collector = new GlyphHandler() {
			private TextImpl current = null;

			public void startTextRun(final int co, final FontStyle fs, final FontMetrics fm) {
				this.current = new TextImpl(co, fs, fm);
			}

			public void glyph(final int co, final char[] ch, final int coff, final byte clen, final int gid) {
				this.current.appendGlyph(ch, coff, clen, gid);
			}

			public void endTextRun() {
				if (this.current.getGlyphCount() > 0) {
					this.current.pack();
					runs.add(this.current);
				}
				this.current = null;
			}

			public void control(final TextControl control) {
				// ラベルに制御コードは含まれない
			}

			public void flush() {
			}

			public void close() {
			}
		};
		final TextShaper shaper = this.fontManager.getTextShaper();
		shaper.setGlyphHandler(collector);
		shaper.fontStyle(this.fontStyle);
		final char[] ch = text.toCharArray();
		shaper.characters(-1, ch, 0, ch.length);
		shaper.close();
		return runs.toArray(new TextImpl[runs.size()]);
	}
}
