package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.PagePos;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.BackgroundBorderDrawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * ページです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: PageBox.java 1561 2018-07-04 11:44:21Z miyabe $
 */
public class PageBox extends AbstractBlockBox {
	protected final UserAgent ua;

	/** この表示頁だけに属する再生木。通常のfixed台帳へは渡しません。 */
	private final List<PageBox> pageContents = new ArrayList<PageBox>();

	public void addPageContent(final PageBox content) {
		this.pageContents.add(content);
	}

	public boolean hasPageContents() {
		return !this.pageContents.isEmpty();
	}

	/** 本文/fixedのz-indexから独立した頁固定層です。 */
	public void drawPageContents(final Drawer drawer) {
		if (this.pageContents.isEmpty()) {
			return;
		}
		final Drawer layer = new Drawer(Integer.MAX_VALUE);
		drawer.visitDrawer(layer);
		for (final PageBox content : this.pageContents) {
			net.zamasoft.foliojet.css.style.running.RunningRenderer.draw(content, layer, 0, 0);
		}
	}

	/**
	 * 塗り足し(bleed)の幅です(2026-09-02)。{@code @page} の背景は仕上り線で
	 * 止めず、この幅だけ外へ描く——裁ち落としで白い縁が出ないように。
	 * {@code PageSequence} がページ生成時に与える。
	 */
	private double bleed = 0;

	public void setBleed(final double bleed) {
		this.bleed = Math.max(0, bleed);
	}

	/** {@code @page}の背景。通常のframe背景(canvas背景)とは別に用紙全面へ描く。 */
	private final Background pageBackground;

	/**
	 * 固定配置ブロックです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: PageBox.java 1561 2018-07-04 11:44:21Z miyabe $
	 */
	protected static class Fixed {
		public final IAbsoluteBox box;
		public final double x, y;

		public Fixed(IAbsoluteBox box, double x, double y) {
			this.box = box;
			this.x = x;
			this.y = y;
		}
	}

	/**
	 * 固定位置指定されたコンテンツ。
	 */
	protected List<Fixed> fixeds = null;

	protected List<Fixed> toAddFixeds = null;

	/**
	 * 表示上のサイズ。
	 */
	protected double visualWidth = 0, visualHeight = 0;

	private boolean replayPage;
	private double replayX, replayY;

	/** 独立ミニ頁の配置原点です。fixedの基準もここへ移し、余白寸法では切りません。 */
	public void setReplayOrigin(final double x, final double y) {
		this.replayPage = true;
		this.replayX = x;
		this.replayY = y;
	}

	public boolean isReplayPage() {
		return this.replayPage;
	}

	public PageBox(BlockParams params, UserAgent ua) {
		this(params, ua, Background.NULL_BACKGROUND);
	}

	public PageBox(BlockParams params, UserAgent ua, Container container) {
		this(params, ua, Background.NULL_BACKGROUND, container);
	}

	public PageBox(BlockParams params, UserAgent ua, Background pageBackground) {
		this(params, ua, pageBackground, new FlowContainer());
	}

	private PageBox(BlockParams params, UserAgent ua, Background pageBackground, Container container) {
		super(params, params.size, params.minSize, new AbsoluteRectFrame(params.frame), container);
		assert !this.size.getWidthType().needsReference();
		assert !this.size.getHeightType().needsReference();

		this.ua = ua;
		this.pageBackground = pageBackground == null ? Background.NULL_BACKGROUND : pageBackground;

		double lineWidth;
		switch (params.flow) {
		case WritingMode.TB:
			// 横書き
			assert this.size.getWidthType() == LengthType.ABSOLUTE;
			lineWidth = this.size.getWidth();
			break;
		case WritingMode.LR:
		case WritingMode.RL:
			// 縦書き
			assert this.size.getHeightType() == LengthType.ABSOLUTE;
			lineWidth = this.size.getHeight();
			break;
		default:
			throw new IllegalStateException();
		}

		RectFrame frame = this.frame.frame;
		{
			Insets insets = frame.margin;
			double top, right, bottom, left;
			switch (insets.getTopType()) {
			case ABSOLUTE:
				top = insets.getTop();
				break;
			case RELATIVE:
				top = insets.getTop() * lineWidth;
				break;
			case MIXED:
				top = insets.getTop() + insets.getTopRatio() * lineWidth;
				break;
			case AUTO:
				top = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (insets.getBottomType()) {
			case ABSOLUTE:
				bottom = insets.getBottom();
				break;
			case RELATIVE:
				bottom = insets.getBottom() * lineWidth;
				break;
			case MIXED:
				bottom = insets.getBottom() + insets.getBottomRatio() * lineWidth;
				break;
			case AUTO:
				bottom = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (insets.getLeftType()) {
			case ABSOLUTE:
				left = insets.getLeft();
				break;
			case RELATIVE:
				left = insets.getLeft() * lineWidth;
				break;
			case MIXED:
				left = insets.getLeft() + insets.getLeftRatio() * lineWidth;
				break;
			case AUTO:
				left = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (insets.getRightType()) {
			case ABSOLUTE:
				right = insets.getRight();
				break;
			case RELATIVE:
				right = insets.getRight() * lineWidth;
				break;
			case MIXED:
				right = insets.getRight() + insets.getRightRatio() * lineWidth;
				break;
			case AUTO:
				right = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			this.frame.margin.top = top;
			this.frame.margin.right = right;
			this.frame.margin.bottom = bottom;
			this.frame.margin.left = left;
			// 内側余白(`@page`のpadding、2026-09-03)。余白と同じ規則で絶対値にする
			// (以前は解決されず0のままで、枠線だけが引かれていた)
			final double[] padding = resolveInsets(frame.padding, lineWidth);
			this.frame.padding.top = padding[0];
			this.frame.padding.right = padding[1];
			this.frame.padding.bottom = padding[2];
			this.frame.padding.left = padding[3];
			if (this.size.getWidthType() == LengthType.ABSOLUTE) {
				this.visualWidth = this.width = this.size.getWidth() - this.frame.getFrameWidth();
			}
			if (this.size.getHeightType() == LengthType.ABSOLUTE) {
				this.visualHeight = this.height = this.size.getHeight() - this.frame.getFrameHeight();
			}
		}
	}

	public final BoxType getType() {
		return BoxType.PAGE;
	}

	public final Pos getPos() {
		return PagePos.POS;
	}

	public final UserAgent getUserAgent() {
		return this.ua;
	}

	/**
	 * Elements whose tagged-PDF structure element is currently open
	 * (identity-keyed: pseudo/anonymous singletons and non-StructureElement
	 * values).
	 */
	private final java.util.Set<Object> openStructElements = java.util.Collections
			.newSetFromMap(new java.util.IdentityHashMap<>());

	/**
	 * Real source elements (elementKey &gt;= 0) whose structure element is
	 * currently open, deduplicated by their logical identity (elementKey).
	 *
	 * <p>
	 * E-6増分4b(2026-07-24): TwoPass range bindでは、liveの祖先ボックス
	 * ({@code CSSElement}保持)と再生された子孫ボックス
	 * ({@code StructureToken}保持)が同じ論理要素を指すことがある
	 * (例: {@code <li>}のprincipal box(live)とmarker box(range再生)。
	 * 従来は同一{@code CSSElement}インスタンスの共有で識別していた)。
	 * 参照identityでは再生境界をまたぐ共有を表現できないため、実要素は
	 * {@code elementKey}(文書順の通し番号=論理identity)で重複開きを
	 * 防ぐ。live同士では同じ論理要素は常に同じインスタンスを共有する
	 * (fragmentはparamsを共有する)ため、この変更でlive挙動は変わらない。
	 * </p>
	 */
	private final java.util.Set<Long> openStructKeys = new java.util.HashSet<>();

	/**
	 * タグ付きPDFの構造宣言先(B-3、2026-07-30)。PageSequence.drawPageが
	 * 表示リスト構築の前に設定する。untagged・非PDF出力ではnullのまま
	 * (declareは呼ばれずnull参照が流れる=従来のno-opと同じ)。
	 */
	private net.zamasoft.pdfg2d.pdf.PDFPageOutput structOut = null;

	/**
	 * 構造要素のページ横断レジストリです(欠陥②の修正、2026-07-30)。
	 * {@code PageSequence}が文書単位で保持し、ページごとにここへ渡される。
	 * untagged時はnull。
	 */
	private TaggedStructureContext structContext = null;

	/**
	 * 反復表示(表の繰り返しヘッダ/フッタ)の描画中の深さです(欠陥②の
	 * 修正、2026-07-30)。正の間、{@link #beginStruct}はページ横断
	 * レジストリを迂回する——反復は「同じ要素の再表示」であって継続では
	 * ないため、併合すると1つのStructElemに同じ内容がページ数ぶん重複する。
	 * 従来どおりページごとの独立した宣言に留める(反復のartifact化は
	 * 別増分で検討)。
	 */
	private int structRepetitionDepth = 0;

	/** 文書順の構造の入れ子(宣言済みrefのスタック)。 */
	private final java.util.ArrayDeque<net.zamasoft.pdfg2d.pdf.StructureRef> structStack = new java.util.ArrayDeque<>();

	public void setStructOutput(final net.zamasoft.pdfg2d.pdf.PDFPageOutput structOut,
			final TaggedStructureContext structContext) {
		this.structOut = structOut;
		this.structContext = structContext;
	}

	/** 反復表示(繰り返しヘッダ/フッタ)区間の開始(worklist stepから)。 */
	public void pushStructRepetition() {
		++this.structRepetitionDepth;
	}

	/** {@link #pushStructRepetition}の対の終了。 */
	public void popStructRepetition() {
		--this.structRepetitionDepth;
	}

	/** 現在の構造の親(スタック頂上。空・番兵はnull=StructTreeRoot直下)。 */
	private net.zamasoft.pdfg2d.pdf.StructureRef structParent() {
		final var top = this.structStack.peek();
		return top == NULL_STRUCT ? null : top;
	}

	/** structStackはnullを積めない(ArrayDeque)ので、番兵で包む。 */
	private net.zamasoft.pdfg2d.pdf.StructureRef declareStruct(final String role, final String scope) {
		if (this.structOut == null) {
			return null;
		}
		return this.structOut.declareStructElement(this.structParent(), role, scope);
	}

	/** ArrayDequeはnull要素を許さないための番兵(untagged時の占位)。 */
	private static final net.zamasoft.pdfg2d.pdf.StructureRef NULL_STRUCT = new net.zamasoft.pdfg2d.pdf.StructureRef() {
	};

	/** Marks the element's structure open; false when it already is. */
	private boolean openStruct(final Object element) {
		if (element instanceof net.zamasoft.foliojet.css.StructureElement se && se.elementKey() >= 0) {
			return this.openStructKeys.add(se.elementKey());
		}
		return this.openStructElements.add(element);
	}

	/** Clears the element's open mark ({@code openStruct}'s counterpart). */
	private void closeStruct(final Object element) {
		if (element instanceof net.zamasoft.foliojet.css.StructureElement se && se.elementKey() >= 0) {
			this.openStructKeys.remove(se.elementKey());
			return;
		}
		this.openStructElements.remove(element);
	}

	/**
	 * Inserts tagged-PDF structure begin markers for a box's element and
	 * returns how many were opened (0 when tagging is off, the element is not
	 * mappable, or the same element is already open — the outer box owns it).
	 * A list item additionally opens an {@code LBody} wrapper.
	 *
	 * @param drawer  the drawer to add markers to
	 * @param element the box's element
	 * @param x       the box x position
	 * @param y       the box y position
	 * @return the number of structure elements opened (to pass to
	 *         {@link #endStruct})
	 */
	public int beginStruct(final Drawer drawer, final Object element, final double x, final double y) {
		if (drawer.isArtifact()) {
			// 2026-07-25(救済分割・増分5、答申§3): artifact drawer(=救済
			// 分割の継続断片)は「見た目は内容、意味の上では先頭断片に
			// 属する」。構造要素は先頭断片が一度だけ開くため、ここは
			// 素通りさせる(elementKey dedupはページごとにsetが別なので
			// 継続断片の抑止には使えない)
			return 0;
		}
		final String role = net.zamasoft.foliojet.ua.props.TaggedPdf.roleIfActive(this.ua, element);
		if (role == null || !this.openStruct(element)) {
			return 0;
		}
		// 欠陥②の修正(2026-07-30): 継続断片は初出時に宣言済みの
		// StructureRefを再利用し、1論理要素=1 StructElemにする(内容の
		// MCIDが複数ページに跨る——pdfg2dの/Type /MCR /Pg)。反復表示
		// (structRepetitionDepth>0)と匿名・擬似要素(elementKey<0)は
		// 対象外で、従来どおりページごとに宣言する
		final long elementKey = this.structRepetitionDepth == 0 && this.structContext != null
				&& element instanceof net.zamasoft.foliojet.css.StructureElement se ? se.elementKey() : -1;
		final String scope = role.equals("TH") ? net.zamasoft.foliojet.ua.props.TaggedPdf.headerScope(element) : null;
		if (elementKey >= 0) {
			final TaggedStructureContext.Binding binding = this.structContext.lookup(elementKey);
			if (binding != null) {
				if (java.util.Objects.equals(binding.role(), role)
						&& java.util.Objects.equals(binding.scope(), scope)
						&& binding.parent() == this.structParent()) {
					// 継続: 再宣言せず既存refへ内容を継ぎ足す
					for (final var ref : binding.refs()) {
						this.structStack.push(ref == null ? NULL_STRUCT : ref);
					}
					drawer.setCurrentStructRef(binding.contentRef());
					return binding.refs().length;
				}
				// role/scope/親の不一致——構造が変わる再宣言は欠陥②の再発
				// なので本来は不変条件違反だが、クラッシュ排除の絶対要件に
				// 従い警告の上で従来どおりの新規宣言(=旧挙動)へ倒す
				java.util.logging.Logger.getLogger(PageBox.class.getName())
						.warning("tagged-PDF continuation mismatch for elementKey=" + elementKey + ": declared=("
								+ binding.role() + "," + binding.scope() + ") now=(" + role + "," + scope
								+ "); falling back to a fresh StructElem (element split across pages)");
			}
		}
		// B-3(2026-07-30): 構造はこの走査(文書順)で即宣言し、描画は
		// PaintCommandが保持する参照へルーティングする。z-indexで別の
		// stacking contextに積まれても、/Kの論理順はここで確定済み
		final var parent = this.structParent();
		final var ref = this.declareStruct(role, scope);
		this.structStack.push(ref == null ? NULL_STRUCT : ref);
		drawer.setCurrentStructRef(ref);
		if (role.equals("LI")) {
			// PDF/UA: an LI's content must sit in an LBody.
			final var lbody = this.declareStruct("LBody", null);
			this.structStack.push(lbody == null ? NULL_STRUCT : lbody);
			drawer.setCurrentStructRef(lbody);
			if (elementKey >= 0 && ref != null) {
				this.structContext.register(elementKey, new TaggedStructureContext.Binding(
						new net.zamasoft.pdfg2d.pdf.StructureRef[] { ref, lbody }, role, scope, parent));
			}
			return 2;
		}
		if (elementKey >= 0 && ref != null) {
			this.structContext.register(elementKey, new TaggedStructureContext.Binding(
					new net.zamasoft.pdfg2d.pdf.StructureRef[] { ref }, role, scope, parent));
		}
		return 1;
	}

	/**
	 * Closes the structure elements opened by a matching {@link #beginStruct}.
	 *
	 * @param drawer  the drawer to add markers to
	 * @param element the box's element
	 * @param count   the value returned by {@link #beginStruct}
	 * @param x       the box x position
	 * @param y       the box y position
	 */
	public void endStruct(final Drawer drawer, final Object element, final int count, final double x, final double y) {
		if (count == 0) {
			return;
		}
		for (int i = 0; i < count; ++i) {
			if (!this.structStack.isEmpty()) {
				this.structStack.pop();
			}
		}
		this.closeStruct(element);
		drawer.setCurrentStructRef(this.structParent());
	}

	public final boolean isSpecifiedPageSize() {
		return false;
	}

	/**
	 * このページが<b>強制改ページで始まった</b>か(2026-07-28新設)。
	 *
	 * <p>
	 * {@code page-break-before/after: always|left|right} は「白紙でも1枚出す」
	 * ことを作者が明示した指定です。何も描かないページを落とす規則
	 * (css-break-3 §4.4、{@code StyleBuilder.drawPage})は、この印がある
	 * ページには適用しません。
	 * </p>
	 */
	private boolean forcedBreakOrigin = false;

	/**
	 * このページが強制改ページで始まったことを記録します
	 * ({@code RootBuilder.pageBreak} 専用)。
	 */
	public final void markForcedBreakOrigin() {
		this.forcedBreakOrigin = true;
	}

	/**
	 * このページが強制改ページで始まったなら true を返します。
	 */
	public final boolean isForcedBreakOrigin() {
		return this.forcedBreakOrigin;
	}

	/**
	 * ページ先頭でのページ名遷移により閉じられたことの印です(名前付き
	 * ページN2b)。この印があり、かつ何も描いていないページは、柱の宣言や
	 * {@link #isForcedBreakOrigin()}に関わらず出力から落とす——旧名の
	 * ページを捨てて新名で作り直す「未確定ページの差し替え」と等価になる。
	 */
	private boolean namedTransitionClosed = false;

	/** ページ名遷移による閉鎖を記録します({@code RootBuilder.pageBreak}専用)。 */
	public final void markNamedTransitionClosed() {
		this.namedTransitionClosed = true;
	}

	/** このページがページ名遷移で閉じられたなら true を返します。 */
	public final boolean isNamedTransitionClosed() {
		return this.namedTransitionClosed;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * ページには本文のほかに<b>固定配置</b>({@code position:fixed})が
	 * 載ります。これは前のページの描画で登録され、以後の全ページに
	 * 描かれるため、コンテナを歩いても見つかりません。
	 * </p>
	 */
	@Override
	public boolean paintsAnything() {
		if (this.pageBackground.isVisible()) {
			return true;
		}
		if (this.fixeds != null && !this.fixeds.isEmpty()) {
			return true;
		}
		if (this.toAddFixeds != null && !this.toAddFixeds.isEmpty()) {
			return true;
		}
		return super.paintsAnything();
	}

	@Override
	public double paintedPageExtent(final WritingMode flow) {
		return this.pageBackground.isVisible() ? this.getPageExtent(flow) : super.paintedPageExtent(flow);
	}

	public final void addFloating(IFloatBox box, double lineAxis, double pageAxis) {
		throw new UnsupportedOperationException();
	}

	public final void setPageAxis(final double newSize) {
		assert !LayoutUtils.isNone(newSize);
		final BlockParams params = this.getBlockParams();
		switch (params.flow) {
		case WritingMode.TB: {
			// 横書き
			this.visualHeight = Math.max(this.visualHeight, newSize);
			if (this.size.getHeightType() != LengthType.AUTO || newSize <= this.height) {
				return;
			}
			this.height = Math.max(this.minPageAxis, newSize);
			this.height = Math.min(this.maxPageAxis, this.height);
		}
			break;
		case WritingMode.LR:
		case WritingMode.RL: {
			// 縦書き
			this.visualWidth = Math.max(this.visualWidth, newSize);
			if (this.size.getWidthType() != LengthType.AUTO || newSize <= this.width) {
				return;
			}
			this.width = Math.max(this.minPageAxis, newSize);
			this.width = Math.min(this.maxPageAxis, this.width);
		}
			break;
		default:
			throw new IllegalStateException();
		}
	}
	
	public double getVisualWidth() {
		return this.visualWidth + this.frame.getFrameWidth();
	}

	public double getVisualHeight() {
		return this.visualHeight + this.frame.getFrameHeight();
	}

	public final void addFixed(Drawer drawer, Visitor visitor, IAbsoluteBox box, double x, double y) {
		AbsolutePos pos = box.getAbsolutePos();
		if (pos.location.getLeftType() != LengthType.AUTO || pos.location.getRightType() != LengthType.AUTO) {
			x = this.replayX;
		}
		if (pos.location.getTopType() != LengthType.AUTO || pos.location.getBottomType() != LengthType.AUTO) {
			y = this.replayY;
		}
		box.finishLayout(this);
		Fixed fixed = new Fixed(box, x, y);
		if (this.toAddFixeds == null) {
			this.toAddFixeds = new ArrayList<Fixed>();
		}
		this.toAddFixeds.add(fixed);

		x = this.replayX + this.offsetX + this.frame.getFrameLeft() - this.frame.margin.left;
		y = this.replayY + this.offsetY + this.frame.getFrameTop() - this.frame.margin.top;
		fixed.box.draw(this, drawer, visitor, null, new AffineTransform(), x, y, fixed.x, fixed.y);
	}

	public final boolean isContextBox() {
		return true;
	}

	public final SplitResult split(double pageLimit, BreakMode mode, byte flags) {
		throw new UnsupportedOperationException();
	}

	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final net.zamasoft.foliojet.ua.UserAgent ua = this.ua;
		final Background pageBackground = this.pageBackground;
		return (state, container) -> new PageBox(params, ua, pageBackground, container);
	}

	/** 余白・内側余白の指定値を行幅を基準に絶対値へ(上・右・下・左)。AUTOは0。 */
	private static double[] resolveInsets(final Insets insets, final double lineWidth) {
		final double[] out = new double[4];
		final LengthType[] types = { insets.getTopType(), insets.getRightType(), insets.getBottomType(),
				insets.getLeftType() };
		final double[] values = { insets.getTop(), insets.getRight(), insets.getBottom(), insets.getLeft() };
		final double[] ratios = { insets.getTopRatio(), insets.getRightRatio(), insets.getBottomRatio(),
				insets.getLeftRatio() };
		for (int i = 0; i < 4; ++i) {
			out[i] = switch (types[i]) {
			case ABSOLUTE -> values[i];
			case RELATIVE -> values[i] * lineWidth;
			case MIXED -> values[i] + ratios[i] * lineWidth;
			case AUTO -> 0;
			default -> throw new IllegalStateException();
			};
		}
		return out;
	}

	public final void drawFlow(Drawer drawer, Visitor visitor) {
		double x = -this.frame.margin.left;
		double y = -this.frame.margin.top;
		if (this.pageBackground.isVisible()) {
			// PageSequenceはGCを余白分だけ平行移動するので、この座標が用紙原点。
			// frame.drawと違ってmarginを控除せず、@page背景を用紙全面へ描く。
			// 塗り足しがあれば、その幅だけ仕上り線の外まで塗る(2026-09-02——以前は
			// 仕上り線で止まり、裁ち口の帯が白いまま出ていた)
			final double b = this.bleed;
			drawer.visitDrawable(new BackgroundBorderDrawable(this, null, 1f, new AffineTransform(),
					this.pageBackground, null, null, this.getWidth() + b * 2, this.getHeight() + b * 2), x - b, y - b);
		}
		this.frames(this, drawer, null, new AffineTransform(), x, y);
		this.draw(this, drawer, visitor, null, new AffineTransform(), x, y, x, y);
	}

	/**
	 * 脚注separator罫線のページ方向位置です(脚注F6/F7答申①、2026-07-31。
	 * 版面内辺原点の論理container座標——RootBuilderがaddFloatingへ渡す
	 * (0,pageAxis)と同じ座標系)。脚注の無いページは-1。
	 */
	private double footnoteSeparatorAxis = -1;

	public void setFootnoteSeparatorAxis(final double pageAxis) {
		this.footnoteSeparatorAxis = pageAxis;
	}

	/** separator罫線の太さと、版面行方向幅に対する長さの割合(UA固定)。 */
	private static final double FOOTNOTE_SEPARATOR_THICKNESS = 0.5;

	/**
	 * 脚注separator罫線を描きます({@code PageSequence.drawPage}のflow後・
	 * fixed前)。装飾なのでartifact(タグ付きPDFの構造要素に入れない)。
	 * ページのframeはmarginのみのため本文コンテナ原点は(0,0)
	 * (答申の座標対応)。
	 */
	public void drawFootnoteSeparator(final Drawer drawer) {
		if (this.footnoteSeparatorAxis < 0) {
			return;
		}
		final BlockParams params = this.getBlockParams();
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = params.flow;
		final double length = this.getInnerLineExtent(flow) / 3;
		final double axis = this.footnoteSeparatorAxis - FOOTNOTE_SEPARATOR_THICKNESS / 2;
		final java.awt.geom.Rectangle2D.Double rect;
		if (!flow.isVertical()) {
			// TB: 版面下端寄りの水平線(行方向の始端から1/3)
			rect = new java.awt.geom.Rectangle2D.Double(0, axis, length, FOOTNOTE_SEPARATOR_THICKNESS);
		} else if (flow == net.zamasoft.foliojet.layout.box.params.WritingMode.RL) {
			// vertical-rl: block-end=左端側の垂直線
			rect = new java.awt.geom.Rectangle2D.Double(
					this.getInnerPageExtent(flow) - axis - FOOTNOTE_SEPARATOR_THICKNESS, 0,
					FOOTNOTE_SEPARATOR_THICKNESS, length);
		} else {
			// vertical-lr: block-end=右端側の垂直線
			rect = new java.awt.geom.Rectangle2D.Double(axis, 0, FOOTNOTE_SEPARATOR_THICKNESS, length);
		}
		drawer.artifactView().visitDrawable(new FootnoteSeparatorDrawable(this, rect), rect.x, rect.y);
	}

	/** separator罫線のdrawableです(装飾。構造要素に入れない)。 */
	private static final class FootnoteSeparatorDrawable
			extends net.zamasoft.foliojet.layout.draw.AbstractDrawable {
		private final java.awt.geom.Rectangle2D.Double rect;

		FootnoteSeparatorDrawable(final PageBox pageBox, final java.awt.geom.Rectangle2D.Double rect) {
			super(pageBox, null, 1f, new AffineTransform());
			this.rect = rect;
		}

		@Override
		public void innerDraw(final net.zamasoft.pdfg2d.gc.GC gc, final double x, final double y)
				throws net.zamasoft.pdfg2d.gc.GraphicsException {
			try (final var state = gc.begin()) {
				gc.setFillPaint(net.zamasoft.pdfg2d.gc.paint.GrayColor.BLACK);
				gc.fill(new java.awt.geom.Rectangle2D.Double(x, y, this.rect.width, this.rect.height));
			}
		}

		@Override
		public String describe() {
			return String.format(java.util.Locale.ROOT, "FootnoteSeparator[w=%.2f h=%.2f]", this.rect.width,
					this.rect.height);
		}
	}

	public final void drawFixed(Drawer drawer, Visitor visitor) {
		double x = this.offsetX + this.frame.getFrameLeft() - this.frame.margin.left;
		double y = this.offsetY + this.frame.getFrameTop() - this.frame.margin.top;
		if (this.fixeds != null) {
			for (int i = 0; i < this.fixeds.size(); ++i) {
				Fixed c = (Fixed) this.fixeds.get(i);
				c.box.draw(this, drawer, visitor, null, new AffineTransform(), x, y, c.x, c.y);
			}
		}
		if (this.toAddFixeds != null && !this.toAddFixeds.isEmpty()) {
			if (this.fixeds == null) {
				this.fixeds = new ArrayList<Fixed>();
			}
			this.fixeds.addAll(this.toAddFixeds);
			this.toAddFixeds.clear();
		}
	}

	public final void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape) {
		if (this.fixeds == null) {
			return;
		}
		for (int i = 0; i < this.fixeds.size(); ++i) {
			Fixed fixed = (Fixed) this.fixeds.get(i);
			builder.addBound(fixed.box);
		}
	}
}
