package dev.wilhelms.stax;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * An {@link XMLStreamWriter} decorator that indents element-only content without
 * changing text, CDATA, or entity content.
 *
 * <p>Events for an open element are retained until that element closes. This is
 * necessary to detect mixed content even when its text appears after a child
 * element. It also ensures no whitespace is put inside an empty element.</p>
 */
public final class IndentingXMLStreamWriter implements XMLStreamWriter {
    /** Two spaces, the default value of the {@code indent} constructor parameter. */
    public static final String DEFAULT_INDENT = "  ";
    /** {@code "\n"}, the default value of the {@code newLine} constructor parameter. */
    public static final String DEFAULT_NEW_LINE = "\n";

    private final XMLStreamWriter delegate;
    private final String indent;
    private final String newLine;
    private final Deque<Element> openElements = new ArrayDeque<>();
    private final List<Node> completedTopLevel = new ArrayList<>();
    private Element pendingStart;
    private boolean documentStarted;
    private boolean topLevelContentWritten;

    /**
     * Creates a writer that indents with {@link #DEFAULT_INDENT} and {@link #DEFAULT_NEW_LINE}.
     *
     * @param delegate the writer to which all events are forwarded
     */
    public IndentingXMLStreamWriter(XMLStreamWriter delegate) {
        this(delegate, DEFAULT_INDENT, DEFAULT_NEW_LINE);
    }

    /**
     * Creates a writer with a custom indent and newline string.
     *
     * @param delegate the writer to which all events are forwarded
     * @param indent the string written once per nesting level; may be empty
     * @param newLine the string written before each indented line; may be empty
     */
    public IndentingXMLStreamWriter(XMLStreamWriter delegate, String indent, String newLine) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.indent = Objects.requireNonNull(indent, "indent");
        this.newLine = Objects.requireNonNull(newLine, "newLine");
    }

    @Override public void writeStartElement(String localName) throws XMLStreamException {
        start(writer -> writer.writeStartElement(localName));
    }
    @Override public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
        start(writer -> writer.writeStartElement(namespaceURI, localName));
    }
    @Override public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        start(writer -> writer.writeStartElement(prefix, localName, namespaceURI));
    }
    @Override public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
        empty(writer -> writer.writeEmptyElement(namespaceURI, localName));
    }
    @Override public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        empty(writer -> writer.writeEmptyElement(prefix, localName, namespaceURI));
    }
    @Override public void writeEmptyElement(String localName) throws XMLStreamException {
        empty(writer -> writer.writeEmptyElement(localName));
    }
    @Override public void writeEndElement() throws XMLStreamException {
        clearPendingStart();
        Element element = openElements.poll();
        if (element == null) {
            throw new XMLStreamException("writeEndElement without an open element");
        }
        addToCurrent(element);
    }
    @Override public void writeEndDocument() throws XMLStreamException {
        if (!openElements.isEmpty()) {
            throw new XMLStreamException("writeEndDocument with unclosed elements");
        }
        emitCompletedTopLevel();
        delegate.writeEndDocument();
    }
    @Override public void close() throws XMLStreamException { emitCompletedTopLevel(); delegate.close(); }
    @Override public void flush() throws XMLStreamException { emitCompletedTopLevel(); delegate.flush(); }
    @Override public void writeAttribute(String localName, String value) throws XMLStreamException {
        attribute(writer -> writer.writeAttribute(localName, value));
    }
    @Override public void writeAttribute(String prefix, String namespaceURI, String localName, String value) throws XMLStreamException {
        attribute(writer -> writer.writeAttribute(prefix, namespaceURI, localName, value));
    }
    @Override public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException {
        attribute(writer -> writer.writeAttribute(namespaceURI, localName, value));
    }
    @Override public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException {
        attribute(writer -> writer.writeNamespace(prefix, namespaceURI));
    }
    @Override public void writeDefaultNamespace(String namespaceURI) throws XMLStreamException {
        attribute(writer -> writer.writeDefaultNamespace(namespaceURI));
    }
    @Override public void writeComment(String data) throws XMLStreamException { leaf(writer -> writer.writeComment(data), false); }
    @Override public void writeProcessingInstruction(String target) throws XMLStreamException { leaf(writer -> writer.writeProcessingInstruction(target), false); }
    @Override public void writeProcessingInstruction(String target, String data) throws XMLStreamException { leaf(writer -> writer.writeProcessingInstruction(target, data), false); }
    @Override public void writeCData(String data) throws XMLStreamException { leaf(writer -> writer.writeCData(data), true); }
    @Override public void writeDTD(String dtd) throws XMLStreamException { leaf(writer -> writer.writeDTD(dtd), false); }
    @Override public void writeEntityRef(String name) throws XMLStreamException { leaf(writer -> writer.writeEntityRef(name), true); }
    @Override public void writeStartDocument() throws XMLStreamException { startDocument(writer -> writer.writeStartDocument()); }
    @Override public void writeStartDocument(String version) throws XMLStreamException { startDocument(writer -> writer.writeStartDocument(version)); }
    @Override public void writeStartDocument(String encoding, String version) throws XMLStreamException { startDocument(writer -> writer.writeStartDocument(encoding, version)); }
    @Override public void writeCharacters(String text) throws XMLStreamException { leaf(writer -> writer.writeCharacters(text), true); }
    @Override public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
        char[] copy = new char[len]; System.arraycopy(text, start, copy, 0, len);
        leaf(writer -> writer.writeCharacters(copy, 0, copy.length), true);
    }
    @Override public String getPrefix(String uri) throws XMLStreamException { return delegate.getPrefix(uri); }
    @Override public void setPrefix(String prefix, String uri) throws XMLStreamException { delegate.setPrefix(prefix, uri); }
    @Override public void setDefaultNamespace(String uri) throws XMLStreamException { delegate.setDefaultNamespace(uri); }
    @Override public void setNamespaceContext(NamespaceContext context) throws XMLStreamException { delegate.setNamespaceContext(context); }
    @Override public NamespaceContext getNamespaceContext() { return delegate.getNamespaceContext(); }
    @Override public Object getProperty(String name) { return delegate.getProperty(name); }

    private void start(Action start) throws XMLStreamException {
        clearPendingStart(); Element element = new Element(start, false); openElements.push(element); pendingStart = element;
    }
    private void empty(Action start) throws XMLStreamException {
        clearPendingStart(); Element element = new Element(start, true); addToCurrent(element); pendingStart = element;
    }
    private void attribute(Action action) throws XMLStreamException {
        if (pendingStart == null) throw new XMLStreamException("Attribute or namespace declaration without an open start tag");
        pendingStart.startTagActions.add(action);
    }
    private void leaf(Action action, boolean text) throws XMLStreamException {
        clearPendingStart(); addToCurrent(new Event(action, text));
    }
    private void startDocument(Action action) throws XMLStreamException {
        emitCompletedTopLevel();
        action.write(delegate);
        documentStarted = true;
        topLevelContentWritten = false;
    }
    private void clearPendingStart() { pendingStart = null; }
    private void addToCurrent(Node node) { if (openElements.isEmpty()) completedTopLevel.add(node); else openElements.peek().content.add(node); }
    private void emitCompletedTopLevel() throws XMLStreamException {
        for (Node node : completedTopLevel) {
            beforeTopLevelContent();
            node.write(delegate, 0, false);
        }
        completedTopLevel.clear();
    }
    private void beforeTopLevelContent() throws XMLStreamException {
        if (topLevelContentWritten || documentStarted) {
            delegate.writeCharacters(newLine);
        }
        topLevelContentWritten = true;
    }
    private void line(XMLStreamWriter writer, int depth) throws XMLStreamException {
        writer.writeCharacters(newLine); for (int i = 0; i < depth; i++) writer.writeCharacters(indent);
    }

    @FunctionalInterface private interface Action { void write(XMLStreamWriter writer) throws XMLStreamException; }
    private interface Node { void write(XMLStreamWriter writer, int depth, boolean withinMixedContent) throws XMLStreamException; boolean isText(); }
    private static final class Event implements Node {
        private final Action action; private final boolean text;
        Event(Action action, boolean text) { this.action = action; this.text = text; }
        @Override public void write(XMLStreamWriter writer, int depth, boolean withinMixedContent) throws XMLStreamException { action.write(writer); }
        @Override public boolean isText() { return text; }
    }
    private final class Element implements Node {
        private final Action start; private final boolean empty; private final List<Action> startTagActions = new ArrayList<>(); private final List<Node> content = new ArrayList<>();
        Element(Action start, boolean empty) { this.start = start; this.empty = empty; }
        @Override public void write(XMLStreamWriter writer, int depth, boolean withinMixedContent) throws XMLStreamException {
            start.write(writer); for (Action action : startTagActions) action.write(writer);
            if (empty) return;
            boolean mixed = withinMixedContent || content.stream().anyMatch(Node::isText);
            if (mixed) { for (Node node : content) node.write(writer, depth + 1, true); }
            else if (!content.isEmpty()) {
                for (Node node : content) { line(writer, depth + 1); node.write(writer, depth + 1, false); }
                line(writer, depth);
            }
            writer.writeEndElement();
        }
        @Override public boolean isText() { return false; }
    }
}
