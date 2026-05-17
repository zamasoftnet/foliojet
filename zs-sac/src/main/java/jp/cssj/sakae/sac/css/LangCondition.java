/*
 * (c) COPYRIGHT 1999 World Wide Web Consortium
 * (Massachusetts Institute of Technology, Institut National de Recherche
 *  en Informatique et en Automatique, Keio University).
 * All Rights Reserved. http://www.w3.org/Consortium/Legal/
 *
 * $Id: LangCondition.java,v 1.1 1999/09/26 09:54:37 plehegar Exp $
 */
package net.zamasoft.pdfg2d.sac.css;

import net.zamasoft.pdfg2d.sac.css.Condition;

/**
 * @version $Revision: 1.1 $
 * @author Philippe Le Hegaret
 * @see Condition#SAC_LANG_CONDITION
 */
public interface LangCondition extends Condition {
	/**
	 * Returns the language
	 */
	public String getLang();
}
