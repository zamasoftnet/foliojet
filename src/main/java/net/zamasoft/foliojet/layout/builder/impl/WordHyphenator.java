package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.text.InlineParamsStack;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.FilterGlyphHandler;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.layout.control.SoftHyphen;
import net.zamasoft.pdfg2d.gc.text.pipeline.Hyphenator;

/**
 * 単語内の分綴機会(CSS hyphens)を SoftHyphen として挿入します。
 * <p>
 * CSSJTextUnitizer(禁則)と BuilderGlyphHandler の間に置かれ、
 * flush() で区切られる単位(単語)をバッファします。hyphens:auto かつ言語の
 * 分綴パターンがある場合は Liang アルゴリズム({@link Hyphenator})で分割点を計算し、
 * グリフ再生時に SoftHyphen + flush() を発行します。ソース中の U+00AD
 * ({@link Marker})は manual/auto の両方で分割点になり、存在する場合は
 * 自動分綴より優先されます(css-text-4)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class WordHyphenator implements FilterGlyphHandler {
	/**
	 * ソース中の U+00AD を表すマーカーです。StyledTextUnitizer が字形化の前に
	 * 発行し、WordHyphenator が SoftHyphen に変換して消費します(下流には流れません)。
	 * JOIN 扱いのため TextAtomizer はこの前後を分割しません。
	 */
	static final class Marker extends TextControl {
		final int charOffset;

		Marker(int charOffset) {
			this.charOffset = charOffset;
		}

		public String getString() {
			return JOIN;
		}

		public double getAdvance() {
			return 0;
		}

		public String toString() {
			return "[SHY?]";
		}
	}

	private record RunStart(int charOffset, FontStyle fontStyle, FontMetrics fontMetrics) {
	}

	private record Glyph(int charOffset, char[] ch, byte clen, int gid, int wordOffset) {
	}

	private static final Object RUN_END = new Object();

	/** 分綴パターンを適用する最小の単語長(leftMin+rightMin)。 */
	private static final int MIN_WORD_LENGTH = 5;

	private GlyphHandler out;

	/**
	 * パイプライン共有のインライン文脈(CSSJTextUnitizer が駆動)。
	 */
	private final InlineParamsStack inlineContext;

	/**
	 * 直前のランのフォント(非バッファ時のU+00AD字形生成用)。
	 */
	private FontStyle fontStyle;

	private FontMetrics fontMetrics;

	/**
	 * バッファ中の単語。flush()間のイベント列(RunStart/Glyph/RUN_END/TextControl)。
	 */
	private final List<Object> events = new ArrayList<Object>();

	private final StringBuilder word = new StringBuilder();

	private boolean buffering = false;

	/**
	 * 自動分綴を適用できる単語(全文字が字母で置換要素等を含まない)。
	 */
	private boolean autoBreaks = true;

	private boolean manualBreaks = false;

	private Hyphenator hyphenator = null;

	/**
	 * バッファ開始時点のフォント(単語の先頭にマーカーが来た場合の字形用)。
	 */
	private FontStyle bufFontStyle;

	private FontMetrics bufFontMetrics;

	public WordHyphenator(InlineParamsStack inlineContext) {
		this.inlineContext = inlineContext;
	}

	public void setGlyphHandler(GlyphHandler glyphHandler) {
		this.out = glyphHandler;
	}

	private AbstractTextParams getParams() {
		return this.inlineContext.current();
	}

	public void startTextRun(int charOffset, FontStyle fontStyle, FontMetrics fontMetrics) {
		this.checkBuffering();
		this.fontStyle = fontStyle;
		this.fontMetrics = fontMetrics;
		if (this.buffering) {
			this.events.add(new RunStart(charOffset, fontStyle, fontMetrics));
		} else {
			this.out.startTextRun(charOffset, fontStyle, fontMetrics);
		}
	}

	public void endTextRun() {
		if (this.buffering) {
			this.events.add(RUN_END);
		} else {
			this.out.endTextRun();
		}
	}

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
		this.checkBuffering();
		if (this.buffering) {
			final char[] chars = new char[clen];
			System.arraycopy(ch, coff, chars, 0, clen);
			this.events.add(new Glyph(charOffset, chars, clen, gid, this.word.length()));
			for (int i = 0; i < clen; ++i) {
				if (!Character.isLetter(chars[i])) {
					this.autoBreaks = false;
				}
			}
			this.word.append(chars);
		} else {
			this.out.glyph(charOffset, ch, coff, clen, gid);
		}
	}

	public void control(TextControl quad) {
		if (quad instanceof InlineQuad) {
			final InlineQuad inlineQuad = (InlineQuad) quad;
			switch (inlineQuad.getType()) {
			case InlineQuad.INLINE_START:
			case InlineQuad.INLINE_END:
				// インライン文脈は共有 InlineParamsStack(上流が駆動)で追跡される
				break;

			case InlineQuad.INLINE_REPLACED:
			case InlineQuad.INLINE_BLOCK:
			case InlineQuad.INLINE_ABSOLUTE:
				// 単語の途中に置換要素等が挟まる場合は自動分綴しない
				this.autoBreaks = false;
				break;

			default:
				throw new IllegalStateException();
			}
		} else if (quad instanceof Marker) {
			final Marker marker = (Marker) quad;
			if (this.getParams().hyphens == AbstractTextParams.HYPHENS_NONE) {
				// hyphens:none ではU+00ADは分割点にならず、描画もされない
				return;
			}
			if (this.buffering) {
				this.events.add(marker);
				this.manualBreaks = true;
			} else if (this.fontMetrics != null) {
				this.out.control(new SoftHyphen(marker.charOffset,
						hyphenText(marker.charOffset, this.fontStyle, this.fontMetrics)));
				this.out.flush();
			}
			return;
		}
		if (this.buffering) {
			this.events.add(quad);
		} else {
			this.out.control(quad);
		}
	}

	public void flush() {
		this.processBuffer();
		this.out.flush();
	}

	public void close() {
		this.processBuffer();
		this.out.close();
	}

	/**
	 * バッファリングの開始判定。単語(flush()区切り)の先頭のグリフ/ランで
	 * hyphens:auto なら以降のイベントをバッファします。
	 */
	private void checkBuffering() {
		if (this.buffering) {
			return;
		}
		final AbstractTextParams params = this.getParams();
		if (params.hyphens == AbstractTextParams.HYPHENS_AUTO && params.hyphenator != null) {
			this.buffering = true;
			this.autoBreaks = true;
			this.manualBreaks = false;
			this.hyphenator = params.hyphenator;
			this.bufFontStyle = this.fontStyle;
			this.bufFontMetrics = this.fontMetrics;
		}
	}

	private static final int[] NO_BREAKS = new int[0];

	/**
	 * バッファした単語を分割点(SoftHyphen+flush)を挿入しながら再生します。
	 */
	private void processBuffer() {
		if (!this.buffering) {
			return;
		}
		this.buffering = false;
		if (this.events.isEmpty()) {
			return;
		}
		int[] breaks = NO_BREAKS;
		if (!this.manualBreaks && this.autoBreaks && this.word.length() >= MIN_WORD_LENGTH) {
			breaks = this.hyphenator.hyphenate(this.word.toString());
		}
		FontStyle fs = this.bufFontStyle;
		FontMetrics fm = this.bufFontMetrics;
		int runCharOffset = 0;
		boolean runPending = false, runOpen = false;
		int bi = 0;
		for (int i = 0; i < this.events.size(); ++i) {
			final Object ev = this.events.get(i);
			if (ev instanceof RunStart) {
				final RunStart rs = (RunStart) ev;
				fs = rs.fontStyle();
				fm = rs.fontMetrics();
				runCharOffset = rs.charOffset();
				runPending = true;
			} else if (ev == RUN_END) {
				if (runOpen) {
					this.out.endTextRun();
					runOpen = false;
				}
				runPending = false;
			} else if (ev instanceof Glyph) {
				final Glyph g = (Glyph) ev;
				// クラスタ内部に落ちた分割点は使えないため読み飛ばす
				while (bi < breaks.length && breaks[bi] < g.wordOffset()) {
					++bi;
				}
				if (bi < breaks.length && breaks[bi] == g.wordOffset() && g.wordOffset() > 0) {
					++bi;
					if (runOpen) {
						this.out.endTextRun();
						runOpen = false;
					}
					this.out.control(new SoftHyphen(g.charOffset(), hyphenText(g.charOffset(), fs, fm)));
					this.out.flush();
				}
				if (!runOpen) {
					this.out.startTextRun(runPending ? runCharOffset : g.charOffset(), fs, fm);
					runOpen = true;
					runPending = false;
				}
				this.out.glyph(g.charOffset(), g.ch(), 0, g.clen(), g.gid());
			} else if (ev instanceof Marker) {
				final Marker marker = (Marker) ev;
				assert !runOpen;
				if (fm != null) {
					this.out.control(new SoftHyphen(marker.charOffset, hyphenText(marker.charOffset, fs, fm)));
					this.out.flush();
				}
			} else {
				assert !runOpen;
				this.out.control((TextControl) ev);
			}
		}
		assert !runOpen;
		this.events.clear();
		this.word.setLength(0);
	}

	/**
	 * 分割点で行末に実体化するハイフン字形を作ります。
	 * TextImplは可変(xadvances等)のため分割点ごとに新しいインスタンスを返します。
	 */
	private static TextImpl hyphenText(int charOffset, FontStyle fontStyle, FontMetrics fontMetrics) {
		char hc = '\u2010'; // HYPHEN
		if (!fontMetrics.getFontSource().canDisplay(hc)) {
			hc = '-';
		}
		final Font font;
		if (fontMetrics instanceof FontMetricsImpl) {
			font = ((FontMetricsImpl) fontMetrics).getFont();
		} else {
			font = fontMetrics.getFontSource().createFont();
		}
		final TextImpl text = new TextImpl(charOffset, fontStyle, fontMetrics);
		text.appendGlyph(new char[] { hc }, 0, (byte) 1, font.toGID(hc));
		text.pack();
		return text;
	}
}
