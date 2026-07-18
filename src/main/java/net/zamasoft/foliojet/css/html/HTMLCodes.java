package net.zamasoft.foliojet.css.html;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.xml.vocab.XHTML;

final public class HTMLCodes {
	private HTMLCodes() {
		// unused
	}

	public static final short ANY = 0;
	public static final short A = 101;
	public static final short ABBR = 102;
	public static final short ACRONYM = 103;
	public static final short ADDRESS = 104;
	public static final short APPLET = 105;
	public static final short AREA = 106;
	public static final short ARTICLE = 107;
	public static final short ASIDE = 108;
	public static final short AUDIO = 109;
	public static final short B = 201;
	public static final short BASE = 202;
	public static final short BASEFONT = 203;
	public static final short BDI = 204;
	public static final short BDO = 205;
	public static final short BGSOUND = 206;
	public static final short BIG = 207;
	public static final short BLINK = 208;
	public static final short BLOCKQUOTE = 209;
	public static final short BODY = 210;
	public static final short BR = 211;
	public static final short BUTTON = 212;
	public static final short CANVAS = 301;
	public static final short CAPTION = 302;
	public static final short CENTER = 303;
	public static final short CITE = 304;
	public static final short CODE = 305;
	public static final short COL = 306;
	public static final short COLGROUP = 307;
	public static final short COMMENT = 308;
	public static final short DATA = 401;
	public static final short DATALIST = 402;
	public static final short DD = 403;
	public static final short DEL = 404;
	public static final short DETAILS = 405;
	public static final short DFN = 406;
	public static final short DIALOG = 407;
	public static final short DIR = 408;
	public static final short DIV = 409;
	public static final short DL = 410;
	public static final short DT = 411;
	public static final short EM = 501;
	public static final short EMBED = 502;
	public static final short FIELDSET = 601;
	public static final short FIGCAPTION = 602;
	public static final short FIGURE = 603;
	public static final short FONT = 604;
	public static final short FOOTER = 605;
	public static final short FORM = 606;
	public static final short FRAME = 607;
	public static final short FRAMESET = 608;
	public static final short H1 = 701;
	public static final short H2 = 702;
	public static final short H3 = 703;
	public static final short H4 = 704;
	public static final short H5 = 705;
	public static final short H6 = 706;
	public static final short HEAD = 707;
	public static final short HEADER = 708;
	public static final short HGROUP = 709;
	public static final short HR = 710;
	public static final short HTML = 711;
	public static final short I = 801;
	public static final short IFRAME = 802;
	public static final short IMG = 804;
	public static final short INPUT = 805;
	public static final short INS = 806;
	public static final short ISINDEX = 807;
	public static final short KBD = 901;
	public static final short KEYGEN = 902;
	public static final short LABEL = 1001;
	public static final short LEGEND = 1003;
	public static final short LI = 1004;
	public static final short LINK = 1005;
	public static final short LISTING = 1006;
	public static final short MAIN = 1101;
	public static final short MAP = 1102;
	public static final short MARK = 1103;
	public static final short MARQUEE = 1104;
	public static final short MENU = 1105;
	public static final short META = 1106;
	public static final short METER = 1107;
	public static final short NAV = 1201;
	public static final short NEXTID = 1202;
	public static final short NOBR = 1203;
	public static final short NOEMBED = 1204;
	public static final short NOFRAMES = 1205;
	public static final short NOLAYER = 1206;
	public static final short NOSCRIPT = 1207;
	public static final short OBJECT = 1301;
	public static final short OL = 1302;
	public static final short OPTGROUP = 1303;
	public static final short OPTION = 1304;
	public static final short OUTPUT = 1305;
	public static final short P = 1401;
	public static final short PARAM = 1402;
	public static final short PICTURE = 1403;
	public static final short PLAINTEXT = 1404;
	public static final short PRE = 1405;
	public static final short PROGRESS = 1406;
	public static final short Q = 1501;
	public static final short RB = 1601;
	public static final short RP = 1602;
	public static final short RT = 1603;
	public static final short RUBY = 1604;
	public static final short S = 1701;
	public static final short SAMP = 1702;
	public static final short SCRIPT = 1703;
	public static final short SECTION = 1704;
	public static final short SELECT = 1705;
	public static final short SERVER = 1706;
	public static final short SMALL = 1707;
	public static final short SOURCE = 1708;
	public static final short SPAN = 1709;
	public static final short STRIKE = 1710;
	public static final short STRONG = 1711;
	public static final short STYLE = 1712;
	public static final short SUB = 1713;
	public static final short SUMMARY = 1714;
	public static final short SUP = 1715;
	public static final short TABLE = 1801;
	public static final short TBODY = 1802;
	public static final short TD = 1803;
	public static final short TEMPLATE = 1804;
	public static final short TEXTAREA = 1805;
	public static final short TFOOT = 1806;
	public static final short TH = 1807;
	public static final short THEAD = 1808;
	public static final short TIME = 1809;
	public static final short TITLE = 1810;
	public static final short TR = 1811;
	public static final short TRACK = 1812;
	public static final short TT = 1813;
	public static final short U = 1901;
	public static final short UL = 1902;
	public static final short VAR = 2001;
	public static final short VIDEO = 2002;
	public static final short WBR = 2101;
	public static final short XMP = 2201;

	public static short code(CSSElement ce) {
		if (ce.uri == null || !ce.uri.equals(XHTML.URI)) {
			return ANY;
		}
		String lName = ce.lName;
		if (lName == null) {
			return ANY;
		}
		short code = ANY;
		switch (lName.charAt(0)) {
		case 'a':
			if (lName.equals("a")) {
				code = A;
			} else if (lName.equals("abbr")) {
				code = ABBR;
			} else if (lName.equals("acronym")) {
				code = ACRONYM;
			} else if (lName.equals("address")) {
				code = ADDRESS;
			} else if (lName.equals("applet")) {
				code = APPLET;
			} else if (lName.equals("area")) {
				code = AREA;
			} else if (lName.equals("article")) {
				code = ARTICLE;
			} else if (lName.equals("aside")) {
				code = ASIDE;
			} else if (lName.equals("audio")) {
				code = AUDIO;
			}
			break;
		case 'b':
			if (lName.equals("b")) {
				code = B;
			} else if (lName.equals("base")) {
				code = BASE;
			} else if (lName.equals("basefont")) {
				code = BASEFONT;
			} else if (lName.equals("bdi")) {
				code = BDI;
			} else if (lName.equals("bdo")) {
				code = BDO;
			} else if (lName.equals("bgsound")) {
				code = BGSOUND;
			} else if (lName.equals("big")) {
				code = BIG;
			} else if (lName.equals("blink")) {
				code = BLINK;
			} else if (lName.equals("blockquote")) {
				code = BLOCKQUOTE;
			} else if (lName.equals("body")) {
				code = BODY;
			} else if (lName.equals("br")) {
				code = BR;
			} else if (lName.equals("button")) {
				code = BUTTON;
			}
			break;
		case 'c':
			if (lName.equals("canvas")) {
				code = CANVAS;
			} else if (lName.equals("caption")) {
				code = CAPTION;
			} else if (lName.equals("center")) {
				code = CENTER;
			} else if (lName.equals("cite")) {
				code = CITE;
			} else if (lName.equals("code")) {
				code = CODE;
			} else if (lName.equals("col")) {
				code = COL;
			} else if (lName.equals("colgroup")) {
				code = COLGROUP;
			} else if (lName.equals("comment")) {
				code = COMMENT;
			}
			break;
		case 'd':
			if (lName.equals("data")) {
				code = DATA;
			} else if (lName.equals("datalist")) {
				code = DATALIST;
			} else if (lName.equals("dd")) {
				code = DD;
			} else if (lName.equals("del")) {
				code = DEL;
			} else if (lName.equals("details")) {
				code = DETAILS;
			} else if (lName.equals("dfn")) {
				code = DFN;
			} else if (lName.equals("dialog")) {
				code = DIALOG;
			} else if (lName.equals("dir")) {
				code = DIR;
			} else if (lName.equals("div")) {
				code = DIV;
			} else if (lName.equals("dl")) {
				code = DL;
			} else if (lName.equals("dt")) {
				code = DT;
			}
			break;
		case 'e':
			if (lName.equals("em")) {
				code = EM;
			} else if (lName.equals("embed")) {
				code = EMBED;
			}
			break;
		case 'f':
			if (lName.equals("fieldset")) {
				code = FIELDSET;
			} else if (lName.equals("figcaption")) {
				code = FIGCAPTION;
			} else if (lName.equals("figure")) {
				code = FIGURE;
			} else if (lName.equals("font")) {
				code = FONT;
			} else if (lName.equals("footer")) {
				code = FOOTER;
			} else if (lName.equals("form")) {
				code = FORM;
			} else if (lName.equals("frame")) {
				code = FRAME;
			} else if (lName.equals("frameset")) {
				code = FRAMESET;
			}
			break;
		case 'h':
			if (lName.equals("h1")) {
				code = H1;
			} else if (lName.equals("h2")) {
				code = H2;
			} else if (lName.equals("h3")) {
				code = H3;
			} else if (lName.equals("h4")) {
				code = H4;
			} else if (lName.equals("h5")) {
				code = H5;
			} else if (lName.equals("h6")) {
				code = H6;
			} else if (lName.equals("head")) {
				code = HEAD;
			} else if (lName.equals("header")) {
				code = HEADER;
			} else if (lName.equals("hgroup")) {
				code = HGROUP;
			} else if (lName.equals("hr")) {
				code = HR;
			} else if (lName.equals("html")) {
				code = HTML;
			}
			break;
		case 'i':
			if (lName.equals("i")) {
				code = I;
			} else if (lName.equals("iframe")) {
				code = IFRAME;
			} else if (lName.equals("img")) {
				code = IMG;
			} else if (lName.equals("input")) {
				code = INPUT;
			} else if (lName.equals("ins")) {
				code = INS;
			} else if (lName.equals("isindex")) {
				code = ISINDEX;
			}
			break;
		case 'k':
			if (lName.equals("kbd")) {
				code = KBD;
			} else if (lName.equals("keygen")) {
				code = KEYGEN;
			}
			break;
		case 'l':
			if (lName.equals("label")) {
				code = LABEL;
			} else if (lName.equals("legend")) {
				code = LEGEND;
			} else if (lName.equals("li")) {
				code = LI;
			} else if (lName.equals("link")) {
				code = LINK;
			} else if (lName.equals("listing")) {
				code = LISTING;
			}
			break;
		case 'm':
			if (lName.equals("main")) {
				code = MAIN;
			} else if (lName.equals("map")) {
				code = MAP;
			} else if (lName.equals("mark")) {
				code = MARK;
			} else if (lName.equals("marquee")) {
				code = MARQUEE;
			} else if (lName.equals("menu")) {
				code = MENU;
			} else if (lName.equals("meta")) {
				code = META;
			} else if (lName.equals("meter")) {
				code = METER;
			}
			break;
		case 'n':
			if (lName.equals("nav")) {
				code = NAV;
			} else if (lName.equals("nextid")) {
				code = NEXTID;
			} else if (lName.equals("nobr")) {
				code = NOBR;
			} else if (lName.equals("noembed")) {
				code = NOEMBED;
			} else if (lName.equals("noframes")) {
				code = NOFRAMES;
			} else if (lName.equals("nolayer")) {
				code = NOLAYER;
			} else if (lName.equals("noscript")) {
				code = NOSCRIPT;
			}
			break;
		case 'o':
			if (lName.equals("object")) {
				code = OBJECT;
			} else if (lName.equals("ol")) {
				code = OL;
			} else if (lName.equals("optgroup")) {
				code = OPTGROUP;
			} else if (lName.equals("option")) {
				code = OPTION;
			} else if (lName.equals("output")) {
				code = OUTPUT;
			}
			break;
		case 'p':
			if (lName.equals("p")) {
				code = P;
			} else if (lName.equals("param")) {
				code = PARAM;
			} else if (lName.equals("picture")) {
				code = PICTURE;
			} else if (lName.equals("plaintext")) {
				code = PLAINTEXT;
			} else if (lName.equals("pre")) {
				code = PRE;
			} else if (lName.equals("progress")) {
				code = PROGRESS;
			}
			break;
		case 'q':
			if (lName.equals("q")) {
				code = Q;
			}
			break;
		case 'r':
			if (lName.equals("rb")) {
				code = RB;
			} else if (lName.equals("rp")) {
				code = RP;
			} else if (lName.equals("rt")) {
				code = RT;
			} else if (lName.equals("ruby")) {
				code = RUBY;
			}
			break;
		case 's':
			if (lName.equals("s")) {
				code = S;
			} else
				switch (lName.charAt(1)) {
				case 'a':
					if (lName.equals("samp")) {
						code = SAMP;
					}
					break;
				case 'c':
					if (lName.equals("script")) {
						code = SCRIPT;
					}
					break;
				case 'e':
					if (lName.equals("section")) {
						code = SECTION;
					} else if (lName.equals("select")) {
						code = SELECT;
					} else if (lName.equals("server")) {
						code = SERVER;
					}
					break;
				case 'm':
					if (lName.equals("small")) {
						code = SMALL;
					}
					break;
				case 'o':
					if (lName.equals("source")) {
						code = SOURCE;
					}
					break;
				case 'p':
					if (lName.equals("span")) {
						code = SPAN;
					}
					break;
				case 't':
					if (lName.equals("strike")) {
						code = STRIKE;
					} else if (lName.equals("strong")) {
						code = STRONG;
					} else if (lName.equals("style")) {
						code = STYLE;
					}
					break;
				case 'u':
					if (lName.equals("sub")) {
						code = SUB;
					} else if (lName.equals("summary")) {
						code = SUMMARY;
					} else if (lName.equals("sup")) {
						code = SUP;
					}
					break;
				}
			break;
		case 't':
			if (lName.equals("table")) {
				code = TABLE;
			} else if (lName.equals("tbody")) {
				code = TBODY;
			} else if (lName.equals("td")) {
				code = TD;
			} else if (lName.equals("template")) {
				code = TEMPLATE;
			} else if (lName.equals("textarea")) {
				code = TEXTAREA;
			} else if (lName.equals("tfoot")) {
				code = TFOOT;
			} else if (lName.equals("th")) {
				code = TH;
			} else if (lName.equals("thead")) {
				code = THEAD;
			} else if (lName.equals("time")) {
				code = TIME;
			} else if (lName.equals("title")) {
				code = TITLE;
			} else if (lName.equals("tr")) {
				code = TR;
			} else if (lName.equals("track")) {
				code = TRACK;
			} else if (lName.equals("tt")) {
				code = TT;
			}
			break;
		case 'u':
			if (lName.equals("u")) {
				code = U;
			} else if (lName.equals("ul")) {
				code = UL;
			}
			break;
		case 'v':
			if (lName.equals("var")) {
				code = VAR;
			} else if (lName.equals("video")) {
				code = VIDEO;
			}
			break;
		case 'w':
			if (lName.equals("wbr")) {
				code = WBR;
			}
			break;
		case 'x':
			if (lName.equals("xmp")) {
				code = XMP;
			}
			break;
		}
		return code;
	}
}
