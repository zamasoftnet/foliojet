package net.zamasoft.foliojet.layout.box.params;

/**
 * Flexコンテナのパラメータです(Flex F0b、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt Q2。{@link GridParams}と同型の
 * {@code BlockParams}拡張)。
 *
 * <p>
 * F0時点は骨格のみ(内容配置は単一列の通常フロー縮退)。
 * direction/wrap/整列/gapのフィールドはF1a以降で追加する——
 * 答申の推奨形: direction/wrap/justifyContent(FlexContentAlignment)/
 * alignItems(BoxAlignment)/alignContent/rowGap/columnGap。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class FlexParams extends BlockParams {
}
