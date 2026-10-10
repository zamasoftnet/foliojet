package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;
import net.zamasoft.foliojet.layout.box.params.ClipPathShape;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.ObjectFitMode;
import net.zamasoft.foliojet.layout.box.params.Offset;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;

import net.zamasoft.foliojet.layout.draw.AbsoluteRectFrameDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.BorderRenderer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * Implements an image box.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractReplacedBox.java 1635 2023-04-03 08:16:41Z miyabe $
 */
public abstract class AbstractReplacedBox extends AbstractBox {
	protected final ReplacedParams params;

	protected final AbsoluteRectFrame frame;

	protected double width = 0;

	protected double height = 0;

	protected double offsetX = 0;

	protected double offsetY = 0;

	public AbstractReplacedBox(final ReplacedParams params) {
		this.params = params;
		this.frame = new AbsoluteRectFrame(params.frame);
	}

	public final BoxType getType() {
		return BoxType.REPLACED;
	}

	public final Params getParams() {
		return this.params;
	}

	public final ReplacedParams getReplacedParams() {
		return this.params;
	}

	@Override
	protected net.zamasoft.foliojet.layout.part.AbsoluteInsets transformReferenceMargin() {
		return this.frame == null ? null : this.frame.margin;
	}

	public final AbsoluteRectFrame getFrame() {
		return this.frame;
	}

	public final double getWidth() {
		return this.width + this.frame.getFrameWidth();
	}

	public final double getHeight() {
		return this.height + this.frame.getFrameHeight();
	}

	public final double getInnerWidth() {
		return this.width;
	}

	public final double getInnerHeight() {
		return this.height;
	}

	/**
	 * Returns the distance from this box's bottom edge (the margin bottom) to the baseline
	 * in a horizontal line (2026-10-04). For ordinary images, the baseline is at the bottom, so this is 0.
	 * For {@link net.zamasoft.foliojet.layout.box.content.BaselineImage} (formulas), convert the image depth
	 * using the scale of the rectangle drawn by object-fit, then add the space below that rectangle
	 * and the bottom of the frame (margin, border, and padding). Line height calculation, vertical
	 * alignment, drawing, and glyph outline collection must all use the same value.
	 */
	public final double getBaselineDescent() {
		if (!(this.params.image instanceof net.zamasoft.foliojet.layout.box.content.BaselineImage baseline)) {
			return 0;
		}
		final Image image = this.params.image;
		if (!(this.width > 0 && this.height > 0 && image.getWidth() > 0 && image.getHeight() > 0)) {
			return 0;
		}
		final double[] r = objectFitRect(this.params.objectFit, this.params.objectPosition, image.getWidth(),
				image.getHeight(), this.width, this.height);
		final double below = this.height - (r[1] + r[3]);
		return this.frame.getFrameBottom() + below + baseline.getDescent() * r[3] / image.getHeight();
	}

	public final void calculateFrame(final double lineAxis) {
		//
		// ■ Calculate padding
		//
		LayoutUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineAxis);
		//
		// ■ Calculate margins
		//
		LayoutUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineAxis);
	}

	/** Content-box height from aspect-ratio (the ratio applies to the box-sizing box; 2026-08-29). */
	private double ratioHeight(final double contentWidth, final double ratio) {
		if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
			return Math.max(0, (contentWidth + this.frame.getBorderWidth()) / ratio - this.frame.getBorderHeight());
		}
		return contentWidth / ratio;
	}

	/** Content-box width from aspect-ratio (the inverse of {@link #ratioHeight}; 2026-08-29). */
	private double ratioWidth(final double contentHeight, final double ratio) {
		if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
			return Math.max(0, (contentHeight + this.frame.getBorderHeight()) * ratio - this.frame.getBorderWidth());
		}
		return contentHeight * ratio;
	}

	public final void calculateSize(final double refWidth, final double refHeight, final double refMaxWidth, final double refMaxHeight) {
		double width = LayoutUtils.computeDimensionWidth(this.params.size, refWidth);
		double height = LayoutUtils.computeDimensionHeight(this.params.size, refHeight);

		if (this.params.image instanceof ReplacedBoxImage) {
			((ReplacedBoxImage) this.params.image).setReplacedBox(this, width, height);
		}
		// aspect-ratio (2026-08-29, css-sizing-4 §5): The specified ratio takes precedence over the intrinsic
		// ratio. Only when auto is also specified does the intrinsic ratio (positive width and height) take
		// precedence; use the specified ratio for images without one (broken images or SVGs without dimensions).
		double ratio = 0;
		if (this.params.aspectRatio > 0) {
			final boolean natural = this.params.image.getWidth() > 0 && this.params.image.getHeight() > 0;
			ratio = this.params.aspectRatioAuto && natural ? 0 : this.params.aspectRatio;
		}
		// SPEC CSS2.1 10.3.2
		if (LayoutUtils.isNone(width) && LayoutUtils.isNone(height)) {
			// Both dimensions are indefinite
			width = this.params.image.getWidth();
			height = this.params.image.getHeight();
			if (ratio > 0) {
				// Keep the intrinsic width and derive the height from the ratio (or derive the width if only height exists)
				if (width > 0) {
					height = this.ratioHeight(width, ratio);
				} else if (height > 0) {
					width = this.ratioWidth(height, ratio);
				}
			} else if (this.params.image.getIntrinsic() == Image.Intrinsic.RATIO && width > 0 && height > 0) {
				// Only a ratio (an SVG with a viewBox but no width/height, 2026-10-08): fill the containing
				// block's inline size and derive the other from the ratio, as CSS 2.1 §10.3.2 suggests and Chrome
				// does, block-level or inline. The viewBox size used to stand in as the natural size (an SVG logo
				// of viewBox 213x52 came out 160pt wide in a 366pt column). Where the containing block's size is
				// not known yet (measuring a float, an inline-block or a table cell), the viewBox size stays, so
				// that the box is not measured to nothing (Chrome gives 0 there).
				final double fill = this.params.flow.isVertical()
						? (LayoutUtils.isNone(refHeight) ? LayoutUtils.NONE : refHeight - this.frame.getFrameHeight())
						: (LayoutUtils.isNone(refWidth) ? LayoutUtils.NONE : refWidth - this.frame.getFrameWidth());
				if (!LayoutUtils.isNone(fill)) {
					final double naturalWidth = width, naturalHeight = height;
					if (this.params.flow.isVertical()) {
						height = Math.max(0, fill);
						width = height * naturalWidth / naturalHeight;
					} else {
						width = Math.max(0, fill);
						height = width * naturalHeight / naturalWidth;
					}
				}
			}
		} else if (LayoutUtils.isNone(width)) {
			// Width is indefinite
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				height -= this.frame.getBorderHeight();
			}
			double intrinsicWidth = this.params.image.getWidth();
			double intrinsicHeight = this.params.image.getHeight();
			if (ratio > 0) {
				width = this.ratioWidth(height, ratio);
			} else if (intrinsicHeight != 0) {
				width = intrinsicWidth * height / intrinsicHeight;
			} else {
				// The source image has zero height [policy: use the smallest layout]
				width = 0;
			}
		} else if (LayoutUtils.isNone(height)) {
			// Height is indefinite
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				width -= this.frame.getBorderWidth();
			}
			double intrinsicHeight = this.params.image.getHeight();
			double intrinsicWidth = this.params.image.getWidth();
			if (ratio > 0) {
				height = this.ratioHeight(width, ratio);
			} else if (intrinsicWidth != 0) {
				height = intrinsicHeight * width / intrinsicWidth;
			} else {
				// The source image has zero width [policy: use the smallest layout]
				height = 0;
			}
		} else if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
			width -= this.frame.getBorderWidth();
			height -= this.frame.getBorderHeight();
		}

		assert !LayoutUtils.isNone(width);
		assert !LayoutUtils.isNone(height);

		// SPEC CSS2.1 10.4
		double maxWidth = LayoutUtils.computeDimensionWidth(this.params.maxSize, refMaxWidth);
		double minWidth = LayoutUtils.computeDimensionWidth(this.params.minSize, refWidth);
		double maxHeight = LayoutUtils.computeDimensionHeight(this.params.maxSize, refMaxHeight);
		double minHeight = LayoutUtils.computeDimensionHeight(this.params.minSize, refHeight);
		// The containing block can be indefinite (NONE) during intrinsic size measurement. In this case,
		// a percentage min-size is a cyclic contribution, so treat it as 0. Passing the sentinel to Math.max
		// as a numeric minimum size would inflate the replaced element to around 10^308 pt.
		if (LayoutUtils.isNone(minWidth)) {
			minWidth = 0;
		}
		if (LayoutUtils.isNone(minHeight)) {
			minHeight = 0;
		}
		if (LayoutUtils.isNone(maxWidth)) {
			maxWidth = Double.MAX_VALUE;
		} else if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
			maxWidth -= this.frame.getBorderWidth();
		}

		if (LayoutUtils.isNone(maxHeight)) {
			maxHeight = Double.MAX_VALUE;
		} else if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
			maxHeight -= this.frame.getBorderHeight();
		}
		maxWidth = Math.max(minWidth, maxWidth);
		maxHeight = Math.max(minHeight, maxHeight);

		if (width > maxWidth) {
			if (height > maxHeight) {
				if (maxWidth / width <= maxHeight / height) {
					// #5
					height = Math.max(minHeight, maxWidth * height / width);
					width = maxWidth;
				} else {
					// #6
					width = Math.max(minWidth, maxHeight * width / height);
					height = maxHeight;
				}
			} else if (height < minHeight) {
				// #10
				width = maxWidth;
				height = minWidth;
			} else {
				// #1
				height = Math.max(maxWidth * height / width, minHeight);
				width = maxWidth;
			}
		} else if (width < minWidth) {
			if (height < minHeight) {
				if (minWidth / width <= minHeight / height) {
					// #7
					if (height != 0) {
						width = Math.min(maxWidth, minHeight * width / height);
					} else {
						width = minWidth;
					}
					height = minHeight;
				} else {
					// #8
					if (width != 0) {
						height = Math.min(maxHeight, minWidth * height / width);
					} else {
						height = minHeight;
					}
					width = minWidth;
				}
			} else if (height > maxHeight) {
				// #9
				width = minWidth;
				height = maxHeight;
			} else {
				// #2
				if (width != 0) {
					height = Math.min(minWidth * height / width, maxHeight);
				} else {
					height = minHeight;
				}
				width = minWidth;
			}
		} else if (height > maxHeight) {
			// #3
			width = Math.max(maxHeight * width / height, minWidth);
			height = maxHeight;
		} else if (height < minHeight) {
			// #4
			if (height != 0) {
				width = Math.min(minHeight * width / height, maxWidth);
			} else {
				width = minWidth;
			}
			height = minHeight;
		}
		this.width = width;
		this.height = height;
	}

	/**
	 * Sets the content size along the line axis to one decided outside, after {@link #calculateSize}: a flex item's
	 * main size, which the flex algorithm resolved from this element's size, limits and margins (2026-10-10,
	 * {@code LayoutUtils.calculateReplacedSize}). The other axis follows: its specified size, else the ratio from the
	 * new size, else what it was; then its own min and max sizes.
	 *
	 * @param vertical whether the line axis is the height (vertical writing)
	 * @param content the content size along the line axis
	 * @param ref the percentage basis of the other axis's size and minimum
	 * @param refMax the percentage basis of the other axis's maximum
	 */
	public final void fillLine(final boolean vertical, final double content, final double ref, final double refMax) {
		final boolean borderBox = this.params.boxSizing == BoxSizingMode.BORDER_BOX;
		double ratio = 0;
		if (this.params.aspectRatio > 0) {
			final boolean natural = this.params.image.getWidth() > 0 && this.params.image.getHeight() > 0;
			ratio = this.params.aspectRatioAuto && natural ? 0 : this.params.aspectRatio;
		}
		final double imageWidth = this.params.image.getWidth(), imageHeight = this.params.image.getHeight();
		if (vertical) {
			double width = LayoutUtils.computeDimensionWidth(this.params.size, ref);
			if (!LayoutUtils.isNone(width)) {
				if (borderBox) {
					width -= this.frame.getBorderWidth();
				}
			} else if (ratio > 0) {
				width = this.ratioWidth(content, ratio);
			} else if (imageWidth > 0 && imageHeight > 0) {
				width = content * imageWidth / imageHeight;
			} else {
				width = this.width;
			}
			this.height = content;
			this.width = this.clampCross(width, LayoutUtils.computeDimensionWidth(this.params.minSize, ref),
					LayoutUtils.computeDimensionWidth(this.params.maxSize, refMax), borderBox ? this.frame.getBorderWidth() : 0);
		} else {
			double height = LayoutUtils.computeDimensionHeight(this.params.size, ref);
			if (!LayoutUtils.isNone(height)) {
				if (borderBox) {
					height -= this.frame.getBorderHeight();
				}
			} else if (ratio > 0) {
				height = this.ratioHeight(content, ratio);
			} else if (imageWidth > 0 && imageHeight > 0) {
				height = content * imageHeight / imageWidth;
			} else {
				height = this.height;
			}
			this.width = content;
			this.height = this.clampCross(height, LayoutUtils.computeDimensionHeight(this.params.minSize, ref),
					LayoutUtils.computeDimensionHeight(this.params.maxSize, refMax),
					borderBox ? this.frame.getBorderHeight() : 0);
		}
	}

	/**
	 * Stretches the element across the line of a column flex laid out in normal flow, {@code outer} being the line for
	 * its margin box (2026-10-10, {@code BlockBuilder}): its content size along the line is what is left of it after
	 * the margins, borders and padding, within its own min and max sizes (whose percentages are of {@code outer}); the
	 * other axis follows as in {@link #fillLine}.
	 *
	 * @param vertical whether the line axis is the height (vertical writing)
	 * @param outer the line size of the column
	 */
	public final void stretchLine(final boolean vertical, final double outer) {
		final boolean borderBox = this.params.boxSizing == BoxSizingMode.BORDER_BOX;
		final double box = vertical ? this.frame.getBorderHeight() : this.frame.getBorderWidth();
		final double content = outer - (vertical ? this.frame.getFrameHeight() : this.frame.getFrameWidth());
		final double min = vertical ? LayoutUtils.computeDimensionHeight(this.params.minSize, outer)
				: LayoutUtils.computeDimensionWidth(this.params.minSize, outer);
		final double max = vertical ? LayoutUtils.computeDimensionHeight(this.params.maxSize, outer)
				: LayoutUtils.computeDimensionWidth(this.params.maxSize, outer);
		this.fillLine(vertical, this.clampCross(content, min, max, borderBox ? box : 0), LayoutUtils.NONE,
				LayoutUtils.NONE);
	}

	/**
	 * Sets the content size along the page axis to one decided outside, after layout (2026-10-10, {@code
	 * FlexItemBox.passPageSizeToReplaced}): the main size of a column, or the stretched cross size of a row, which keeps
	 * to the element's min and max sizes.
	 *
	 * @param vertical whether the line axis is the height (vertical writing; the page axis is then the width)
	 * @param content the content size along the page axis
	 * @param clamp whether the element's own min and max sizes bound it
	 * @param ref the percentage basis of those min and max sizes, the container's page-axis size (NONE: percentages
	 *            do not apply; 2026-10-10, max-height: 50% of a stretched row)
	 */
	public final void fillPage(final boolean vertical, final double content, final boolean clamp, final double ref) {
		double size = content;
		if (clamp) {
			final boolean borderBox = this.params.boxSizing == BoxSizingMode.BORDER_BOX;
			final double box = borderBox ? (vertical ? this.frame.getBorderWidth() : this.frame.getBorderHeight()) : 0;
			final double max = vertical ? LayoutUtils.computeDimensionWidth(this.params.maxSize, ref)
					: LayoutUtils.computeDimensionHeight(this.params.maxSize, ref);
			final double min = vertical ? LayoutUtils.computeDimensionWidth(this.params.minSize, ref)
					: LayoutUtils.computeDimensionHeight(this.params.minSize, ref);
			if (!LayoutUtils.isNone(max)) {
				size = Math.min(size, max - box);
			}
			if (!LayoutUtils.isNone(min)) {
				size = Math.max(size, min - box);
			}
		}
		if (vertical) {
			this.width = Math.max(0, size);
		} else {
			this.height = Math.max(0, size);
		}
	}

	/** A content size between a minimum and a maximum (NONE: none), both on the box-sizing scale. */
	private double clampCross(final double size, final double min, final double max, final double box) {
		double result = size;
		if (!LayoutUtils.isNone(max)) {
			result = Math.min(result, max - box);
		}
		if (!LayoutUtils.isNone(min)) {
			result = Math.max(result, min - box);
		}
		return Math.max(0, result);
	}

	public void finishLayoutSelf(IFramedBox containerBox) {
		// Relative positioning
		AbstractStaticPos pos = (AbstractStaticPos) this.getPos();
		if (pos.offset != null) {
			//
			// ■ Calculate the relative position
			//
			this.offsetX = LayoutUtils.computeOffsetX(pos.offset, containerBox);
			this.offsetY = LayoutUtils.computeOffsetY(pos.offset, containerBox);
		}

		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
		assert !LayoutUtils.isNone(this.offsetX) : "Undefined offsetX";
		assert !LayoutUtils.isNone(this.offsetY) : "Undefined offsetY";
	}

	/**
	 * Does nothing because this is a leaf (has no children).
	 */
	public void pushFinishLayoutChildren(IFramedBox containerBox, java.util.Deque<FinishLayoutStep> worklist) {
	}

	protected static class ReplacedBoxDrawable extends AbsoluteRectFrameDrawable {
		protected final Image image;

		protected final ObjectFitMode objectFit;

		protected final Offset objectPosition;

		public ReplacedBoxDrawable(PageBox pageBox, Shape clip, float opacity, AffineTransform transform,
				AbsoluteRectFrame frame, Image image, ObjectFitMode objectFit, Offset objectPosition, double width,
				double height) {
			super(pageBox, clip, opacity, transform, frame, width, height, null);
			this.image = image;
			this.objectFit = objectFit;
			this.objectPosition = objectPosition;
		}

		@Override
		public String describe() {
			// Emit the resolved number of a footnote label (F5) to the display list so the golden can lock down
			// the number characters themselves as well as their coordinates.
			if (this.image instanceof net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage label) {
				return String.format(java.util.Locale.ROOT, "FootnoteLabel[\"%s\" w=%.2f h=%.2f]",
						label.getAltString(), this.width, this.height);
			}
			// Single-pass target-counter() field. Its value is set at draw time (at document close for a later page).
			if (this.image instanceof net.zamasoft.foliojet.layout.box.impl.TargetCounterSlotImage slot) {
				return String.format(java.util.Locale.ROOT, "TargetCounterSlot[\"%s\" w=%.2f h=%.2f]",
						slot.getURI().getRawFragment(), this.width, this.height);
			}
			// Emit the actual drawing rectangle when object-fit/object-position are not the defaults
			// (the internal transform in innerDraw does not appear in the display list, so expose the same
			// calculation here to let the golden lock down how the image fits). Keep the default
			// output as before, leaving existing goldens unchanged.
			if (this.objectFit != ObjectFitMode.FILL || !isCenterPosition(this.objectPosition)) {
				final double width = this.width - this.frame.getFrameWidth();
				final double height = this.height - this.frame.getFrameHeight();
				if (width > 0 && height > 0 && this.image.getWidth() > 0 && this.image.getHeight() > 0) {
					final double[] r = this.fitRect(width, height);
					return super.describe() + String.format(java.util.Locale.ROOT,
							"[objectFit=%s dx=%.2f dy=%.2f w=%.2f h=%.2f]", this.objectFit, r[0], r[1], r[2], r[3]);
				}
			}
			return super.describe();
		}

		/**
		 * {@code filter: drop-shadow()}: For raster images, draw a blurred shadow of the opacity silhouette
		 * at the image's position and size, with an offset (2026-08-29).
		 * For non-raster images, use a box-shaped shadow (AbsoluteRectFrameDrawable).
		 */
		@Override
		protected void drawFilterShadow(GC gc, double x, double y,
				final net.zamasoft.foliojet.css.value.css3.FilterValue.DropShadow filterShadow)
				throws GraphicsException {
			final double left = x + this.frame.getFrameLeft(), top = y + this.frame.getFrameTop();
			final double width = this.width - this.frame.getFrameWidth();
			final double height = this.height - this.frame.getFrameHeight();
			if (width > 0 && height > 0 && this.image.getWidth() > 0 && this.image.getHeight() > 0) {
				final double[] r = this.fitRect(width, height);
				final double sx = r[2] / this.image.getWidth(), sy = r[3] / this.image.getHeight();
				// Half the blur radius is the standard deviation (filter-effects-1 §9.2). Convert it
				// to the logical units of the image.
				final double sigma = filterShadow.blur() > 0 ? filterShadow.blur() / 2 / Math.sqrt(sx * sy) : 0;
				final net.zamasoft.foliojet.layout.util.FilterOps.Shadow shadow = net.zamasoft.foliojet.layout.util.FilterOps
						.shadowOf(this.image, filterShadow.color(), sigma);
				if (shadow != null) {
					final AffineTransform at = AffineTransform.getTranslateInstance(
							left + r[0] + filterShadow.x() - shadow.padX() * sx,
							top + r[1] + filterShadow.y() - shadow.padY() * sy);
					at.scale(sx, sy);
					try (final var gcState = gc.begin()) {
						gc.transform(at);
						gc.drawImage(shadow.image());
					}
					return;
				}
			}
			super.drawFilterShadow(gc, x, y, filterShadow);
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			super.innerDraw(gc, x, y);
			x += this.frame.getFrameLeft();
			y += this.frame.getFrameTop();
			double width = this.width - this.frame.getFrameWidth();
			double height = this.height - this.frame.getFrameHeight();
			// Do not draw images with an intrinsic size of 0: calculating the scale would divide by zero (Infinity).
			// Skipping them is safer than passing a broken transformation matrix to the backend.
			if (width > 0 && height > 0 && this.image.getWidth() > 0 && this.image.getHeight() > 0) {
				final double[] r = this.fitRect(width, height);
				final double dx = r[0], dy = r[1], drawWidth = r[2], drawHeight = r[3];
				final AffineTransform at = AffineTransform.getTranslateInstance(x + dx, y + dy);
				at.scale(drawWidth / this.image.getWidth(), drawHeight / this.image.getHeight());
				// Clip content that overflows the content box (cover/none, etc.),
				// as browsers do.
				final boolean overflows = dx < -0.001 || dy < -0.001 || dx + drawWidth > width + 0.001
						|| dy + drawHeight > height + 0.001;
				try (final var gcState = gc.begin()) {
					/* NoAndroid begin */
					if (this.frame.frame.border.isRounded()) {
						Shape shape = BorderRenderer.INSTANCE.getBorderShape(this.frame.frame.border, x, y, width,
								height);
						gc.clip(shape);
					}
					/* NoAndroid end */
					if (overflows) {
						gc.clip(new Rectangle2D.Double(x, y, width, height));
					}
					gc.transform(at);
					gc.drawImage(this.image);
				}
			}
		}

		private double[] fitRect(final double width, final double height) {
			return objectFitRect(this.objectFit, this.objectPosition, this.image.getWidth(), this.image.getHeight(),
					width, height);
		}
	}

	/**
	 * SPEC css-images-3 object-fit/object-position: Returns the actual drawing rectangle
	 * {dx, dy, drawWidth, drawHeight} within the content box (width×height).
	 * This single calculation lets drawing ({@code ReplacedBoxDrawable}) and the coordinate transform
	 * for link annotations ({@code AbstractVisitor}) share the same geometry.
	 * The caller must check that the dimensions are positive.
	 */
	public static double[] objectFitRect(final ObjectFitMode objectFit, final Offset objectPosition,
			final double imageWidth, final double imageHeight, final double width, final double height) {
		// Actual drawing dimensions (concrete object size)
		final double drawWidth, drawHeight;
		switch (objectFit) {
		case CONTAIN: {
			double s = Math.min(width / imageWidth, height / imageHeight);
			drawWidth = imageWidth * s;
			drawHeight = imageHeight * s;
			break;
		}
		case COVER: {
			double s = Math.max(width / imageWidth, height / imageHeight);
			drawWidth = imageWidth * s;
			drawHeight = imageHeight * s;
			break;
		}
		case NONE:
			drawWidth = imageWidth;
			drawHeight = imageHeight;
			break;
		case SCALE_DOWN: {
			double s = Math.min(1, Math.min(width / imageWidth, height / imageHeight));
			drawWidth = imageWidth * s;
			drawHeight = imageHeight * s;
			break;
		}
		default:
			drawWidth = width;
			drawHeight = height;
			break;
		}
		// Distribute the free space (which may be negative) according to object-position
		final double dx = positionOffset(objectPosition.getX(), objectPosition.getXRatio(),
				objectPosition.getXType(), width - drawWidth);
		final double dy = positionOffset(objectPosition.getY(), objectPosition.getYRatio(),
				objectPosition.getYType(), height - drawHeight);
		return new double[] { dx, dy, drawWidth, drawHeight };
	}

	private static double positionOffset(double value, double ratio, LengthType type, double space) {
		switch (type) {
		case ABSOLUTE:
			return value;
		case RELATIVE:
			return value * space;
		case MIXED:
			return value + ratio * space;
		default:
			return space / 2;
		}
	}

	/** Whether object-position is the default (50% 50%). Offset is a value class, so compare its components. */
	static boolean isCenterPosition(final Offset pos) {
		return pos.getXType() == LengthType.RELATIVE && pos.getYType() == LengthType.RELATIVE
				&& pos.getX() == .5 && pos.getY() == .5;
	}

	public final void pushGetTextSteps(final StringBuilder textBuff, java.util.Deque<GetTextStep> worklist) {
		String str = this.getReplacedParams().image.getAltString();
		if (str != null) {
			textBuff.append(str);
		}
	}
	
	public void pushTextShapeSteps(PageBox pageBox, TextShapeSink sink, AffineTransform transform, double x, double d,
			java.util.Deque<TextShapeStep> worklist) {
		// ignore
	}

	/**
	 * The clip combined with {@code clip-path} (2026-08-29). Replaced elements do not go through
	 * {@link net.zamasoft.foliojet.layout.box.AbstractContainerBox}, so apply the same rule here:
	 * resolve the shape using the actual reference-box dimensions and intersect it with the existing clip.
	 * Clip the background, frame, and image of {@code <img>} together (css-masking-1).
	 */
	private Shape clipWithClipPath(final Shape clip, final double x, final double y) {
		final ClipPathShape clipPath = this.params.clipPath;
		if (clipPath == null) {
			return clip;
		}
		final double ml = this.frame.margin.left, mt = this.frame.margin.top;
		final double bl = this.frame.frame.border.getLeft().width, bt = this.frame.frame.border.getTop().width;
		final double pl = this.frame.padding.left, pt = this.frame.padding.top;
		final double mw = this.frame.margin.getFrameWidth(), mh = this.frame.margin.getFrameHeight();
		final double bw = this.frame.frame.border.getFrameWidth(), bh = this.frame.frame.border.getFrameHeight();
		final double pw = this.frame.padding.getFrameWidth(), ph = this.frame.padding.getFrameHeight();
		// A replaced element's width/height are the actual margin-box dimensions (innerDraw subtracts the frame
		// to get the content box). Subtract the appropriate amount for each reference box.
		final Rectangle2D.Double ref = switch (clipPath.referenceBox) {
		case MARGIN_BOX -> new Rectangle2D.Double(x, y, this.width, this.height);
		case BORDER_BOX -> new Rectangle2D.Double(x + ml, y + mt, this.width - mw, this.height - mh);
		case PADDING_BOX -> new Rectangle2D.Double(x + ml + bl, y + mt + bt, this.width - mw - bw,
				this.height - mh - bh);
		case CONTENT_BOX -> new Rectangle2D.Double(x + ml + bl + pl, y + mt + bt + pt, this.width - mw - bw - pw,
				this.height - mh - bh - ph);
		};
		final Shape shape = clipPath.resolve(ref.x, ref.y, ref.width, ref.height);
		if (clip == null) {
			return shape;
		}
		final java.awt.geom.Area area = new java.awt.geom.Area(shape);
		area.intersect(new java.awt.geom.Area(clip));
		return area;
	}

	public final void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			java.util.Deque<DrawStep> worklist) {
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			final Drawer newDrawer = new Drawer(this.params, transform);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}

		x += this.offsetX;
		y += this.offsetY;

		transform = this.transform(transform, x, y);
		drawer.adoptTransform(this.params, transform);

		visitor.visitBox(transform, this, drawer, x, y);

		if (this.params.opacity != 0) {
			// Tagged PDF: wrap an image in a Figure element so its alternate
			// text attaches to the figure rather than the enclosing block.
			// No-op for non-image replaced content and when untagged.
			final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);
			clip = this.clipWithClipPath(clip, x, y);
			final Drawable drawable = new ReplacedBoxDrawable(pageBox, clip, this.params.opacity, transform, this.frame,
					this.params.image, this.params.objectFit, this.params.objectPosition, this.getWidth(),
					this.getHeight()).withBlendMode(this.params.blendMode).withFilter(this.params.filter);
			drawer.visitDrawable(drawable, x, y);
			pageBox.endStruct(drawer, this.params.element, structCount, x, y);
		}
	}
}
