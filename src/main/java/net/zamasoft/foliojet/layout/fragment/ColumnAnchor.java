package net.zamasoft.foliojet.layout.fragment;

import java.util.List;

import net.zamasoft.foliojet.layout.box.content.Container;

/**
 * COLUMN継続のowner直下の残余です(2026-07-21新設、M6b Phase B B4、
 * 未配線・型のみ)。PAGE継続のroot fragmentと異なり、ownerボックス自体は
 * fragment再構成されない({@code AbstractContainerBox.newColumn()}が
 * 同一インスタンスへ新しい空Containerを追加するだけ)ため、
 * 継続フレームではなくこの専用型で表す(ChatGPT Pro相談、
 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b4-column-target.md
 * 参照)。
 *
 * @param remainder   owner直下の閉部分木の再生範囲を持つコンテナ
 * @param prefixItems remainderへ適用する再生範囲(closedとして再生)
 */
public record ColumnAnchor(Container remainder, List<Continuation.SourceRange> prefixItems) {

	public ColumnAnchor {
		prefixItems = List.copyOf(prefixItems);
	}
}
