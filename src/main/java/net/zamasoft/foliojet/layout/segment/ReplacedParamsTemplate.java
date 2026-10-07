package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.ObjectFitMode;
import net.zamasoft.foliojet.layout.box.params.Offset;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * A template that freezes the contents of {@link ReplacedParams}
 * (replaced-element params used by all four {@link ReplacedRecipe} variants)
 * and materializes an independent, fresh {@code ReplacedParams} on each call
 * (introduced 2026-07-22, M6d-A replaced-element support).
 *
 * <p>
 * {@code ReplacedParams} directly extends {@code AbstractTextParams} (like {@code InlineParams} ),
 * so ancestor fields are delegated to {@link TextParamsFields} .
 * Its own fields {@code size} /{@code minSize}/{@code maxSize} ({@code Dimension}),
 * {@code boxSizing} (enum), and {@code frame} ({@code RectFrame}) need no copying
 * for the same reason as {@link BlockParamsFields} :
 * existing implementations are effectively immutable value classes with only final fields.
 * {@code lineHeight} is a primitive and copied unchanged.
 * </p>
 *
 * <p>
 * Only {@code image} ({@code Image}) differs.
 * Ordinary images (loaded from URLs, etc.) are immutable, reentrant shared resources,
 * but {@link ReplacedBoxImage} implementations (currently only {@code BarcodeImage} ) write a
 * back-reference to the live box into themselves through
 * {@code setReplacedBox(box, width, height)} , called by {@code AbstractReplacedBox.calculateSize()} .
 * They have unshareable state that mutates on every layout.
 * The old implementation (through E-6 increment 3b-5) detected this and failed closed,
 * returning {@code Optional.empty()} to produce a Barrier.
 * E-6 increment 3b-6 changed this to a total function based on
 * {@link ReplacedBoxImage#duplicate()} (independent copies):
 * {@link #freeze} freezes a copy at recording time to sever shared state with the live object,
 * and {@link #materialize} distributes further copies on each call to sever shared state between replays.
 * This preserves M6d-A's most important contract:
 * multiple materializations from a frozen template are mutually independent.
 * The reason for producing a Barrier is thus eliminated, and replaced-element freezing has no failure variant.
 * </p>
 *
 * <p>
 * As the sole exception to Stage2 (2026-07-22, which replaced other Template/Fields types with immutable
 * records),
 * this type remains an ordinary final class.
 * The canonical constructor of a {@code public record} must have the same public visibility as the record.
 * Converting this type to a record would let anyone bypass the image normalization above:
 * {@code freeze()} is the only construction path, guaranteeing that a live {@code ReplacedBoxImage}
 * cannot be stored directly in a frozen value (JLS 8.10.4.2).
 * This safety-critical M6d-A contract takes precedence over uniform use of records.
 * </p>
 */
public final class ReplacedParamsTemplate {
	private final TextParamsFields common;
	private final Dimension size;
	private final Dimension minSize;
	private final Dimension maxSize;
	private final BoxSizingMode boxSizing;
	private final ObjectFitMode objectFit;
	private final Offset objectPosition;
	private final RectFrame frame;
	private final double lineHeight;
	private final Image image;
	private final double aspectRatio;
	private final boolean aspectRatioAuto;

	private ReplacedParamsTemplate(final TextParamsFields common, final Dimension size, final Dimension minSize,
			final Dimension maxSize, final BoxSizingMode boxSizing, final ObjectFitMode objectFit,
			final Offset objectPosition, final RectFrame frame, final double lineHeight, final Image image,
			final double aspectRatio, final boolean aspectRatioAuto) {
		this.common = common;
		this.size = size;
		this.minSize = minSize;
		this.maxSize = maxSize;
		this.boxSizing = boxSizing;
		this.objectFit = objectFit;
		this.objectPosition = objectPosition;
		this.frame = frame;
		this.lineHeight = lineHeight;
		this.image = image;
		this.aspectRatio = aspectRatio;
		this.aspectRatioAuto = aspectRatioAuto;
	}

	/**
	 * Freezes live params (made a total function in E-6 increment 3b-6; no failure variant).
	 * If {@code source.image} is a {@link ReplacedBoxImage} (an unshareable image with a back-reference),
	 * freezes an independent copy from {@link ReplacedBoxImage#duplicate()} .
	 * No shared state remains between the live box's image state and the template.
	 */
	public static ReplacedParamsTemplate freeze(final ReplacedParams source) {
		final Image image = source.image instanceof ReplacedBoxImage unsafe ? unsafe.duplicate() : source.image;
		return new ReplacedParamsTemplate(TextParamsFields.freeze(source), source.size, source.minSize,
				source.maxSize, source.boxSizing, source.objectFit, source.objectPosition, source.frame,
				source.lineHeight, image, source.aspectRatio, source.aspectRatioAuto);
	}

	/** Whether width, height, or their bounds still contain percentage sizes referencing the containing block. */
	public boolean hasRelativeSize() {
		return hasRelativeSize(this.size) || hasRelativeSize(this.minSize) || hasRelativeSize(this.maxSize);
	}

	private static boolean hasRelativeSize(final Dimension size) {
		return size.getWidthType() == LengthType.RELATIVE || size.getWidthType() == LengthType.MIXED
				|| size.getHeightType() == LengthType.RELATIVE || size.getHeightType() == LengthType.MIXED;
	}

	/** Returns a fresh {@code ReplacedParams} on each call (multiple calls do not affect one another). */
	public ReplacedParams materialize() {
		final ReplacedParams params = new ReplacedParams();
		this.common.materializeInto(params);
		params.size = this.size;
		params.minSize = this.minSize;
		params.maxSize = this.maxSize;
		params.boxSizing = this.boxSizing;
		params.objectFit = this.objectFit;
		params.objectPosition = this.objectPosition;
		params.frame = this.frame;
		params.lineHeight = this.lineHeight;
		params.aspectRatio = this.aspectRatio; // 2026-08-29
		params.aspectRatioAuto = this.aspectRatioAuto;
		// Distribute a copy of ReplacedBoxImage on each materialize call. Sharing the frozen copy
		// itself would make multiple replay boxes compete
		// for the setReplacedBox back-reference, violating the contract that
		// materialization results are mutually independent (E-6 increment 3b-6).
		params.image = this.image instanceof ReplacedBoxImage frozen ? frozen.duplicate() : this.image;
		return params;
	}
}
