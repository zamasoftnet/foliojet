package net.zamasoft.foliojet.css.value.ext;

import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList.FontPolicy;
import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSJFontPolicyValue implements Value {
	private static final long serialVersionUID = 1L;

	public static final CSSJFontPolicyValue CORE_CID_KEYED_VALUE = new CSSJFontPolicyValue(
			new FontPolicy[] { FontPolicy.CORE, FontPolicy.CID_KEYED });

	public static final CSSJFontPolicyValue CORE_CID_IDENTITY_VALUE = new CSSJFontPolicyValue(
			new FontPolicy[] { FontPolicy.CORE, FontPolicy.CID_IDENTITY });

	public static final CSSJFontPolicyValue CORE_EMBEDDED_VALUE = new CSSJFontPolicyValue(
			new FontPolicy[] { FontPolicy.CORE, FontPolicy.EMBEDDED });

	public static final CSSJFontPolicyValue OUTLINES_VALUE = new CSSJFontPolicyValue(
			new FontPolicy[] { FontPolicy.OUTLINES, FontPolicy.EMBEDDED });

	public static final CSSJFontPolicyValue PDFA1_VALUE = new CSSJFontPolicyValue(
			new FontPolicy[] { FontPolicy.EMBEDDED });

	private final FontPolicyList list;

	public CSSJFontPolicyValue(FontPolicy[] fontPolicy) {
		this.list = new FontPolicyList(fontPolicy);
	}

	public FontPolicyList asFontPolicyList() {
		return this.list;
	}

	public int getLength() {
		return this.list.getLength();
	}

	public FontPolicy get(int index) {
		return this.list.get(index);
	}

	@Override
	public String toString() {
		return this.list.toString();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof CSSJFontPolicyValue v && this.list.equals(v.list);
	}

	@Override
	public int hashCode() {
		return this.list.hashCode();
	}
}
