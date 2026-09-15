package net.zamasoft.foliojet.ua.impl.svg;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

import org.apache.batik.gvt.font.GVTFont;
import org.apache.batik.gvt.font.GVTGlyphVector;
import org.apache.batik.gvt.font.GVTLineMetrics;

import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

public class MyGVTFont implements GVTFont {
	protected final FontMetricsImpl m;
	protected final FontStyle fontStyle;
	protected final net.zamasoft.pdfg2d.font.Font font;
	protected final FontSource source;
	/** 同じ FontStyle で解決した書体一覧(代替書体の送りを引くため)。null なら代替なし */
	protected final FontListMetrics list;

	public MyGVTFont(FontMetrics m, FontStyle fontStyle) {
		this(m, fontStyle, null);
	}

	public MyGVTFont(FontMetrics m, FontStyle fontStyle, FontListMetrics list) {
		this.m = (FontMetricsImpl) m;
		this.fontStyle = fontStyle;
		this.font = this.m.getFont();
		this.source = this.m.getFontSource();
		this.list = list;
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
		// 字形ごとの送り。この書体に無い字は代替書体の送りを入れる(下記)
		final java.util.List<Double> advances = new java.util.ArrayList<>();
		char c = text.first();
		int sgid = -1;
		byte clen = 0;
		while (c != CharacterIterator.DONE) {
			int gid = this.font.toGID(c);
			if (gid == -1) {
				// Batik の StrokingTextPainter は「表示できない字」を次の書体に回さず、
				// 前の run の末尾に残す(currentIndex = displayUpToIndex + 1 で飛ばした字が
				// 未割当のまま既定書体に付く)。描画は MyTextPainter が書体一覧で組み直すので
				// 字形は正しく出るが、この GlyphVector の送りにその字が無いと次の run の
				// 位置がその分だけ手前になり、字が重なっていた(「SVG 日本語」の日と本、2026-09-14)。
				// 代替書体の送りを持つ仮の字形(空白)を置いて run の長さを合わせる
				if (sgid != -1) {
					advances.add(ti.appendGlyph(this.ch, 0, clen, sgid));
					sgid = -1;
					clen = 0;
				}
				final int space = this.font.toGID(' ');
				this.ch[0] = c;
				ti.appendGlyph(this.ch, 0, (byte) 1, space == -1 ? 0 : space);
				advances.add(this.fallbackAdvance(c));
				c = text.next();
				continue;
			}
			if (sgid != -1) {
				int lgid = this.font.getLigature(sgid, gid);
				if (lgid == -1) {
					advances.add(ti.appendGlyph(this.ch, 0, clen, sgid));
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
			advances.add(ti.appendGlyph(this.ch, 0, clen, sgid));
		}
		final double[] adv = new double[advances.size()];
		for (int i = 0; i < adv.length; ++i) {
			adv[i] = advances.get(i);
		}
		return new MyGVTGlyphVector(ti, this, frc, adv);
	}

	/** この書体に無い字を、同じ FontStyle の書体一覧で最初に表示できる書体で組んだときの送り。 */
	private double fallbackAdvance(final char c) {
		if (this.list != null) {
			for (int i = 0; i < this.list.getLength(); ++i) {
				final FontMetrics m2 = this.list.getFontMetrics(i);
				if (m2 == this.m || !m2.getFontSource().canDisplay(c)) {
					continue;
				}
				final int gid2 = ((FontMetricsImpl) m2).getFont().toGID(c);
				if (gid2 != -1) {
					return m2.getAdvance(gid2);
				}
			}
		}
		final int space = this.font.toGID(' ');
		return space == -1 ? this.fontStyle.getSize() : this.m.getAdvance(space);
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
