package net.zamasoft.foliojet.ua.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.net.URI;
import java.net.URISyntaxException;

import net.zamasoft.foliojet.css.StructureElement;
import net.zamasoft.foliojet.css.util.LengthUtils;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.InlineFragmentView;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.visitor.Visitor;

import net.zamasoft.foliojet.ua.Counter;
import net.zamasoft.foliojet.ua.CounterScope;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.ImageMap;
import net.zamasoft.foliojet.ua.PageRef;
import net.zamasoft.foliojet.ua.PendingStringSet;
import net.zamasoft.foliojet.ua.SectionState;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.Constants;
import net.zamasoft.foliojet.xml.vocab.CSSJML;
import net.zamasoft.foliojet.xml.vocab.XHTML;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.foliojet.css.token.Unit;

public abstract class AbstractVisitor implements Visitor {
	/**
	 * Converts an id to an in-document reference ({@code #…}). Encodes characters invalid in URIs
	 * (spaces, {@code %}, etc.). 2026-10-06, jigensha report: {@code id="with space"} emitted warning 10252.
	 * Links are written encoded as {@code href="#with%20space"}, so this produces the same form.
	 */
	private static String fragment(final String id) throws URISyntaxException {
		return "#" + new URI(null, null, id).getRawFragment();
	}

	private static boolean isHyperlinkBox(BoxType type) {
		switch (type) {
		case LINE:
		case REPLACED:
		case INLINE:
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

	private static boolean isMarkupBox(BoxType type) {
		switch (type) {
		case PAGE:
		case TEXT_BLOCK:
		case LINE:
		case TABLE:
		case TABLE_COLUMN_GROUP:
		case TABLE_COLUMN:
			return false;
		}
		return true;
	}

	private static void appendSemanticText(final IBox box, final StringBuilder text) {
		if (box instanceof InlineFragmentView fragment) {
			fragment.appendSemanticText(text);
		} else {
			box.getText(text);
		}
	}

	protected final UserAgent ua;
	private Counter[] counters = null;
	private boolean processPageReference;

	/**
	 * Registers only per-id counters to fill {@code target-counter()} slots later in single-pass PDF
	 * (2026-10-04). Captures body text and sections only when page references are enabled.
	 */
	private final boolean slotCounters;
	private boolean hyperlinks;

	private boolean fragments;

	private boolean bookmarks;

	private boolean forms;

	/** The drawer for the box currently being visited (set in visitBox). */
	protected Drawer drawer;

	/** Form controls already emitted on the current page (dedup by identity). */
	private final java.util.Set<StructureElement> emittedControls = java.util.Collections
			.newSetFromMap(new java.util.IdentityHashMap<>());

	protected AbstractVisitor(UserAgent ua) {
		this.ua = ua;
		this.setProcessPageReference(UAProps.PROCESSING_PAGE_REFERENCES.getBoolean(this.ua));
		this.slotCounters = net.zamasoft.foliojet.layout.box.impl.TargetCounterSlotImage.available(ua);
	}

	protected abstract void addFragment(String id, Point2D location);

	protected abstract void addLink(Shape s, URI uri, StructureElement ce, String contents);

	/**
	 * Emits an interactive PDF form field for a simple HTML form control
	 * (input/textarea). The default implementation does nothing; PDF output
	 * overrides it.
	 *
	 * @param rect the widget rectangle in page coordinates
	 * @param box  the control's box (for reading textarea content)
	 * @param ce   the control element (input/textarea)
	 */
	protected void addFormField(Shape rect, IBox box, StructureElement ce) {
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
	protected void beginSelect(Shape rect, StructureElement ce) {
		// no-op by default
	}

	/**
	 * Reports an {@code <option>} belonging to the most recently begun
	 * {@code <select>}. No-op by default.
	 *
	 * @param optionCe  the option element
	 * @param optionBox the option box (for its label text)
	 */
	protected void addSelectOption(StructureElement optionCe, IBox optionBox) {
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

	public void setForms(boolean forms) {
		this.forms = forms;
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

	/** Registers running and all string-set assignments exactly once from finalized page anchors. */
	@Override
	public void visitAssignment(final net.zamasoft.foliojet.css.style.running.RunningRegistry.Placement placement) {
		this.ua.getPassContext().getRunningRegistry().assign(placement);
		StringBuilder text = null;
		for (final PendingStringSet assignment : placement.strings()) {
			final StringBuilder resolved = new StringBuilder();
			for (final Object part : assignment.parts) {
				if (part == PendingStringSet.CONTENT) {
					if (text == null) {
						text = new StringBuilder();
						if (placement.sourceText() != null) {
							text.append(placement.sourceText());
						} else {
							appendSemanticText(placement.box(), text);
						}
					}
					resolved.append(text);
				} else {
					resolved.append((String) part);
				}
			}
			this.ua.getPassContext().getStringState().assign(assignment.name, resolved.toString(),
					assignment.order, placement.beginsPage());
			// Also delivers values completed by content() to build-side forward references (string() in body text).
			// String/counter-only assignments were registered at build time under this order (last-wins, same value)
			this.ua.getPassContext().getBuildStringState().complete(assignment.name, resolved.toString(),
					assignment.order);
		}
	}

	public void startPage() {
		// ignore
	}

	public void endPage() {
		if (this.forms) {
			this.flushForms();
		}
		// Process string-set (GCPM) unconditionally: it is independent and does not piggyback on a specific feature.
		this.ua.getPassContext().getStringState().endPage();
		this.ua.getPassContext().getBuildStringState().endPage();
		this.ua.getPassContext().getRunningRegistry().endPage();
	}

	public void visitBox(AffineTransform transform, IBox box, Drawer drawer, double x, double y) {
		this.drawer = drawer;
		// E-6 increment 3b-4: the element of a source-replayed box is a StructureToken
		// (not CSSElement); read through the shared StructureElement contract
		final StructureElement ce = box.getParams().element;
		if (ce == null || ce.atts() == null) {
			return;
		}

		// @container G4 (2026-08-15, stage 4, the development records §2):
		// At this point after layout is finalized, write the query container's
		// (container-type: inline-size, already recorded by StyleEventMachine.startStyle)
		// used inline-size to ContainerFacts. The flow axis determines
		// whether width or height is the inline axis (height in vertical writing).
		// StyleContext.merge in the next pass reads it (not used to evaluate later elements
		// in this pass: the design uses pass N dimensions for pass N+1 queries)
		if (ce.elementKey() >= 0) {
			final net.zamasoft.foliojet.ua.ContainerFacts containerFacts = this.ua.getUAContext()
					.getContainerFacts();
			if (containerFacts.isInlineSizeContainer(ce.elementKey()) && box.getParams() instanceof BlockParams bp) {
				final double inlineSize = bp.flow.isVertical() ? box.getHeight() : box.getWidth();
				containerFacts.setInlineSize(ce.elementKey(), inlineSize);
			}
		}

		final PageRef pageRef;
		if (this.processPageReference) {
			pageRef = this.ua.getUAContext().getPageRef();
		} else {
			pageRef = null;
		}
		final PageRef counterRef = pageRef != null || !this.slotCounters ? pageRef
				: this.ua.getUAContext().getPageRef();

		final BoxType type = box.getType();
		// Hyperlinks
		if (this.hyperlinks && isHyperlinkBox(type)) {
			// Anchor tag
			String href = null;
			URI uri = null;
			try {
				href = Constants.XLINK_HREF_ATTR.getValue(ce.atts());
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
				appendSemanticText(box, tb);
				String contents = tb.toString().trim();
				this.addLink(s, uri, ce, contents.isEmpty() ? null : contents);
			}
			
			if (type == BoxType.REPLACED) {
				// Image map
				String usemap = XHTML.USEMAP_ATTR.getValue(ce.atts());
				if (usemap != null && usemap.startsWith("#")) {
					usemap = usemap.substring(1);
					ImageMap imageMap = this.ua.getUAContext().getImageMaps().get(usemap);
					if (imageMap != null) {
						// Translation uses physical coordinates; area coordinates are image-local px.
						// Therefore, the order must be translate → scale
						// (previously scale → translate also applied the px → pt scale factor
						// to the position. The SVG link code immediately below had the correct order
						// and provided counterevidence. 2026-07-25)
						final double f = LengthUtils.convert(this.ua, 1.0, Unit.PX, Unit.PT);
						final AffineTransform t2 = AffineTransform.getTranslateInstance(x, y);
						t2.scale(f, f);
						for (ImageMap.Area area : imageMap) {
							Shape s;
							if (area.shape == null) {
								// shape="default" = entire image. Physical coordinates, so do not pass through t2
								s = new java.awt.geom.Rectangle2D.Double(x, y, box.getWidth(), box.getHeight());
							} else {
								s = area.shape;
								if (!t2.isIdentity()) {
									s = t2.createTransformedShape(s);
								}
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
				if (imageMap != null && box.getInnerWidth() > 0 && box.getInnerHeight() > 0
						&& params.image.getWidth() > 0 && params.image.getHeight() > 0) {
					// Match the actual drawing rectangle from object-fit/object-position (shares
					// geometry with ReplacedBoxDrawable used for drawing; 2026-08-27).
					// The default (fill, centered) yields the same transform as before
					final double[] r = net.zamasoft.foliojet.layout.box.AbstractReplacedBox.objectFitRect(
							params.objectFit, params.objectPosition, params.image.getWidth(),
							params.image.getHeight(), box.getInnerWidth(), box.getInnerHeight());
					AffineTransform t2 = AffineTransform.getTranslateInstance(x + r[0], y + r[1]);
					t2.scale(r[2] / params.image.getWidth(), r[3] / params.image.getHeight());
					// Overflow outside the content box (cover/none, etc.) is clipped and invisible,
					// so intersect annotations with the content box as well
					final boolean fitOverflows = r[0] < -0.001 || r[1] < -0.001
							|| r[0] + r[2] > box.getInnerWidth() + 0.001
							|| r[1] + r[3] > box.getInnerHeight() + 0.001;
					final Rectangle2D fitContentBox = fitOverflows
							? new Rectangle2D.Double(x, y, box.getInnerWidth(), box.getInnerHeight())
							: null;
					for(ImageMap.Area link : imageMap) {
						Shape s = link.shape;
						if (!t2.isIdentity()) {
							s = t2.createTransformedShape(s);
						}
						if (fitContentBox != null) {
							final java.awt.geom.Area clipped = new java.awt.geom.Area(s);
							clipped.intersect(new java.awt.geom.Area(fitContentBox));
							if (clipped.isEmpty()) {
								continue;
							}
							s = clipped;
						}
						if (!transform.isIdentity()) {
							s = transform.createTransformedShape(s);
						}
						this.addLink(s, link.href, null, null);
					}
				}
			}
		}

		// Bidi visual fragments retain each fragment's link rectangle, while semantic side effects
		// (IDs, reference strings, string-set, bookmarks, etc.) run once in the fragment containing the logical start.
		if (box instanceof InlineFragmentView fragment && !fragment.hasLineStartEdge()) {
			return;
		}

		// Output form controls as interactive form fields
		if (this.forms && (type == BoxType.REPLACED || type == BoxType.BLOCK) && ce.lName() != null
				&& this.emittedControls.add(ce)) {
			final String lName = ce.lName().toLowerCase(java.util.Locale.ROOT);
			if (lName.equals("input") || lName.equals("textarea")) {
				this.addFormField(controlRect(transform, box, x, y), box, ce);
			} else if (lName.equals("select")) {
				this.beginSelect(controlRect(transform, box, x, y), ce);
			} else if (lName.equals("option")) {
				this.addSelectOption(ce, box);
			}
		}

		// Fragments
		if ((this.fragments || counterRef != null) && isMarkupBox(type)) {
			String id = XHTML.ID_ATTR.getValue(ce.atts());
			if (id != null) {
				if (this.fragments || pageRef != null) {
					// When page references are used, emit fragments in either case
					Point2D location = new Point2D.Double(x, y);
					if (!transform.isIdentity()) {
						location = transform.transform(location, location);
					}
					this.addFragment(id, location);
				}
				if (counterRef != null) {
					// Page references
					try {
						URI uri = URIHelper.resolve(this.ua.getDocumentContext().getEncoding(),
								this.ua.getDocumentContext().getBaseURI(), fragment(id));
						String text = null;
						if (pageRef != null) {
							// Also capture text for target-text()
							StringBuilder textBuff = new StringBuilder();
							appendSemanticText(box, textBuff);
							text = textBuff.length() == 0 ? null : textBuff.toString();
						}
						counterRef.addFragment(uri, this.getCounters(), text);
					} catch (URISyntaxException e) {
						this.ua.message(MessageCodes.WARN_BAD_LINK_URI, e.getMessage());
					}
				}
			}
		}

		// Bookmarks
		if ((this.bookmarks || pageRef != null) && isMarkupBox(type)) {
			String header = CSSJML.HEADER_ATTR.getValue(ce.atts());
			// bookmark-level/bookmark-label (2026-10-04): when specified, take precedence over the heading's
			// level/text. none (0) suppresses bookmarks/sections even for headings
			final net.zamasoft.foliojet.layout.box.params.BookmarkSpec bookmark = box.getParams().bookmark;
			if (bookmark != null && bookmark.level() != net.zamasoft.foliojet.layout.box.params.BookmarkSpec.LEVEL_AUTO) {
				header = bookmark.level() == 0 ? null : String.valueOf(bookmark.level());
			}
			if (header != null) {
				// Heading processing
				try {
					int level = Integer.parseInt(header);
					SectionState state = this.ua.getPassContext().getSectionState();

					StringBuilder textBuff = new StringBuilder();
					appendSemanticText(box, textBuff);
					String title;
					if (textBuff.length() == 0) {
						title = null;
					} else {
						title = textBuff.toString();
					}
					if (bookmark != null) {
						title = bookmark.title(title);
					}
					this.ua.message(MessageCodes.INFO_HEADING_TITLE, title == null ? "" : title);

					for (int j = state.sectionLevel - level; j >= 0 && state.sectionDepth > 0; --j) {
						// End of heading
						if (this.bookmarks) {
							this.endBookmark();
						}
						if (pageRef != null) {
							pageRef.endSection();
						}
						--state.sectionDepth;
						--state.sectionLevel;
					}

					String ref = "cssj-header-" + (++state.sectionCount);

					Point2D location = null;
					if (this.bookmarks || pageRef != null) {
						location = new Point2D.Double(x, y);
						if (!transform.isIdentity()) {
							location = transform.transform(location, location);
						}
					}

					// Start of heading
					if (this.bookmarks) {
						// Bookmarks
						this.startBookmark(title, location);
					}
					if (pageRef != null) {
						// Page references
						try {
							URI uri = URIHelper.resolve(this.ua.getDocumentContext().getEncoding(),
									this.ua.getDocumentContext().getBaseURI(), fragment(ref));
							pageRef.startSection(uri, title, this.getCounters());
							this.addFragment(ref, location);
						} catch (URISyntaxException e) {
							this.ua.message(MessageCodes.WARN_BAD_LINK_URI, e.getMessage());
						}
					}

					++state.sectionDepth;
					state.sectionLevel = level;
				} catch (NumberFormatException e) {
					this.ua.message(MessageCodes.WARN_BAD_HEADER, header);
				}
			}

			if (ce.atts() != null) {
				// Annotations
				String annot = CSSJML.ANNOT_ATTR.getValue(ce.atts());
				if (annot != null) {
					this.ua.message(MessageCodes.INFO_ANNOTATION, annot);
				}
			}
		}
	}
};
