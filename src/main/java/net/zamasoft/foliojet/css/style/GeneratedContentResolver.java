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
 * 生成コンテンツの参照解決です(2026-08-01、85点計画増分14——
 * StyleEventMachineからstring-set/target-*系の解決ロジックを分離)。
 * カウンタスコープ・文書横断参照(PageRef)・収束警告という
 * 「パス跨ぎの解決」だけを持ち、sinkへの発行はStyleEventMachineに残る。
 * sinkなしで解決規則を単体テストできる。
 *
 * @author MIYABE Tatsuhiko
 */
final class GeneratedContentResolver {
	private static final java.util.logging.Logger LOG = java.util.logging.Logger
			.getLogger(GeneratedContentResolver.class.getName());

	private final UserAgent ua;

	private boolean warnedUnconvergedTarget = false;

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

	static String targetRef(byte type, String ref, CSSStyle style) {
		switch (type) {
		case TargetCounterValue.ATTR: {
			// 属性から
			CSSElement parentCe = style.getParentStyle().getCSSElement();
			if (parentCe.atts == null) {
				return null;
			}
			String str = parentCe.atts.getValue(ref);
			if (str == null) {
				return null;
			}
			if (!ref.equals("href") && str.indexOf("#") == -1) {
				// 互換性のため
				str = "#" + str;
			}
			return str;
		}
		case TargetCounterValue.REF: {
			// ID指定
			String id = ref;
			if (id.indexOf("#") == -1) {
				// 互換性のため
				id = "#" + id;
			}
			return id;
		}
		default:
			throw new IllegalStateException();
		}
	}

/**
	 * 収束性の軽量チェック: 最終パスで解決したフラグメントが今回パスで
	 * 書き込まれたものではなく(1パス以上前のstaleな値のまま)確定した
	 * 場合、1文書につき1回だけ警告する。振動検出・自動再試行は行わない
	 * (自動昇格断念の判断と同じ方針)。
	 */
	void checkConverged(PageRef pageRef, Fragment frag) {
		if (this.warnedUnconvergedTarget || !this.ua.isLastPass()) {
			return;
		}
		if (frag.generation < pageRef.getGeneration()) {
			this.warnedUnconvergedTarget = true;
			LOG.warning("target-counter()/target-counters()/target-text() did not resolve to a fresh value "
					+ "by the final layout pass; consider increasing processing.pass-count.");
		}
	}
}
