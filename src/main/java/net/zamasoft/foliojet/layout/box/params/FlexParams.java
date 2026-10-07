package net.zamasoft.foliojet.layout.box.params;

/**
 * Flex container parameters (Flex F0b, 2026-08-02;
 * consult-codex-2026-08-02-flexbox.txt Q2. A {@code BlockParams} extension analogous to {@link GridParams}).
 *
 * <p>
 * FlexBuilder resolves alignment used values (combining the default alignItems stretch with
 * an item's alignSelf=AUTO in F3c).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class FlexParams extends BlockParams {
	/** Main-axis direction (flex-direction; distinct from BlockParams.direction = text direction). */
	public FlexDirection flexDirection = FlexDirection.ROW;

	/** Wrapping (flex-wrap). */
	public FlexWrap flexWrap = FlexWrap.NOWRAP;

	/** Gap between lines (row-gap; cross direction in a row container). */
	public double rowGap;

	/** Gap between items (column-gap; main-axis direction in a row container). */
	public double columnGap;

	/** Main-axis content distribution (justify-content; F3b). */
	public FlexContentAlignment justifyContent = FlexContentAlignment.NORMAL;

	/** Default cross-axis alignment of items (align-items; F3c). */
	public BoxAlignment alignItems = BoxAlignment.STRETCH;

	/** Cross-axis distribution of lines (align-content; F3d). */
	public FlexContentAlignment alignContent = FlexContentAlignment.NORMAL;
}
