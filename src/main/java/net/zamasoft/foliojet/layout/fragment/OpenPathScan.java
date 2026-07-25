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
	 * PAGE継続用: flowStack(root含む、破断時点のスナップショット)を
	 * 分類します。各レベルの分類自体({@code ContinuationCapability
	 * .classify}の呼び出し)はmodeに関わらず一度だけ行うが、収集を許可
	 * するか({@code capability.supportsPageSplitThrough(mode)})はmodeに
	 * 依存する(2026-07-21、M6b Phase B B3。強制改ページでは{@code
	 * MULTICOL}を収集しない——{@link ContinuationCapability
	 * #supportsPageSplitThrough}参照)。
	 *
	 * @param boxes flowStackの各レベルのボックス(index 0 = root)。空不可
	 * @param mode  この破断の{@code BreakMode}(自動か強制かで収集許可が変わる)
	 */
	public static OpenPathScan capture(final List<AbstractContainerBox> boxes,
			final net.zamasoft.foliojet.layout.box.content.BreakMode mode) {
		return scan(boxes, OpenPathSnapshot.AnchorKind.PAGE_ROOT, mode, ContinuationCapability::supportsPageSplitThrough);
	}

	/**
	 * COLUMN継続用: 段組ownerから相対的に数えたopen pathを分類します
	 * (2026-07-21新設、M6b Phase B B4。2026-07-25時点で
	 * {@code BreakableBuilder}から使われている)。index 0はowner自身で
	 * あり、{@link ContinuationCapability#classify}は呼ばない(owner自体
	 * は{@code MULTICOL}として分類しない——owner内側にさらに現れる別の
	 * 段組だけが{@code MULTICOL}になる)。anchorの書字方向はowner自身の
	 * 書字方向を使う(ChatGPT Pro相談、
	 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b4-column-target.md
	 * 参照)。
	 *
	 * @param boxes owner(index 0)+その内側で現在開いている子孫。空不可
	 * @param mode  この破断のBreakMode
	 */
	public static OpenPathScan captureColumn(final List<AbstractContainerBox> boxes,
			final net.zamasoft.foliojet.layout.box.content.BreakMode mode) {
		return scan(boxes, OpenPathSnapshot.AnchorKind.COLUMN_OWNER, mode,
				ContinuationCapability::supportsColumnSplitThrough);
	}

	private interface Admission {
		boolean supports(ContinuationCapability capability, net.zamasoft.foliojet.layout.box.content.BreakMode mode);
	}

	private static OpenPathScan scan(final List<AbstractContainerBox> boxes,
			final OpenPathSnapshot.AnchorKind anchorKind, final net.zamasoft.foliojet.layout.box.content.BreakMode mode,
			final Admission admission) {
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
				role = new OpenPathSnapshot.OpenLevelRole.Anchor(anchorKind);
			} else {
				final ContinuationCapability capability = ContinuationCapability.classify(box, anchorFlow);
				role = new OpenPathSnapshot.OpenLevelRole.Ancestor(capability);
				if (firstBarrier == null) {
					if (admission.supports(capability, mode)) {
						approved.add(box);
					} else {
						firstBarrier = new OpenPathSnapshot.CapabilityBarrier(i, capability);
					}
				}
			}

			descriptors.add(new OpenPathSnapshot.OpenLevelDescriptor(i, box.getClass(),
					box.getBlockParams().flow, box.getColumnCount(), box.getSourceAnchor(), role));
		}

		final OpenPathSnapshot snapshot = new OpenPathSnapshot(anchorFlow, descriptors,
				Optional.ofNullable(firstBarrier));
		return new OpenPathScan(snapshot, approved);
	}
}
