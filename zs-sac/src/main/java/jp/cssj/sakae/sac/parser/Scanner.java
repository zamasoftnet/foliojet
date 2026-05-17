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

import java.io.IOException;
import java.io.Reader;

import net.zamasoft.pdfg2d.sac.util.io.NormalizingReader;
import net.zamasoft.pdfg2d.sac.util.io.StreamNormalizingReader;
import net.zamasoft.pdfg2d.sac.util.io.StringNormalizingReader;

/**
 * This class represents a CSS scanner - an object which decodes CSS lexical
 * units.
 * 
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion </a>
 * @version $Id: Scanner.java 1555 2018-04-26 04:15:29Z miyabe $
 */
public class Scanner {

	/**
	 * The reader.
	 */
	protected NormalizingReader reader;

	/**
	 * The current char.
	 */
	protected int current;

	/**
	 * The recording buffer.
	 */
	protected char[] buffer = new char[128];

	/**
	 * The current position in the buffer.
	 */
	protected int position;

	/**
	 * The type of the current lexical unit.
	 */
	protected int type;

	/**
	 * The start offset of the last lexical unit.
	 */
	protected int start;

	/**
	 * The end offset of the last lexical unit.
	 */
	protected int end;

	/**
	 * The characters to skip to create the string which represents the current
	 * token.
	 */
	protected int blankCharacters;

	/**
	 * Creates a new Scanner object.
	 * 
	 * @param r
	 *            The reader to scan.
	 */
	public Scanner(Reader r) throws IOException {
		this.reader = new StreamNormalizingReader(r);
		this.current = nextChar();
	}

	/**
	 * Creates a new Scanner object.
	 * 
	 * @param s
	 *            The reader to scan.
	 */
	public Scanner(String s) throws IOException {
		this.reader = new StringNormalizingReader(s);
		this.current = nextChar();
	}

	/**
	 * Returns the current line.
	 */
	public int getLine() {
		return this.reader.getLine();
	}

	/**
	 * Returns the current column.
	 */
	public int getColumn() {
		return this.reader.getColumn();
	}

	/**
	 * Returns the buffer used to store the chars.
	 */
	public char[] getBuffer() {
		return this.buffer;
	}

	/**
	 * Returns the start offset of the last lexical unit.
	 */
	public int getStart() {
		return this.start;
	}

	/**
	 * Returns the end offset of the last lexical unit.
	 */
	public int getEnd() {
		return this.end;
	}

	/**
	 * Clears the buffer.
	 */
	public void clearBuffer() {
		if (this.position <= 0) {
			this.position = 0;
		} else {
			this.buffer[0] = this.buffer[this.position - 1];
			this.position = 1;
		}
	}

	/**
	 * The current lexical unit type like defined in LexicalUnits.
	 */
	public int getType() {
		return this.type;
	}

	/**
	 * Returns the string representation of the current lexical unit value.
	 */
	public String getStringValue() {
		return new String(this.buffer, this.start, this.end - this.start);
	}

	/**
	 * Returns the string representation of the current lexical unit.
	 */
	public String getRawStringValue() {
		return new String(this.buffer, this.start, this.end - this.start + this.endUnitGap());
	}

	/**
	 * Scans a
	 * 
	 * at-rule value. This method assumes that the current lexical unit is a at
	 *       keyword.
	 */
	public void scanAtRule() throws IOException {
		// waiting for EOF, ';' or '{'
		loop: for (;;) {
			switch (this.current) {
			case '{':
				int brackets = 1;
				for (;;) {
					nextChar();
					switch (this.current) {
					case '}':
						if (--brackets > 0) {
							break;
						}
					case -1:
						break loop;
					case '{':
						brackets++;
					}
				}
			case -1:
			case ';':
				break loop;
			}
			nextChar();
		}
		this.end = this.position;
	}

	/**
	 * Returns the next token.
	 */
	public int next() throws IOException, ParseException {
		this.blankCharacters = 0;
		this.start = this.position - 1;
		nextToken();
		this.end = this.position - endGap();
		return this.type;
	}

	/**
	 * Returns the end gap of the current lexical unit.
	 */
	protected int endGap() {
		int result = (this.current == -1) ? 0 : 1;
		result += this.endUnitGap();
		return result + this.blankCharacters;
	}

	protected int endUnitGap() {
		int result = 0;
		switch (this.type) {
		case LexicalUnits.FUNCTION:
		case LexicalUnits.STRING:
		case LexicalUnits.S:
		case LexicalUnits.PERCENTAGE:
			result += 1;
			break;
		case LexicalUnits.COMMENT:
		case LexicalUnits.HZ:
		case LexicalUnits.EM:
		case LexicalUnits.EX:
		case LexicalUnits.PC:
		case LexicalUnits.PT:
		case LexicalUnits.PX:
		case LexicalUnits.CM:
		case LexicalUnits.MM:
		case LexicalUnits.IN:
		case LexicalUnits.MS:
		case LexicalUnits.CH:
			result += 2;
			break;
		case LexicalUnits.KHZ:
		case LexicalUnits.DEG:
		case LexicalUnits.RAD:
		case LexicalUnits.REM:
			result += 3;
			break;
		case LexicalUnits.GRAD:
			result += 4;
		}
		return result;
	}

	/**
	 * Returns the next token.
	 */
	protected void nextToken() throws IOException, ParseException {
		switch (this.current) {
		case -1:
			this.type = LexicalUnits.EOF;
			return;
		case '{':
			nextChar();
			this.type = LexicalUnits.LEFT_CURLY_BRACE;
			return;
		case '}':
			nextChar();
			this.type = LexicalUnits.RIGHT_CURLY_BRACE;
			return;
		case '=':
			nextChar();
			this.type = LexicalUnits.EQUAL;
			return;
		case '+':
			nextChar();
			this.type = LexicalUnits.PLUS;
			return;
		case ',':
			nextChar();
			this.type = LexicalUnits.COMMA;
			return;
		case ';':
			nextChar();
			this.type = LexicalUnits.SEMI_COLON;
			return;
		case '>':
			nextChar();
			this.type = LexicalUnits.PRECEDE;
			return;
		case '[':
			nextChar();
			this.type = LexicalUnits.LEFT_BRACKET;
			return;
		case ']':
			nextChar();
			this.type = LexicalUnits.RIGHT_BRACKET;
			return;
		case '*':
			nextChar();
			this.type = LexicalUnits.ANY;
			return;
		case '(':
			nextChar();
			this.type = LexicalUnits.LEFT_BRACE;
			return;
		case ')':
			nextChar();
			this.type = LexicalUnits.RIGHT_BRACE;
			return;
		case ':':
			nextChar();
			this.type = LexicalUnits.COLON;
			return;
		case ' ':
		case '\u3000':
		case '\ufeff':
		case '\t':
		case '\r':
		case '\n':
		case '\f':
			do {
				nextChar();
			} while (ScannerUtilities.isCSSSpace((char) this.current));
			this.type = LexicalUnits.SPACE;
			return;
		case '/':
			nextChar();
			if (this.current != '*') {
				this.type = LexicalUnits.DIVIDE;
				return;
			}
			// Comment
			nextChar();
			this.start = this.position - 1;
			do {
				while (this.current != -1 && this.current != '*') {
					nextChar();
				}
				do {
					nextChar();
				} while (this.current != -1 && this.current == '*');
			} while (this.current != -1 && this.current != '/');
			if (this.current == -1) {
				throw new ParseException("eof", this.reader.getLine(), this.reader.getColumn());
			}
			nextChar();
			this.type = LexicalUnits.COMMENT;
			return;
		case '\'': // String1
			this.type = string1();
			return;
		case '"': // String2
			this.type = string2();
			return;
		case '<':
			nextChar();
			if (this.current != '!') {
				throw new ParseException("character", this.reader.getLine(), this.reader.getColumn());
			}
			nextChar();
			if (this.current == '-') {
				nextChar();
				if (this.current == '-') {
					nextChar();
					this.type = LexicalUnits.CDO;
					return;
				}
			}
			throw new ParseException("character", this.reader.getLine(), this.reader.getColumn());
		case '|':
			nextChar();
			if (this.current == '=') {
				nextChar();
				this.type = LexicalUnits.DASHMATCH;
				return;
			}
			this.type = LexicalUnits.DASH;
			return;
		case '~':
			nextChar();
			if (this.current == '=') {
				nextChar();
				this.type = LexicalUnits.INCLUDES;
				return;
			}
			throw new ParseException("character", this.reader.getLine(), this.reader.getColumn());
		case '#':
			nextChar();
			if (ScannerUtilities.isCSSNameCharacter((char) this.current)) {
				this.start = this.position - 1;
				do {
					nextChar();
					while (this.current == '\\') {
						nextChar();
						escape();
					}
				} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
				this.type = LexicalUnits.HASH;
				return;
			}
			throw new ParseException("character", this.reader.getLine(), this.reader.getColumn());
		case '@':
			nextChar();
			switch (this.current) {
			case 'c':
			case 'C':
				this.start = this.position - 1;
				if (isEqualIgnoreCase(nextChar(), 'h') && isEqualIgnoreCase(nextChar(), 'a')
						&& isEqualIgnoreCase(nextChar(), 'r') && isEqualIgnoreCase(nextChar(), 's')
						&& isEqualIgnoreCase(nextChar(), 'e') && isEqualIgnoreCase(nextChar(), 't')) {
					nextChar();
					this.type = LexicalUnits.CHARSET_SYMBOL;
					return;
				}
				break;
			case 'f':
			case 'F':
				this.start = this.position - 1;
				if (isEqualIgnoreCase(nextChar(), 'o') && isEqualIgnoreCase(nextChar(), 'n')
						&& isEqualIgnoreCase(nextChar(), 't') && isEqualIgnoreCase(nextChar(), '-')
						&& isEqualIgnoreCase(nextChar(), 'f') && isEqualIgnoreCase(nextChar(), 'a')
						&& isEqualIgnoreCase(nextChar(), 'c') && isEqualIgnoreCase(nextChar(), 'e')) {
					nextChar();
					this.type = LexicalUnits.FONT_FACE_SYMBOL;
					return;
				}
				break;
			case 'i':
			case 'I':
				this.start = this.position - 1;
				if (isEqualIgnoreCase(nextChar(), 'm') && isEqualIgnoreCase(nextChar(), 'p')
						&& isEqualIgnoreCase(nextChar(), 'o') && isEqualIgnoreCase(nextChar(), 'r')
						&& isEqualIgnoreCase(nextChar(), 't')) {
					nextChar();
					this.type = LexicalUnits.IMPORT_SYMBOL;
					return;
				}
				break;
			case 'm':
			case 'M':
				this.start = this.position - 1;
				if (isEqualIgnoreCase(nextChar(), 'e') && isEqualIgnoreCase(nextChar(), 'd')
						&& isEqualIgnoreCase(nextChar(), 'i') && isEqualIgnoreCase(nextChar(), 'a')) {
					nextChar();
					this.type = LexicalUnits.MEDIA_SYMBOL;
					return;
				}
				break;
			case 'n':
			case 'N':
				this.start = this.position - 1;
				if (isEqualIgnoreCase(nextChar(), 'a') && isEqualIgnoreCase(nextChar(), 'm')
						&& isEqualIgnoreCase(nextChar(), 'e') && isEqualIgnoreCase(nextChar(), 's')
						&& isEqualIgnoreCase(nextChar(), 'p') && isEqualIgnoreCase(nextChar(), 'a')
						&& isEqualIgnoreCase(nextChar(), 'c') && isEqualIgnoreCase(nextChar(), 'e')) {
					nextChar();
					this.type = LexicalUnits.NAMESPACE_SYMBOL;
					return;
				}
				break;
			case 'p':
			case 'P':
				this.start = this.position - 1;
				if (isEqualIgnoreCase(nextChar(), 'a') && isEqualIgnoreCase(nextChar(), 'g')
						&& isEqualIgnoreCase(nextChar(), 'e')) {
					nextChar();
					this.type = LexicalUnits.PAGE_SYMBOL;
					return;
				}
				break;
			case '-':
				this.start = this.position - 1;
				if (isEqualIgnoreCase(nextChar(), 'c') && isEqualIgnoreCase(nextChar(), 's')
						&& isEqualIgnoreCase(nextChar(), 's') && isEqualIgnoreCase(nextChar(), 'j')
						&& isEqualIgnoreCase(nextChar(), '-') && isEqualIgnoreCase(nextChar(), 'p')
						&& isEqualIgnoreCase(nextChar(), 'a') && isEqualIgnoreCase(nextChar(), 'g')
						&& isEqualIgnoreCase(nextChar(), 'e') && isEqualIgnoreCase(nextChar(), '-')
						&& isEqualIgnoreCase(nextChar(), 'c') && isEqualIgnoreCase(nextChar(), 'o')
						&& isEqualIgnoreCase(nextChar(), 'n') && isEqualIgnoreCase(nextChar(), 't')
						&& isEqualIgnoreCase(nextChar(), 'e') && isEqualIgnoreCase(nextChar(), 'n')
						&& isEqualIgnoreCase(nextChar(), 't')) {
					nextChar();
					this.type = LexicalUnits.PAGE_CONTENT_SYMBOL;
					return;
				}
				break;
			default:
				if (!ScannerUtilities.isCSSIdentifierStartCharacter((char) this.current)) {
					throw new ParseException("identifier.character", this.reader.getLine(), this.reader.getColumn());
				}
				this.start = this.position - 1;
			}
			do {
				nextChar();
				while (this.current == '\\') {
					nextChar();
					escape();
				}
			} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
			this.type = LexicalUnits.AT_KEYWORD;
			return;
		case '!':
			do {
				nextChar();
			} while (this.current != -1 && ScannerUtilities.isCSSSpace((char) this.current));
			if (isEqualIgnoreCase(this.current, 'i') && isEqualIgnoreCase(nextChar(), 'm')
					&& isEqualIgnoreCase(nextChar(), 'p') && isEqualIgnoreCase(nextChar(), 'o')
					&& isEqualIgnoreCase(nextChar(), 'r') && isEqualIgnoreCase(nextChar(), 't')
					&& isEqualIgnoreCase(nextChar(), 'a') && isEqualIgnoreCase(nextChar(), 'n')
					&& isEqualIgnoreCase(nextChar(), 't')) {
				nextChar();
				type = LexicalUnits.IMPORTANT_SYMBOL;
				return;
			}
			if (this.current == -1) {
				throw new ParseException("eof", this.reader.getLine(), this.reader.getColumn());
			} else {
				throw new ParseException("character", this.reader.getLine(), this.reader.getColumn());
			}
		case '0':
		case '1':
		case '2':
		case '3':
		case '4':
		case '5':
		case '6':
		case '7':
		case '8':
		case '9':
			this.type = number();
			return;
		case '.':
			switch (nextChar()) {
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				this.type = this.dotNumber();
				return;
			default:
				this.type = LexicalUnits.DOT;
				return;
			}
		case 'u':
		case 'U':
			this.nextChar();
			switch (this.current) {
			case '+':
				nextChar();
				while (this.current != -1 && (ScannerUtilities.isCSSHexadecimalCharacter((char) this.current)
						|| this.current == '?' || this.current == '-')) {
					nextChar();
				}
				this.type = LexicalUnits.UNICODE_RANGE;
				return;
			case 'r':
			case 'R':
				nextChar();
				switch (this.current) {
				case 'l':
				case 'L':
					nextChar();
					switch (this.current) {
					case '(':
						do {
							nextChar();
						} while (this.current != -1 && ScannerUtilities.isCSSSpace((char) this.current));
						switch (this.current) {
						case '\'':
							string1();
							this.blankCharacters += 2;
							while (this.current != -1 && ScannerUtilities.isCSSSpace((char) this.current)) {
								this.blankCharacters++;
								nextChar();
							}
							if (this.current == -1) {
								throw new ParseException("eof", this.reader.getLine(), this.reader.getColumn());
							}
							if (this.current != ')') {
								throw new ParseException("character", this.reader.getLine(), this.reader.getColumn());
							}
							nextChar();
							this.type = LexicalUnits.URI;
							return;
						case '"':
							string2();
							this.blankCharacters += 2;
							while (this.current != -1 && ScannerUtilities.isCSSSpace((char) this.current)) {
								this.blankCharacters++;
								nextChar();
							}
							if (this.current == -1) {
								throw new ParseException("eof", this.reader.getLine(), this.reader.getColumn());
							}
							if (this.current != ')') {
								throw new ParseException("character", this.reader.getLine(), this.reader.getColumn());
							}
							nextChar();
							this.type = LexicalUnits.URI;
							return;
						case ')':
							this.start = this.position;
							nextChar();
							this.type = LexicalUnits.URI;
							return;
						default:
							if (!ScannerUtilities.isCSSURICharacter((char) this.current)) {
								throw new ParseException("character", this.reader.getLine(), this.reader.getColumn());
							}
							this.start = this.position - 1;
							do {
								nextChar();
							} while (this.current != -1 && ScannerUtilities.isCSSURICharacter((char) this.current));
							this.blankCharacters++;
							while (this.current != -1 && ScannerUtilities.isCSSSpace((char) this.current)) {
								this.blankCharacters++;
								nextChar();
							}
							if (this.current == -1) {
								throw new ParseException("eof", this.reader.getLine(), this.reader.getColumn());
							}
							if (this.current != ')') {
								throw new ParseException("character", this.reader.getLine(), this.reader.getColumn());
							}
							nextChar();
							this.type = LexicalUnits.URI;
							return;
						}
					}
				}
			}
			while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
				nextChar();
			}
			if (this.current == '(') {
				nextChar();
				this.type = LexicalUnits.FUNCTION;
				return;
			}
			this.type = LexicalUnits.IDENTIFIER;
			return;
		case '-':
			nextChar();
			switch (this.current) {
			case '-':
				nextChar();
				if (this.current == '>') {
					nextChar();
					this.type = LexicalUnits.CDC;
					return;
				}
				throw new ParseException("character", this.reader.getLine(), this.reader.getColumn());
			case '.':
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				this.type = LexicalUnits.MINUS;
				return;
			}
		default:
			if (this.current == '\\' || ScannerUtilities.isCSSIdentifierStartCharacter((char) this.current)) {
				while (this.current == '\\') {
					nextChar();
					escape();
				}

				// Identifier
				while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					nextChar();
					while (this.current == '\\') {
						nextChar();
						escape();
					}
				}

				if (this.current == '(') {
					nextChar();
					this.type = LexicalUnits.FUNCTION;
					return;
				}
				this.type = LexicalUnits.IDENTIFIER;
				return;
			}
			nextChar();
			throw new ParseException("identifier.character", this.reader.getLine(), this.reader.getColumn());
		}
	}

	/**
	 * Scans a single quoted string.
	 */
	protected int string1() throws IOException, ParseException {
		nextChar();
		this.start = this.position - 1;
		loop: for (;;) {
			switch (this.current) {
			case -1:
				throw new ParseException("eof", this.reader.getLine(), this.reader.getColumn());
			case '\'':
				break loop;
			case '"':
				break;
			case '\\':
				switch (nextChar()) {
				case '\n':
				case '\f':
					prevChar();
					prevChar();
					break;
				default:
					escape();
					continue;
				}
				break;
			default:
				if (!ScannerUtilities.isCSSStringCharacter((char) this.current)) {
					throw new ParseException("character", this.reader.getLine(), this.reader.getColumn());
				}
			}
			nextChar();
		}
		nextChar();
		return LexicalUnits.STRING;
	}

	/**
	 * Scans a double quoted string.
	 */
	protected int string2() throws IOException, ParseException {
		nextChar();
		this.start = this.position - 1;
		loop: for (;;) {
			switch (this.current) {
			case -1:
				throw new ParseException("eof", this.reader.getLine(), this.reader.getColumn());
			case '\'':
				break;
			case '"':
				break loop;
			case '\\':
				switch (nextChar()) {
				case '\n':
				case '\f':
					prevChar();
					prevChar();
					break;
				default:
					escape();
					continue;
				}
				break;
			default:
				if (!ScannerUtilities.isCSSStringCharacter((char) this.current)) {
					throw new ParseException("character", this.reader.getLine(), this.reader.getColumn());
				}
			}
			nextChar();
		}
		nextChar();
		return LexicalUnits.STRING;
	}

	/**
	 * Scans a number.
	 */
	protected int number() throws IOException, ParseException {
		loop: for (;;) {
			switch (nextChar()) {
			case '.':
				switch (nextChar()) {
				case '0':
				case '1':
				case '2':
				case '3':
				case '4':
				case '5':
				case '6':
				case '7':
				case '8':
				case '9':
					return dotNumber();
				}
				throw new ParseException("character", this.reader.getLine(), this.reader.getColumn());
			default:
				break loop;
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
			}
		}
		return numberUnit(true);
	}

	/**
	 * Scans the decimal part of a number.
	 */
	protected int dotNumber() throws IOException {
		loop: for (;;) {
			switch (nextChar()) {
			default:
				break loop;
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
			}
		}
		return numberUnit(false);
	}

	/**
	 * Scans the unit of a number.
	 */
	protected int numberUnit(boolean integer) throws IOException {
		switch (this.current) {
		case '%':
			nextChar();
			return LexicalUnits.PERCENTAGE;
		case 'c':
		case 'C':
			switch (nextChar()) {
			case 'h':
			case 'H':
				nextChar();
				if (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					do {
						nextChar();
					} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
					return LexicalUnits.DIMENSION;
				}
				return LexicalUnits.CH;
			case 'm':
			case 'M':
				nextChar();
				if (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					do {
						nextChar();
					} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
					return LexicalUnits.DIMENSION;
				}
				return LexicalUnits.CM;
			default:
				while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					nextChar();
				}
				return LexicalUnits.DIMENSION;
			}
		case 'd':
		case 'D':
			switch (nextChar()) {
			case 'e':
			case 'E':
				switch (nextChar()) {
				case 'g':
				case 'G':
					nextChar();
					if (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
						do {
							nextChar();
						} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
						return LexicalUnits.DIMENSION;
					}
					return LexicalUnits.DEG;
				}
			default:
				while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					nextChar();
				}
				return LexicalUnits.DIMENSION;
			}
		case 'e':
		case 'E':
			switch (nextChar()) {
			case 'm':
			case 'M':
				nextChar();
				if (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					do {
						nextChar();
					} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
					return LexicalUnits.DIMENSION;
				}
				return LexicalUnits.EM;
			case 'x':
			case 'X':
				nextChar();
				if (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					do {
						nextChar();
					} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
					return LexicalUnits.DIMENSION;
				}
				return LexicalUnits.EX;
			default:
				while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					nextChar();
				}
				return LexicalUnits.DIMENSION;
			}
		case 'g':
		case 'G':
			switch (nextChar()) {
			case 'r':
			case 'R':
				switch (nextChar()) {
				case 'a':
				case 'A':
					switch (nextChar()) {
					case 'd':
					case 'D':
						nextChar();
						if (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
							do {
								nextChar();
							} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
							return LexicalUnits.DIMENSION;
						}
						return LexicalUnits.GRAD;
					}
				}
			default:
				while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					nextChar();
				}
				return LexicalUnits.DIMENSION;
			}
		case 'h':
		case 'H':
			nextChar();
			switch (this.current) {
			case 'z':
			case 'Z':
				nextChar();
				if (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					do {
						nextChar();
					} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
					return LexicalUnits.DIMENSION;
				}
				return LexicalUnits.HZ;
			default:
				while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					nextChar();
				}
				return LexicalUnits.DIMENSION;
			}
		case 'i':
		case 'I':
			switch (nextChar()) {
			case 'n':
			case 'N':
				nextChar();
				if (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					do {
						nextChar();
					} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) current));
					return LexicalUnits.DIMENSION;
				}
				return LexicalUnits.IN;
			default:
				while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					nextChar();
				}
				return LexicalUnits.DIMENSION;
			}
		case 'k':
		case 'K':
			switch (nextChar()) {
			case 'h':
			case 'H':
				switch (nextChar()) {
				case 'z':
				case 'Z':
					nextChar();
					if (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
						do {
							nextChar();
						} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
						return LexicalUnits.DIMENSION;
					}
					return LexicalUnits.KHZ;
				}
			default:
				while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					nextChar();
				}
				return LexicalUnits.DIMENSION;
			}
		case 'm':
		case 'M':
			switch (nextChar()) {
			case 'm':
			case 'M':
				nextChar();
				if (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					do {
						nextChar();
					} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
					return LexicalUnits.DIMENSION;
				}
				return LexicalUnits.MM;
			case 's':
			case 'S':
				nextChar();
				if (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					do {
						nextChar();
					} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
					return LexicalUnits.DIMENSION;
				}
				return LexicalUnits.MS;
			default:
				while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					nextChar();
				}
				return LexicalUnits.DIMENSION;
			}
		case 'p':
		case 'P':
			switch (nextChar()) {
			case 'c':
			case 'C':
				nextChar();
				if (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					do {
						nextChar();
					} while (current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
					return LexicalUnits.DIMENSION;
				}
				return LexicalUnits.PC;
			case 't':
			case 'T':
				nextChar();
				if (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					do {
						nextChar();
					} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
					return LexicalUnits.DIMENSION;
				}
				return LexicalUnits.PT;
			case 'x':
			case 'X':
				nextChar();
				if (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					do {
						nextChar();
					} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
					return LexicalUnits.DIMENSION;
				}
				return LexicalUnits.PX;
			default:
				while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					nextChar();
				}
				return LexicalUnits.DIMENSION;
			}
		case 'r':
		case 'R':
			switch (nextChar()) {
			case 'a':
			case 'A':
				switch (nextChar()) {
				case 'd':
				case 'D':
					nextChar();
					if (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
						do {
							nextChar();
						} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
						return LexicalUnits.DIMENSION;
					}
					return LexicalUnits.RAD;
				}
			case 'e':
			case 'E':
				switch (nextChar()) {
				case 'm':
				case 'M':
					nextChar();
					if (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
						do {
							nextChar();
						} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
						return LexicalUnits.DIMENSION;
					}
					return LexicalUnits.REM;
				}
			default:
				while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current)) {
					nextChar();
				}
				return LexicalUnits.DIMENSION;
			}
		case 's':
		case 'S':
			nextChar();
			return LexicalUnits.S;
		default:
			if (this.current != -1 && ScannerUtilities.isCSSIdentifierStartCharacter((char) this.current)) {
				do {
					nextChar();
				} while (this.current != -1 && ScannerUtilities.isCSSNameCharacter((char) this.current));
				return LexicalUnits.DIMENSION;
			}
			return (integer) ? LexicalUnits.INTEGER : LexicalUnits.REAL;
		}
	}

	/**
	 * Scans an escape sequence, if one.
	 */
	protected void escape() throws IOException, ParseException {
		int escapeStart = this.position - 1;
		if (ScannerUtilities.isCSSHexadecimalCharacter((char) this.current)) {
			for (int i = 0; i < 4; ++i) {
				nextChar();
				if (!ScannerUtilities.isCSSHexadecimalCharacter((char) this.current)) {
					break;
				}
			}
			String hex = new String(this.buffer, escapeStart, this.position - escapeStart - 1);
			char ch = (char) this.current;
			for (int i = hex.length() + 1; i >= 0; --i) {
				prevChar();
			}
			try {
				addChar((char) (Integer.parseInt(hex, 16)));
				if (!ScannerUtilities.isCSSSpace(ch)) {
					addChar(ch);
				} else {
					nextChar();
				}
				return;
			} catch (NumberFormatException e) {
				// ignore
			}
		} else {
			if ((this.current >= ' ' && this.current <= '~') || this.current >= 128) {
				char ch = (char) this.current;
				prevChar();
				prevChar();
				addChar(ch);
				nextChar();
				return;
			}
		}
		throw new ParseException("character", this.reader.getLine(), this.reader.getColumn());
	}

	/**
	 * Compares the given int with the given character, ignoring case.
	 */
	protected static boolean isEqualIgnoreCase(int i, char c) {
		return (i == -1) ? false : Character.toLowerCase((char) i) == c;
	}

	/**
	 * Sets the value of the current char to the next character or -1 if the end of
	 * stream has been reached.
	 */
	protected int nextChar() throws IOException {
		this.current = this.reader.read();

		if (this.current == -1) {
			return this.current;
		}

		if (this.position == this.buffer.length) {
			char[] t = new char[this.position * 3 / 2];
			for (int i = 0; i < this.position; i++) {
				t[i] = this.buffer[i];
			}
			this.buffer = t;
		}

		return this.buffer[this.position++] = (char) this.current;
	}

	protected void prevChar() {
		this.current = this.buffer[--this.position];
	}

	protected void addChar(char ch) {
		this.current = ch;
		this.buffer[position++] = (char) this.current;
	}
}