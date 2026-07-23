package net.zamasoft.foliojet.layout.balance;

import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.builder.impl.ColumnBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RootBuilder;

/**
 * M6cバランスプローブ候補構築専用のrootlessな{@link ColumnBuilder}です
 * (2026-07-24新設、排除域P2のM6c-2——codex設計§1.5)。
 *
 * <p>
 * live builderを親({@code layoutStack})にしない——親はnullで、
 * {@link #getPageContext()}は常にnullを返す。これにより改段は
 * {@code BreakableBuilder.columnBreak()}の既存rootless経路(legacy
 * {@code newColumn()}、型付きresumeなし)を通り、liveの
 * {@code RootBuilder}のresumeスコープ・リース・診断へ一切触れない。
 * </p>
 */
public final class BalanceProbeBuilder extends ColumnBuilder {

	public BalanceProbeBuilder(final MulticolumnBlockBox shell) {
		super(null, shell);
	}

	/**
	 * 常にnull——候補はliveのページ文脈から完全に切断されている
	 * (親の{@code BlockBuilder.getPageContext()}は{@code layoutStack}を
	 * 経由するためnull親ではNPEになる。ここで明示的に切る)。
	 */
	@Override
	public RootBuilder getPageContext() {
		return null;
	}
}
