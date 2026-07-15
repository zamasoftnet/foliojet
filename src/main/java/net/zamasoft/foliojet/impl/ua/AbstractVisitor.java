package net.zamasoft.foliojet.impl.ua;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.net.URI;
import java.net.URISyntaxException;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.util.LengthUtils;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.params.ReplacedParams;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.visitor.Visitor;
import net.zamasoft.foliojet.ua.Counter;
import net.zamasoft.foliojet.ua.CounterScope;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.ImageMap;
import net.zamasoft.foliojet.ua.PageRef;
import net.zamasoft.foliojet.ua.SectionState;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.Constants;
import net.zamasoft.foliojet.xml.vocab.CSSJML;
import net.zamasoft.foliojet.xml.vocab.XHTML;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.foliojet.css.token.Unit;

public abstract class AbstractVisitor implements Visitor {
	private static boolean isHyperlinkBox(short type) {
		switch (type) {
		case IBox.TYPE_LINE:
		case IBox.TYPE_REPLACED:
		case IBox.TYPE_INLINE:
			return true;
		}
		return false;
	}

	/** The page-space rectangle of a box's border edge (top-left origin). */
	private static Shape controlRect(AffineTransform transform, IBox box, double x, double y) {
		Shape s = new Rectangle2D.Double(x, y, box.getWidth(), box.getHeight());
		if (!transform.isIdentity()) {
			s = transform.createTransformedShape(s);
		}
		return s;
	}

	private static boolean isMarkupBox(short type) {
		switch (type) {
		case IBox.TYPE_PAGE:
		case IBox.TYPE_TEXT_BLOCK:
		case IBox.TYPE_LINE:
		case IBox.TYPE_TABLE:
		case IBox.TYPE_TABLE_COLUMN_GROUP:
		case IBox.TYPE_TABLE_COLUMN:
			return false;
		}
		return true;
	}

	protected final UserAgent ua;
	private Counter[] counters = null;
	private boolean processPageReference;
	private boolean hyperlinks;

	private boolean fragments;

	private boolean bookmarks;

	private boolean forms;

	/** The drawer for the box currently being visited (set in visitBox). */
	protected Drawer drawer;

	/** Form controls already emitted on the current page (dedup by identity). */
	private final java.util.Set<CSSElement> emittedControls = java.util.Collections
			.newSetFromMap(new java.util.IdentityHashMap<>());

	protected AbstractVisitor(UserAgent ua) {
		this.ua = ua;
		this.setProcessPageReference(UAProps.PROCESSING_PAGE_REFERENCES.getBoolean(this.ua));
	}

	protected abstract void addFragment(String id, Point2D location);

	protected abstract void addLink(Shape s, URI uri, CSSElement ce, String contents);

	/**
	 * Emits an interactive PDF form field for a simple HTML form control
	 * (input/textarea). The default implementation does nothing; PDF output
	 * overrides it.
	 *
	 * @param rect the widget rectangle in page coordinates
	 * @param box  the control's box (for reading textarea content)
	 * @param ce   the control element (input/textarea)
	 */
	protected void addFormField(Shape rect, IBox box, CSSElement ce) {
		// no-op by default
	}

	/**
	 * Begins collecting a {@code <select>} control; its {@code <option>}
	 * children are reported by {@link #addSelectOption} and the field is emitted
	 * at {@link #flushForms}. No-op by default.
	 *
	 * @param rect the widget rectangle in page coordinates
	 * @param ce   the select element
	 */
	protected void beginSelect(Shape rect, CSSElement ce) {
		// no-op by default
	}

	/**
	 * Reports an {@code <option>} belonging to the most recently begun
	 * {@code <select>}. No-op by default.
	 *
	 * @param optionCe  the option element
	 * @param optionBox the option box (for its label text)
	 */
	protected void addSelectOption(CSSElement optionCe, IBox optionBox) {
		// no-op by default
	}

	/** Emits any pending {@code <select>} fields collected on this page. */
	protected void flushForms() {
		// no-op by default
	}

	protected abstract void endBookmark();

	private Counter[] getCounters() {
		if (this.counters == null) {
			CounterScope counter = this.ua.getPassContext().getCounterScope(0, false);
			Counter[] counters;
			if (counter == null) {
				counters = null;
			} else {
				counters = counter.copyCounters();
			}
			this.counters = counters;
		}
		return this.counters;
	}

	public boolean isBookmarks() {
		return bookmarks;
	}

	public boolean isFragments() {
		return fragments;
	}

	public boolean isHyperlinks() {
		return hyperlinks;
	}

	public boolean isForms() {
		return forms;
	}

	public void setForms(boolean forms) {
		this.forms = forms;
	}

	public boolean isProcessPageReference() {
		return processPageReference;
	}

	public void nextPage() {
		this.counters = null;
		this.emittedControls.clear();
	}

	public void setBookmarks(boolean bookmarks) {
		this.bookmarks = bookmarks;
	}

	public void setFragments(boolean fragments) {
		this.fragments = fragments;
	}

	public void setHyperlinks(boolean hyperlinks) {
		this.hyperlinks = hyperlinks;
	}

	public void setProcessPageReference(boolean processPageReference) {
		this.processPageReference = processPageReference;
	}

	protected abstract void startBookmark(String title, Point2D location);

	public void startPage() {
		// ignore
	}

	public void endPage() {
		if (this.forms) {
			this.flushForms();
		}
		if (this.bookmarks || this.processPageReference) {
			SectionState state = this.ua.getPassContext().getSectionState();
			for (int i = 0; i < state.firstChangedSections.length; ++i) {
				state.firstChangedSections[i] = false;
				state.firstSections[i] = state.lastSections[i];
			}
		}
	}

	public void visitBox(AffineTransform transform, IBox box, Drawer drawer, double x, double y) {
		this.drawer = drawer;
		final CSSElement ce = (CSSElement) box.getParams().element;
		if (ce == null || ce.atts == null) {
			return;
		}

		final PageRef pageRef;
		if (this.processPageReference) {
			pageRef = this.ua.getUAContext().getPageRef();
		} else {
			pageRef = null;
		}

		final short type = box.getType();
		// ハイパーリンク
		if (this.hyperlinks && isHyperlinkBox(type)) {
			// Anchor tag
			String href = null;
			URI uri = null;
			try {
				href = Constants.XLINK_HREF_ATTR.getValue(ce.atts);
				if (href != null) {
					if (href.length() > 4096) {
						throw new URISyntaxException(href, "URI too long: >4096");
					}
					DocumentContext context = this.ua.getDocumentContext();
					uri = URIHelper.create(context.getEncoding(), href);
				}
			} catch (URISyntaxException e) {
				this.ua.message(MessageCodes.WARN_BAD_LINK_URI, e.getMessage());
			}
			if (uri != null) {
				double width = box.getWidth();
				double height = box.getHeight();
				Shape s = new Rectangle2D.Double(x, y, width, height);
				if (!transform.isIdentity()) {
					s = transform.createTransformedShape(s);
				}
				// The link text becomes the annotation's alt description (PDF/UA).
				final StringBuilder tb = new StringBuilder();
				box.getText(tb);
				String contents = tb.toString().trim();
				this.addLink(s, uri, ce, contents.isEmpty() ? null : contents);
			}
			
			if (type == IBox.TYPE_REPLACED) {
				// Image map
				String usemap = XHTML.USEMAP_ATTR.getValue(ce.atts);
				if (usemap != null && usemap.startsWith("#")) {
					usemap = usemap.substring(1);
					ImageMap imageMap = this.ua.getUAContext().getImageMaps().get(usemap);
					if (imageMap != null) {
						double f = LengthUtils.convert(this.ua, 1.0, Unit.PX, Unit.PT);
						AffineTransform t2 = AffineTransform.getScaleInstance(f, f);
						t2.translate(x, y);
						for (ImageMap.Area area : imageMap) {
							Shape s = area.shape;
							if (!t2.isIdentity()) {
								s = t2.createTransformedShape(s);
							}
							if (!transform.isIdentity()) {
								s = transform.createTransformedShape(s);
							}
							this.addLink(s, area.href, null, null);
						}
					}
				}
				
				// SVG Links
				ReplacedParams params = (ReplacedParams)box.getParams();
				ImageMap imageMap = this.ua.getUAContext().getImageMaps().remove(params.image);
				if (imageMap != null) {
					AffineTransform t2 = AffineTransform.getTranslateInstance(x, y);
					t2.scale(box.getInnerWidth() / params.image.getWidth(), box.getInnerHeight() / params.image.getHeight());
					for(ImageMap.Area link : imageMap) {
						Shape s = link.shape;
						if (!t2.isIdentity()) {
							s = t2.createTransformedShape(s);
						}
						if (!transform.isIdentity()) {
							s = transform.createTransformedShape(s);
						}
						this.addLink(s, link.href, null, null);
					}
				}
			}
		}

		// フォーム部品を対話フォームフィールドとして出力
		if (this.forms && (type == IBox.TYPE_REPLACED || type == IBox.TYPE_BLOCK) && ce.lName != null
				&& this.emittedControls.add(ce)) {
			final String lName = ce.lName.toLowerCase(java.util.Locale.ROOT);
			if (lName.equals("input") || lName.equals("textarea")) {
				this.addFormField(controlRect(transform, box, x, y), box, ce);
			} else if (lName.equals("select")) {
				this.beginSelect(controlRect(transform, box, x, y), ce);
			} else if (lName.equals("option")) {
				this.addSelectOption(ce, box);
			}
		}

		// フラグメント
		if ((this.fragments || pageRef != null) && isMarkupBox(type)) {
			String id = XHTML.ID_ATTR.getValue(ce.atts);
			if (id != null) {
				// ページ参照を使う場合はいずれにしてもフラグメントを出す
				Point2D location = new Point2D.Double(x, y);
				if (!transform.isIdentity()) {
					location = transform.transform(location, location);
				}
				this.addFragment(id, location);
				if (pageRef != null) {
					// ページ参照
					try {
						URI uri = URIHelper.resolve(this.ua.getDocumentContext().getEncoding(),
								this.ua.getDocumentContext().getBaseURI(), "#" + id);
						pageRef.addFragment(uri, this.getCounters());
					} catch (URISyntaxException e) {
						this.ua.message(MessageCodes.WARN_BAD_LINK_URI, e.getMessage());
					}
				}
			}
		}

		// ブックマーク
		if ((this.bookmarks || pageRef != null) && isMarkupBox(type)) {
			String header = CSSJML.HEADER_ATTR.getValue(ce.atts);
			if (header != null) {
				// 見出しの処理
				try {
					int level = Integer.parseInt(header);
					SectionState state = this.ua.getPassContext().getSectionState();

					StringBuilder textBuff = new StringBuilder();
					box.getText(textBuff);
					String title;
					if (textBuff.length() == 0) {
						title = null;
					} else {
						title = textBuff.toString();
					}
					this.ua.message(MessageCodes.INFO_HEADING_TITLE, title == null ? "" : title);

					// System.out.println(level+"/"+state.sectionLevel
					// +"/"+state.sectionDepth);
					for (int j = state.sectionLevel - level; j >= 0 && state.sectionDepth > 0; --j) {
						// 見出し終了
						if (this.bookmarks) {
							this.endBookmark();
						}
						if (pageRef != null) {
							pageRef.endSection();
						}
						--state.sectionDepth;
						--state.sectionLevel;
						state.lastSections[state.sectionLevel] = null;
					}

					String ref = "cssj-header-" + (++state.sectionCount);

					Point2D location = null;
					if (this.bookmarks || pageRef != null) {
						location = new Point2D.Double(x, y);
						if (!transform.isIdentity()) {
							location = transform.transform(location, location);
						}
					}

					// 見出し開始
					if (this.bookmarks) {
						// ブックマーク
						this.startBookmark(title, location);
					}
					if (pageRef != null) {
						// ページ参照
						try {
							URI uri = URIHelper.resolve(this.ua.getDocumentContext().getEncoding(),
									this.ua.getDocumentContext().getBaseURI(), "#" + ref);
							pageRef.startSection(uri, title, this.getCounters());
							this.addFragment(ref, location);
						} catch (URISyntaxException e) {
							this.ua.message(MessageCodes.WARN_BAD_LINK_URI, e.getMessage());
						}
					}

					++state.sectionDepth;
					state.sectionLevel = level;
					if (!state.firstChangedSections[level - 1]) {
						state.firstSections[level - 1] = title;
						state.firstChangedSections[level - 1] = true;
					}
					state.lastSections[level - 1] = title;
				} catch (NumberFormatException e) {
					this.ua.message(MessageCodes.WARN_BAD_HEADER, header);
				}
			}

			if (ce.atts != null) {
				// アノテーション
				String annot = CSSJML.ANNOT_ATTR.getValue(ce.atts);
				if (annot != null) {
					this.ua.message(MessageCodes.INFO_ANNOTATION, annot);
				}
			}
		}
	}
};
