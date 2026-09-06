package net.zamasoft.foliojet.css.impl.lang;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineStartQuad;
import net.zamasoft.foliojet.layout.text.InlineParamsStack;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextShaper;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.breaking.impl.TextAtomizer;

/**
 * インライン境界(InlineQuad)を追跡して行分割規則を切り替える unitizer です。
 * パイプラインの先頭ステージとして InlineParamsStack を駆動し、
 * 下流ステージ(WordHyphenator 等)は同じスタックを共有して読み取ります。
 */
public class CSSJTextUnitizer extends TextAtomizer implements Cloneable {

	private final InlineParamsStack inlineContext;
	private FontStyle runStyle;
	private FontMetrics runMetrics;
	private char[] pendingChars = new char[0];
	private int pendingStart, pendingCharOffset;

	/** shaper へ渡した文字のうち、まだ glyph() が来ていない末尾だけを保持する。 */
	public void characters(final TextShaper shaper, final int charOffset, final char[] ch, final int off,
			final int len) {
		final int retained = this.pendingChars.length - this.pendingStart;
		final char[] chars = new char[retained + len];
		System.arraycopy(this.pendingChars, this.pendingStart, chars, 0, retained);
		System.arraycopy(ch, off, chars, retained, len);
		this.pendingChars = chars;
		this.pendingStart = 0;
		shaper.characters(charOffset, ch, off, len);
		if (this.pendingStart >= retained) {
			this.pendingCharOffset = charOffset + this.pendingStart - retained;
		}
		this.pendingChars = java.util.Arrays.copyOfRange(chars, this.pendingStart, chars.length);
		this.pendingStart = 0;
	}

	/**
	 * 未確定クラスタを計量用に配達する。本文の shaper は flush しない。
	 * 選択済みフォントで末尾クラスタの字形だけを再現し、禁則状態の複製へ渡す。
	 * 現行 TextShaper は末尾の1クラスタだけを保留する。フォント選択はやり直さない。
	 * 終端 flush は発行せず、字間の正規の分割判定だけを複製側で行う。
	 */
	public void deliverText(final GlyphHandler measurement) {
		if (this.pendingStart == this.pendingChars.length) {
			return;
		}
		try {
			final CSSJTextUnitizer copy = (CSSJTextUnitizer) this.clone();
			copy.setGlyphHandler(measurement);
			final var font = this.runMetrics instanceof FontMetricsImpl metrics ? metrics.getFont()
					: this.runMetrics.getFontSource().createFont();
			int gid = -1;
			for (int i = this.pendingStart; i < this.pendingChars.length;) {
				int cp = Character.codePointAt(this.pendingChars, i);
				i += Character.charCount(cp);
				if (cp == '\u00A0') cp = ' ';
				final int ligature = font.getLigature(gid, cp, this.runStyle.getFeatures());
				assert gid == -1 || ligature != -1 : "pending text spans shaping clusters";
				gid = ligature == -1 ? font.toGID(cp, this.runStyle.getFeatures()) : ligature;
			}
			copy.glyph(this.pendingCharOffset, this.pendingChars, this.pendingStart,
					(byte) (this.pendingChars.length - this.pendingStart), gid);
		} catch (final CloneNotSupportedException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public void startTextRun(final int charOffset, final FontStyle style, final FontMetrics metrics) {
		this.runStyle = style;
		this.runMetrics = metrics;
		super.startTextRun(charOffset, style, metrics);
	}

	@Override
	public void glyph(final int charOffset, final char[] ch, final int coff, final byte clen, final int gid) {
		this.pendingStart += clen;
		super.glyph(charOffset, ch, coff, clen, gid);
	}

	public CSSJTextUnitizer(AbstractTextParams params) {
		this(new InlineParamsStack(params));
	}

	public CSSJTextUnitizer(InlineParamsStack inlineContext) {
		super(inlineContext.current().lineBreakRules);
		this.inlineContext = inlineContext;
	}

	public void control(TextControl quad) {
		if (quad instanceof InlineQuad) {
			final InlineQuad inlineQuad = (InlineQuad) quad;
			switch (inlineQuad.getType()) {
			case InlineQuad.INLINE_START: {
				final InlineStartQuad inlineStartQuad = (InlineStartQuad) inlineQuad;
				this.inlineContext.push(inlineStartQuad.box.getTextParams());
				this.setTextBreakingRules(this.inlineContext.current().lineBreakRules);
			}
				break;

			case InlineQuad.INLINE_END: {
				this.inlineContext.pop();
				this.setTextBreakingRules(this.inlineContext.current().lineBreakRules);
			}
				break;

			case InlineQuad.INLINE_REPLACED:
			case InlineQuad.INLINE_BLOCK:
			case InlineQuad.INLINE_ABSOLUTE:
				break;

			default:
				throw new IllegalStateException();
			}
		}
		super.control(quad);
	}

}
