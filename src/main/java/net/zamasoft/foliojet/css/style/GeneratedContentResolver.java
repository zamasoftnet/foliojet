package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.counterstyle.CounterStyles;
import net.zamasoft.foliojet.css.util.GeneratedValueUtils;
import net.zamasoft.foliojet.css.value.AttrValue;
import net.zamasoft.foliojet.css.value.CounterValue;
import net.zamasoft.foliojet.css.value.CountersValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.TargetCounterValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.CounterScope;
import net.zamasoft.foliojet.ua.PageRef;
import net.zamasoft.foliojet.ua.PageRef.Fragment;
import net.zamasoft.foliojet.ua.PassContext;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Resolves generated-content references (2026-08-01, increment 14 of the 85-point plan:
 * separated string-set/target-* resolution logic from StyleEventMachine).
 * Handles only resolution across passes: counter scopes, cross-document references
 * (PageRef), and convergence warnings. StyleEventMachine retains emission to the sink.
 * Resolution rules can be unit-tested without a sink.
 *
 * @author MIYABE Tatsuhiko
 */
final class GeneratedContentResolver {
	private final UserAgent ua;

	GeneratedContentResolver(final UserAgent ua) {
		this.ua = ua;
	}

	String stringSetPart(Value part, CSSElement ce, int depth) {
		if (part instanceof StringValue str) {
			return str.getString();
		} else if (part instanceof CounterValue counter) {
			final String name = counter.getName();
			final short counterStyle = counter.getStyle();
			int number = 0;
			final PassContext pc = this.ua.getPassContext();
			for (int level = depth; level >= 0; --level) {
				CounterScope scope = pc.getCounterScope(level, false);
				if (scope != null && scope.defined(name)) {
					number = scope.get(name);
					break;
				}
			}
			final String str = CounterStyles.of(this.ua).format(number, counterStyle);
			return str != null ? str : "";
		} else if (part instanceof CountersValue counters) {
			final String name = counters.getName();
			final String delim = counters.getDelimiter();
			final short counterStyle = counters.getStyle();
			final StringBuilder buff = new StringBuilder();
			final PassContext pc = this.ua.getPassContext();
			boolean first = true;
			for (int level = 0; level <= depth; ++level) {
				CounterScope scope = pc.getCounterScope(level, false);
				if (scope != null && scope.defined(name)) {
					if (!first && delim != null && delim.length() > 0) {
						buff.append(delim);
					}
					first = false;
					final String str = CounterStyles.of(this.ua).format(scope.get(name), counterStyle);
					if (str != null) {
						buff.append(str);
					}
				}
			}
			return buff.toString();
		} else if (part instanceof AttrValue attr) {
			if (ce.atts != null) {
				final String str = ce.atts.getValue(attr.getName());
				if (str != null) {
					return str;
				}
			}
			return "";
		}
		return "";
	}

	/**
	 * Resolves target references (ATTR/REF) for {@code target-counter()} and related
	 * functions / {@code target-text()} to a {@code "#id"} string (or href) for querying
	 * {@code PageRef}. Returns {@code null} if the attribute value is absent.
	 */
	static String targetRef(byte type, String ref, CSSStyle style) {
		switch (type) {
		case TargetCounterValue.ATTR: {
			// From an attribute
			CSSElement parentCe = style.getParentStyle().getCSSElement();
			if (parentCe.atts == null) {
				return null;
			}
			String str = parentCe.atts.getValue(ref);
			if (str == null) {
				return null;
			}
			if (!ref.equals("href") && str.indexOf("#") == -1) {
				// For compatibility
				str = "#" + str;
			}
			return str;
		}
		case TargetCounterValue.REF: {
			// ID specification
			String id = ref;
			if (id.indexOf("#") == -1) {
				// For compatibility
				id = "#" + id;
			}
			return id;
		}
		default:
			throw new IllegalStateException();
		}
	}

/**
	 * Lightweight convergence check: warns once per document if a fragment resolved on
	 * the final pass is finalized using a stale value from one or more passes earlier,
	 * rather than one written in this pass. Does not detect oscillation or retry
	 * automatically (same policy as the decision to abandon automatic promotion).
	 */
	void checkConverged(PageRef pageRef, Fragment frag, String counter) {
		if (!this.ua.isLastPass()) {
			return;
		}
		if (frag.generation < pageRef.getGeneration()) {
			// Forward reference (the target has not yet been laid out in this pass). Reading
			// the previous pass's value is normal; non-convergence occurs **only when that value
			// changes in this pass**. PageRef checks when the target is rewritten,
			// and the warning is issued only once after the final pass completes.
			// A null counter means body text (target-text()) was read.
			if (counter == null) {
				frag.markStaleText();
			} else {
				frag.markStaleCounter(counter);
			}
		}
	}
}
