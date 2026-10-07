package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.content.Container;

/**
 * Materials for the continuation fragment of a split block float
 * (introduced 2026-07-24, exclusion area A-3a; the type in design consultation §3).
 *
 * <p>
 * Previously, {@code containerBox.split} immediately constructed the remainder box
 * ({@code splitPage} → {@code recipe.instantiate} ). In A-3a, the cut mutates the preceding fragment
 * ({@code splitPageState}) exactly once, and this type carries the remainder box's construction materials
 * (recipe, state, remainder container, crossExtent). It calls {@link #materialize()} exactly once when
 * attaching to the receiving {@code Floatings} .
 * </p>
 *
 * <p>
 * {@code materialize()} is <b>one-shot</b>: constructing a box has the side effect
 * {@code container.setBox} (reassigning the container's box reference), so running it twice breaks the
 * connections between the preceding fragment and the remainder.
 * A second call throws {@link IllegalStateException} .
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class PreparedFloatFragment {
	private final int serial;
	private final FragmentRecipe recipe;
	private final FragmentState state;
	private final Container remainder;
	private final double crossExtent;
	private boolean materialized = false;

	/**
	 * @param serial float identifier managed by the caller ({@code Floatings}); passed unchanged to the
	 * remainder Floating
	 * @param recipe fragment-box reconstruction recipe (obtained before mutating the preceding fragment)
	 * @param state fragment state (the actual output of {@code splitPageState} , not recomputed)
	 * @param remainder remainder container (content already separated by the cut)
	 * @param crossExtent cross-axis extent at the cut (raw width/height before mutating the preceding fragment,
	 * as in {@code splitPage} )
	 */
	public PreparedFloatFragment(final int serial, final FragmentRecipe recipe, final FragmentState state,
			final Container remainder, final double crossExtent) {
		this.serial = serial;
		this.recipe = recipe;
		this.state = state;
		this.remainder = remainder;
		this.crossExtent = crossExtent;
	}

	public int serial() {
		return this.serial;
	}

	/**
	 * Constructs the continuation-fragment box (one-shot).
	 * Uses the same {@link AbstractBlockBox#continueFragment(FragmentRecipe, FragmentState, Container, double)}
	 * as the former immediate path ({@code splitPage}).
	 *
	 * @return the continuation-fragment box
	 * @throws IllegalStateException on a second call
	 */
	public IFloatBox materialize() {
		if (this.materialized) {
			throw new IllegalStateException("PreparedFloatFragmentは一回しかmaterializeできない: serial=" + this.serial);
		}
		this.materialized = true;
		return (IFloatBox) AbstractBlockBox.continueFragment(this.recipe, this.state, this.remainder,
				this.crossExtent);
	}
}
