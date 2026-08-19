# Indenting XMLStreamWriter

[![Verify](https://github.com/jpwilhelms/indenting-xml-stream-writer/actions/workflows/verify.yml/badge.svg)](https://github.com/jpwilhelms/indenting-xml-stream-writer/actions/workflows/verify.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

Small Java 17 library that decorates an existing `XMLStreamWriter` and indents element-only XML. It is a modern, dependency-free alternative to older `stax-utils` implementations and `com.sun.xml.txw2.output.IndentingXMLStreamWriter`; it uses only the JDK StAX API.

Project page: [github.com/jpwilhelms/indenting-xml-stream-writer](https://github.com/jpwilhelms/indenting-xml-stream-writer). The API package is `dev.wilhelms.stax`; the Java module is `dev.wilhelms.stax.indenting`.

> This library was written with the help of an AI coding assistant (Claude). The design, review, and all changes were guided and checked by a human maintainer, but you should factor this into your own review before relying on it.

## Maven

```xml
<dependency>
  <groupId>dev.wilhelms</groupId>
  <artifactId>indenting-xml-stream-writer</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Usage

```java
XMLStreamWriter raw = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
XMLStreamWriter writer = new IndentingXMLStreamWriter(raw, "  ", "\n");
```

`indent` and `newLine` are immutable constructor parameters. The exact configured string, such as `"\n"` or `"\r\n"`, is passed to the delegate; the library deliberately does not use `System.lineSeparator()`. An `XMLStreamWriter` may still escape carriage returns while serializing character data, so the final bytes remain the delegate's responsibility. Empty `indent` and `newLine` values are allowed.

## JAXB and Woodstox

The main library has no JAXB dependency. The usual StAX integration works with Jakarta JAXB 4.x:

```java
Marshaller marshaller = context.createMarshaller();
XMLStreamWriter woodstox = outputFactory.createXMLStreamWriter(outputStream, "UTF-8");
IndentingXMLStreamWriter writer = new IndentingXMLStreamWriter(woodstox, "  ", "\r\n");
marshaller.marshal(value, writer);
writer.flush();
```

Woodstox automatic empty-element output can be enabled with:

```java
outputFactory.setProperty(XMLOutputFactory2.P_AUTOMATIC_EMPTY_ELEMENTS, true);
```

The decorator never writes artificial whitespace between `writeStartElement()` and an immediately following `writeEndElement()`. Woodstox can therefore still serialize this as `<foo/>` rather than `<foo></foo>`.

## Mixed content and streaming

For each open element, StAX events are retained until the element closes. Only then can the writer reliably determine whether it contains text, CDATA, or entity content—even for `<p><b/>tail</p>`. If it does, the library adds no pretty-print whitespace inside that element. Element-only structures, comments, and processing instructions are indented.

Consequently, bytes for an open element are intentionally sent to the delegated writer only at `writeEndElement()`. Completed top-level events are emitted after the root element closes and on `flush()`. This is normal for JAXB because `marshal` closes the root element before callers typically invoke `flush()`.

Instances are not thread-safe. `flush()` and `close()` are forwarded to the decorated writer. Per the `XMLStreamWriter.close()` contract, this must not close the underlying output stream; whether the destination itself is closed is entirely up to the delegate implementation.
