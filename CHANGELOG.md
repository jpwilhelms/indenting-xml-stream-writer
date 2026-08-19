# Changelog

## 1.0.0

Initial public release.

- `IndentingXMLStreamWriter`, a dependency-free `XMLStreamWriter` decorator that indents element-only XML content without touching text, CDATA, or entity content.
- Mixed-content detection retains events for an open element until it closes, so text appearing after a child element (`<p><b/>tail</p>`) is still detected correctly, including in nested element-only children of a mixed-content ancestor.
- Configurable `indent` and `newLine` strings (constructor parameters); empty strings are allowed.
- No artificial whitespace is added between `writeStartElement()` and an immediately following `writeEndElement()`, so writers like Woodstox can still self-close empty elements.
- Verified against the JDK reference StAX implementation, Woodstox, and Jakarta JAXB 4.x marshalling.
