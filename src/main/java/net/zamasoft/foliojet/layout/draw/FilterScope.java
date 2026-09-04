package net.zamasoft.foliojet.layout.draw;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.zamasoft.foliojet.css.value.css3.FilterValue;
import net.zamasoft.foliojet.layout.util.AbstractDelegatingGC;
import net.zamasoft.foliojet.layout.util.DelegatingGC;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * {@code filter}を要素ごとの層で掛けているあいだ、その層のGCを包む印です
 * (2026-09-03新設、filter-element-group-design.md §2-4)。
 *
 */
public final class FilterScope extends AbstractDelegatingGC implements GroupImageGC {
	private final GroupImageGC group;
	private final Set<FilterValue> enclosing;

	public FilterScope(final GroupImageGC group, final FilterScope outer, final FilterValue filter) {
		super(group);
		this.group = group;
		this.enclosing = Collections.newSetFromMap(new IdentityHashMap<FilterValue, Boolean>());
		if (outer != null) {
			this.enclosing.addAll(outer.enclosing);
		}
		this.enclosing.add(filter);
	}

	public static FilterValue effective(GC gc, final FilterValue filter) {
		while (gc != null) {
			if (gc instanceof FilterScope scope) {
				return filter.excluding(scope.enclosing);
			}
			gc = gc instanceof DelegatingGC d ? d.delegate() : null;
		}
		return filter;
	}

	@Override
	public Image finish() throws GraphicsException {
		return this.group.finish();
	}
}
