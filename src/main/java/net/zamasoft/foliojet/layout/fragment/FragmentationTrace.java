package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1回の改ページ/改段判定中に生じた{@link FragmentationEvent}の列です
 * (2026-07-21新設、M6d-0.5)。{@link FragmentationAudit#current()}経由で
 * 取得し、既存の分岐処理に割り込まない副作用として{@link #record}する。
 */
public final class FragmentationTrace {
	private final List<FragmentationEvent> events = new ArrayList<FragmentationEvent>();

	FragmentationTrace() {
	}

	public void record(final FragmentationEvent event) {
		this.events.add(event);
	}

	/** 記録済みイベント(発生順、変更不可)を返します。 */
	public List<FragmentationEvent> events() {
		return Collections.unmodifiableList(this.events);
	}
}
