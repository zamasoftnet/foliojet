package net.zamasoft.foliojet.layout.draw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * 描画可能なオブジェクトを描画します。
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: Drawer.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Drawer implements Comparable<Drawer> {
	/**
	 * 位置が決められた描画可能ボックスです。
	 *
	 * @author MIYABE Tatsuhiko
	 * @version $Id: Drawer.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	protected static class ArrangedDrawable {
		private final Drawable drawable;
		private final double x, y;
		/**
		 * PDFのartifact(装飾)として出すか(2026-07-25、救済分割・増分2)。
		 * 通常の描画では常にfalseで、既存の出力は完全に不変です。
		 */
		private final boolean artifact;

		public ArrangedDrawable(Drawable drawable, double x, double y) {
			this(drawable, x, y, false);
		}

		public ArrangedDrawable(Drawable drawable, double x, double y, boolean artifact) {
			assert !LayoutUtils.isNone(x) : "Undefined x";
			assert !LayoutUtils.isNone(y) : "Undefined y";
			this.drawable = drawable;
			this.x = x;
			this.y = y;
			this.artifact = artifact;
		}

		public boolean isArtifact() {
			return this.artifact;
		}

		public void draw(GC gc) throws GraphicsException {
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
		}
	}

	protected final int z;
	protected List<ArrangedDrawable> drawables = null;
	protected List<Drawer> drawers = null;

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
	 * {@link #draw(GC)}は通常のDrawableを先に描き、子Drawerをz順で
	 * 安定ソートしてから描くため、子を1段増やすと既存の重なり順が
	 * 変わってしまいます(答申§3)。このビューは新しいz階層を作らず、
	 * 追加をそのままこのDrawerの表示リストへ流し、ArrangedDrawableに
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
	 * します(子Drawerへも再帰的に伝播)。
	 */
	protected void markArtifact() {
		if (this.artifact) {
			return;
		}
		this.artifact = true;
		if (this.drawables != null) {
			for (int i = 0; i < this.drawables.size(); ++i) {
				final ArrangedDrawable arranged = this.drawables.get(i);
				if (!arranged.artifact) {
					this.drawables.set(i, new ArrangedDrawable(arranged.drawable, arranged.x, arranged.y, true));
				}
			}
		}
		if (this.drawers != null) {
			for (int i = 0; i < this.drawers.size(); ++i) {
				this.drawers.get(i).markArtifact();
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
		if (this.drawables == null) {
			this.drawables = new ArrayList<ArrangedDrawable>();
		}
		this.drawables.add(new ArrangedDrawable(drawable, x, y, artifact));
	}

	public void visitDrawer(Drawer drawer) {
		this.addDrawer(drawer, this.artifact);
	}

	/**
	 * 子Drawerを追加します。{@code artifact}が真なら子(とその子孫)へ
	 * 属性を伝播します。
	 */
	protected void addDrawer(Drawer drawer, boolean artifact) {
		if (artifact) {
			drawer.markArtifact();
		}
		if (this.drawers == null) {
			this.drawers = new ArrayList<Drawer>();
		}
		this.drawers.add(drawer);
	}

	public void draw(GC gc) throws GraphicsException {
		if (this.drawables != null) {
			for (int i = 0; i < this.drawables.size(); ++i) {
				ArrangedDrawable drawable = (ArrangedDrawable) this.drawables.get(i);
				drawable.draw(gc);
			}
		}

		if (this.drawers != null) {
			// z-indexによるソート
			// 原則ドキュメントの開始順に整列するため、順序が安定したソートアルゴリズムを使用すること
			Collections.sort(this.drawers);
			for (int i = 0; i < this.drawers.size(); ++i) {
				Drawer drawer = (Drawer) this.drawers.get(i);
				drawer.draw(gc);
			}
		}
	}

	public int compareTo(Drawer o) {
		Drawer a1 = this;
		Drawer a2 = (Drawer) o;
		long s1 = a1.z;
		long s2 = a2.z;
		return s1 < s2 ? -1 : s1 > s2 ? 1 : 0;
	}

	/**
	 * 表示リストをテキストとしてダンプします。draw()と同じ順序で出力します。
	 */
	public void dump(StringBuilder sb, String indent) {
		sb.append(indent).append("drawer z=").append(this.z);
		if (this.artifact) {
			// 通常の描画では立たないため、既存のgoldenは不変
			sb.append(" artifact");
		}
		sb.append('\n');
		if (this.drawables != null) {
			for (int i = 0; i < this.drawables.size(); ++i) {
				ArrangedDrawable drawable = this.drawables.get(i);
				sb.append(indent).append("  ")
						.append(String.format(java.util.Locale.ROOT, "x=%.2f y=%.2f ", drawable.x, drawable.y));
				if (drawable.artifact) {
					sb.append("artifact ");
				}
				sb.append(drawable.drawable.describe()).append('\n');
			}
		}
		if (this.drawers != null) {
			Collections.sort(this.drawers);
			for (int i = 0; i < this.drawers.size(); ++i) {
				this.drawers.get(i).dump(sb, indent + "  ");
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

		@Override
		public Drawer artifactView() {
			return this;
		}

		// markArtifactは基底の早期returnで済む(ビューは常にartifact)。
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
