package net.zamasoft.foliojet.css.counterstyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.css.util.GeneratedValueUtils;
import net.zamasoft.foliojet.css.value.CounterStyleValue;
import net.zamasoft.foliojet.css.value.ListStyleTypeSource;
import net.zamasoft.foliojet.css.value.ListStyleTypeValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * The per-document registry of author-defined counter styles ({@code @counter-style})
 * and the <b>entry point for counter formatting</b> (2026-08-02; ranked fifth in PLAN §2).
 *
 * <p>
 * Retains the existing design of passing counter styles as codes ({@code short}).
 * Built-in styles use {@link ListStyleTypeValue} constants; author-defined styles
 * receive a code per name starting at {@link ListStyleTypeValue#FIRST_CUSTOM}.
 * Name-to-code assignment works even if {@code list-style-type: foo}
 * is read before its {@code @counter-style} rule (the definition is filled in later),
 * because CSS imposes no rule source-order constraint.
 * </p>
 *
 * <p>
 * Formatting goes through this entry point, which delegates built-in codes to {@link GeneratedValueUtils}.
 * This placement keeps dependencies one-way (counterstyle → util),
 * and lets {@code fallback}/{@code extends} referencing built-in styles
 * resolve through the same entry point.
 * </p>
 */
public final class CounterStyles {

	/**
	 * Resolves a counter style name to a value (its constant if built-in;
	 * otherwise assigns a document-specific code as an author-defined style).
	 */
	public static ListStyleTypeSource styleValue(final UserAgent ua, final String name) {
		final ListStyleTypeValue builtin = GeneratedValueUtils.toListStyleType(name);
		if (builtin != null) {
			return builtin;
		}
		return new CounterStyleValue(ua.getUAContext().getCounterStyles().code(name));
	}

	/** Resolves a counter style name to a code. */
	public static short styleCode(final UserAgent ua, final String name) {
		return styleValue(ua, name).getListStyleType();
	}

	/** The document's counter style registry. */
	public static CounterStyles of(final UserAgent ua) {
		return ua.getUAContext().getCounterStyles();
	}

	private final Map<String, Short> nameToCode = new HashMap<>();

	private final List<CounterStyleDef> defs = new ArrayList<>();

	/**
	 * Returns the code for a name (also assigns unknown names;
	 * if undefined, formatting falls back to {@code decimal}, as specified).
	 */
	public synchronized short code(final String name) {
		final String key = name.toLowerCase();
		final Short existing = this.nameToCode.get(key);
		if (existing != null) {
			return existing;
		}
		final short code = (short) (ListStyleTypeValue.FIRST_CUSTOM + this.defs.size());
		if (code < ListStyleTypeValue.FIRST_CUSTOM) {
			// Code allocation exhausted (unrealistic in practice): fall back to decimal.
			return ListStyleTypeValue.DECIMAL;
		}
		this.nameToCode.put(key, code);
		this.defs.add(null);
		return code;
	}

	/** Registers a definition (later definitions of the same name win, per CSS rules). */
	public synchronized void define(final String name, final CounterStyleDef def) {
		final short code = this.code(name);
		if (code >= ListStyleTypeValue.FIRST_CUSTOM) {
			this.defs.set(code - ListStyleTypeValue.FIRST_CUSTOM, def);
		}
	}

	/** The definition for a code (null if undefined). */
	public synchronized CounterStyleDef def(final short code) {
		final int index = code - ListStyleTypeValue.FIRST_CUSTOM;
		return index >= 0 && index < this.defs.size() ? this.defs.get(index) : null;
	}

	/**
	 * Formats a counter. Delegates built-in styles to {@link GeneratedValueUtils}
	 * and follows the definition for author-defined styles. Returns null for symbolic
	 * markers that cannot be represented as strings (disc, etc.).
	 */
	public String format(final int number, final short style) {
		return this.format(number, style, 0);
	}

	private String format(final int number, final short style, final int depth) {
		if (style < ListStyleTypeValue.FIRST_CUSTOM) {
			return GeneratedValueUtils.format(number, style);
		}
		final CounterStyleDef def = this.def(style);
		if (def == null) {
			// Undefined names use decimal (§CSS Counter Styles 3 §7).
			return String.valueOf(number);
		}
		final String str = def.format(number, this, depth);
		return str != null ? str : String.valueOf(number);
	}

	/** Resolves {@code fallback}/{@code extends} (follows names). */
	String formatByName(final String name, final int number, final int depth) {
		if (depth > 8) {
			return String.valueOf(number);
		}
		final ListStyleTypeValue builtin = GeneratedValueUtils.toListStyleType(name);
		if (builtin != null) {
			return GeneratedValueUtils.format(number, builtin.getListStyleType());
		}
		return this.format(number, this.code(name), depth);
	}

	/** Marker prefix string (empty for built-in styles). */
	public String prefix(final short style) {
		final CounterStyleDef def = style >= ListStyleTypeValue.FIRST_CUSTOM ? this.def(style) : null;
		return def == null ? "" : def.prefix;
	}

	/** Marker suffix string ({@code "."}, etc. for built-in styles). */
	public String suffix(final short style) {
		if (style < ListStyleTypeValue.FIRST_CUSTOM) {
			return GeneratedValueUtils.period(style);
		}
		final CounterStyleDef def = this.def(style);
		return def == null ? "." : def.suffix;
	}
}
