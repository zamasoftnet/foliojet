package net.zamasoft.foliojet.layout.box.params;

/**
 * Flexコンテナのパラメータです(Flex F0b、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt Q2。{@link GridParams}と同型の
 * {@code BlockParams}拡張)。
 *
 * <p>
 * F1a時点はdirection/wrapのみ(内容配置はまだ単一列の通常フロー縮退)。
 * 整列(justifyContent=FlexContentAlignment/alignItems=BoxAlignment/
 * alignContent)はF3a、rowGap/columnGapはF2cで追加する。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class FlexParams extends BlockParams {
	/** 主軸方向(flex-direction。BlockParams.direction=文字方向と別物)。 */
	public FlexDirection flexDirection = FlexDirection.ROW;

	/** 折り返し(flex-wrap)。 */
	public FlexWrap flexWrap = FlexWrap.NOWRAP;
}
