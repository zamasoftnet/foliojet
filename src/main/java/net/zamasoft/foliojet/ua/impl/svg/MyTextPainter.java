package net.zamasoft.foliojet.ua.impl.svg;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.text.AttributedCharacterIterator;
import java.util.List;

import java.awt.font.TextAttribute;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.batik.bridge.DefaultFontFamilyResolver;
import org.apache.batik.bridge.FontFace;
import org.apache.batik.bridge.FontFamilyResolver;
import org.apache.batik.bridge.StrokingTextPainter;
import org.apache.batik.bridge.TextSpanLayout;
import org.apache.batik.gvt.font.GVTFontFamily;
import org.apache.batik.gvt.text.TextPaintInfo;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.text.TextLayoutHandler;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRulesBundle;
import net.zamasoft.pdfg2d.gc.text.layout.PageLayoutGlyphHandler;

class MyTextPainter extends StrokingTextPainter {
	private static final Logger LOG = Logger.getLogger(MyTextPainter.class.getName());

	protected final UserAgent ua;
	protected final FontManager fm;
	protected final TextBreakingRules rules;

	public MyTextPainter(UserAgent ua) {
		this.ua = ua;
		this.fm = ua.getFontManager();
		this.rules = TextBreakingRulesBundle.getRules(null);
	}

	/**
	 * 「どの指定フォントでも表示できない文字」へのフォント割当を、AWTの
	 * システムフォント解決ではなくチャンクの解決済みフォント(先頭=
	 * {@link MyGVTFont})へ落とします(2026-08-07)。
	 *
	 * <p>
	 * 旧同梱batik-all 1.14には{@code StrokingTextPainter}のこの箇所を
	 * 書き換えるパッチが入っていた(product/lib/batik-patch.txt)。batik
	 * 1.19ではこの解決器がprotectedメソッドで差し替えられるため、jarへの
	 * パッチを廃止してここで同じ結果を得る: {@code getFamilyThatCanDisplay}
	 * がnullを返すと、呼び出し側はdefaultFont(=チャンクの解決済み
	 * フォントの先頭)へ落ちる。未割当になるのは解決済みリストの全フォント
	 * (UA既定を含む)が表示できない文字だけなので、どのフォントを選んでも
	 * 豆腐になる点は同じ——AWT由来のGVTFontが混ざると{@code paintTextRuns}
	 * が{@code fallbackFontStyle}経由でrun全体を既定フォントへ差し替えて
	 * しまうため、null固定が正しい。
	 * </p>
	 */
	@Override
	protected FontFamilyResolver getFontFamilyResolver() {
		return NO_SYSTEM_FONT_RESOLVER;
	}

	private static final FontFamilyResolver NO_SYSTEM_FONT_RESOLVER = new FontFamilyResolver() {
		public GVTFontFamily resolve(String familyName) {
			return DefaultFontFamilyResolver.SINGLETON.resolve(familyName);
		}

		public GVTFontFamily resolve(String familyName, FontFace fontFace) {
			return DefaultFontFamilyResolver.SINGLETON.resolve(familyName, fontFace);
		}

		public GVTFontFamily loadFont(java.io.InputStream in, FontFace fontFace) throws Exception {
			return DefaultFontFamilyResolver.SINGLETON.loadFont(in, fontFace);
		}

		public GVTFontFamily getDefault() {
			return DefaultFontFamilyResolver.SINGLETON.getDefault();
		}

		public GVTFontFamily getFamilyThatCanDisplay(char c) {
			return null;
		}
	};

	protected void paintTextRuns(@SuppressWarnings("rawtypes") List textRuns, Graphics2D g2d) {
		// TODO 輪郭だけの描画
		// TODO SVG埋め込みフォント(Batik側の制限がある模様)
		GC gc = ((BridgeGraphics2D) g2d).getGC();
		for (int i = 0; i < textRuns.size(); i++) {
			TextRun textRun = (TextRun) textRuns.get(i);
			AttributedCharacterIterator aci = textRun.getACI();

			// 塗りの設定
			TextPaintInfo tpi = (TextPaintInfo) aci.getAttribute(StrokingTextPainter.PAINT_INFO);
			if (tpi != null) {
				if (tpi.composite != null) {
					g2d.setComposite(tpi.composite);
				}
				if (tpi.fillPaint != null) {
					g2d.setPaint(tpi.fillPaint);
				}
			}

			// フォント情報取得。指定されたfont-familyがどれも解決できない場合、
			// Batik側の既定フォント解決(AWTGVTFont)にフォールバックしてしまう
			// ことがある(2026-07-18、SVG中の'MS-Mincho'指定でClassCastException
			// が実際に発生した——このアプリケーションのフォント体系の外なので
			// クラッシュではなくCopper PDFの既定フォントで代替描画する)
			FontStyle fontStyle;
			Object gvtFont = aci.getAttribute(GVT_FONT);
			if (gvtFont instanceof MyGVTFont myFont) {
				fontStyle = myFont.fontStyle;
			} else {
				fontStyle = this.fallbackFontStyle(aci);
			}

			// 文字列抽出
			char[] ch = new char[aci.getEndIndex() - aci.getBeginIndex()];
			aci.first();
			for (int j = 0; aci.getIndex() < aci.getEndIndex(); ++j) {
				ch[j] = aci.current();
				aci.next();
			}

			// 描画
			TextSpanLayout layout = textRun.getLayout();
			Point2D position = layout.getOffset();
			try (final var gcState = gc.begin()) {
				double x = position.getX();
				double y = position.getY();
				AffineTransform at = AffineTransform.getTranslateInstance(x, y - fontStyle.getSize());
				gc.transform(at);
				PageLayoutGlyphHandler lineHandler = new PageLayoutGlyphHandler(gc);
			lineHandler.setDirection(fontStyle.getDirection());
			lineHandler.setLineAdvance(Double.MAX_VALUE);
			TextLayoutHandler tlf = new TextLayoutHandler(gc, this.rules, lineHandler);
			tlf.setDirection(fontStyle.getDirection());
				tlf.fontStyle(fontStyle);
				tlf.characters(-1, ch, 0, ch.length);
				tlf.flush();
				lineHandler.close();
			}
		}
	}

	/**
	 * GVT_FONTがMyGVTFontでない(=このアプリケーションのフォント体系で
	 * 解決できなかった)場合の代替FontStyleを組み立てます。
	 * フォントサイズ等はACIに残っている属性(MySVGTextElementBridge.getFontList
	 * が常にセットする)から可能な範囲で復元し、フォントファミリーだけ
	 * ユーザーエージェントの既定に差し替える。
	 */
	private FontStyle fallbackFontStyle(AttributedCharacterIterator aci) {
		LOG.log(Level.WARNING, "SVG中のfont-familyを解決できませんでした。既定フォントで代替します。");
		Float sizeAttr = (Float) aci.getAttribute(TextAttribute.SIZE);
		double size = sizeAttr != null ? sizeAttr.doubleValue() : 10d;
		Float postureAttr = (Float) aci.getAttribute(TextAttribute.POSTURE);
		FontStyle.Style style = postureAttr != null && postureAttr.equals(TextAttribute.POSTURE_OBLIQUE)
				? FontStyle.Style.ITALIC
				: FontStyle.Style.NORMAL;
		Float weightAttr = (Float) aci.getAttribute(TextAttribute.WEIGHT);
		final float weightValue = weightAttr == null ? TextAttribute.WEIGHT_REGULAR.floatValue()
				: weightAttr.floatValue();
		final FontStyle.Weight weight = weightValue >= 2.75f ? FontStyle.Weight.W_900
				: weightValue >= 2.25f ? FontStyle.Weight.W_800
						: weightValue >= 2.0f ? FontStyle.Weight.W_700
								: weightValue >= 1.5f ? FontStyle.Weight.W_600
										: weightValue >= 1.25f ? FontStyle.Weight.W_500 : FontStyle.Weight.W_400;
		return new FontStyleImpl(this.ua.getDefaultFontFamily().asFontFamilyList(), size, style, weight,
				FontStyle.Direction.LTR, this.ua.getDefaultFontPolicy().asFontPolicyList());
	}
}
