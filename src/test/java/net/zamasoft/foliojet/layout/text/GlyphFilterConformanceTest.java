package net.zamasoft.foliojet.layout.text;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.impl.lang.CSSJTextUnitizer;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.builder.impl.WordHyphenator;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.FilterGlyphHandler;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.layout.control.WhiteSpace;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules;

/**
 * グリフフィルタの<b>プロトコル保存検査キット</b>です(2026-08-01、
 * エレガンス改善(b))。
 *
 * <p>
 * グリフ連鎖(TextShaper→CSSJTextUnitizer→WordHyphenator→
 * BuilderGlyphHandler)は多メソッドインターフェースのため、中間フィルタが
 * イベントを転送し忘れると<b>黙って情報が落ちる</b>(font-features
 * xPlacement設計時にcodexが指摘した実在の危険——当時はFontMetrics
 * 再導出で回避した)。このキットは正準のイベント列を各フィルタ単体に
 * 通し、保存されるべき性質を機械検査する:
 * </p>
 * <ul>
 * <li>テキスト保存: 配達されたclusterの連結が入力と一致(脱落・重複なし)</li>
 * <li>オフセット単調性: 配達順のcharOffsetが非減少</li>
 * <li>run均衡: glyphは必ずrun内、close後に未配達の保留がない</li>
 * <li>制御保存: 注入した制御が(追加はあっても)脱落せず相対順を保つ</li>
 * </ul>
 */
public class GlyphFilterConformanceTest extends TestCase {

	private static final FontStyle DUMMY_FONT_STYLE = new FontStyle() {
		public Direction getDirection() {
			return Direction.LTR;
		}

		public Weight getWeight() {
			return Weight.W_400;
		}

		public Style getStyle() {
			return Style.NORMAL;
		}

		public net.zamasoft.pdfg2d.gc.font.FontFamilyList getFamily() {
			return null;
		}

		public double getSize() {
			return 10;
		}

		public net.zamasoft.pdfg2d.gc.font.FontPolicyList getPolicy() {
			return null;
		}
	};

	private static final TextBreakingRules DUMMY_LINE_BREAK_RULES = new TextBreakingRules() {
		public boolean atomic(final char c1, final char c2) {
			return false;
		}

		public boolean canSeparate(final char c1, final char c2) {
			return true;
		}
	};

	private static final FontManager DUMMY_FONT_MANAGER = new FontManager() {
		public void addFontFace(final net.zamasoft.pdfg2d.gc.font.FontFace face) {
		}

		public net.zamasoft.pdfg2d.gc.font.FontListMetrics getFontListMetrics(final FontStyle fontStyle) {
			return null;
		}

		public net.zamasoft.pdfg2d.gc.text.TextShaper getTextShaper() {
			return null;
		}
	};

	private static final FontMetrics DUMMY_FONT_METRICS = new FontMetrics() {
		public double getFontSize() {
			return 10;
		}

		public double getXHeight() {
			return 5;
		}

		public double getAscent() {
			return 8;
		}

		public double getDescent() {
			return 2;
		}

		public double getAdvance(final int gid) {
			return 6;
		}

		public double getWidth(final int gid) {
			return 6;
		}

		public double getSpaceAdvance() {
			return 3;
		}

		public double getKerning(final int gid, final int sgid) {
			return 0;
		}

		public net.zamasoft.pdfg2d.font.FontSource getFontSource() {
			return null;
		}
	};

	private static final net.zamasoft.pdfg2d.gc.font.FontListMetrics DUMMY_FLM = new net.zamasoft.pdfg2d.gc.font.FontListMetrics(
			new FontMetrics[] { DUMMY_FONT_METRICS });

	/** 配達された全イベントを記録する終端ハンドラです。 */
	private static final class Recording implements GlyphHandler {
		final StringBuilder text = new StringBuilder();
		final List<Integer> offsets = new ArrayList<>();
		final List<TextControl> controls = new ArrayList<>();
		int openRuns = 0, runStarts = 0, runEnds = 0;
		boolean closed = false;
		boolean glyphOutsideRun = false;

		public void startTextRun(final int co, final FontStyle fs, final FontMetrics fm) {
			++this.openRuns;
			++this.runStarts;
		}

		public void endTextRun() {
			--this.openRuns;
			++this.runEnds;
		}

		public void glyph(final int co, final char[] ch, final int coff, final byte clen, final int gid) {
			if (this.openRuns <= 0) {
				this.glyphOutsideRun = true;
			}
			this.text.append(ch, coff, clen);
			this.offsets.add(co);
		}

		public void control(final TextControl control) {
			this.controls.add(control);
		}

		public void flush() {
		}

		public void close() {
			this.closed = true;
		}
	}

	private static BlockParams params() {
		final BlockParams params = new BlockParams();
		params.fontStyle = DUMMY_FONT_STYLE;
		params.lineBreakRules = DUMMY_LINE_BREAK_RULES;
		params.fontManager = DUMMY_FONT_MANAGER;
		return params;
	}

	/**
	 * 正準列: run内の語(1文字1cluster、連続オフセット)+空白制御+続きの語。
	 * {@code from}からのオフセットで駆動する。
	 */
	private static void drive(final GlyphHandler h, final String word1, final String word2,
			final TextControl control) {
		h.startTextRun(0, DUMMY_FONT_STYLE, null);
		int off = 0;
		for (int i = 0; i < word1.length(); ++i, ++off) {
			h.glyph(off, new char[] { word1.charAt(i) }, 0, (byte) 1, 100 + i);
		}
		h.control(control);
		++off;
		for (int i = 0; i < word2.length(); ++i, ++off) {
			h.glyph(off, new char[] { word2.charAt(i) }, 0, (byte) 1, 200 + i);
		}
		h.endTextRun();
		h.flush();
		h.close();
	}

	private static void assertConformance(final String filterName, final Recording out, final String expectedText,
			final TextControl control) {
		assertEquals(filterName + ": テキスト保存(脱落・重複なし)", expectedText, out.text.toString());
		int prev = -1;
		for (final int off : out.offsets) {
			assertTrue(filterName + ": charOffset単調性 " + out.offsets, off >= prev);
			prev = off;
		}
		assertFalse(filterName + ": run外glyph", out.glyphOutsideRun);
		assertEquals(filterName + ": run開閉の均衡", out.runStarts, out.runEnds);
		assertEquals(filterName + ": close後の未配達run", 0, out.openRuns);
		assertTrue(filterName + ": close伝播", out.closed);
		assertTrue(filterName + ": 注入した制御の保存(追加は許容) " + out.controls,
				out.controls.contains(control));
	}

	public void testCssjTextAtomizerConservesProtocol() throws Exception {
		final Recording out = new Recording();
		final FilterGlyphHandler unitizer = new CSSJTextUnitizer(new InlineParamsStack(params()));
		unitizer.setGlyphHandler(out);
		final WhiteSpace space = new WhiteSpace(DUMMY_FLM, 3);
		drive(unitizer, "alpha", "beta", space);
		assertConformance("CSSJTextUnitizer", out, "alphabeta", space);
	}

	public void testWordHyphenatorConservesProtocol() throws Exception {
		final Recording out = new Recording();
		final FilterGlyphHandler hyphenator = new WordHyphenator(new InlineParamsStack(params()));
		hyphenator.setGlyphHandler(out);
		final WhiteSpace space = new WhiteSpace(DUMMY_FLM, 3);
		drive(hyphenator, "alpha", "beta", space);
		assertConformance("WordHyphenator", out, "alphabeta", space);
	}

	public void testChainedFiltersConserveProtocol() throws Exception {
		final Recording out = new Recording();
		final FilterGlyphHandler unitizer = new CSSJTextUnitizer(new InlineParamsStack(params()));
		final FilterGlyphHandler hyphenator = new WordHyphenator(new InlineParamsStack(params()));
		hyphenator.setGlyphHandler(out);
		unitizer.setGlyphHandler(hyphenator);
		final WhiteSpace space = new WhiteSpace(DUMMY_FLM, 3);
		drive(unitizer, "gamma", "delta", space);
		assertConformance("CSSJTextUnitizer→WordHyphenator", out, "gammadelta", space);
	}
}
