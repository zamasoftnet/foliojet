package net.zamasoft.foliojet.layout.box.params;

/**
 * Flexコンテナのパラメータです(Flex F0b、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt Q2。{@link GridParams}と同型の
 * {@code BlockParams}拡張)。
 *
 * <p>
 * 整列のused value解決はFlexBuilder側(alignItemsの既定stretchと
 * itemのalignSelf=AUTOの合成はF3c)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class FlexParams extends BlockParams {
	/** 主軸方向(flex-direction。BlockParams.direction=文字方向と別物)。 */
	public FlexDirection flexDirection = FlexDirection.ROW;

	/** 折り返し(flex-wrap)。 */
	public FlexWrap flexWrap = FlexWrap.NOWRAP;

	/** 行間gap(row-gap——rowコンテナではcross方向)。 */
	public double rowGap;

	/** item間gap(column-gap——rowコンテナでは主軸方向)。 */
	public double columnGap;

	/** 主軸のcontent distribution(justify-content。F3b)。 */
	public FlexContentAlignment justifyContent = FlexContentAlignment.NORMAL;

	/** itemのcross軸整列の既定(align-items。F3c)。 */
	public BoxAlignment alignItems = BoxAlignment.STRETCH;

	/** 行群のcross軸分配(align-content。F3d)。 */
	public FlexContentAlignment alignContent = FlexContentAlignment.NORMAL;
}
