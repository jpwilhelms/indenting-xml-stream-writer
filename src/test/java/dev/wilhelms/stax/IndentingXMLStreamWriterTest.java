package dev.wilhelms.stax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctc.wstx.stax.WstxOutputFactory;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.io.StringWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.codehaus.stax2.XMLOutputFactory2;
import org.junit.jupiter.api.Test;

class IndentingXMLStreamWriterTest {
    @Test void indentsNestedElementsAndSiblings() throws Exception {
        assertEquals("<root>\n  <first>\n    <child/>\n  </first>\n  <second/>\n</root>", write(w -> {
            w.writeStartElement("root"); w.writeStartElement("first"); w.writeStartElement("child"); w.writeEndElement(); w.writeEndElement();
            w.writeStartElement("second"); w.writeEndElement(); w.writeEndElement();
        }));
    }
    @Test void startAndEndOfEmptyElementStayAdjacentForWoodstox() throws Exception {
        StringWriter output = new StringWriter(); WstxOutputFactory factory = new WstxOutputFactory();
        factory.setProperty(XMLOutputFactory2.P_AUTOMATIC_EMPTY_ELEMENTS, true);
        IndentingXMLStreamWriter writer = new IndentingXMLStreamWriter(factory.createXMLStreamWriter(output));
        writer.writeStartElement("root"); writer.writeStartElement("foo"); writer.writeEndElement(); writer.writeEndElement(); writer.flush();
        assertEquals("<root>\n  <foo/>\n</root>", output.toString());
    }
    @Test void writeEmptyElementIsIndentedAndSelfClosing() throws Exception {
        assertEquals("<root>\n  <empty/>\n</root>", write(w -> { w.writeStartElement("root"); w.writeEmptyElement("empty"); w.writeEndElement(); }));
    }
    @Test void preservesTextAndCdata() throws Exception {
        assertEquals("<root>\n  <foo>bar</foo>\n  <c><![CDATA[x<y]]></c>\n</root>", write(w -> { w.writeStartElement("root"); w.writeStartElement("foo"); w.writeCharacters("bar"); w.writeEndElement(); w.writeStartElement("c"); w.writeCData("x<y"); w.writeEndElement(); w.writeEndElement(); }));
    }
    @Test void preservesMixedContentEvenWhenTextComesAfterAChild() throws Exception {
        assertEquals("<p>Hello <b>world</b>!</p>", write(w -> { w.writeStartElement("p"); w.writeCharacters("Hello "); w.writeStartElement("b"); w.writeCharacters("world"); w.writeEndElement(); w.writeCharacters("!"); w.writeEndElement(); }));
        assertEquals("<p><b/>tail</p>", write(w -> { w.writeStartElement("p"); w.writeStartElement("b"); w.writeEndElement(); w.writeCharacters("tail"); w.writeEndElement(); }));
    }
    @Test void indentsCommentsAndProcessingInstructions() throws Exception {
        assertEquals("<root>\n  <!--note-->\n  <?go now?>\n</root>", write(w -> { w.writeStartElement("root"); w.writeComment("note"); w.writeProcessingInstruction("go", "now"); w.writeEndElement(); }));
    }
    @Test void elementOnlyChildNestedInMixedContentStaysUnindented() throws Exception {
        assertEquals("<p>Hello <b><i>x</i></b>!</p>", write(w -> {
            w.writeStartElement("p"); w.writeCharacters("Hello ");
            w.writeStartElement("b"); w.writeStartElement("i"); w.writeCharacters("x"); w.writeEndElement(); w.writeEndElement();
            w.writeCharacters("!"); w.writeEndElement();
        }));
    }
    @Test void topLevelSiblingsAreSeparatedByNewlines() throws Exception {
        assertEquals("<!--license-->\n<!--generated-->\n<root/>", write(w -> {
            w.writeComment("license"); w.writeComment("generated"); w.writeStartElement("root"); w.writeEndElement();
        }));
    }
    @Test void dtdAndRootAreSeparatedAfterStartDocument() throws Exception {
        StringWriter output = new StringWriter();
        IndentingXMLStreamWriter writer = new IndentingXMLStreamWriter(XMLOutputFactory.newFactory().createXMLStreamWriter(output));
        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeDTD("<!DOCTYPE root>");
        writer.writeStartElement("root"); writer.writeEndElement();
        writer.writeEndDocument();
        assertEquals("<?xml version='1.0' encoding='UTF-8'?>\n<!DOCTYPE root>\n<root/>", output.toString());
    }
    @Test void namespacesAndAttributesRemainOnTheirStartTag() throws Exception {
        assertEquals("<r xmlns=\"urn:test\" id=\"7\">\n  <child xmlns:p=\"urn:p\" p:name=\"n\"/>\n</r>", write(w -> {
            w.writeStartElement("r"); w.writeDefaultNamespace("urn:test"); w.writeAttribute("id", "7"); w.writeStartElement("child"); w.writeNamespace("p", "urn:p"); w.writeAttribute("p", "urn:p", "name", "n"); w.writeEndElement(); w.writeEndElement();
        }));
    }
    @Test void supportsLfCrLfAndTabs() throws Exception {
        StringWriter output = new StringWriter(); CountingWriter raw = new CountingWriter(XMLOutputFactory.newFactory().createXMLStreamWriter(output));
        IndentingXMLStreamWriter writer = new IndentingXMLStreamWriter(raw, "\t", "\r\n");
        writer.writeStartElement("r"); writer.writeStartElement("x"); writer.writeEndElement(); writer.writeEndElement(); writer.flush();
        assertEquals("\r\n\t\r\n", raw.writtenCharacters.toString());
        assertEquals("<r>&#xd;\n\t<x/>&#xd;\n</r>", output.toString());
        assertEquals("<r>\n  <x/>\n</r>", write(w -> { w.writeStartElement("r"); w.writeStartElement("x"); w.writeEndElement(); w.writeEndElement(); }));
    }
    @Test void hasNoDepthLimitOrLeadingOrTrailingNewline() throws Exception {
        String actual = write(w -> { for (int i = 0; i < 40; i++) w.writeStartElement("e"); for (int i = 0; i < 40; i++) w.writeEndElement(); });
        assertTrue(actual.startsWith("<e>")); assertTrue(actual.endsWith("</e>")); assertEquals(-1, actual.indexOf("\n\n"));
    }
    @Test void startAndEndDocumentAreDelegatedWithoutExtraLine() throws Exception {
        assertEquals("<?xml version='1.0' encoding='UTF-8'?>\n<root/>", write(w -> { w.writeStartDocument("UTF-8", "1.0"); w.writeStartElement("root"); w.writeEndElement(); w.writeEndDocument(); }));
    }
    @Test void entityContentDisablesFormattingInItsParent() throws Exception {
        assertEquals("<r><b/>&amp;</r>", write(w -> { w.writeStartElement("r"); w.writeStartElement("b"); w.writeEndElement(); w.writeEntityRef("amp"); w.writeEndElement(); }));
    }
    @Test void flushAndCloseAreForwarded() throws Exception {
        StringWriter output = new StringWriter(); CountingWriter raw = new CountingWriter(XMLOutputFactory.newFactory().createXMLStreamWriter(output));
        IndentingXMLStreamWriter writer = new IndentingXMLStreamWriter(raw); writer.writeStartElement("r"); writer.writeEndElement(); writer.flush(); writer.close();
        assertEquals(1, raw.flushes); assertEquals(1, raw.closes); assertEquals("<r/>", output.toString());
    }
    @Test void worksWithJakartaJaxbWithoutLibraryRuntimeDependency() throws Exception {
        StringWriter output = new StringWriter(); IndentingXMLStreamWriter writer = new IndentingXMLStreamWriter(XMLOutputFactory.newFactory().createXMLStreamWriter(output));
        Marshaller marshaller = JAXBContext.newInstance(JaxbValue.class).createMarshaller(); marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true); marshaller.marshal(new JaxbValue(), writer); writer.flush();
        assertEquals("<value>\n  <foo>bar</foo>\n  <empty></empty>\n</value>", output.toString());
    }
    private static String write(ThrowingConsumer<IndentingXMLStreamWriter> actions) throws Exception { return write("  ", "\n", actions); }
    private static String write(String indent, String newLine, ThrowingConsumer<IndentingXMLStreamWriter> actions) throws Exception {
        StringWriter output = new StringWriter(); IndentingXMLStreamWriter writer = new IndentingXMLStreamWriter(XMLOutputFactory.newFactory().createXMLStreamWriter(output), indent, newLine); actions.accept(writer); writer.flush(); return output.toString();
    }
    @FunctionalInterface private interface ThrowingConsumer<T> { void accept(T value) throws Exception; }
    @XmlRootElement(name = "value") public static class JaxbValue { @XmlElement public String foo = "bar"; @XmlElement public String empty = ""; public JaxbValue() { } }
    private static final class CountingWriter implements XMLStreamWriter {
        private final XMLStreamWriter delegate; int flushes; int closes; final StringBuilder writtenCharacters = new StringBuilder(); CountingWriter(XMLStreamWriter delegate) { this.delegate = delegate; }
        public void writeStartElement(String x) throws XMLStreamException { delegate.writeStartElement(x); } public void writeStartElement(String a,String b) throws XMLStreamException { delegate.writeStartElement(a,b); } public void writeStartElement(String a,String b,String c) throws XMLStreamException { delegate.writeStartElement(a,b,c); }
        public void writeEmptyElement(String a,String b) throws XMLStreamException { delegate.writeEmptyElement(a,b); } public void writeEmptyElement(String a,String b,String c) throws XMLStreamException { delegate.writeEmptyElement(a,b,c); } public void writeEmptyElement(String x) throws XMLStreamException { delegate.writeEmptyElement(x); } public void writeEndElement() throws XMLStreamException { delegate.writeEndElement(); } public void writeEndDocument() throws XMLStreamException { delegate.writeEndDocument(); }
        public void close() throws XMLStreamException { closes++; delegate.close(); } public void flush() throws XMLStreamException { flushes++; delegate.flush(); } public void writeAttribute(String a,String b) throws XMLStreamException { delegate.writeAttribute(a,b); } public void writeAttribute(String a,String b,String c,String d) throws XMLStreamException { delegate.writeAttribute(a,b,c,d); } public void writeAttribute(String a,String b,String c) throws XMLStreamException { delegate.writeAttribute(a,b,c); } public void writeNamespace(String a,String b) throws XMLStreamException { delegate.writeNamespace(a,b); } public void writeDefaultNamespace(String a) throws XMLStreamException { delegate.writeDefaultNamespace(a); } public void writeComment(String a) throws XMLStreamException { delegate.writeComment(a); } public void writeProcessingInstruction(String a) throws XMLStreamException { delegate.writeProcessingInstruction(a); } public void writeProcessingInstruction(String a,String b) throws XMLStreamException { delegate.writeProcessingInstruction(a,b); } public void writeCData(String a) throws XMLStreamException { delegate.writeCData(a); } public void writeDTD(String a) throws XMLStreamException { delegate.writeDTD(a); } public void writeEntityRef(String a) throws XMLStreamException { delegate.writeEntityRef(a); } public void writeStartDocument() throws XMLStreamException { delegate.writeStartDocument(); } public void writeStartDocument(String a) throws XMLStreamException { delegate.writeStartDocument(a); } public void writeStartDocument(String a,String b) throws XMLStreamException { delegate.writeStartDocument(a,b); } public void writeCharacters(String a) throws XMLStreamException { writtenCharacters.append(a); delegate.writeCharacters(a); } public void writeCharacters(char[] a,int b,int c) throws XMLStreamException { writtenCharacters.append(a, b, c); delegate.writeCharacters(a,b,c); } public String getPrefix(String a) throws XMLStreamException { return delegate.getPrefix(a); } public void setPrefix(String a,String b) throws XMLStreamException { delegate.setPrefix(a,b); } public void setDefaultNamespace(String a) throws XMLStreamException { delegate.setDefaultNamespace(a); } public void setNamespaceContext(javax.xml.namespace.NamespaceContext a) throws XMLStreamException { delegate.setNamespaceContext(a); } public javax.xml.namespace.NamespaceContext getNamespaceContext() { return delegate.getNamespaceContext(); } public Object getProperty(String a) { return delegate.getProperty(a); }
    }
}
