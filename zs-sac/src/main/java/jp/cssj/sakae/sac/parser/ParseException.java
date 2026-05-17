/*

 ============================================================================
 The Apache Software License, Version 1.1
 ============================================================================

 Copyright (C) 1999-2003 The Apache Software Foundation. All rights reserved.

 Redistribution and use in source and binary forms, with or without modifica-
 tion, are permitted provided that the following conditions are met:

 1. Redistributions of  source code must  retain the above copyright  notice,
 this list of conditions and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution.

 3. The end-user documentation included with the redistribution, if any, must
 include  the following  acknowledgment:  "This product includes  software
 developed  by the  Apache Software Foundation  (http://www.apache.org/)."
 Alternately, this  acknowledgment may  appear in the software itself,  if
 and wherever such third-party acknowledgments normally appear.

 4. The names "Batik" and  "Apache Software Foundation" must  not  be
 used to  endorse or promote  products derived from  this software without
 prior written permission. For written permission, please contact
 apache@apache.org.

 5. Products  derived from this software may not  be called "Apache", nor may
 "Apache" appear  in their name,  without prior written permission  of the
 Apache Software Foundation.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 FITNESS  FOR A PARTICULAR  PURPOSE ARE  DISCLAIMED.  IN NO  EVENT SHALL  THE
 APACHE SOFTWARE  FOUNDATION  OR ITS CONTRIBUTORS  BE LIABLE FOR  ANY DIRECT,
 INDIRECT, INCIDENTAL, SPECIAL,  EXEMPLARY, OR CONSEQUENTIAL  DAMAGES (INCLU-
 DING, BUT NOT LIMITED TO, PROCUREMENT  OF SUBSTITUTE GOODS OR SERVICES; LOSS
 OF USE, DATA, OR  PROFITS; OR BUSINESS  INTERRUPTION)  HOWEVER CAUSED AND ON
 ANY  THEORY OF LIABILITY,  WHETHER  IN CONTRACT,  STRICT LIABILITY,  OR TORT
 (INCLUDING  NEGLIGENCE OR  OTHERWISE) ARISING IN  ANY WAY OUT OF THE  USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 This software  consists of voluntary contributions made  by many individuals
 on  behalf of the Apache Software  Foundation. For more  information on the
 Apache Software Foundation, please see <http://www.apache.org/>.

 */

package net.zamasoft.pdfg2d.sac.parser;

/**
 * This class encapsulates a general parse error or warning.
 * 
 * <p>
 * This class can contain basic error or warning information from either the
 * parser or the application.
 * 
 * <p>
 * If the application needs to pass through other types of exceptions, it must
 * wrap those exceptions in a ParseException.
 * 
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion </a>
 * @version $Id: ParseException.java 1441 2015-05-31 07:42:33Z miyabe $
 */
public class ParseException extends Exception {
	private static final long serialVersionUID = 0;

	/**
	 * @serial The embedded exception if tunnelling, or null.
	 */
	protected Exception exception;

	/**
	 * @serial The line number.
	 */
	protected int lineNumber;

	/**
	 * @serial The column number.
	 */
	protected int columnNumber;

	/**
	 * Creates a new ParseException.
	 * 
	 * @param message
	 *            The error or warning message.
	 * @param line
	 *            The line of the last parsed character.
	 * @param column
	 *            The column of the last parsed character.
	 */
	public ParseException(String message, int line, int column) {
		super(message);
		exception = null;
		lineNumber = line;
		columnNumber = column;
	}

	/**
	 * Return the embedded exception, if any.
	 * 
	 * @return The embedded exception, or null if there is none.
	 */
	public Exception getException() {
		return exception;
	}

	/**
	 * Returns the line of the last parsed character.
	 */
	public int getLineNumber() {
		return lineNumber;
	}

	/**
	 * Returns the column of the last parsed character.
	 */
	public int getColumnNumber() {
		return columnNumber;
	}
}