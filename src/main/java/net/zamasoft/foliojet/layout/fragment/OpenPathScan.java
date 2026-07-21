package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * {@code flowStack}の収集可能プレフィックススキャンの結果です
 * (2026-07-21新設、M6b Phase B B2)。{@link #capture}は副作用を持たない
 * (統計カウンタへの記録は呼び出し側の責務——{@link OpenPathSnapshot}の
 * 構築とスキャンの実行を、観測(統計)から分離するため)。
 *
 * @param snapshot      全レベルの分類結果
 * @param approvedBoxes 先頭から最初の違反レベルまでの収集可能な祖先
 *                      ({@link BreakPlan}の{@code chain}に相当。
 *                      {@code root}は含まない)
 */
public record OpenPathScan(OpenPathSnapshot snapshot, List<AbstractContainerBox> approvedBoxes) {

	public OpenPathScan {
		approvedBoxes = List.copyOf(approvedBoxes);
	}

	/** 既存の{@link BreakPlan}機構への変換(挙動不変であることの根拠)。 */
	public BreakPlan toBreakPlan() {
		return new BreakPlan(this.approvedBoxes, this.snapshot.depth(), 0);
	}

	/**
	 * flowStack(root含む、破断時点のスナップショット)を分類します。
	 * 各レベルの分類自体({@code ContinuationCapability.classify}の
	 * 呼び出し)はmodeに関わらず一度だけ行うが、収集を許可するか
	 * ({@code capability.supportsPageSplitThrough(mode)})はmodeに依存する
	 * (2026-07-21、M6b Phase B B3。強制改ページでは{@code MULTICOL}を
	 * 収集しない——{@link ContinuationCapability#supportsPageSplitThrough}
	 * 参照)。
	 *
	 * @param boxes flowStackの各レベルのボックス(index 0 = root)。空不可
	 * @param mode  この破断の{@code BreakMode}(自動か強制かで収集許可が変わる)
	 */
	public static OpenPathScan capture(final List<AbstractContainerBox> boxes,
			final net.zamasoft.foliojet.layout.box.content.BreakMode mode) {
		if (boxes.isEmpty()) {
			throw new IllegalArgumentException("open path is empty");
		}

		final WritingMode anchorFlow = boxes.get(0).getBlockParams().flow;
		final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors = new ArrayList<>(boxes.size());
		final List<AbstractContainerBox> approved = new ArrayList<>();
		OpenPathSnapshot.CapabilityBarrier firstBarrier = null;

		for (int i = 0; i < boxes.size(); ++i) {
			final AbstractContainerBox box = boxes.get(i);

			final OpenPathSnapshot.OpenLevelRole role;
			if (i == 0) {
				role = new OpenPathSnapshot.OpenLevelRole.Anchor(OpenPathSnapshot.AnchorKind.PAGE_ROOT);
			} else {
				final ContinuationCapability capability = ContinuationCapability.classify(box, anchorFlow);
				role = new OpenPathSnapshot.OpenLevelRole.Ancestor(capability);
				if (firstBarrier == null) {
					if (capability.supportsPageSplitThrough(mode)) {
						approved.add(box);
					} else {
						firstBarrier = new OpenPathSnapshot.CapabilityBarrier(i, capability);
					}
				}
			}

			descriptors.add(new OpenPathSnapshot.OpenLevelDescriptor(i, box.getClass(), box.getSubtype(),
					box.getBlockParams().flow, box.getColumnCount(), box.getSourceAnchor(), role));
		}

		final OpenPathSnapshot snapshot = new OpenPathSnapshot(anchorFlow, descriptors,
				Optional.ofNullable(firstBarrier));
		return new OpenPathScan(snapshot, approved);
	}
}
