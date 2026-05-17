package jp.cssj.balancer;

import jp.cssj.balancer.ElementProps.ElementProp;

import org.apache.xerces.util.XMLAttributesImpl;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;

/**
 * Element info for each start element. This information is used when closing
 * unbalanced inline elements. For example:
 * 
 * <pre>
 * &lt;i&gt;unbalanced &lt;b&gt;HTML&lt;/i&gt; content&lt;/b&gt;
 * </pre>
 * <p>
 * It seems that it is a waste of processing and memory to copy the attributes
 * for every start element even if there are no unbalanced inline elements in
 * the document. However, if the attributes are <em>not</em> saved, then
 * important attributes such as style information would be lost.
 * 
 * @author Andy Clark
 */
class Info {

	//
	// Data
	//

	/** The element. */
	public ElementProp prop;

	/** The element qualified name. */
	public final QName qname;

	/** The element attributes. */
	public final XMLAttributes atts;

	//
	// Constructors
	//

	/**
	 * Creates an element information object.
	 * <p>
	 * <strong>Note:</strong> This constructor makes a copy of the element
	 * information.
	 * 
	 * @param prop
	 *            The element qualified name.
	 * @param attributes
	 *            The element attributes.
	 */
	public Info(ElementProp prop, QName qname, XMLAttributes attributes) {
		this.prop = prop;
		this.qname = new QName(qname);
		if (attributes != null) {
			int length = attributes.getLength();
			if (length > 0) {
				QName aqname = new QName();
				XMLAttributes newattrs = new XMLAttributesImpl();
				for (int i = 0; i < length; i++) {
					attributes.getName(i, aqname);
					String type = attributes.getType(i);
					String value = attributes.getValue(i);
					String nonNormalizedValue = attributes.getNonNormalizedValue(i);
					boolean specified = attributes.isSpecified(i);
					newattrs.addAttribute(aqname, type, value);
					newattrs.setNonNormalizedValue(i, nonNormalizedValue);
					newattrs.setSpecified(i, specified);
				}
				attributes = newattrs;
			} else {
				attributes = null;
			}
		}
		this.atts = attributes;
	} // <init>(HTMLElements.Element,QName,XMLAttributes)

	/**
	 * Simple representation to make debugging easier
	 */
	public String toString() {
		return super.toString() + qname;
	}
} // class Info

/** Unsynchronized stack of element information. */
class InfoStack {

	//
	// Data
	//

	/** The top of the stack. */
	public int top;

	/** The stack data. */
	public Info[] data = new Info[10];

	//
	// Public methods
	//

	/** Pushes element information onto the stack. */
	public void push(Info info) {
		if (top == data.length) {
			Info[] newarray = new Info[top + 10];
			System.arraycopy(data, 0, newarray, 0, top);
			data = newarray;
		}
		data[top++] = info;
	} // push(Info)

	/** Peeks at the top of the stack. */
	public Info peek() {
		return data[top - 1];
	} // peek():Info

	/** Pops the top item off of the stack. */
	public Info pop() {
		return data[--top];
	} // pop():Info

	/**
	 * Simple representation to make debugging easier
	 */
	public String toString() {
		final StringBuffer sb = new StringBuffer("InfoStack(");
		for (int i = top - 1; i >= 0; --i) {
			sb.append(data[i]);
			if (i != 0)
				sb.append(", ");
		}
		sb.append(")");
		return sb.toString();
	}

} // class InfoStack
