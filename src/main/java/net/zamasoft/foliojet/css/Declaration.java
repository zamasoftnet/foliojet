package net.zamasoft.foliojet.css;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.property.CustomProperty;
import net.zamasoft.foliojet.css.property.Property;

/**
 * A style declaration.
 * 
 * <p>
 * A style declaration is the part of CSS that lists properties.
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 */
public class Declaration {
	private final List<Property> properties = new ArrayList<Property>();

	/**
	 * Merges style declarations.
	 * 
	 * @param declaration
	 *            the style declaration to add; does nothing if null
	 */
	public void merge(Declaration declaration) {
		if (declaration == null) {
			return;
		}
		for (int i = 0; i < declaration.getLength(); ++i) {
			Property property = declaration.get(i);
			this.addProperty(property);
		}
	}

	/**
	 * Adds only the {@code !important} declarations of another declaration (2026-10-08): appended after the normal
	 * cascade, they have the effect of {@link #applyImportantProperties} in one {@link #applyProperties}.
	 */
	public void mergeImportant(Declaration declaration) {
		for (int i = 0; i < declaration.getLength(); ++i) {
			final Property property = declaration.get(i);
			if (property.isImportant()) {
				this.addProperty(property);
			}
		}
	}

	/**
	 * Adds a property.
	 * 
	 * @param property
	 */
	public void addProperty(Property property) {
		this.properties.add(property);
	}

	public Property get(int i) {
		return (Property) this.properties.get(i);
	}

	public int getLength() {
		return this.properties.size();
	}

	/**
	 * Applies properties in order from the beginning.
	 * <p>
	 * Use two passes to apply custom properties ({@link CustomProperty}) before all other
	 * properties (which may reference var()). The CSS specification requires
	 * var() substitution during used-value calculation, after all custom properties
	 * on the element are determined. A reference must not become unavailable
	 * merely because the source order (cascade order) on the same element happens
	 * to apply the var()-using declaration first. Preserve the relative
	 * order (cascade order) within each of the two passes.
	 * </p>
	 *
	 * @param style
	 */
	public void applyProperties(CSSStyle style) {
		for (int i = 0; i < this.properties.size(); ++i) {
			Property property = (Property) this.properties.get(i);
			if (property instanceof CustomProperty) {
				property.applyProperty(style);
			}
		}
		for (int i = 0; i < this.properties.size(); ++i) {
			Property property = (Property) this.properties.get(i);
			if (!(property instanceof CustomProperty)) {
				property.applyProperty(style);
			}
		}
	}

	/**
	 * Applies only declarations with {@code !important} (added on 2026-08-03).
	 *
	 * <p>
	 * Used to express <b>priority reversal</b> when combining {@code @layer} and {@code !important}
	 * (CSS Cascade 5: among important declarations, unlayered ones are weakest
	 * and earlier layers are stronger). After applying the cascade once in normal order,
	 * apply only important declarations <b>again in reverse order</b>
	 * ({@link CSSStyle#set} lets later important declarations win, so the strongest
	 * important declaration is applied last). Leave non-important declarations untouched
	 * here to avoid applying normal declarations twice.
	 * </p>
	 */
	public void applyImportantProperties(CSSStyle style) {
		for (int i = 0; i < this.properties.size(); ++i) {
			Property property = (Property) this.properties.get(i);
			if (property.isImportant() && property instanceof CustomProperty) {
				property.applyProperty(style);
			}
		}
		for (int i = 0; i < this.properties.size(); ++i) {
			Property property = (Property) this.properties.get(i);
			if (property.isImportant() && !(property instanceof CustomProperty)) {
				property.applyProperty(style);
			}
		}
	}

	public String toString() {
		StringBuilder buff = new StringBuilder();
		for (int i = 0; i < this.properties.size(); ++i) {
			buff.append(this.properties.get(i));
			buff.append(";\n");
		}
		return buff.toString();
	}
}
