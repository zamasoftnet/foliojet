package net.zamasoft.foliojet.layout.balance;

import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * M6cバランスプローブ候補構築専用の{@link PageGenerator}です
 * (2026-07-24新設、排除域P2のM6c-2——codex設計§1.5)。
 *
 * <p>
 * 候補にはliveの{@code RootBuilder}と{@code PageGenerator}を一切渡さない。
 * UAだけを提供し、ページ生成・描画・liveソースログへのアクセスは
 * 呼ばれたら例外にする(silent fallbackを避ける——候補構築がこれらを
 * 必要とした時点で隔離性の前提が壊れている)。
 * </p>
 */
public final class ProbePageGenerator implements PageGenerator {

	private final UserAgent ua;

	public ProbePageGenerator(final UserAgent ua) {
		this.ua = ua;
	}

	@Override
	public UserAgent getUserAgent() {
		return this.ua;
	}

	@Override
	public PageBreakMode getPageSide() {
		throw new IllegalStateException("balance probe candidate must not consult the page side");
	}

	@Override
	public PageBox nextPage() {
		throw new IllegalStateException("balance probe candidate must not create pages");
	}

	@Override
	public void drawPage(final PageBox page) {
		throw new IllegalStateException("balance probe candidate must not draw pages");
	}

	@Override
	public net.zamasoft.foliojet.layout.fragment.LayoutSource getLayoutSource() {
		// 入力は事前capture済み(BalanceProbeInput)——候補構築中にliveログを
		// 参照してはならない
		throw new IllegalStateException("balance probe candidate must not read the live layout source");
	}

	@Override
	public void compactLayoutSource(final long watermark) {
		throw new IllegalStateException("balance probe candidate must not compact the live layout source");
	}
}
