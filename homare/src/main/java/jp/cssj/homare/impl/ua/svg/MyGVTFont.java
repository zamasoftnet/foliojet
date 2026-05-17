package jp.cssj.homare.impl.ua.svg;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

import org.apache.batik.gvt.font.GVTFont;
import org.apache.batik.gvt.font.GVTGlyphVector;
import org.apache.batik.gvt.font.GVTLineMetrics;

import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

public class MyGVTFont implements GVTFont {
	protected final FontMetricsImpl m;
	protected final FontStyle fontStyle;
	protected final net.zamasoft.pdfg2d.font.Font font;
	protected final FontSource source;

	public MyGVTFont(FontMetrics m, FontStyle fontStyle) {
		this.m = (FontMetricsImpl) m;
		this.fontStyle = fontStyle;
		this.font = this.m.getFont();
		this.source = this.m.getFontSource();
	}

	public boolean canDisplay(char c) {
		return this.source.canDisplay(c);
	}

	public int canDisplayUpTo(CharacterIterator text, int start, int limit) {
		char c = text.setIndex(start);
		while (c != CharacterIterator.DONE && text.getIndex() < limit) {
			if (!this.source.canDisplay(c)) {
				return text.getIndex();
			}
			c = text.next();
		}
		return -1;
	}

	public int canDisplayUpTo(char[] text, int start, int limit) {
		return this.canDisplayUpTo(new StringCharacterIterator(new String(text)), start, limit);
	}

	public int canDisplayUpTo(String text) {
		return this.canDisplayUpTo(new StringCharacterIterator(text), 0, text.length());
	}

	public GVTLineMetrics getLineMetrics(CharacterIterator text, int start, int limit, FontRenderContext frc) {
		float ascent = (float) this.m.getAscent();
		int baselineIndex = Font.ROMAN_BASELINE;
		float leading = 0;

		float[] baselineOffsets = null;
		float descent = (float) this.m.getDescent();
		float height = (float) this.fontStyle.getSize();
		int numChars = limit - start;
		float strikethroughOffset = -height / 2f;
		float strikethroughThickness = height / 12f;
		float underlineOffset = 0;
		float underlineThickness = strikethroughThickness;
		float overlineOffset = -height;
		float overlineThickness = strikethroughThickness;
		return new GVTLineMetrics(ascent, baselineIndex, baselineOffsets, descent, height, leading, numChars,
				strikethroughOffset, strikethroughThickness, underlineOffset, underlineThickness, overlineOffset,
				overlineThickness);
	}

	public GVTLineMetrics getLineMetrics(char[] text, int start, int limit, FontRenderContext frc) {
		return this.getLineMetrics(new String(text), start, limit, frc);
	}

	public GVTLineMetrics getLineMetrics(String text, FontRenderContext frc) {
		return this.getLineMetrics(text, 0, text.length(), frc);
	}

	public GVTLineMetrics getLineMetrics(String text, int start, int limit, FontRenderContext frc) {
		return this.getLineMetrics(new StringCharacterIterator(text), start, limit, frc);
	}

	public GVTGlyphVector createGlyphVector(FontRenderContext frc, int[] glyphCodes, CharacterIterator text) {
		throw new UnsupportedOperationException();
	}

	final char[] ch = new char[3];

	public GVTGlyphVector createGlyphVector(FontRenderContext frc, CharacterIterator text) {
		TextImpl ti = new TextImpl(-1, this.fontStyle, this.m);
		char c = text.first();
		int sgid = -1;
		byte clen = 0;
		while (c != CharacterIterator.DONE) {
			int gid = this.font.toGID(c);
			if (gid == -1) {
				break;
			}
			if (sgid != -1) {
				int lgid = this.font.getLigature(sgid, gid);
				if (lgid == -1) {
					ti.appendGlyph(this.ch, 0, clen, sgid);
					sgid = gid;
					clen = 0;
				} else {
					sgid = lgid;
				}
			} else {
				sgid = gid;
			}
			this.ch[clen] = c;
			++clen;
			c = text.next();
		}
		if (clen > 0) {
			ti.appendGlyph(this.ch, 0, clen, sgid);
		}
		return new MyGVTGlyphVector(ti, this, frc);
	}

	public GVTGlyphVector createGlyphVector(FontRenderContext frc, char[] text) {
		return this.createGlyphVector(frc, new String(text));
	}

	public GVTGlyphVector createGlyphVector(FontRenderContext frc, String text) {
		return this.createGlyphVector(frc, new StringCharacterIterator(text));
	}

	public GVTFont deriveFont(float size) {
		throw new UnsupportedOperationException();
	}

	public String getFamilyName() {
		return this.source.getFontName();
	}

	public float getHKern(int g1, int g2) {
		return (float) this.m.getKerning(g1, g2);
	}

	public float getSize() {
		return (float) this.fontStyle.getSize();
	}

	public float getVKern(int g1, int g2) {
		return (float) this.m.getKerning(g1, g2);
	}

}
