package net.zamasoft.foliojet.ua.impl;

import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.layout.imposition.Imposition;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;

/**
 * Output-side entry point for creating an imposition ({@link Imposition}) from output settings
 * (2026-09-02).
 *
 * <p>
 * Previously in {@code layout.util.LayoutUtils}, creating a reverse dependency where layout
 * knew concrete ua.impl classes such as {@code SinglePageImposition} (design review §1-1).
 * Moved here because imposition selection is an output (UA) concern.
 * </p>
 */
public final class Impositions {
	private Impositions() {
	}

	/**
	 * Creates an imposition for the current processing stage and output settings.
	 * Intermediate passes and structure scans retain only logical page progression
	 * and never start a GC or serializer.
	 */
	public static Imposition createImposition(final UserAgent ua) {
		// Several documents in one output (EPUB spine items) share the pass's imposition (2026-10-08)
		final Imposition shared = ua.getPassContext().getSharedImposition();
		if (shared != null) {
			return shared;
		}
		if (ua.isMeasurePass() || ua.isStructureScanPass()) {
			return new NopImposition(ua);
		}
		final int nUp = UAProps.OUTPUT_N_UP.getInteger(ua);
		// Values outside 1–256 warn on read and use the default of 1
		if (nUp > 1) {
			return new NUpImposition(ua, nUp, UAProps.OUTPUT_N_UP_ORDER.get(ua));
		}
		return new SinglePageImposition(ua);
	}

	/**
	 * Finishes the imposition at the end of a document, unless it is shared by the documents of this pass: the
	 * owner of a shared imposition finishes it once after the last document (2026-10-08). Finishing closes a partly
	 * filled n-up sheet, so finishing per EPUB item started a new sheet for every item.
	 */
	public static void finishDocument(final UserAgent ua, final Imposition imposition)
			throws net.zamasoft.pdfg2d.gc.GraphicsException {
		if (ua.getPassContext().getSharedImposition() != imposition) {
			imposition.finish();
		}
	}

	public static void setupImposition(final UserAgent ua, final Imposition imposition) {
		imposition.setAutoRotate(UAProps.OUTPUT_AUTO_ROTATE.get(ua));
		imposition.setAlign(UAProps.OUTPUT_FIT_TO_PAPER.get(ua));

		// Left/right trim margins
		{
			String s = UAProps.OUTPUT_HTRIM.getString(ua);
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
			if (length != null) {
				double l = length.getLength();
				imposition.setTrims(imposition.getTrimTop(), l, imposition.getTrimBottom(), l);
			} else {
				ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_HTRIM.name, s);
			}
		}
		// Top/bottom trim margins
		{
			String s = UAProps.OUTPUT_VTRIM.getString(ua);
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
			if (length != null) {
				double l = length.getLength();
				imposition.setTrims(l, imposition.getTrimRight(), l, imposition.getTrimLeft());
			} else {
				ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_VTRIM.name, s);
			}
		}
		{
			double[] trims;
			String s = UAProps.OUTPUT_TRIMS.getString(ua);
			if (s != null) {
				String[] values = s.trim().split("[\\s]+");
				if (values.length <= 0 || values.length > 4) {
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_TRIMS.name, s);
					trims = null;
				} else {
					trims = new double[values.length];
					for (int i = 0; i < values.length; ++i) {
						AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, values[i]);
						if (length != null) {
							trims[i] = length.getLength();
						} else {
							ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_TRIMS.name, s);
							trims = null;
							break;
						}
					}
				}
				// Keep trim margins on parse failure (warned). Until 2026-10-05 reading a null length crashed conversion
				switch (trims == null ? 0 : trims.length) {
				case 0:
					break;
				case 1:
					imposition.setTrims(trims[0], trims[0], trims[0], trims[0]);
					break;
				case 2:
					imposition.setTrims(trims[0], trims[1], trims[0], trims[1]);
					break;
				case 3:
					imposition.setTrims(trims[0], trims[1], trims[2], trims[1]);
					break;
				case 4:
					imposition.setTrims(trims[0], trims[1], trims[2], trims[3]);
					break;
				}
			}
		}

		// Crop marks
		switch (UAProps.OUTPUT_MARKS.get(ua)) {
		case NONE:
			imposition.setTrims(0, 0, 0, 0);
			imposition.setCuttingMargin(0);
			imposition.setNote(null);
			break;
		case CROP:
			imposition.setCrop(true);
			imposition.setNote("page {0}");
			break;
		case CROSS:
			imposition.setCross(true);
			imposition.setNote("page {0}");
			break;
		case BOTH:
			imposition.setCrop(true);
			imposition.setCross(true);
			imposition.setNote("page {0}");
			break;
		case HIDDEN:
			imposition.setNote("page {0}");
			break;
		default:
			throw new IllegalStateException();
		}
		// Trim position for data including bleed (2026-08-29, user report B-3).
		// Place **after** the branch that sets bleed to zero when output.marks is none
		// because the outer band is itself bleed, so restore it here
		{
			String s = UAProps.OUTPUT_TRIM_INSET.getString(ua);
			if (s != null) {
				AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
				if (length != null && length.getLength() >= 0) {
					final double l = length.getLength();
					imposition.setTrimInset(l);
					imposition.setCuttingMargin(l);
				} else {
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_TRIM_INSET.name, s);
				}
			}
		}

		imposition.setClip(UAProps.OUTPUT_CLIP.getBoolean(ua));

		// Spine
		{
			String s = UAProps.OUTPUT_MARKS_SPINE_WIDTH.getString(ua);
			if (s != null) {
				AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
				if (length != null) {
					double l = length.getLength();
					imposition.setSpineWidth(l);
					if (imposition.getNote() != null) {
						imposition.setNote(imposition.getNote() + " / spine " + s);
					}
				} else {
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_MARKS_SPINE_WIDTH.name, s);
				}
			}
		}

		{
			// Paper width
			String s = UAProps.OUTPUT_PAPER_WIDTH.getString(ua);
			if (s != null) {
				AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
				if (length != null) {
					double l = length.getLength();
					imposition.setPaperWidth(l);
				} else {
					imposition.fitPaperWidth();
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAPER_WIDTH.name, s);
				}
			} else {
				imposition.fitPaperWidth();
			}
		}

		{
			// Paper height
			String s = UAProps.OUTPUT_PAPER_HEIGHT.getString(ua);
			if (s != null) {
				AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
				if (length != null) {
					double l = length.getLength();
					imposition.setPaperHeight(l);
				} else {
					imposition.fitPaperHeight();
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAPER_HEIGHT.name, s);
				}
			} else {
				imposition.fitPaperHeight();
			}
		}
	}
}
