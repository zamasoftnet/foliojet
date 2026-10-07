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
 * Display list for one stacking context. Holds a sequence of paint commands and child stacking contexts (drawn in
 * z-index order).
 *
 * <p>
 * Drawing order is children with negative z-index → own paint commands → children with nonnegative z-index (CSS 2.1
 * Appendix E). Sort child contexts by (z, insertion order). Previously relied implicitly on {@code
 * Collections.sort} stability; insertion order became an explicit sort key (B-1, 2026-07-30). This total order
 * gives the same result on repeated sorts, so dump/draw share the same order. Appendix E's further distinction,
 * placing children between the parent's own background and inline content, requires paint-command classification
 * and is outside this change's scope.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class Drawer {
	/**
	 * A drawable box with a determined position (paint command).
	 */
	protected static class PaintCommand {
		private final Drawable drawable;
		private final double x, y;
		/**
		 * Whether to emit as a PDF artifact (decoration) (2026-07-25, rescue splitting, increment 2). Always false for
		 * normal drawing, leaving existing output completely unchanged.
		 */
		private final boolean artifact;

		/**
		 * Declared structure element this command belongs to (B-3, 2026-07-30). Structure order is fixed at declaration
		 * time (document order); drawing only routes to this reference, so z-order drawing does not disrupt structure.
		 * Always null for untagged output.
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
			// Reach PDF structure output even through wrappers such as ApproximationGC.
			final PDFPageOutput structOut = (this.structRef != null
					&& net.zamasoft.foliojet.layout.util.DelegatingGC.unwrap(gc) instanceof PDFGC pdfgc
					&& pdfgc.getPDFGraphicsOutput() instanceof PDFPageOutput out) ? out : null;
			if (structOut != null) {
				structOut.beginStructContent(this.structRef);
			}
			try {
				if (this.artifact) {
					// Keep the appearance unchanged but exclude it from logical structure. If the GC does not
					// support artifacts (e.g. untagged PDF), it returns a no-op scope,
					// so output exactly matches the non-artifact case.
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

	/** Child stacking context and insertion order (sort key for equal z). */
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

	/** Validated transform and dimensions for placing a filter layer in element coordinates (2026-09-03). */
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
	 * Paint-command position where the context's own background/frame ends (decoration first added by the
	 * stacking-context root box). Draw child contexts with negative z-index **after this position and before remaining
	 * content**, per CSS 2.1 Appendix E ③. Without a marker (0), draw negative children before all own commands.
	 */
	private int ownDecorationEnd = 0;

	/** Called immediately after the stacking-context root box adds its own background/frame. */
	public void markOwnDecorationEnd() {
		this.ownDecorationEnd = this.paintCommands == null ? 0 : this.paintCommands.size();
	}

	/** Number of paint commands. */
	private int paintCount() {
		return this.paintCommands == null ? 0 : this.paintCommands.size();
	}

	/** Insertion position for negative children (decoration end, capped at the command count). */
	private int decorationSplit() {
		return Math.min(this.ownDecorationEnd, this.paintCount());
	}
	private List<StackingContextEntry> stackingContexts = null;

	/**
	 * Whether content subsequently added to this Drawer is emitted as an artifact (added 2026-07-25, rescue splitting,
	 * increment 2; <b>not yet set by production paths</b>).
	 *
	 * <p>
	 * Continuation fragments from rescue splitting visually contain content that semantically belongs to the first
	 * fragment. Emit them as PDF artifacts to prevent duplicate text extraction, read-aloud content, and structure
	 * tags.
	 * </p>
	 */
	protected boolean artifact = false;

	/** Shared view returned by {@link #artifactView()} (created lazily). */
	private Drawer artifactView = null;

	/**
	 * Declared structure element to which subsequently added commands belong (B-3, 2026-07-30).
	 * PageBox.beginStruct/endStruct updates it during document-order traversal (display-list construction). Always
	 * null for untagged output.
	 */
	private StructureRef currentStructRef = null;
	/** This element's own structure reference, to which the filter layer's Figure belongs. */
	private StructureRef structRef = null;
	/** This element's own filter, applied to the entire stacking context. */
	private FilterValue filter = null;
	/** Element that created this stacking context. Also used for identity checks during adopt. */
	private final Params params;
	/** Composite ancestor transform known at creation time (defensive copy). */
	private final AffineTransform fallbackTransform;
	/** Composite transform including this element (defensive copy). */
	private AffineTransform adoptedTransform = null;
	/** Marker treating even a null adopt as the first adoption. */
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

	/** Adopts the composite transform computed by the same element only once, on the first call (2026-09-03). */
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
	 * Returns true if content added to this Drawer is an artifact.
	 */
	public boolean isArtifact() {
		return this.artifact;
	}

	/**
	 * Returns a <b>view of the same display list</b> differing only in the marker that subsequent additions are
	 * artifacts (added 2026-07-25, rescue splitting, increment 2).
	 *
	 * <p>
	 * Important: never add a wrapper Drawer <b>as a child</b>. The current {@link #draw(GC)} draws negative children,
	 * self, then nonnegative children, so adding a child level changes existing stacking order (recommendation §3).
	 * This view creates no new z level; it forwards additions directly to this Drawer's display list and only marks
	 * PaintCommand.
	 * </p>
	 *
	 * <p>
	 * When a child Drawer is added through the view, propagate the artifact attribute to the child ({@link
	 * #visitDrawer(Drawer)}).
	 * </p>
	 *
	 * @return shared view with the artifact marker
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
	 * Marks this Drawer and all existing and future content as artifacts (propagates iteratively to child Drawers
	 * too).
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
						// Artifact marking also removes it from structure (B-3: drop structure references).
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
	 * Adds a Drawable to the display list (with an artifact marker). Shared views delegate this to the owner.
	 */
	protected void addDrawable(Drawable drawable, double x, double y, boolean artifact) {
		// isNone lets sentinel arithmetic results (NONE+10, etc.) and NaN through.
		// This is the sole checkpoint for all display-list positions,
		// so reject values outside a plausible printable range (see LayoutUtils.isDrawable).
		assert LayoutUtils.isDrawable(x) : "描画位置xが異常: " + x + " (" + drawable + ")";
		assert LayoutUtils.isDrawable(y) : "描画位置yが異常: " + y + " (" + drawable + ")";
		if (this.paintCommands == null) {
			this.paintCommands = new ArrayList<PaintCommand>();
		}
		// Do not include artifacts in logical structure (do not give them structure references either).
		this.paintCommands.add(new PaintCommand(drawable, x, y, artifact, artifact ? null : this.currentStructRef));
	}

	public void visitDrawer(Drawer drawer) {
		this.addDrawer(drawer, this.artifact);
	}

	/**
	 * Adds a child stacking context. If {@code artifact} is true, propagates it to the child and its descendants.
	 * Insertion order is the sort key for equal z.
	 */
	protected void addDrawer(Drawer drawer, boolean artifact) {
		if (artifact) {
			drawer.markArtifact();
		}
		if (this.stackingContexts == null) {
			this.stackingContexts = new ArrayList<StackingContextEntry>();
		}
		// Pass the current structure reference to the child stacking context (B-3); even when drawn in z order,
		// the child's content belongs to its document-order parent element.
		drawer.currentStructRef = this.currentStructRef;
		this.stackingContexts.add(new StackingContextEntry(drawer, this.stackingContexts.size()));
	}

	/**
	 * Returns child contexts sorted by (z, insertion order). Idempotent because the order is total.
	 */
	private List<StackingContextEntry> sortedContexts() {
		Collections.sort(this.stackingContexts);
		return this.stackingContexts;
	}

	/** First position with z-index at least 0 among sorted child contexts. */
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
	 * Traverses in preorder; for a stacking context with a filter, groups the subtree into one layer before applying
	 * effects (2026-09-03).
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

	/** Whether the output can handle this Drawer's actual effects together. */
	private static boolean groupsFilters(final GC gc, final FilterValue filter) {
		return gc.supports(GC.Capability.GROUP_FILTER)
				&& (filter.blur <= 0 || gc.supports(GC.Capability.GAUSSIAN_BLUR))
				&& (filter.shadow == null || gc.supports(GC.Capability.DROP_SHADOW));
	}

	/** Applies the filter to the captured layer and reports the actual output path. */
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
	 * Dumps the display list as text in the same order as draw().
	 */
	public void dump(StringBuilder sb, String indent) {
		final Map<Long, String> visualText = this.collectLogicalVisualText();
		final Set<Long> dumpedLines = new java.util.HashSet<>();
		// Iterative preorder traversal, as in draw(). Only indentation follows the hierarchy.
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
					// Not set during normal drawing, so existing goldens remain unchanged.
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
	 * Artifact-marked view sharing the owner's display list (added 2026-07-25, rescue splitting, increment 2).
	 *
	 * <p>
	 * Has no display list of its own; delegates all additions to the owner. Thus z order and insertion order remain
	 * unchanged when using the shared view. The owner performs {@link #draw(GC)} and {@link #dump}, so the view itself
	 * stays empty (= draws nothing).
	 * </p>
	 */
	private static final class ArtifactView extends Drawer {
		private final Drawer owner;

		ArtifactView(final Drawer owner) {
			super(owner.z);
			this.owner = owner;
			this.artifact = true;
		}

		// Share the structure reference with the owner (the view itself holds none);
		// in practice unused, since artifact additions carry no structure reference.
		@Override
		public StructureRef getCurrentStructRef() {
			return this.owner.getCurrentStructRef();
		}

		@Override
		public Drawer artifactView() {
			return this;
		}

		// The base class's early continue suffices for markArtifact (the view is always an artifact).
		// Do not propagate to the owner, which also contains normal content.

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
