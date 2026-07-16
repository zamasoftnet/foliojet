package net.zamasoft.foliojet.layout.text.breaking;

import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.FilterGlyphHandler;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.foliojet.layout.text.breaking.LineBreakRules;

/**
 * 禁則処理のためテキストを分割できないまとまりに分解すします。
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractHyphenation.java,v 1.11 2006/09/06 04:32:46 harumanx
 *          Exp $
 */
public class TextUnitizer implements FilterGlyphHandler {
	protected LineBreakRules breakRules;

	private GlyphHandler glyphHandler;

	/**
	 * Previous character.
	 */
	private char prevChar = 0;

	private TextControl beforeQuad = null;

	public TextUnitizer(LineBreakRules breakRules) {
		this.breakRules = breakRules;
	}

	public LineBreakRules getLineBreakRules() {
		return breakRules;
	}

	public void setLineBreakRules(LineBreakRules breakRules) {
		this.breakRules = breakRules;
	}

	public void setGlyphHandler(GlyphHandler glyphHandler) {
		this.glyphHandler = glyphHandler;
	}

	public void startTextRun(int charOffset, FontStyle fontStyle, FontMetrics fontMetrics) {
		this.glyphHandler.startTextRun(charOffset, fontStyle, fontMetrics);
	}

	public void endTextRun() {
		this.glyphHandler.endTextRun();
	}

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
		char c1 = ch[coff];
		char c2 = ch[coff + clen - 1];
		this.nextGlyph(c1, c2, clen);
		if (this.beforeQuad != null) {
			this.glyphHandler.control(this.beforeQuad);
			this.beforeQuad = null;
		}
		this.glyphHandler.glyph(charOffset, ch, coff, clen, gid);
	}

	public void control(TextControl quad) {
		String str = quad.getString();
		// System.err.println("TU QUAD: " + quad + "/" + Integer.toHexString(this.prevChar));
		if (str == TextControl.BREAK) {
			// CONTINUE_BEFORE, CONTINUE_AFTERを除いて文字列を区切る
			if (this.prevChar != 0 && this.prevChar != '\u2060') {
				this.internalFlush();
			}
			this.prevChar = '\u200B';
		} else if (str == TextControl.CONTINUE_BEFORE) {
			// 前の文字として扱う(後の文字列にくっつける<span>...)
			if (this.prevChar == '\u200B') {
				this.internalFlush();
				this.prevChar = '\u2060';
			} else {
				if (this.beforeQuad != null) {
					this.glyphHandler.control(this.beforeQuad);
				}
				this.beforeQuad = quad;
				return;
			}
		} else if (str == TextControl.CONTINUE_AFTER) {
			// 前の文字として扱う(前の文字列にくっつける...</span>)
			if (this.prevChar == '\u200B') {
				this.internalFlush();
				this.prevChar = '\u2060';
			}
		} else if (str.length() == 0) {
			// 前の文字として扱う(前後にくっつける)
			if (this.prevChar == '\u200B') {
				this.internalFlush();
			}
			this.prevChar = '\u2060';
		} else {
			// 相当する文字列を使う
			int strlen = str.length();
			char c1 = str.charAt(0);
			char c2 = str.charAt(strlen - 1);
			this.nextGlyph(c1, c2, strlen);
		}
		if (this.beforeQuad != null) {
			this.glyphHandler.control(this.beforeQuad);
			this.beforeQuad = null;
		}
		this.glyphHandler.control(quad);
	}

	private void internalFlush() {
		this.glyphHandler.flush();
	}

	public void flush() {
		if (this.beforeQuad != null) {
			this.glyphHandler.control(this.beforeQuad);
			this.beforeQuad = null;
		}
		this.internalFlush();
	}

	public void close() {
		if (this.beforeQuad != null) {
			this.glyphHandler.control(this.beforeQuad);
			this.beforeQuad = null;
		}
		this.glyphHandler.close();
	}

	/**
	 * 
	 * @param c1
	 *            グリフに対応する最初の文字。
	 * @param c2
	 *            グリフに対応する最後の文字。
	 * @param charCount
	 *            文字数
	 */
	private void nextGlyph(char c1, char c2, int charCount) {
		if (this.prevChar != 0 && this.prevChar != '\u2060'
				&& (this.prevChar == '\u200B' || !this.breakRules.atomic(this.prevChar, c1))) {
			this.internalFlush();
		}
		this.prevChar = c2;
	}
}
