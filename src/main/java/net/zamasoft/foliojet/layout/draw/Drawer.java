package net.zamasoft.foliojet.layout.draw;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.StructureRef;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * 1つのstacking contextの表示リストです。paint command列と、子の
 * stacking context(z-index順に描く)を保持します。
 *
 * <p>
 * 描画順は「自分のpaint command→子contextを(z, 挿入順)で整列して順に」
 * ——以前は{@code Collections.sort}の安定性へ暗黙に依存していたが、
 * 挿入順を明示の順序キーに昇格した(B-1、2026-07-30。全順序なので
 * 何度整列しても同じ結果になり、dump/drawが同じ順序を共有する)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class Drawer {
	/**
	 * 位置が決められた描画可能ボックス(paint command)です。
	 */
	protected static class PaintCommand {
		private final Drawable drawable;
		private final double x, y;
		/**
		 * PDFのartifact(装飾)として出すか(2026-07-25、救済分割・増分2)。
		 * 通常の描画では常にfalseで、既存の出力は完全に不変です。
		 */
		private final boolean artifact;

		/**
		 * このcommandが属する宣言済み構造要素(B-3、2026-07-30)。
		 * 構造の順序は宣言時(文書順)に確定済みで、描画はこの参照へ
		 * ルーティングするだけ——z順で描いても構造は乱れない。
		 * untaggedでは常にnull。
		 */
		private final StructureRef structRef;

		public PaintCommand(Drawable drawable, double x, double y, boolean artifact, StructureRef structRef) {
			assert !LayoutUtils.isNone(x) : "Undefined x";
			assert !LayoutUtils.isNone(y) : "Undefined y";
			this.drawable = drawable;
			this.x = x;
			this.y = y;
			this.artifact = artifact;
			this.structRef = structRef;
		}

		public boolean isArtifact() {
			return this.artifact;
		}

		public void draw(GC gc) throws GraphicsException {
			// 包み紙(ApproximationGC等)越しでもPDFの構造出力へ辿る
			final PDFPageOutput structOut = (this.structRef != null
					&& net.zamasoft.foliojet.layout.util.DelegatingGC.unwrap(gc) instanceof PDFGC pdfgc
					&& pdfgc.getPDFGraphicsOutput() instanceof PDFPageOutput out) ? out : null;
			if (structOut != null) {
				structOut.beginStructContent(this.structRef);
			}
			try {
				if (this.artifact) {
					// 見た目は同じまま、論理構造には入れない。GCがartifactに
					// 対応しない(untagged PDF等)場合はno-opのscopeが返るため、
					// 出力はartifactでない場合と完全に一致する。
					try (final GC.State scope = gc.beginArtifactScope()) {
						this.drawable.draw(gc, this.x, this.y);
					}
				} else {
					this.drawable.draw(gc, this.x, this.y);
				}
			} finally {
				if (structOut != null) {
					structOut.endStructContent();
				}
			}
		}
	}

	/** 子stacking contextと、その挿入順(同zの順序キー)です。 */
	private static final class StackingContextEntry implements Comparable<StackingContextEntry> {
		private final Drawer drawer;
		private final int insertionOrdinal;

		StackingContextEntry(final Drawer drawer, final int insertionOrdinal) {
			this.drawer = drawer;
			this.insertionOrdinal = insertionOrdinal;
		}

		@Override
		public int compareTo(final StackingContextEntry o) {
			if (this.drawer.z != o.drawer.z) {
				return this.drawer.z < o.drawer.z ? -1 : 1;
			}
			return Integer.compare(this.insertionOrdinal, o.insertionOrdinal);
		}
	}

	protected final int z;
	protected List<PaintCommand> paintCommands = null;
	private List<StackingContextEntry> stackingContexts = null;

	/**
	 * このDrawerへ以後追加される内容をartifactとして出すか
	 * (2026-07-25新設、救済分割・増分2。<b>まだ本番経路からは
	 * 立てられません</b>)。
	 *
	 * <p>
	 * 救済分割の継続断片は「見た目は内容、意味の上では先頭断片に属する」
	 * ため、PDFのartifactとして出してテキスト抽出・読み上げ・構造タグの
	 * 二重化を防ぎます。
	 * </p>
	 */
	protected boolean artifact = false;

	/** {@link #artifactView()}が返す共有ビュー(遅延生成)。 */
	private Drawer artifactView = null;

	/**
	 * 以後追加されるcommandが属する宣言済み構造要素(B-3、2026-07-30)。
	 * 文書順の走査(表示リストの構築)中にPageBox.beginStruct/endStructが
	 * 更新する。untaggedでは常にnull。
	 */
	private StructureRef currentStructRef = null;

	public void setCurrentStructRef(final StructureRef ref) {
		this.currentStructRef = ref;
	}

	public StructureRef getCurrentStructRef() {
		return this.currentStructRef;
	}

	public Drawer(int z) {
		this.z = z;
	}

	/**
	 * このDrawerへ追加される内容がartifactであればtrueを返します。
	 */
	public boolean isArtifact() {
		return this.artifact;
	}

	/**
	 * 「以後の追加はartifact」という印だけが違う、<b>同じ表示リストの
	 * ビュー</b>を返します(2026-07-25新設、救済分割・増分2)。
	 *
	 * <p>
	 * 重要: ラッパーDrawerを<b>子として</b>追加してはいけません。現在の
	 * {@link #draw(GC)}は通常のDrawableを先に描き、子Drawerをz順(同zは
	 * 挿入順)で整列してから描くため、子を1段増やすと既存の重なり順が
	 * 変わってしまいます(答申§3)。このビューは新しいz階層を作らず、
	 * 追加をそのままこのDrawerの表示リストへ流し、PaintCommandに
	 * 印だけを付けます。
	 * </p>
	 *
	 * <p>
	 * ビュー経由で子Drawerが追加された場合は、artifact属性を子へ伝播
	 * します({@link #visitDrawer(Drawer)})。
	 * </p>
	 *
	 * @return artifact印つきの共有ビュー
	 */
	public Drawer artifactView() {
		if (this.artifact) {
			return this;
		}
		if (this.artifactView == null) {
			this.artifactView = new ArtifactView(this);
		}
		return this.artifactView;
	}

	/**
	 * このDrawerと、既に追加済み・今後追加されるすべての内容をartifactに
	 * します(子Drawerへも反復的に伝播)。
	 */
	protected void markArtifact() {
		final Deque<Drawer> work = new ArrayDeque<>();
		work.push(this);
		while (!work.isEmpty()) {
			final Drawer drawer = work.pop();
			if (drawer.artifact) {
				continue;
			}
			drawer.artifact = true;
			if (drawer.paintCommands != null) {
				for (int i = 0; i < drawer.paintCommands.size(); ++i) {
					final PaintCommand command = drawer.paintCommands.get(i);
					if (!command.artifact) {
						// artifact化は構造からも外す(B-3: 構造参照を落とす)
						drawer.paintCommands.set(i, new PaintCommand(command.drawable, command.x, command.y, true, null));
					}
				}
			}
			if (drawer.stackingContexts != null) {
				for (int i = 0; i < drawer.stackingContexts.size(); ++i) {
					work.push(drawer.stackingContexts.get(i).drawer);
				}
			}
		}
	}

	public void visitDrawable(Drawable drawable, double x, double y) {
		this.addDrawable(drawable, x, y, this.artifact);
	}

	/**
	 * 表示リストへDrawableを追加します(artifact印つき)。共有ビューは
	 * これをオーナー側へ委譲します。
	 */
	protected void addDrawable(Drawable drawable, double x, double y, boolean artifact) {
		// isNoneでは番兵の算術結果(NONE+10等)もNaNも素通りする。
		// ここは表示リストに載る全ての位置が通る唯一の隘路なので、
		// 「印刷物としてあり得る範囲か」で弾く(LayoutUtils.isDrawable参照)
		assert LayoutUtils.isDrawable(x) : "描画位置xが異常: " + x + " (" + drawable + ")";
		assert LayoutUtils.isDrawable(y) : "描画位置yが異常: " + y + " (" + drawable + ")";
		if (this.paintCommands == null) {
			this.paintCommands = new ArrayList<PaintCommand>();
		}
		// artifactは論理構造に入れない(構造参照も持たせない)
		this.paintCommands.add(new PaintCommand(drawable, x, y, artifact, artifact ? null : this.currentStructRef));
	}

	public void visitDrawer(Drawer drawer) {
		this.addDrawer(drawer, this.artifact);
	}

	/**
	 * 子stacking contextを追加します。{@code artifact}が真なら子(とその
	 * 子孫)へ属性を伝播します。挿入順が同zの順序キーになります。
	 */
	protected void addDrawer(Drawer drawer, boolean artifact) {
		if (artifact) {
			drawer.markArtifact();
		}
		if (this.stackingContexts == null) {
			this.stackingContexts = new ArrayList<StackingContextEntry>();
		}
		// 子stacking contextへ現在の構造参照を引き継ぐ(B-3)——z順で描かれても
		// 子の内容は文書順の親要素に属する
		drawer.setCurrentStructRef(this.currentStructRef);
		this.stackingContexts.add(new StackingContextEntry(drawer, this.stackingContexts.size()));
	}

	/**
	 * 子contextを(z, 挿入順)で整列して返します。全順序なので冪等です。
	 */
	private List<StackingContextEntry> sortedContexts() {
		Collections.sort(this.stackingContexts);
		return this.stackingContexts;
	}

	public void draw(GC gc) throws GraphicsException {
		// 前順走査(自分のpaint command→z順の子)。反復化(B-1、2026-07-30)
		final Deque<Drawer> work = new ArrayDeque<>();
		work.push(this);
		while (!work.isEmpty()) {
			final Drawer drawer = work.pop();
			if (drawer.paintCommands != null) {
				for (int i = 0; i < drawer.paintCommands.size(); ++i) {
					drawer.paintCommands.get(i).draw(gc);
				}
			}
			if (drawer.stackingContexts != null) {
				final List<StackingContextEntry> sorted = drawer.sortedContexts();
				for (int i = sorted.size() - 1; i >= 0; --i) {
					work.push(sorted.get(i).drawer);
				}
			}
		}
	}

	/**
	 * 表示リストをテキストとしてダンプします。draw()と同じ順序で出力します。
	 */
	public void dump(StringBuilder sb, String indent) {
		// draw()と同じ前順走査の反復化。インデントだけ階層に追随する
		record DumpStep(Drawer drawer, String indent) {
		}
		final Deque<DumpStep> work = new ArrayDeque<>();
		work.push(new DumpStep(this, indent));
		while (!work.isEmpty()) {
			final DumpStep step = work.pop();
			final Drawer drawer = step.drawer;
			sb.append(step.indent).append("drawer z=").append(drawer.z);
			if (drawer.artifact) {
				// 通常の描画では立たないため、既存のgoldenは不変
				sb.append(" artifact");
			}
			sb.append('\n');
			if (drawer.paintCommands != null) {
				for (int i = 0; i < drawer.paintCommands.size(); ++i) {
					final PaintCommand command = drawer.paintCommands.get(i);
					sb.append(step.indent).append("  ")
							.append(String.format(java.util.Locale.ROOT, "x=%.2f y=%.2f ", command.x, command.y));
					if (command.artifact) {
						sb.append("artifact ");
					}
					sb.append(command.drawable.describe()).append(command.drawable.describeClip()).append('\n');
				}
			}
			if (drawer.stackingContexts != null) {
				final List<StackingContextEntry> sorted = drawer.sortedContexts();
				for (int i = sorted.size() - 1; i >= 0; --i) {
					work.push(new DumpStep(sorted.get(i).drawer, step.indent + "  "));
				}
			}
		}
	}

	/**
	 * オーナーと表示リストを共有する、artifact印つきのビューです
	 * (2026-07-25新設、救済分割・増分2)。
	 *
	 * <p>
	 * 自前の表示リストは一切持たず、追加はすべてオーナーへ委譲します。
	 * したがってz順・追加順は共有ビューを使っても変わりません。
	 * {@link #draw(GC)}・{@link #dump}はオーナーが行うため、ビュー自身は
	 * 空のまま(=何も描かない)です。
	 * </p>
	 */
	private static final class ArtifactView extends Drawer {
		private final Drawer owner;

		ArtifactView(final Drawer owner) {
			super(owner.z);
			this.owner = owner;
			this.artifact = true;
		}

		// 構造参照はオーナーと共有(ビュー自身は何も持たない)——
		// もっともartifactの追加は構造参照を持たないので実際には使われない
		@Override
		public StructureRef getCurrentStructRef() {
			return this.owner.getCurrentStructRef();
		}

		@Override
		public Drawer artifactView() {
			return this;
		}

		// markArtifactは基底の早期continueで済む(ビューは常にartifact)。
		// オーナーへ伝播してはいけない——オーナーには通常内容も入る。

		@Override
		protected void addDrawable(final Drawable drawable, final double x, final double y, final boolean artifact) {
			this.owner.addDrawable(drawable, x, y, true);
		}

		@Override
		protected void addDrawer(final Drawer drawer, final boolean artifact) {
			this.owner.addDrawer(drawer, true);
		}
	}
}
