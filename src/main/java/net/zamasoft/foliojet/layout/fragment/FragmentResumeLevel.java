package net.zamasoft.foliojet.layout.fragment;

import java.util.List;

import net.zamasoft.foliojet.layout.box.content.Container;

/**
 * first-classにコンパイルされたfragment継続の1レベルです(2026-07-21新設、
 * M6b Phase B B4。旧{@code ResumeProgram.ResumeLevel}をPAGE/COLUMN共有型
 * として抽出した)。{@code Continuation.ContinuationFrame}と1対1対応するが、
 * tailを持たない(tailは{@link PageResumeProgram#tail()}/
 * {@link ColumnResumeProgram#tail()}へ集約する)。
 */
public record FragmentResumeLevel(int openPathIndex, FragmentRecipe recipe, FragmentState state, Container remainder,
		double crossExtent, List<Continuation.SourceRange> prefixItems,
		OpenPathSnapshot.OpenLevelDescriptor descriptor) {

	public FragmentResumeLevel {
		prefixItems = List.copyOf(prefixItems);
	}
}
