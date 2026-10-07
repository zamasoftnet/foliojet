package net.zamasoft.foliojet.layout.fragment;

/**
 * Recipe for reconstructing a fragment box (C1d-B).
 *
 * <p>
 * Created by capturing required values from the preceding fragment at split time; resume builds
 * the continuation fragment box from the fragment state and remainder container. Previously,
 * this construction was a virtual call on the preceding fragment box (splitPage override),
 * so the continuation description retained the mutable old box as a factory.
 * Recipes make continuation independent of box identity.
 * </p>
 *
 * <p>
 * Implementation contract: capture the required values (params/pos references + subtype-specific
 * resolved state), and <b>do not retain a reference to the preceding fragment box (this)</b>.
 * Method references such as {@code prev::splitPage} are forbidden: they keep holding the mutable
 * old box, merely renaming the dependency on prev (external review finding).
 * </p>
 */
public interface FragmentRecipe {
	/**
	 * Constructs a continuation fragment box.
	 *
	 * @param state     fragment state
	 * @param container contents of the continuation fragment
	 * @return continuation fragment box
	 */
	net.zamasoft.foliojet.layout.box.AbstractBlockBox instantiate(FragmentState state,
			net.zamasoft.foliojet.layout.box.content.Container container);
}
