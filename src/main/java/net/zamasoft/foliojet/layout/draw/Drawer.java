package net.zamasoft.foliojet.layout.draw;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.zamasoft.foliojet.css.value.css3.FilterValue;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.util.ApproximationGC;
import net.zamasoft.foliojet.layout.util.DelegatingGC;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.GroupEffects;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.StructureRef;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;

/**
 * 1つのstacking contextの表示リストです。paint command列と、子の
 * stacking context(z-index順に描く)を保持します。
 *
 * <p>
 * 描画順は「負のz-indexの子context→自分のpaint command→0以上の
 * 子context」です(CSS 2.1 Appendix E)。子context内は(z, 挿入順)で
 * 整列します。以前は{@code Collections.sort}の安定性へ暗黙に依存していたが、
 * 挿入順を明示の順序キーに昇格した(B-1、2026-07-30。全順序なので
 * 何度整列しても同じ結果になり、dump/drawが同じ順序を共有する)。
 * Appendix Eがさらに分ける親自身の背景とインライン内容の間への配置は
 * paint commandの分類が必要なため、この変更の範囲外です。
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

		public void draw(GC gc, final Map<Long, LineTextScope> lineScopes) throws GraphicsException {
			if (!this.artifact && this.drawable instanceof LogicalTextDrawable text
					&& text.getLogicalLineEmission() != null) {
				text.drawLogicalText(gc, this.x, this.y, this.structRef,
						lineScopes.get(text.getLogicalLineEmission().lineId()));
				return;
			}
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

	private static final class LinePlan {
		private final net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission emission;
		private int count;
		private int firstPosition = -1;
		private int lastPosition = -1;
		private Object stream;
		private boolean suppressed;

		LinePlan(final net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission emission) {
			this.emission = emission;
		}

		void add(final int position, final Object stream, final boolean rasterized) {
			if (this.count++ == 0) {
				this.firstPosition = position;
				this.stream = stream;
			} else if (this.stream != stream) {
				this.suppressed = true;
			}
			this.lastPosition = position;
			this.suppressed |= rasterized;
		}

		LineTextScope build() {
			// Marked-content scopes cannot cross another line's text or an output-stream boundary.
			final boolean interleaved = this.lastPosition - this.firstPosition + 1 != this.count;
			return new LineTextScope(this.emission, this.count, this.suppressed || interleaved);
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

	/** filter層を要素座標へ置くための、検証済みの変換と寸法です(2026-09-03)。 */
	private record FilterPlacement(AffineTransform outerTransform, AffineTransform groupTransform, double width,
			double height) {
		private static final double MAX_PAGE_EXTENT = 64;

		static FilterPlacement create(final AffineTransform transform, final double pageWidth,
				final double pageHeight) {
			final FilterPlacement page = page(pageWidth, pageHeight);
			if (!Double.isFinite(pageWidth) || !Double.isFinite(pageHeight) || pageWidth <= 0 || pageHeight <= 0
					|| transform == null || !isFinite(transform)) {
				return page;
			}
			try {
				final AffineTransform inverse = transform.createInverse();
				if (!isFinite(inverse)) {
					return page;
				}
				final Rectangle2D bounds = inverse
						.createTransformedShape(new Rectangle2D.Double(0, 0, pageWidth, pageHeight)).getBounds2D();
				if (!isFinite(bounds) || bounds.getWidth() <= 0 || bounds.getHeight() <= 0
						|| bounds.getWidth() / pageWidth > MAX_PAGE_EXTENT
						|| bounds.getHeight() / pageHeight > MAX_PAGE_EXTENT) {
					return page;
				}

				final AffineTransform outerTransform = new AffineTransform(transform);
				outerTransform.translate(bounds.getX(), bounds.getY());
				final AffineTransform groupTransform = AffineTransform.getTranslateInstance(-bounds.getX(),
						-bounds.getY());
				groupTransform.concatenate(inverse);
				if (!isFinite(outerTransform) || !isFinite(groupTransform)) {
					return page;
				}
				return new FilterPlacement(outerTransform, groupTransform, bounds.getWidth(), bounds.getHeight());
			} catch (final NoninvertibleTransformException e) {
				return page;
			}
		}

		private static FilterPlacement page(final double pageWidth, final double pageHeight) {
			return new FilterPlacement(new AffineTransform(), new AffineTransform(), pageWidth, pageHeight);
		}

		private static boolean isFinite(final AffineTransform transform) {
			final double[] matrix = new double[6];
			transform.getMatrix(matrix);
			for (final double value : matrix) {
				if (!Double.isFinite(value)) {
					return false;
				}
			}
			return true;
		}

		private static boolean isFinite(final Rectangle2D bounds) {
			return Double.isFinite(bounds.getMinX()) && Double.isFinite(bounds.getMinY())
					&& Double.isFinite(bounds.getMaxX()) && Double.isFinite(bounds.getMaxY())
					&& Double.isFinite(bounds.getWidth()) && Double.isFinite(bounds.getHeight());
		}
	}

	protected final int z;
	protected List<PaintCommand> paintCommands = null;

	/**
	 * 自分の背景・枠(stacking context の根の箱が最初に積む装飾)が終わる
	 * paint command の位置です。負の z-index の子 context は、CSS 2.1
	 * Appendix E ③のとおり**この位置の後・残りの内容の前**に描きます。
	 * 印が無い(0)場合は負の子を自分の command 全部より先に描く。
	 */
	private int ownDecorationEnd = 0;

	/** stacking context の根の箱が自分の背景・枠を積み終えた直後に呼びます。 */
	public void markOwnDecorationEnd() {
		this.ownDecorationEnd = this.paintCommands == null ? 0 : this.paintCommands.size();
	}

	/** paint command の件数です。 */
	private int paintCount() {
		return this.paintCommands == null ? 0 : this.paintCommands.size();
	}

	/** 負の子を挟む位置(装飾の終端、command 件数で頭打ち)です。 */
	private int decorationSplit() {
		return Math.min(this.ownDecorationEnd, this.paintCount());
	}
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
	/** filter層のFigureを所属させる、この要素自身の構造参照。 */
	private StructureRef structRef = null;
	/** このstacking context全体へ掛ける、この要素自身のfilter。 */
	private FilterValue filter = null;
	/** このstacking contextを作った要素。adopt時の同一性判定にも使う。 */
	private final Params params;
	/** 生成時点で分かっている祖先の合成変換(防御的コピー)。 */
	private final AffineTransform fallbackTransform;
	/** 自要素まで含めた合成変換(防御的コピー)。 */
	private AffineTransform adoptedTransform = null;
	/** nullのadoptも最初の1回として扱うための印。 */
	private boolean transformAdopted = false;

	public void setCurrentStructRef(final StructureRef ref) {
		this.currentStructRef = ref;
		if (this.filter != null && this.structRef == null && ref != null) {
			this.structRef = ref;
		}
	}

	public StructureRef getCurrentStructRef() {
		return this.currentStructRef;
	}

	public Drawer(int z) {
		this.z = z;
		this.params = null;
		this.fallbackTransform = null;
	}

	public Drawer(final Params params) {
		this(params, null);
	}

	public Drawer(final Params params, final AffineTransform fallbackTransform) {
		this.z = params.zIndexValue;
		this.params = params;
		this.fallbackTransform = fallbackTransform == null ? null : new AffineTransform(fallbackTransform);
		final FilterValue own = params.filter.own();
		if (own.needsGroup()) {
			this.filter = own;
		}
	}

	/** 同じ要素が計算した合成変換を最初の1回だけ採用します(2026-09-03)。 */
	public void adoptTransform(final Params owner, final AffineTransform transform) {
		if (owner != this.params || this.transformAdopted) {
			return;
		}
		this.adoptedTransform = transform == null ? null : new AffineTransform(transform);
		this.transformAdopted = true;
	}

	private AffineTransform elementTransform() {
		return this.transformAdopted ? this.adoptedTransform : this.fallbackTransform;
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
	 * {@link #draw(GC)}は負の子、自身、0以上の子の順で描くため、子を1段
	 * 増やすと既存の重なり順が
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
		drawer.currentStructRef = this.currentStructRef;
		this.stackingContexts.add(new StackingContextEntry(drawer, this.stackingContexts.size()));
	}

	/**
	 * 子contextを(z, 挿入順)で整列して返します。全順序なので冪等です。
	 */
	private List<StackingContextEntry> sortedContexts() {
		Collections.sort(this.stackingContexts);
		return this.stackingContexts;
	}

	/** 整列済み子contextのうち、最初のz-index 0以上の位置です。 */
	private static int firstNonNegative(final List<StackingContextEntry> sorted) {
		int i = 0;
		while (i < sorted.size() && sorted.get(i).drawer.z < 0) {
			++i;
		}
		return i;
	}

	public void draw(GC gc) throws GraphicsException {
		this.draw(gc, Double.NaN, Double.NaN);
	}

	/**
	 * 前順走査し、filterを持つstacking contextは部分木を1つの層へ
	 * まとめてから効果を掛けます(2026-09-03)。
	 */
	public void draw(final GC gc, final double pageWidth, final double pageHeight) throws GraphicsException {
		record GroupFrame(GC outer, FilterScope outerScope, FilterScope scope, FilterValue filter,
				FilterPlacement placement, StructureRef structRef, boolean artifact) {
		}
		record Step(Drawer drawer, GroupFrame end, boolean paint, int from, int to) {
		}
		final Map<Long, LineTextScope> lineScopes = this.prepareLineTextScopes(gc, pageWidth, pageHeight);
		final Deque<Step> work = new ArrayDeque<>();
		work.push(new Step(this, null, false, 0, 0));
		GC current = gc;
		FilterScope currentScope = null;
		try {
			while (!work.isEmpty()) {
				final Step step = work.pop();
				if (step.end != null) {
					final GroupFrame frame = step.end;
					final Image image = frame.scope.finish();
					current = frame.outer;
					currentScope = frame.outerScope;
					try (final GC.State state = current.begin()) {
						if (!frame.placement.outerTransform.isIdentity()) {
							current.transform(frame.placement.outerTransform);
						}
						final PDFPageOutput structOut = (frame.structRef != null
								&& DelegatingGC.unwrap(current) instanceof PDFGC pdfgc
								&& pdfgc.getPDFGraphicsOutput() instanceof PDFPageOutput out) ? out : null;
						if (structOut != null) {
							structOut.beginStructContent(frame.structRef);
						}
						try {
							if (frame.artifact) {
								try (final GC.State scope = current.beginArtifactScope()) {
									drawGroupEffects(current, image, frame.filter);
								}
							} else {
								drawGroupEffects(current, image, frame.filter);
							}
						} finally {
							if (structOut != null) {
								structOut.endStructContent();
							}
						}
					}
					continue;
				}

				final Drawer drawer = step.drawer;
				if (step.paint) {
					if (drawer.paintCommands != null) {
						for (int i = step.from; i < Math.min(step.to, drawer.paintCommands.size()); ++i) {
							final PaintCommand command = drawer.paintCommands.get(i);
							command.draw(command.drawable instanceof PageOutputDrawable ? gc : current, lineScopes);
						}
					}
					continue;
				}
				if (drawer.filter != null && Double.isFinite(pageWidth) && Double.isFinite(pageHeight) && pageWidth > 0
						&& pageHeight > 0 && !FilterScope.effective(current, drawer.filter).isNone()
						&& groupsFilters(current, drawer.filter)) {
					final FilterPlacement placement = FilterPlacement.create(drawer.elementTransform(), pageWidth,
							pageHeight);
					final GC outer = current;
					final GroupImageGC group;
					try (final GC.State state = outer.begin()) {
						if (!placement.outerTransform.isIdentity()) {
							outer.transform(placement.outerTransform);
						}
						group = outer.createFilterGroup(placement.width, placement.height);
					}
					if (!placement.groupTransform.isIdentity()) {
						group.transform(placement.groupTransform);
					}
					final FilterScope scope = new FilterScope(group, currentScope, drawer.filter);
					final StructureRef structRef = drawer.structRef == null ? drawer.currentStructRef : drawer.structRef;
					final GroupFrame frame = new GroupFrame(outer, currentScope, scope, drawer.filter, placement,
							structRef, drawer.artifact);
					work.push(new Step(null, frame, false, 0, 0));
					current = scope;
					currentScope = scope;
				}
				if (drawer.stackingContexts != null) {
					final List<StackingContextEntry> sorted = drawer.sortedContexts();
					final int split = firstNonNegative(sorted);
					final int deco = drawer.decorationSplit();
					for (int i = sorted.size() - 1; i >= split; --i) {
						work.push(new Step(sorted.get(i).drawer, null, false, 0, 0));
					}
					work.push(new Step(drawer, null, true, deco, drawer.paintCount()));
					for (int i = split - 1; i >= 0; --i) {
						work.push(new Step(sorted.get(i).drawer, null, false, 0, 0));
					}
					work.push(new Step(drawer, null, true, 0, deco));
				} else {
					work.push(new Step(drawer, null, true, 0, drawer.paintCount()));
				}
			}
		} finally {
			for (final LineTextScope scope : lineScopes.values()) {
				scope.close();
			}
		}
	}

	/**
	 * Counts all fragments before painting so the first/last fragment can share one
	 * replacement. PDF filter groups that rasterize are deliberately suppressed:
	 * rasterized text is non-searchable (bidi logical-output spike section 3).
	 */
	private Map<Long, LineTextScope> prepareLineTextScopes(final GC gc, final double pageWidth,
			final double pageHeight) {
		record PlanStep(Drawer drawer, Object stream, boolean rasterized, Set<FilterValue> grouped, boolean paint, int from,
				int to) {
		}
		final Map<Long, LinePlan> plans = new LinkedHashMap<>();
		final Deque<PlanStep> work = new ArrayDeque<>();
		work.push(new PlanStep(this, DelegatingGC.unwrap(gc), false,
				Collections.newSetFromMap(new IdentityHashMap<FilterValue, Boolean>()), false, 0, 0));
		int textPosition = 0;
		while (!work.isEmpty()) {
			final PlanStep step = work.pop();
			final Drawer drawer = step.drawer;
			Object stream = step.stream;
			boolean rasterized = step.rasterized;
			Set<FilterValue> grouped = step.grouped;
			final boolean group = !step.paint && drawer.filter != null && Double.isFinite(pageWidth)
					&& Double.isFinite(pageHeight) && pageWidth > 0 && pageHeight > 0 && !drawer.filter.isNone()
					&& groupsFilters(gc, drawer.filter);
			if (group) {
				stream = drawer;
				// PDF keeps an opacity-only capture as a vector Form, but rasterizes
				// blur, drop-shadow and color matrices. Preserve semantics for the
				// vector replay and suppress them only for the bitmap path.
				rasterized |= gc.rasterizesGroupEffects()
						&& (drawer.filter.matrix != null || drawer.filter.blur > 0 || drawer.filter.shadow != null);
				grouped = Collections.newSetFromMap(new IdentityHashMap<FilterValue, Boolean>());
				grouped.addAll(step.grouped);
				grouped.add(drawer.filter);
			}
			if (step.paint && drawer.paintCommands != null) {
				for (int i = step.from; i < Math.min(step.to, drawer.paintCommands.size()); ++i) {
					final PaintCommand command = drawer.paintCommands.get(i);
					if (!(command.drawable instanceof LogicalTextDrawable text)) {
						continue;
					}
					final int position = textPosition++;
					if (command.artifact || text.getLogicalLineEmission() == null) {
						continue;
					}
					final Object commandStream = command.drawable instanceof AbstractDrawable drawable
							&& drawable.createsOwnGroup(gc, grouped) ? command : stream;
					final var emission = text.getLogicalLineEmission();
					plans.computeIfAbsent(emission.lineId(), key -> new LinePlan(emission))
							.add(position, commandStream, rasterized);
				}
			}
			if (!step.paint && drawer.stackingContexts != null) {
				final List<StackingContextEntry> sorted = drawer.sortedContexts();
				final int split = firstNonNegative(sorted);
				final int deco = drawer.decorationSplit();
				for (int i = sorted.size() - 1; i >= split; --i) {
					work.push(new PlanStep(sorted.get(i).drawer, stream, rasterized, grouped, false, 0, 0));
				}
				work.push(new PlanStep(drawer, stream, rasterized, grouped, true, deco, drawer.paintCount()));
				for (int i = split - 1; i >= 0; --i) {
					work.push(new PlanStep(sorted.get(i).drawer, stream, rasterized, grouped, false, 0, 0));
				}
				work.push(new PlanStep(drawer, stream, rasterized, grouped, true, 0, deco));
			} else if (!step.paint) {
				work.push(new PlanStep(drawer, stream, rasterized, grouped, true, 0, drawer.paintCount()));
			}
		}
		final Map<Long, LineTextScope> scopes = new LinkedHashMap<>();
		for (final Map.Entry<Long, LinePlan> entry : plans.entrySet()) {
			scopes.put(entry.getKey(), entry.getValue().build());
		}
		return scopes;
	}

	/** このDrawerの実際の効果だけを出力先がまとめて扱えるか。 */
	private static boolean groupsFilters(final GC gc, final FilterValue filter) {
		return gc.supports(GC.Capability.GROUP_FILTER)
				&& (filter.blur <= 0 || gc.supports(GC.Capability.GAUSSIAN_BLUR))
				&& (filter.shadow == null || gc.supports(GC.Capability.DROP_SHADOW));
	}

	/** 捕捉した層へfilterを掛け、実際の出力経路を報告します。 */
	private static void drawGroupEffects(final GC outer, final Image image, final FilterValue filter)
			throws GraphicsException {
		final GroupEffects.DropShadow shadow = filter.shadow == null ? null
				: new GroupEffects.DropShadow(filter.shadow.x(), filter.shadow.y(), filter.shadow.blur() / 2,
						filter.shadow.color());
		final GC.GroupEffectsResult result = outer.drawGroupEffects(image,
				new GroupEffects(filter.matrix, filter.blur, shadow, filter.opacity));
		switch (result) {
		case RASTERIZED:
			ApproximationGC.report(outer, "filter", "2822.filter-rasterized");
			break;
		case LIMIT_FALLBACK:
			ApproximationGC.report(outer, "filter", "2822.filter-limit");
			break;
		case UNSUPPORTED:
			assert false : "GROUP_FILTER was advertised but drawGroupEffects returned UNSUPPORTED";
			outer.drawImage(image);
			ApproximationGC.report(outer, "filter", "2822.per-drawable");
			break;
		case VECTOR:
			break;
		}
	}

	/**
	 * 表示リストをテキストとしてダンプします。draw()と同じ順序で出力します。
	 */
	public void dump(StringBuilder sb, String indent) {
		final Map<Long, String> visualText = this.collectLogicalVisualText();
		final Set<Long> dumpedLines = new java.util.HashSet<>();
		// draw()と同じ前順走査の反復化。インデントだけ階層に追随する
		record DumpStep(Drawer drawer, String indent, boolean paint, int from, int to) {
		}
		final Deque<DumpStep> work = new ArrayDeque<>();
		work.push(new DumpStep(this, indent, false, 0, 0));
		while (!work.isEmpty()) {
			final DumpStep step = work.pop();
			final Drawer drawer = step.drawer;
			if (!step.paint) {
				sb.append(step.indent).append("drawer z=").append(drawer.z);
				if (drawer.filter != null) {
					sb.append(" filter=[").append(drawer.filter.declared).append(']');
				}
				if (drawer.artifact) {
					// 通常の描画では立たないため、既存のgoldenは不変
					sb.append(" artifact");
				}
				sb.append('\n');
			}
			if (step.paint && drawer.paintCommands != null) {
				for (int i = step.from; i < Math.min(step.to, drawer.paintCommands.size()); ++i) {
					final PaintCommand command = drawer.paintCommands.get(i);
					if (!command.artifact && command.drawable instanceof LogicalTextDrawable text
							&& text.getLogicalLineEmission() != null) {
						final var emission = text.getLogicalLineEmission();
						if (!dumpedLines.add(emission.lineId())) {
							continue;
						}
						final String visual = visualText.get(emission.lineId());
						sb.append(step.indent).append("  ")
								.append(String.format(java.util.Locale.ROOT, "x=%.2f y=%.2f ", command.x, command.y))
								.append("text logical=\"").append(escapeDump(emission.logicalText()))
								.append("\" visual=\"").append(escapeDump(visual == null ? "" : visual))
								.append('\"').append(command.drawable.describeGeometry(command.x, command.y))
								.append(command.drawable.describeClip()).append('\n');
						continue;
					}
					sb.append(step.indent).append("  ")
							.append(String.format(java.util.Locale.ROOT, "x=%.2f y=%.2f ", command.x, command.y));
					if (command.artifact) {
						sb.append("artifact ");
					}
					sb.append(command.drawable.describe()).append(command.drawable.describeGeometry(command.x, command.y))
							.append(command.drawable.describeClip()).append('\n');
				}
			}
			if (!step.paint && drawer.stackingContexts != null) {
				final List<StackingContextEntry> sorted = drawer.sortedContexts();
				final int split = firstNonNegative(sorted);
				final int deco = drawer.decorationSplit();
				for (int i = sorted.size() - 1; i >= split; --i) {
					work.push(new DumpStep(sorted.get(i).drawer, step.indent + "  ", false, 0, 0));
				}
				work.push(new DumpStep(drawer, step.indent, true, deco, drawer.paintCount()));
				for (int i = split - 1; i >= 0; --i) {
					work.push(new DumpStep(sorted.get(i).drawer, step.indent + "  ", false, 0, 0));
				}
				work.push(new DumpStep(drawer, step.indent, true, 0, deco));
			} else if (!step.paint) {
				work.push(new DumpStep(drawer, step.indent, true, 0, drawer.paintCount()));
			}
		}
	}

	private Map<Long, String> collectLogicalVisualText() {
		final Map<Long, String> text = new LinkedHashMap<>();
		record CollectStep(Drawer drawer, boolean paint, int from, int to) {
		}
		final Deque<CollectStep> work = new ArrayDeque<>();
		work.push(new CollectStep(this, false, 0, 0));
		while (!work.isEmpty()) {
			final CollectStep step = work.pop();
			final Drawer drawer = step.drawer;
			if (step.paint && drawer.paintCommands != null) {
				for (int i = step.from; i < Math.min(step.to, drawer.paintCommands.size()); ++i) {
					final PaintCommand command = drawer.paintCommands.get(i);
					if (!command.artifact && command.drawable instanceof LogicalTextDrawable logical
							&& logical.getLogicalLineEmission() != null) {
						text.putIfAbsent(logical.getLogicalLineEmission().lineId(), logical.getLineVisualText());
					}
				}
			}
			if (!step.paint && drawer.stackingContexts != null) {
				final List<StackingContextEntry> sorted = drawer.sortedContexts();
				final int split = firstNonNegative(sorted);
				final int deco = drawer.decorationSplit();
				for (int i = sorted.size() - 1; i >= split; --i) {
					work.push(new CollectStep(sorted.get(i).drawer, false, 0, 0));
				}
				work.push(new CollectStep(drawer, true, deco, drawer.paintCount()));
				for (int i = split - 1; i >= 0; --i) {
					work.push(new CollectStep(sorted.get(i).drawer, false, 0, 0));
				}
				work.push(new CollectStep(drawer, true, 0, deco));
			} else if (!step.paint) {
				work.push(new CollectStep(drawer, true, 0, drawer.paintCount()));
			}
		}
		return text;
	}

	private static String escapeDump(final String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r")
				.replace("\n", "\\n");
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
