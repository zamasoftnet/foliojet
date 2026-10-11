package net.zamasoft.foliojet.ua;

import java.util.HashMap;
import java.util.Map;

/**
 * Element facts for {@code @container} queries (2026-08-15, stage 4; development record §2). Like
 * {@link SelectorFacts}, this uses {@code CSSElement.elementKey} as the key.
 *
 * <p>
 * Both are written during style resolution, when the container's declarations are finalized and before its
 * descendants are styled: its {@code container-type: inline-size} and {@code container-name}, and its {@link Size},
 * the inline size estimated from the styles ({@code ContainerSizeEstimate}, 2026-10-11). Until 2026-10-11 the size was the one
 * measured in the previous pass, so no query held in one pass, nested containers took a pass per level and the
 * passes had to be checked for a fixed point.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ContainerFacts {
	private Map<Long, String[]> containerNames;

	private Map<Long, Size> sizes;

	/**
	 * Call once at the start of STRUCTURE_SCAN, as {@link SelectorFacts#reset()}: later passes overwrite the facts with
	 * the same values.
	 */
	public void reset() {
		this.containerNames = null;
		this.sizes = null;
	}

	/**
	 * Records that the element with elementKey is a {@code container-type: inline-size} query container.
	 * Does not record {@code normal} (non-container), saving space and matching the default false
	 * returned by {@link #isInlineSizeContainer}.
	 * Does not record {@code container-type: size}, which is outside the first stage's scope
	 * (design §4, "exclude container-type: size from the initial implementation").
	 */
	public void setInlineSizeContainer(long elementKey, String[] names) {
		if (this.containerNames == null) {
			this.containerNames = new HashMap<Long, String[]>();
		}
		this.containerNames.put(elementKey, names);
	}

	public boolean isInlineSizeContainer(long elementKey) {
		return this.containerNames != null && this.containerNames.containsKey(elementKey);
	}

	/** The container's {@code container-name} (an empty array if unnamed). */
	public String[] getContainerNames(long elementKey) {
		if (this.containerNames == null) {
			return EMPTY_NAMES;
		}
		String[] names = this.containerNames.get(elementKey);
		return names != null ? names : EMPTY_NAMES;
	}

	private static final String[] EMPTY_NAMES = new String[0];

	/**
	 * A query container's inline size estimated from the styles (pt, {@code NaN} if not known), and the font sizes (pt)
	 * em and rem in its queries stand for: its own and the root's, as in Chrome (2026-10-11).
	 */
	public record Size(double inline, double em, double rem) {
	}

	private static final Size NOT_KNOWN = new Size(Double.NaN, Double.NaN, Double.NaN);

	public void setSize(long elementKey, Size size) {
		if (this.sizes == null) {
			this.sizes = new HashMap<Long, Size>();
		}
		this.sizes.put(elementKey, size);
	}

	/** The container's {@link Size}, all {@code NaN} if not recorded. */
	public Size getSize(long elementKey) {
		final Size size = this.sizes == null ? null : this.sizes.get(elementKey);
		return size != null ? size : NOT_KNOWN;
	}

	/** The container's estimated inline size (pt). {@code Double.NaN} if not known. */
	public double getInlineSize(long elementKey) {
		return this.getSize(elementKey).inline();
	}
}
