package net.zamasoft.foliojet.layout.util;

import java.util.Set;

import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * Wrapper that adds a path for notifying users of "approximate rendering" to a page's GC
 * (2026-08-29).
 *
 * <p>
 * Blur, conic gradients, repeating gradients, filter, and mix-blend-mode query whether the
 * destination can render them exactly ({@link GC#supports}) at draw time; otherwise they
 * approximate. Approximation is reported via {@link MessageCodes#WARN_APPROXIMATED_RENDERING}
 * (2822), but drawables receive only the destination GC and have no route to the UA.
 * Therefore, {@code PageSequence.drawPage} wraps the page's GC with this, and drawables report
 * through {@link #report} (following the wrappers). To avoid reporting the same approximation
 * repeatedly within a document, reported keys are stored in a set in
 * {@link net.zamasoft.foliojet.ua.UAContext} (whose lifetime is one conversion).
 * </p>
 *
 * <p>
 * Searches by unwrapping {@link DelegatingGC#delegate()} in sequence so it is reachable even
 * from inside other wrappers such as {@link FilterGC}. Group image GCs are also returned
 * wrapped with the same reporting destination.
 * </p>
 */
public final class ApproximationGC extends AbstractDelegatingGC {
	private final UserAgent ua;
	private final String outputType;
	private final Set<String> reported;

	private ApproximationGC(final GC gc, final UserAgent ua, final String outputType, final Set<String> reported) {
		super(gc);
		this.ua = ua;
		this.outputType = outputType;
		this.reported = reported;
	}

	/** Wraps a page's GC. */
	public static GC wrap(final GC gc, final UserAgent ua) {
		if (gc == null || gc instanceof ApproximationGC) {
			return gc;
		}
		return new ApproximationGC(gc, ua, UAProps.OUTPUT_TYPE.getString(ua),
				ua.getUAContext().getReportedApproximations());
	}

	/**
	 * Reports approximate rendering. Does nothing if {@code gc} has no reporting path
	 * (e.g., a bare GC in a unit test).
	 *
	 * @param gc       drawing destination (may be a wrapper)
	 * @param property  CSS property name (literal spelling, such as {@code box-shadow})
	 * @param detailKey message catalog key describing the approximation
	 *                   (e.g., {@code 2822.blur-rings}; text is emitted in the user's language)
	 */
	public static void report(GC gc, final String property, final String detailKey) {
		while (gc != null) {
			if (gc instanceof ApproximationGC a) {
				a.approximated(property, detailKey);
				return;
			}
			gc = gc instanceof DelegatingGC d ? d.delegate() : null;
		}
	}

	private void approximated(final String property, final String detailKey) {
		if (this.reported.add(property + ' ' + detailKey)) {
			this.ua.message(MessageCodes.WARN_APPROXIMATED_RENDERING, property, this.outputType,
					net.zamasoft.foliojet.message.MessageCodeUtils.detail(detailKey));
		}
	}

	@Override
	public GroupImageGC createGroupImage(final double width, final double height) throws GraphicsException {
		return new Group(this.gc.createGroupImage(width, height), this);
	}

	@Override
	public GroupImageGC createFilterGroup(final double width, final double height) throws GraphicsException {
		return new Group(this.gc.createFilterGroup(width, height), this);
	}

	/** Wrapper that gives group image GCs the same reporting path. */
	private static final class Group extends AbstractDelegatingGC implements GroupImageGC {
		private final GroupImageGC group;

		Group(final GroupImageGC group, final ApproximationGC outer) {
			super(new ApproximationGC(group, outer.ua, outer.outputType, outer.reported));
			this.group = group;
		}

		@Override
		public Image finish() throws GraphicsException {
			return this.group.finish();
		}
	}
}
