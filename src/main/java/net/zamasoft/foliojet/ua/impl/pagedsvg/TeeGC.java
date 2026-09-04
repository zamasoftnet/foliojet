package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * 同じ描画を2つのGCへ流す描画文脈です(2026-09-03、ページ分割SVGとPDFの同時出力)。
 *
 * <p>
 * 組版は1回で、ページの描画を主(ページSVG)と従(PDF)の両方に流す。状態の読み出しと
 * 対応可否({@link #supports})は主に従う——描画側は主の答えで描き方を決め、従は
 * GCの既定の縮退(ぼかしは塗り、効果は無視)で受ける。文字は主のフォント管理で整形
 * 済みだが、フォント倉庫を従と共有している({@code PagedSVGUserAgent.getFontManager})
 * ので、従のPDFもそのまま文字として書ける。群画像は両方で作って{@link TeeImage}に
 * まとめ、描くときにそれぞれの絵をそれぞれへ渡す。
 * </p>
 */
class TeeGC implements GC {
	private final GC primary;
	private final GC secondary;

	TeeGC(final GC primary, final GC secondary) {
		this.primary = primary;
		this.secondary = secondary;
	}

	GC primary() {
		return this.primary;
	}

	GC secondary() {
		return this.secondary;
	}

	@Override
	public FontManager getFontManager() {
		return this.primary.getFontManager();
	}

	@Override
	public State begin() throws GraphicsException {
		final State a = this.primary.begin();
		final State b = this.secondary.begin();
		return () -> {
			try {
				a.close();
			} finally {
				b.close();
			}
		};
	}

	@Override
	public State beginArtifactScope() throws GraphicsException {
		final State a = this.primary.beginArtifactScope();
		final State b = this.secondary.beginArtifactScope();
		return () -> {
			try {
				a.close();
			} finally {
				b.close();
			}
		};
	}

	@Override
	public State beginTextReplacement(final String logicalText) throws GraphicsException {
		final State a = this.primary.beginTextReplacement(logicalText);
		final State b = this.secondary.beginTextReplacement(logicalText);
		return () -> {
			try {
				a.close();
			} finally {
				b.close();
			}
		};
	}

	@Override
	public void resetState() throws GraphicsException {
		this.primary.resetState();
		this.secondary.resetState();
	}

	@Override
	public void setStrokePaint(final Paint paint) throws GraphicsException {
		this.primary.setStrokePaint(paint);
		this.secondary.setStrokePaint(paint);
	}

	@Override
	public Paint getStrokePaint() {
		return this.primary.getStrokePaint();
	}

	@Override
	public void setFillPaint(final Paint paint) throws GraphicsException {
		this.primary.setFillPaint(paint);
		this.secondary.setFillPaint(paint);
	}

	@Override
	public Paint getFillPaint() {
		return this.primary.getFillPaint();
	}

	@Override
	public float getStrokeAlpha() {
		return this.primary.getStrokeAlpha();
	}

	@Override
	public void setStrokeAlpha(final float strokeAlpha) throws GraphicsException {
		this.primary.setStrokeAlpha(strokeAlpha);
		this.secondary.setStrokeAlpha(strokeAlpha);
	}

	@Override
	public float getFillAlpha() {
		return this.primary.getFillAlpha();
	}

	@Override
	public void setFillAlpha(final float fillAlpha) throws GraphicsException {
		this.primary.setFillAlpha(fillAlpha);
		this.secondary.setFillAlpha(fillAlpha);
	}

	@Override
	public void setBlendMode(final net.zamasoft.pdfg2d.gc.paint.BlendMode mode) throws GraphicsException {
		this.primary.setBlendMode(mode);
		this.secondary.setBlendMode(mode);
	}

	@Override
	public boolean supports(final Capability capability) {
		return this.primary.supports(capability);
	}

	@Override
	public net.zamasoft.pdfg2d.gc.paint.BlendMode getBlendMode() {
		return this.primary.getBlendMode();
	}

	@Override
	public void setLineWidth(final double width) throws GraphicsException {
		this.primary.setLineWidth(width);
		this.secondary.setLineWidth(width);
	}

	@Override
	public double getLineWidth() {
		return this.primary.getLineWidth();
	}

	@Override
	public void setLinePattern(final double[] pattern) throws GraphicsException {
		this.primary.setLinePattern(pattern);
		this.secondary.setLinePattern(pattern);
	}

	@Override
	public double[] getLinePattern() {
		return this.primary.getLinePattern();
	}

	@Override
	public void setLineJoin(final LineJoin style) throws GraphicsException {
		this.primary.setLineJoin(style);
		this.secondary.setLineJoin(style);
	}

	@Override
	public LineJoin getLineJoin() {
		return this.primary.getLineJoin();
	}

	@Override
	public void setLineCap(final LineCap style) throws GraphicsException {
		this.primary.setLineCap(style);
		this.secondary.setLineCap(style);
	}

	@Override
	public LineCap getLineCap() {
		return this.primary.getLineCap();
	}

	@Override
	public void setTextMode(final TextMode textMode) throws GraphicsException {
		this.primary.setTextMode(textMode);
		this.secondary.setTextMode(textMode);
	}

	@Override
	public TextMode getTextMode() {
		return this.primary.getTextMode();
	}

	@Override
	public void transform(final AffineTransform at) throws GraphicsException {
		this.primary.transform(at);
		this.secondary.transform(at);
	}

	@Override
	public AffineTransform getTransform() {
		return this.primary.getTransform();
	}

	@Override
	public void clip(final Shape shape) throws GraphicsException {
		this.primary.clip(shape);
		this.secondary.clip(shape);
	}

	@Override
	public void draw(final Shape shape) throws GraphicsException {
		this.primary.draw(shape);
		this.secondary.draw(shape);
	}

	@Override
	public void fill(final Shape shape) throws GraphicsException {
		this.primary.fill(shape);
		this.secondary.fill(shape);
	}

	@Override
	public void fillDraw(final Shape shape) throws GraphicsException {
		this.primary.fillDraw(shape);
		this.secondary.fillDraw(shape);
	}

	@Override
	public void fillBlurred(final Shape shape, final double sigma) throws GraphicsException {
		this.primary.fillBlurred(shape, sigma);
		this.secondary.fillBlurred(shape, sigma);
	}

	/**
	 * 主・従それぞれで試します。従(PDF)が描けない(PDF/A-1 など透明不可)ときは従だけ
	 * ぼかし無しの塗りで埋める——段階塗りの近似は foliojet 側にしか無く、併産のこの
	 * 組み合わせは稀なので、設計どおりの縮退(pdf-blur-raster-design.md §0-11)。戻り値は主の結果。
	 */
	@Override
	public boolean tryFillBlurred(final Shape shape, final double sigma) throws GraphicsException {
		final boolean drawn = this.primary.tryFillBlurred(shape, sigma);
		if (!this.secondary.tryFillBlurred(shape, sigma)) {
			this.secondary.fill(shape);
		}
		return drawn;
	}

	@Override
	public void drawImage(final Image image) throws GraphicsException {
		if (image instanceof final TeeImage tee) {
			this.primary.drawImage(tee.primary);
			this.secondary.drawImage(tee.secondary);
			return;
		}
		this.primary.drawImage(image);
		this.secondary.drawImage(forSecondary(image));
	}

	/** 従へ渡す絵。取得元付きの絵は従(PDF)が同じ取得元から作った絵に差し替える。 */
	private Image forSecondary(final Image image) {
		if (image instanceof final SourcedImage sourced && sourced.companion != null
				&& this.secondary instanceof net.zamasoft.pdfg2d.pdf.gc.PDFGC) {
			// 従が本物の PDF の GC のときだけ PDF 用の画像に差し替える。filter の捕捉群
			// (あとで画素に再生される)の中では PDF 専用画像は描けないので主の画像のまま
			return sourced.companion;
		}
		return image;
	}

	@Override
	public void drawImage(final Image image, final net.zamasoft.pdfg2d.gc.GroupEffects effects)
			throws GraphicsException {
		if (image instanceof final TeeImage tee) {
			this.primary.drawImage(tee.primary, effects);
			this.secondary.drawImage(tee.secondary, effects);
			return;
		}
		this.primary.drawImage(image, effects);
		this.secondary.drawImage(forSecondary(image), effects);
	}

	@Override
	public void drawText(final Text text, final double x, final double y) throws GraphicsException {
		// フォント倉庫を共有しているので(PagedSVGUserAgent.getFontManager)、
		// 主で整形した文字をそのまま従にも描ける
		this.primary.drawText(text, x, y);
		this.secondary.drawText(text, x, y);
	}

	@Override
	public GroupImageGC createGroupImage(final double width, final double height) throws GraphicsException {
		final GroupImageGC a = this.primary.createGroupImage(width, height);
		final GroupImageGC b = this.secondary.createGroupImage(width, height);
		return new TeeGroupImageGC(a, b);
	}

	/**
	 * filter 用の捕捉群(2026-09-03)。主・従それぞれの捕捉群を束ねる。従(PDF)の捕捉群は
	 * あとで画素に再生されるので、その中では従にも主の画像(画素あり)を渡す
	 * ({@link #forSecondary} は従が PDFGC でないときは差し替えない)。
	 */
	@Override
	public GroupImageGC createFilterGroup(final double width, final double height) throws GraphicsException {
		final GroupImageGC a = this.primary.createFilterGroup(width, height);
		final GroupImageGC b = this.secondary.createFilterGroup(width, height);
		return new TeeGroupImageGC(a, b);
	}

	/** 主の結果を返す。従が UNSUPPORTED なら従には効果なしで描く(併産の縮退)。 */
	@Override
	public GroupEffectsResult drawGroupEffects(final Image image, final net.zamasoft.pdfg2d.gc.GroupEffects effects)
			throws GraphicsException {
		final Image a = image instanceof final TeeImage tee ? tee.primary : image;
		final Image b = image instanceof final TeeImage tee ? tee.secondary : forSecondary(image);
		final GroupEffectsResult result = this.primary.drawGroupEffects(a, effects);
		if (this.secondary.drawGroupEffects(b, effects) == GroupEffectsResult.UNSUPPORTED) {
			this.secondary.drawImage(b);
		}
		return result;
	}

	@Override
	public boolean rasterizesGroupEffects() {
		return this.primary.rasterizesGroupEffects();
	}

	/** 両方の群画像へ描き、終わったら{@link TeeImage}を返す群画像の文脈。 */
	private static final class TeeGroupImageGC extends TeeGC implements GroupImageGC {
		private final GroupImageGC a;
		private final GroupImageGC b;

		TeeGroupImageGC(final GroupImageGC a, final GroupImageGC b) {
			super(a, b);
			this.a = a;
			this.b = b;
		}

		@Override
		public Image finish() throws GraphicsException {
			return new TeeImage(this.a.finish(), this.b.finish());
		}
	}

	/** 主と従それぞれの群画像の対。主のGCへ渡ると主の絵が、従へは従の絵が描かれる。 */
	static final class TeeImage implements Image {
		final Image primary;
		final Image secondary;

		TeeImage(final Image primary, final Image secondary) {
			this.primary = primary;
			this.secondary = secondary;
		}

		@Override
		public Intrinsic getIntrinsic() {
			return this.primary.getIntrinsic();
		}

		@Override
		public double getWidth() {
			return this.primary.getWidth();
		}

		@Override
		public double getHeight() {
			return this.primary.getHeight();
		}

		@Override
		public void drawTo(final GC gc) throws GraphicsException {
			if (gc instanceof final TeeGC tee) {
				this.primary.drawTo(tee.primary);
				this.secondary.drawTo(tee.secondary);
			} else {
				this.primary.drawTo(gc);
			}
		}

		@Override
		public String getAltString() {
			return this.primary.getAltString();
		}
	}
}
