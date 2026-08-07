package net.zamasoft.foliojet.xml.parser;

import org.commonmark.internal.Bracket;
import org.commonmark.internal.Delimiter;
import org.commonmark.internal.inline.*;
import org.commonmark.internal.util.Escaping;
import org.commonmark.internal.util.LinkScanner;
import org.commonmark.node.*;
import org.commonmark.parser.InlineParser;
import org.commonmark.parser.InlineParserContext;
import org.commonmark.parser.SourceLines;
import org.commonmark.parser.beta.Scanner;
import org.commonmark.parser.beta.*;
import org.commonmark.parser.delimiter.DelimiterProcessor;
import org.commonmark.parser.delimiter.DelimiterRun;
import org.commonmark.text.Characters;

import java.util.*;

/**
 * CJK句読点に隣接する強調(** / *)を解釈できるインラインパーサーです。
 * <p>
 * CommonMarkのフランキング規則は「閉じデリミタの直前が句読点なら、直後は
 * 空白または句読点でなければならない」と定めるため、
 * {@code **精密に：**希釈は…} のように全角句読点(：。、）等)の直後で強調を
 * 閉じて地の文(漢字・かな)が続く和文の常用パターンが強調と認められず、
 * {@code **} がそのまま出力される(GitHub等でも壊れる日本語Markdownの既知問題)。
 * 本実装はフランキング判定に限りCJK句読点(U+3000-303F、U+FF00-FFEF、U+30FB)を
 * 句読点として扱わないことでこれを解消する。CommonMark仕様からの意図的な
 * 逸脱であり、欧文の挙動(ASCII句読点)は変えない。
 * </p>
 * <p>
 * 本体は commonmark-java 0.29.0 (BSD-2-Clause, Copyright (c) 2015, Atlassian
 * Pty Ltd) の {@code org.commonmark.internal.InlineParserImpl} の複製で、
 * 差分は {@link #scanDelimiters} の句読点判定({@code isFlankingPunctuation})
 * のみ。フランキング判定は private メソッドの内部にあり継承等では差し替え
 * られないため、全体複製が必要になっている。commonmark-java を更新する際は
 * この複製も元クラスと同期し直すこと。
 * </p>
 *
 * @see MarkdownParser
 */
public class CjkFriendlyInlineParser implements InlineParser, InlineParserState {

    private final InlineParserContext context;
    private final List<InlineContentParserFactory> inlineContentParserFactories;
    private final Map<Character, DelimiterProcessor> delimiterProcessors;
    private final List<LinkProcessor> linkProcessors;
    private final BitSet specialCharacters;
    private final BitSet linkMarkers;

    private Map<Character, List<InlineContentParser>> inlineParsers;
    private Scanner scanner;
    private boolean includeSourceSpans;
    private int trailingSpaces;

    /**
     * Top delimiter (emphasis, strong emphasis or custom emphasis). (Brackets are on a separate stack, different
     * from the algorithm described in the spec.)
     */
    private Delimiter lastDelimiter;

    /**
     * Top opening bracket (<code>[</code> or <code>![)</code>).
     */
    private Bracket lastBracket;

    public CjkFriendlyInlineParser(InlineParserContext context) {
        this.context = context;
        this.inlineContentParserFactories = calculateInlineContentParserFactories(context.getCustomInlineContentParserFactories());
        this.delimiterProcessors = calculateDelimiterProcessors(context.getCustomDelimiterProcessors());
        this.linkProcessors = calculateLinkProcessors(context.getCustomLinkProcessors());
        this.linkMarkers = calculateLinkMarkers(context.getCustomLinkMarkers());
        this.specialCharacters = calculateSpecialCharacters(linkMarkers, this.delimiterProcessors.keySet(), this.inlineContentParserFactories);
    }

    private List<InlineContentParserFactory> calculateInlineContentParserFactories(List<InlineContentParserFactory> customFactories) {
        // Custom parsers can override built-in parsers if they want, so make sure they are tried first
        var list = new ArrayList<>(customFactories);
        list.add(new BackslashInlineParser.Factory());
        list.add(new BackticksInlineParser.Factory());
        list.add(new EntityInlineParser.Factory());
        list.add(new AutolinkInlineParser.Factory());
        list.add(new HtmlInlineParser.Factory());
        return list;
    }

    private List<LinkProcessor> calculateLinkProcessors(List<LinkProcessor> linkProcessors) {
        // Custom link processors can override the built-in behavior, so make sure they are tried first
        var list = new ArrayList<>(linkProcessors);
        list.add(new CoreLinkProcessor());
        return list;
    }

    private static Map<Character, DelimiterProcessor> calculateDelimiterProcessors(List<DelimiterProcessor> delimiterProcessors) {
        var map = new HashMap<Character, DelimiterProcessor>();
        addDelimiterProcessors(List.of(new AsteriskDelimiterProcessor(), new UnderscoreDelimiterProcessor()), map);
        addDelimiterProcessors(delimiterProcessors, map);
        return map;
    }

    private static void addDelimiterProcessors(Iterable<DelimiterProcessor> delimiterProcessors, Map<Character, DelimiterProcessor> map) {
        for (DelimiterProcessor delimiterProcessor : delimiterProcessors) {
            char opening = delimiterProcessor.getOpeningCharacter();
            char closing = delimiterProcessor.getClosingCharacter();
            if (opening == closing) {
                DelimiterProcessor old = map.get(opening);
                if (old != null && old.getOpeningCharacter() == old.getClosingCharacter()) {
                    StaggeredDelimiterProcessor s;
                    if (old instanceof StaggeredDelimiterProcessor) {
                        s = (StaggeredDelimiterProcessor) old;
                    } else {
                        s = new StaggeredDelimiterProcessor(opening);
                        s.add(old);
                    }
                    s.add(delimiterProcessor);
                    map.put(opening, s);
                } else {
                    addDelimiterProcessorForChar(opening, delimiterProcessor, map);
                }
            } else {
                addDelimiterProcessorForChar(opening, delimiterProcessor, map);
                addDelimiterProcessorForChar(closing, delimiterProcessor, map);
            }
        }
    }

    private static void addDelimiterProcessorForChar(char delimiterChar, DelimiterProcessor toAdd, Map<Character, DelimiterProcessor> delimiterProcessors) {
        DelimiterProcessor existing = delimiterProcessors.put(delimiterChar, toAdd);
        if (existing != null) {
            throw new IllegalArgumentException("Delimiter processor conflict with delimiter char '" + delimiterChar + "'");
        }
    }

    private static BitSet calculateLinkMarkers(Set<Character> linkMarkers) {
        var bitSet = new BitSet();
        for (var c : linkMarkers) {
            bitSet.set(c);
        }
        bitSet.set('!');
        return bitSet;
    }

    private static BitSet calculateSpecialCharacters(BitSet linkMarkers,
                                                     Set<Character> delimiterCharacters,
                                                     List<InlineContentParserFactory> inlineContentParserFactories) {
        BitSet bitSet = (BitSet) linkMarkers.clone();
        for (Character c : delimiterCharacters) {
            bitSet.set(c);
        }
        for (var factory : inlineContentParserFactories) {
            for (var c : factory.getTriggerCharacters()) {
                bitSet.set(c);
            }
        }
        bitSet.set('[');
        bitSet.set(']');
        bitSet.set('!');
        bitSet.set('\n');
        return bitSet;
    }

    private Map<Character, List<InlineContentParser>> createInlineContentParsers() {
        var map = new HashMap<Character, List<InlineContentParser>>();
        for (var factory : inlineContentParserFactories) {
            var parser = factory.create();
            for (var c : factory.getTriggerCharacters()) {
                map.computeIfAbsent(c, k -> new ArrayList<>()).add(parser);
            }
        }
        return map;
    }

    @Override
    public Scanner scanner() {
        return scanner;
    }

    /**
     * Parse content in block into inline children, appending them to the block node.
     */
    @Override
    public void parse(SourceLines lines, Node block) {
        reset(lines);

        while (true) {
            var nodes = parseInline();
            if (nodes == null) {
                break;
            }
            for (Node node : nodes) {
                block.appendChild(node);
            }
        }

        processDelimiters(null);
        mergeChildTextNodes(block);
    }

    void reset(SourceLines lines) {
        this.scanner = Scanner.of(lines);
        this.includeSourceSpans = !lines.getSourceSpans().isEmpty();
        this.trailingSpaces = 0;
        this.lastDelimiter = null;
        this.lastBracket = null;
        this.inlineParsers = createInlineContentParsers();
    }

    private Text text(SourceLines sourceLines) {
        Text text = new Text(sourceLines.getContent());
        text.setSourceSpans(sourceLines.getSourceSpans());
        return text;
    }

    /**
     * Parse the next inline element in subject, advancing our position.
     * On success, return the new inline node.
     * On failure, return null.
     */
    private List<? extends Node> parseInline() {
        char c = scanner.peek();

        switch (c) {
            case '[':
                return List.of(parseOpenBracket());
            case ']':
                return List.of(parseCloseBracket());
            case '\n':
                return List.of(parseLineBreak());
            case Scanner.END:
                return null;
        }

        if (linkMarkers.get(c)) {
            var markerPosition = scanner.position();
            var nodes = parseLinkMarker();
            if (nodes != null) {
                return nodes;
            }
            // Reset and try other things (e.g. inline parsers below)
            scanner.setPosition(markerPosition);
        }

        // No inline parser, delimiter or other special handling.
        if (!specialCharacters.get(c)) {
            return List.of(parseText());
        }

        List<InlineContentParser> inlineParsers = this.inlineParsers.get(c);
        if (inlineParsers != null) {
            Position position = scanner.position();
            for (InlineContentParser inlineParser : inlineParsers) {
                ParsedInline parsedInline = inlineParser.tryParse(this);
                if (parsedInline instanceof ParsedInlineImpl) {
                    ParsedInlineImpl parsedInlineImpl = (ParsedInlineImpl) parsedInline;
                    Node node = parsedInlineImpl.getNode();
                    scanner.setPosition(parsedInlineImpl.getPosition());
                    if (includeSourceSpans && node.getSourceSpans().isEmpty()) {
                        node.setSourceSpans(scanner.getSource(position, scanner.position()).getSourceSpans());
                    }
                    return List.of(node);
                } else {
                    // Reset position
                    scanner.setPosition(position);
                }
            }
        }

        DelimiterProcessor delimiterProcessor = delimiterProcessors.get(c);
        if (delimiterProcessor != null) {
            List<? extends Node> nodes = parseDelimiters(delimiterProcessor, c);
            if (nodes != null) {
                return nodes;
            }
        }

        // If we get here, even for a special/delimiter character, we will just treat it as text.
        return List.of(parseText());
    }

    /**
     * Attempt to parse delimiters like emphasis, strong emphasis or custom delimiters.
     */
    private List<? extends Node> parseDelimiters(DelimiterProcessor delimiterProcessor, char delimiterChar) {
        DelimiterData res = scanDelimiters(delimiterProcessor, delimiterChar);
        if (res == null) {
            return null;
        }

        List<Text> characters = res.characters;

        // Add entry to stack for this opener
        lastDelimiter = new Delimiter(characters, delimiterChar, res.canOpen, res.canClose, lastDelimiter);
        if (lastDelimiter.previous != null) {
            lastDelimiter.previous.next = lastDelimiter;
        }

        return characters;
    }

    /**
     * Add open bracket to delimiter stack and add a text node to block's children.
     */
    private Node parseOpenBracket() {
        Position start = scanner.position();
        scanner.next();
        Position contentPosition = scanner.position();

        Text node = text(scanner.getSource(start, contentPosition));

        // Add entry to stack for this opener
        addBracket(Bracket.link(node, start, contentPosition, lastBracket, lastDelimiter));

        return node;
    }

    /**
     * If next character is {@code [}, add a bracket to the stack.
     * Otherwise, return null.
     */
    private List<? extends Node> parseLinkMarker() {
        var markerPosition = scanner.position();
        scanner.next();
        var bracketPosition = scanner.position();
        if (scanner.next('[')) {
            var contentPosition = scanner.position();
            var bangNode = text(scanner.getSource(markerPosition, bracketPosition));
            var bracketNode = text(scanner.getSource(bracketPosition, contentPosition));

            // Add entry to stack for this opener
            addBracket(Bracket.withMarker(bangNode, markerPosition, bracketNode, bracketPosition, contentPosition, lastBracket, lastDelimiter));
            return List.of(bangNode, bracketNode);
        } else {
            return null;
        }
    }

    /**
     * Try to match close bracket against an opening in the delimiter stack. Return either a link or image, or a
     * plain [ character. If there is a matching delimiter, remove it from the delimiter stack.
     */
    private Node parseCloseBracket() {
        Position beforeClose = scanner.position();
        scanner.next();
        Position afterClose = scanner.position();

        // Get previous `[` or `![`
        Bracket opener = lastBracket;
        if (opener == null) {
            // No matching opener, just return a literal.
            return text(scanner.getSource(beforeClose, afterClose));
        }

        if (!opener.allowed) {
            // Matching opener, but it's not allowed, just return a literal.
            removeLastBracket();
            return text(scanner.getSource(beforeClose, afterClose));
        }

        var linkOrImage = parseLinkOrImage(opener, beforeClose);
        if (linkOrImage != null) {
            return linkOrImage;
        }
        scanner.setPosition(afterClose);

        // Nothing parsed, just parse the bracket as text and continue
        removeLastBracket();
        return text(scanner.getSource(beforeClose, afterClose));
    }

    private Node parseLinkOrImage(Bracket opener, Position beforeClose) {
        var linkInfo = parseLinkInfo(opener, beforeClose);
        if (linkInfo == null) {
            return null;
        }
        var processorStartPosition = scanner.position();

        for (var linkProcessor : linkProcessors) {
            var linkResult = linkProcessor.process(linkInfo, scanner, context);
            if (!(linkResult instanceof LinkResultImpl)) {
                // Reset position in case the processor used the scanner, and it didn't work out.
                scanner.setPosition(processorStartPosition);
                continue;
            }

            var result = (LinkResultImpl) linkResult;
            var node = result.getNode();
            var position = result.getPosition();
            var includeMarker = result.isIncludeMarker();

            switch (result.getType()) {
                case WRAP:
                    scanner.setPosition(position);
                    return wrapBracket(opener, node, includeMarker);
                case REPLACE:
                    scanner.setPosition(position);
                    return replaceBracket(opener, node, includeMarker);
            }
        }

        return null;
    }

    private LinkInfo parseLinkInfo(Bracket opener, Position beforeClose) {
        // Check to see if we have a link (or image, with a ! in front). The different types:
        // - Inline:       `[foo](/uri)` or with optional title `[foo](/uri "title")`
        // - Reference links
        //   - Full:      `[foo][bar]` (foo is the text and bar is the label that needs to match a reference)
        //   - Collapsed: `[foo][]`    (foo is both the text and label)
        //   - Shortcut:  `[foo]`      (foo is both the text and label)

        // Starting position is after the closing `]`
        var afterClose = scanner.position();

        // Maybe an inline link/image
        var destinationTitle = parseInlineDestinationTitle(scanner);
        if (destinationTitle != null) {
            var text = scanner.getSource(opener.contentPosition, beforeClose).getContent();
            return new LinkInfoImpl(opener.markerNode, opener.bracketNode, text, null, destinationTitle.destination, destinationTitle.title, afterClose);
        }
        // Not an inline link/image, rewind back to after `]`.
        scanner.setPosition(afterClose);

        // Maybe a reference link/image like `[foo][bar]`, `[foo][]` or `[foo]`.
        // Note that even `[foo](` could be a valid link if foo is a reference, which is why we try this even if the `(`
        // failed to be parsed as an inline link/image before.

        // See if there's a link label like `[bar]` or `[]`
        var label = parseLinkLabel(scanner);
        if (label == null) {
            // No label, rewind back
            scanner.setPosition(afterClose);
        }
        var textIsReference = label == null || label.isEmpty();
        if (opener.bracketAfter && textIsReference && opener.markerNode == null) {
            // In case of shortcut or collapsed links, the text is used as the reference. But the reference is not allowed to
            // contain an unescaped bracket, so if that's the case we don't need to continue. This is an optimization.
            return null;
        }

        var text = scanner.getSource(opener.contentPosition, beforeClose).getContent();
        return new LinkInfoImpl(opener.markerNode, opener.bracketNode, text, label, null, null, afterClose);
    }

    private Node wrapBracket(Bracket opener, Node wrapperNode, boolean includeMarker) {
        // Add all nodes between the opening bracket and now (closing bracket) as child nodes of the link
        Node n = opener.bracketNode.getNext();
        while (n != null) {
            Node next = n.getNext();
            wrapperNode.appendChild(n);
            n = next;
        }

        if (includeSourceSpans) {
            var startPosition = includeMarker && opener.markerPosition != null ? opener.markerPosition : opener.bracketPosition;
            wrapperNode.setSourceSpans(scanner.getSource(startPosition, scanner.position()).getSourceSpans());
        }

        // Process delimiters such as emphasis inside link/image
        processDelimiters(opener.previousDelimiter);
        mergeChildTextNodes(wrapperNode);
        // We don't need the corresponding text node anymore, we turned it into a link/image node
        if (includeMarker && opener.markerNode != null) {
            opener.markerNode.unlink();
        }
        opener.bracketNode.unlink();
        removeLastBracket();

        // Links within links are not allowed. We found this link, so there can be no other links around it.
        if (opener.markerNode == null) {
            disallowPreviousLinks();
        }

        return wrapperNode;
    }

    private Node replaceBracket(Bracket opener, Node node, boolean includeMarker) {
        // Remove delimiters (but keep text nodes)
        while (lastDelimiter != null && lastDelimiter != opener.previousDelimiter) {
            removeDelimiterKeepNode(lastDelimiter);
        }

        if (includeSourceSpans) {
            var startPosition = includeMarker && opener.markerPosition != null ? opener.markerPosition : opener.bracketPosition;
            node.setSourceSpans(scanner.getSource(startPosition, scanner.position()).getSourceSpans());
        }

        removeLastBracket();

        // Remove nodes that we added since the opener, because we're replacing them
        Node n = includeMarker && opener.markerNode != null ? opener.markerNode : opener.bracketNode;
        while (n != null) {
            var next = n.getNext();
            n.unlink();
            n = next;
        }

        // Links within links are not allowed. We found this link, so there can be no other links around it.
        // Note that this makes any syntax like `[foo]` behave the same as built-in links, which is probably a good
        // default (it works for footnotes). It might be useful for a `LinkProcessor` to be able to specify the
        // behavior; something we could add to `LinkResult` in the future if requested.
        if (opener.markerNode == null || !includeMarker) {
            disallowPreviousLinks();
        }

        return node;
    }

    private void addBracket(Bracket bracket) {
        if (lastBracket != null) {
            lastBracket.bracketAfter = true;
        }
        lastBracket = bracket;
    }

    private void removeLastBracket() {
        lastBracket = lastBracket.previous;
    }

    private void disallowPreviousLinks() {
        Bracket bracket = lastBracket;
        while (bracket != null) {
            if (bracket.markerNode == null) {
                // Disallow link opener. It will still get matched, but will not result in a link.
                bracket.allowed = false;
            }
            bracket = bracket.previous;
        }
    }

    /**
     * Try to parse the destination and an optional title for an inline link/image.
     */
    private static DestinationTitle parseInlineDestinationTitle(Scanner scanner) {
        if (!scanner.next('(')) {
            return null;
        }

        scanner.whitespace();
        String dest = parseLinkDestination(scanner);
        if (dest == null) {
            return null;
        }

        String title = null;
        int whitespace = scanner.whitespace();
        // title needs a whitespace before
        if (whitespace >= 1) {
            title = parseLinkTitle(scanner);
            scanner.whitespace();
        }
        if (!scanner.next(')')) {
            // Don't have a closing `)`, so it's not a destination and title.
            // Note that something like `[foo](` could still be valid later, `(` will just be text.
            return null;
        }
        return new DestinationTitle(dest, title);
    }

    /**
     * Attempt to parse link destination, returning the string or null if no match.
     */
    private static String parseLinkDestination(Scanner scanner) {
        char delimiter = scanner.peek();
        Position start = scanner.position();
        if (!LinkScanner.scanLinkDestination(scanner)) {
            return null;
        }

        String dest;
        if (delimiter == '<') {
            // chop off surrounding <..>:
            String rawDestination = scanner.getSource(start, scanner.position()).getContent();
            dest = rawDestination.substring(1, rawDestination.length() - 1);
        } else {
            dest = scanner.getSource(start, scanner.position()).getContent();
        }

        return Escaping.unescapeString(dest);
    }

    /**
     * Attempt to parse link title (sans quotes), returning the string or null if no match.
     */
    private static String parseLinkTitle(Scanner scanner) {
        Position start = scanner.position();
        if (!LinkScanner.scanLinkTitle(scanner)) {
            return null;
        }

        // chop off ', " or parens
        String rawTitle = scanner.getSource(start, scanner.position()).getContent();
        String title = rawTitle.substring(1, rawTitle.length() - 1);
        return Escaping.unescapeString(title);
    }

    /**
     * Attempt to parse a link label, returning the label between the brackets or null.
     */
    static String parseLinkLabel(Scanner scanner) {
        if (!scanner.next('[')) {
            return null;
        }

        Position start = scanner.position();
        if (!LinkScanner.scanLinkLabelContent(scanner)) {
            return null;
        }
        Position end = scanner.position();

        if (!scanner.next(']')) {
            return null;
        }

        String content = scanner.getSource(start, end).getContent();
        // spec: A link label can have at most 999 characters inside the square brackets.
        if (content.length() > 999) {
            return null;
        }

        return content;
    }

    private Node parseLineBreak() {
        scanner.next();

        var hard = trailingSpaces >= 2;
        trailingSpaces = 0;
        if (hard) {
            return new HardLineBreak();
        } else {
            return new SoftLineBreak();
        }
    }

    /**
     * Parse the next character as plain text, and possibly more if the following characters are non-special.
     */
    private Node parseText() {
        Position start = scanner.position();
        scanner.next();
        char c;
        while (true) {
            c = scanner.peek();
            if (c == Scanner.END || specialCharacters.get(c)) {
                break;
            }
            scanner.next();
        }

        SourceLines source = scanner.getSource(start, scanner.position());
        String content = source.getContent();

        if (c == '\n') {
            // We parsed until the end of the line. Trim any trailing spaces and remember them (for hard line breaks).
            int end = Characters.skipBackwards(' ', content, content.length() - 1, 0) + 1;
            trailingSpaces = content.length() - end;
            content = content.substring(0, end);
        } else if (c == Scanner.END) {
            // For the last line, both tabs and spaces are trimmed for some reason (checked with commonmark.js).
            int end = Characters.skipSpaceTabBackwards(content, content.length() - 1, 0) + 1;
            content = content.substring(0, end);
        }

        Text text = new Text(content);
        text.setSourceSpans(source.getSourceSpans());
        return text;
    }

    /**
     * Scan a sequence of characters with code delimiterChar, and return information about the number of delimiters
     * and whether they are positioned such that they can open and/or close emphasis or strong emphasis.
     *
     * @return information about delimiter run, or {@code null}
     */
    private DelimiterData scanDelimiters(DelimiterProcessor delimiterProcessor, char delimiterChar) {
        int before = scanner.peekPreviousCodePoint();
        Position start = scanner.position();

        // Quick check to see if we have enough delimiters.
        int delimiterCount = scanner.matchMultiple(delimiterChar);
        if (delimiterCount < delimiterProcessor.getMinLength()) {
            scanner.setPosition(start);
            return null;
        }

        // We do have enough, extract a text node for each delimiter character.
        List<Text> delimiters = new ArrayList<>();
        scanner.setPosition(start);
        Position positionBefore = start;
        while (scanner.next(delimiterChar)) {
            delimiters.add(text(scanner.getSource(positionBefore, scanner.position())));
            positionBefore = scanner.position();
        }

        int after = scanner.peekCodePoint();

        // We could be more lazy here, in most cases we don't need to do every match case.
        // [CJK対応差分] Characters.isPunctuationCodePoint → isFlankingPunctuation
        boolean beforeIsPunctuation = before == Scanner.END || isFlankingPunctuation(before);
        boolean beforeIsWhitespace = before == Scanner.END || Characters.isWhitespaceCodePoint(before);
        boolean afterIsPunctuation = after == Scanner.END || isFlankingPunctuation(after);
        boolean afterIsWhitespace = after == Scanner.END || Characters.isWhitespaceCodePoint(after);

        boolean leftFlanking = !afterIsWhitespace &&
                (!afterIsPunctuation || beforeIsWhitespace || beforeIsPunctuation);
        boolean rightFlanking = !beforeIsWhitespace &&
                (!beforeIsPunctuation || afterIsWhitespace || afterIsPunctuation);
        boolean canOpen;
        boolean canClose;
        if (delimiterChar == '_') {
            canOpen = leftFlanking && (!rightFlanking || beforeIsPunctuation);
            canClose = rightFlanking && (!leftFlanking || afterIsPunctuation);
        } else {
            canOpen = leftFlanking && delimiterChar == delimiterProcessor.getOpeningCharacter();
            canClose = rightFlanking && delimiterChar == delimiterProcessor.getClosingCharacter();
        }

        return new DelimiterData(delimiters, canOpen, canClose);
    }

    /**
     * [CJK対応差分] フランキング判定用の句読点判定です。CJK句読点は句読点として
     * 扱いません(クラスコメント参照)。対象はCJKの記号と句読点(U+3000-303F)、
     * 半角・全角形(U+FF00-FFEF)、中点(U+30FB)。
     */
    private static boolean isFlankingPunctuation(int codePoint) {
        if ((codePoint >= 0x3000 && codePoint <= 0x303F) || (codePoint >= 0xFF00 && codePoint <= 0xFFEF)
                || codePoint == 0x30FB) {
            return false;
        }
        return Characters.isPunctuationCodePoint(codePoint);
    }

    /**
     * 複製元と同じパッケージのpackage-privateクラス
     * {@code org.commonmark.internal.StaggeredDelimiterProcessor} の複製です
     * (本体クラスと同様にcommonmark-java 0.29.0由来、無改変)。
     * 同一文字に複数のDelimiterProcessorが登録された場合にデリミタ長で
     * 振り分けるための内部部品で、複製元がpackage-privateのため参照できず、
     * フォークを1ファイルに自己完結させるためネストクラスとして取り込んでいる。
     */
    private static class StaggeredDelimiterProcessor implements DelimiterProcessor {

        private final char delim;
        private int minLength = 0;
        private LinkedList<DelimiterProcessor> processors = new LinkedList<>(); // in reverse getMinLength order

        StaggeredDelimiterProcessor(char delim) {
            this.delim = delim;
        }

        @Override
        public char getOpeningCharacter() {
            return delim;
        }

        @Override
        public char getClosingCharacter() {
            return delim;
        }

        @Override
        public int getMinLength() {
            return minLength;
        }

        void add(DelimiterProcessor dp) {
            final int len = dp.getMinLength();
            ListIterator<DelimiterProcessor> it = processors.listIterator();
            boolean added = false;
            while (it.hasNext()) {
                DelimiterProcessor p = it.next();
                int pLen = p.getMinLength();
                if (len > pLen) {
                    it.previous();
                    it.add(dp);
                    added = true;
                    break;
                } else if (len == pLen) {
                    throw new IllegalArgumentException("Cannot add two delimiter processors for char '" + delim + "' and minimum length " + len + "; conflicting processors: " + p + ", " + dp);
                }
            }
            if (!added) {
                processors.add(dp);
                this.minLength = len;
            }
        }

        private DelimiterProcessor findProcessor(int len) {
            for (DelimiterProcessor p : processors) {
                if (p.getMinLength() <= len) {
                    return p;
                }
            }
            return processors.getFirst();
        }

        @Override
        public int process(DelimiterRun openingRun, DelimiterRun closingRun) {
            return findProcessor(openingRun.length()).process(openingRun, closingRun);
        }
    }

    private void processDelimiters(Delimiter stackBottom) {

        Map<Character, Delimiter> openersBottom = new HashMap<>();

        // find first closer above stackBottom:
        Delimiter closer = lastDelimiter;
        while (closer != null && closer.previous != stackBottom) {
            closer = closer.previous;
        }
        // move forward, looking for closers, and handling each
        while (closer != null) {
            char delimiterChar = closer.delimiterChar;

            DelimiterProcessor delimiterProcessor = delimiterProcessors.get(delimiterChar);
            if (!closer.canClose() || delimiterProcessor == null) {
                closer = closer.next;
                continue;
            }

            char openingDelimiterChar = delimiterProcessor.getOpeningCharacter();

            // Found delimiter closer. Now look back for first matching opener.
            int usedDelims = 0;
            boolean openerFound = false;
            boolean potentialOpenerFound = false;
            Delimiter opener = closer.previous;
            while (opener != null && opener != stackBottom && opener != openersBottom.get(delimiterChar)) {
                if (opener.canOpen() && opener.delimiterChar == openingDelimiterChar) {
                    potentialOpenerFound = true;
                    usedDelims = delimiterProcessor.process(opener, closer);
                    if (usedDelims > 0) {
                        openerFound = true;
                        break;
                    }
                }
                opener = opener.previous;
            }

            if (!openerFound) {
                if (!potentialOpenerFound) {
                    // Set lower bound for future searches for openers.
                    // Only do this when we didn't even have a potential
                    // opener (one that matches the character and can open).
                    // If an opener was rejected because of the number of
                    // delimiters (e.g. because of the "multiple of 3" rule),
                    // we want to consider it next time because the number
                    // of delimiters can change as we continue processing.
                    openersBottom.put(delimiterChar, closer.previous);
                    if (!closer.canOpen()) {
                        // We can remove a closer that can't be an opener,
                        // once we've seen there's no matching opener:
                        removeDelimiterKeepNode(closer);
                    }
                }
                closer = closer.next;
                continue;
            }

            // Remove number of used delimiters nodes.
            for (int i = 0; i < usedDelims; i++) {
                Text delimiter = opener.characters.remove(opener.characters.size() - 1);
                delimiter.unlink();
            }
            for (int i = 0; i < usedDelims; i++) {
                Text delimiter = closer.characters.remove(0);
                delimiter.unlink();
            }

            removeDelimitersBetween(opener, closer);

            // No delimiter characters left to process, so we can remove delimiter and the now empty node.
            if (opener.length() == 0) {
                removeDelimiterAndNodes(opener);
            }

            if (closer.length() == 0) {
                Delimiter next = closer.next;
                removeDelimiterAndNodes(closer);
                closer = next;
            }
        }

        // remove all delimiters
        while (lastDelimiter != null && lastDelimiter != stackBottom) {
            removeDelimiterKeepNode(lastDelimiter);
        }
    }

    private void removeDelimitersBetween(Delimiter opener, Delimiter closer) {
        Delimiter delimiter = closer.previous;
        while (delimiter != null && delimiter != opener) {
            Delimiter previousDelimiter = delimiter.previous;
            removeDelimiterKeepNode(delimiter);
            delimiter = previousDelimiter;
        }
    }

    /**
     * Remove the delimiter and the corresponding text node. For used delimiters, e.g. `*` in `*foo*`.
     */
    private void removeDelimiterAndNodes(Delimiter delim) {
        removeDelimiter(delim);
    }

    /**
     * Remove the delimiter but keep the corresponding node as text. For unused delimiters such as `_` in `foo_bar`.
     */
    private void removeDelimiterKeepNode(Delimiter delim) {
        removeDelimiter(delim);
    }

    private void removeDelimiter(Delimiter delim) {
        if (delim.previous != null) {
            delim.previous.next = delim.next;
        }
        if (delim.next == null) {
            // top of stack
            lastDelimiter = delim.previous;
        } else {
            delim.next.previous = delim.previous;
        }
    }

    private void mergeChildTextNodes(Node node) {
        // No children, no need for merging
        if (node.getFirstChild() == null) {
            return;
        }

        mergeTextNodesInclusive(node.getFirstChild(), node.getLastChild());
    }

    private void mergeTextNodesInclusive(Node fromNode, Node toNode) {
        Text first = null;
        Text last = null;
        int length = 0;

        Node node = fromNode;
        while (node != null) {
            if (node instanceof Text) {
                Text text = (Text) node;
                if (first == null) {
                    first = text;
                }
                length += text.getLiteral().length();
                last = text;
            } else {
                mergeIfNeeded(first, last, length);
                first = null;
                last = null;
                length = 0;

                mergeChildTextNodes(node);
            }
            if (node == toNode) {
                break;
            }
            node = node.getNext();
        }

        mergeIfNeeded(first, last, length);
    }

    private void mergeIfNeeded(Text first, Text last, int textLength) {
        if (first != null && last != null && first != last) {
            StringBuilder sb = new StringBuilder(textLength);
            sb.append(first.getLiteral());
            SourceSpans sourceSpans = null;
            if (includeSourceSpans) {
                sourceSpans = new SourceSpans();
                sourceSpans.addAll(first.getSourceSpans());
            }
            Node node = first.getNext();
            Node stop = last.getNext();
            while (node != stop) {
                sb.append(((Text) node).getLiteral());
                if (sourceSpans != null) {
                    sourceSpans.addAll(node.getSourceSpans());
                }

                Node unlink = node;
                node = node.getNext();
                unlink.unlink();
            }
            String literal = sb.toString();
            first.setLiteral(literal);
            if (sourceSpans != null) {
                first.setSourceSpans(sourceSpans.getSourceSpans());
            }
        }
    }

    private static class DelimiterData {

        final List<Text> characters;
        final boolean canClose;
        final boolean canOpen;

        DelimiterData(List<Text> characters, boolean canOpen, boolean canClose) {
            this.characters = characters;
            this.canOpen = canOpen;
            this.canClose = canClose;
        }
    }

    /**
     * A destination and optional title for a link or image.
     */
    private static class DestinationTitle {
        final String destination;
        final String title;

        public DestinationTitle(String destination, String title) {
            this.destination = destination;
            this.title = title;
        }
    }

    private static class LinkInfoImpl implements LinkInfo {

        private final Text marker;
        private final Text openingBracket;
        private final String text;
        private final String label;
        private final String destination;
        private final String title;
        private final Position afterTextBracket;

        private LinkInfoImpl(Text marker, Text openingBracket, String text, String label,
                             String destination, String title, Position afterTextBracket) {
            this.marker = marker;
            this.openingBracket = openingBracket;
            this.text = text;
            this.label = label;
            this.destination = destination;
            this.title = title;
            this.afterTextBracket = afterTextBracket;
        }

        @Override
        public Text marker() {
            return marker;
        }

        @Override
        public Text openingBracket() {
            return openingBracket;
        }

        @Override
        public String text() {
            return text;
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        public String destination() {
            return destination;
        }

        @Override
        public String title() {
            return title;
        }

        @Override
        public Position afterTextBracket() {
            return afterTextBracket;
        }
    }
}
