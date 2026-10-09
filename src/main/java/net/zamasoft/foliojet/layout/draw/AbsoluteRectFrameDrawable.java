package net.zamasoft.foliojet.layout.draw;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.text.TextClip;

public class AbsoluteRectFrameDrawable extends AbstractDrawable {
	protected final AbsoluteRectFrame frame;
	protected final double width, height;
	protected final TextClip textClip;

	/**
	 * A drawable of the frame.
	 *
	 * @param textClip the text that clips the background ({@code background-clip: text}), or null
	 */
	public AbsoluteRectFrameDrawable(PageBox pageBox, Shape clip, float opacity, AffineTransform transform,
			AbsoluteRectFrame frame, double width, double height, TextClip textClip) {
		super(pageBox, clip, opacity, transform);
		// If sentinel arithmetic results or NaN leak into finalized dimensions, content silently
		// disappears without appearing anywhere on the paper (see LayoutUtils.isDrawable).
		assert net.zamasoft.foliojet.layout.util.LayoutUtils.isDrawable(width) : "描画幅が異常: " + width;
		assert net.zamasoft.foliojet.layout.util.LayoutUtils.isDrawable(height) : "描画高が異常: " + height;
		this.frame = frame;
		this.width = width;
		this.height = height;
		this.textClip = textClip;
	}

	/** What this drawable paints of the frame. */
	protected AbsoluteRectFrame.Part part() {
		return AbsoluteRectFrame.Part.ALL;
	}

	/**
	 * The frame without its background, for a box whose background is clipped to the text and drawn with the
	 * box's content (2026-10-09).
	 */
	public static final class WithoutBackground extends AbsoluteRectFrameDrawable {
		public WithoutBackground(PageBox pageBox, Shape clip, float opacity, AffineTransform transform,
				AbsoluteRectFrame frame, double width, double height) {
			super(pageBox, clip, opacity, transform, frame, width, height, null);
		}

		@Override
		protected AbsoluteRectFrame.Part part() {
			return AbsoluteRectFrame.Part.DECORATIONS;
		}
	}

	/**
	 * The background alone, clipped to the text and drawn with the box's content (2026-10-09).
	 */
	public static final class BackgroundOnly extends AbsoluteRectFrameDrawable {
		public BackgroundOnly(PageBox pageBox, Shape clip, float opacity, AffineTransform transform,
				AbsoluteRectFrame frame, double width, double height, TextClip textClip) {
			super(pageBox, clip, opacity, transform, frame, width, height, textClip);
		}

		@Override
		protected AbsoluteRectFrame.Part part() {
			return AbsoluteRectFrame.Part.BACKGROUND;
		}
	}

	public void innerDraw(GC gc, double x, double y) throws GraphicsException {
		this.frame.draw(gc, x, y, this.width, this.height, this.textClip, this.part());
	}

	/**
	 * {@code filter: drop-shadow()}: draws a shadow in the shape of the border box (inside the margins) (2026-08-29).
	 */
	@Override
	protected void drawFilterShadow(GC gc, double x, double y,
			final net.zamasoft.foliojet.css.value.css3.FilterValue.DropShadow shadow) throws GraphicsException {
		if (this.part() == AbsoluteRectFrame.Part.BACKGROUND) {
			// The rest of the frame casts the shadow of the border box
			return;
		}
		final net.zamasoft.foliojet.layout.part.AbsoluteInsets margin = this.frame.margin;
		net.zamasoft.foliojet.layout.util.BoxDecorationRenderer.drawDropShadow(gc, this.frame.frame,
				x + margin.left, y + margin.top, this.width - margin.getFrameWidth(),
				this.height - margin.getFrameHeight(), shadow.x(), shadow.y(), shadow.blur(), shadow.color());
	}

	@Override
	public String describe() {
		final StringBuilder s = new StringBuilder(
				String.format(java.util.Locale.ROOT, "AbsoluteRectFrame[w=%.2f h=%.2f]", this.width, this.height));
		if (this.part() != AbsoluteRectFrame.Part.ALL) {
			s.append(' ').append(this.part().name().toLowerCase(java.util.Locale.ROOT));
		}
		// Append shadows/outlines only for boxes that have them (leave other boxes' goldens unchanged).
		final net.zamasoft.foliojet.layout.box.params.RectFrame f = this.frame.frame;
		if (f.shadows != null && this.part() != AbsoluteRectFrame.Part.BACKGROUND) {
			for (final net.zamasoft.foliojet.layout.box.params.BoxShadow sh : f.shadows) {
				s.append(String.format(java.util.Locale.ROOT, " shadow[%s%.2f,%.2f,%.2f,%.2f]", sh.inset ? "inset " : "",
						sh.x, sh.y, sh.blur, sh.spread));
			}
		}
		if (f.outline != null && this.part() != AbsoluteRectFrame.Part.BACKGROUND) {
			s.append(String.format(java.util.Locale.ROOT, " outline[style=%d w=%.2f offset=%.2f]",
					f.outline.border.style, f.outline.border.width, f.outline.offset));
		}
		// Append a fill summary for gradient backgrounds (2026-08-29; goldens for boxes
		// without them remain unchanged).
		if (this.part() != AbsoluteRectFrame.Part.DECORATIONS) {
			s.append(f.background.describeGradients());
		}
		// The text that clips the background (background-clip: text, 2026-10-09)
		if (this.textClip != null) {
			s.append(String.format(java.util.Locale.ROOT, " text-clip[runs=%d]", this.textClip.getRuns().size()));
		}
		return this.describeTransform(s.toString());
	}
}
