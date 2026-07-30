package org.treblereel.j2cl.plugin.xbt;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XtbPreprocessor {

    private static final String PH_OPEN = "<ph ";
    private static final String PH_CLOSE = "</ph>";
    private static final String PH_SELF_CLOSE = "/>";

    public static InputStream preprocess(InputStream input) throws Exception {
        return preprocess(input, null);
    }

    public static InputStream preprocess(InputStream input, String locale) throws Exception {
        DocumentBuilderFactory dbf = createDocumentBuilderFactory();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(input);
        doc.getDocumentElement().normalize();

        NodeList translationbundleNode = doc.getElementsByTagName("translationbundle");
        if (translationbundleNode.getLength() == 0) {
            return new ByteArrayInputStream(new byte[0]);
        }

        Node selectedBundle = selectBundle(translationbundleNode, locale);

        String lang = selectedBundle.getAttributes().getNamedItem("lang").getNodeValue();

        StringBuffer sb = new StringBuffer();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("\n");
        sb.append("<!DOCTYPE translationbundle SYSTEM \"translationbundle.dtd\">");
        sb.append("\n");
        sb.append("<translationbundle lang=\"" + lang + "\">");
        sb.append("\n");

        appendTranslations(sb, selectedBundle);

        sb.append("</translationbundle>");
        sb.append("\n");

        return new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String getInnerContent(Node node) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(node);
            transformer.transform(source, result);
            return result.getWriter().toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    static List<String> parse(String content) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char character = content.charAt(i);
            if (character == '<' && (i + 4) < content.length()) {
                String tag = content.substring(i, i + 4);
                if (tag.equals(PH_OPEN)) {
                    if (sb.length() > 0) {
                        parts.add(escape(sb.toString()));
                    }
                    sb.setLength(0);
                    sb.append(PH_OPEN);
                    int temp = i + PH_OPEN.length();
                    boolean run = true;
                    while (run && temp < content.length()) {
                        boolean isSelfClosing = false;
                        boolean isClosing = false;
                        if (content.length() >= (temp + PH_SELF_CLOSE.length())) {
                            isSelfClosing = content.substring(temp, temp + PH_SELF_CLOSE.length()).equals(PH_SELF_CLOSE);
                        }
                        if (content.length() >= (temp + PH_CLOSE.length())) {
                            isClosing = content.substring(temp, temp + PH_CLOSE.length()).equals(PH_CLOSE);
                        }
                        if (isSelfClosing || isClosing) {
                            sb.append(isSelfClosing ? PH_SELF_CLOSE : PH_CLOSE);
                            parts.add(sb.toString());
                            i += sb.length() - 1;
                            run = false;
                            sb.setLength(0);
                        } else {
                            char current = content.charAt(temp);
                            sb.append(current);
                            temp++;
                        }
                    }
                } else {
                    sb.append(character);
                }
            } else {
                sb.append(character);
            }
        }
        if (sb.length() > 0) {
            parts.add(escape(sb.toString()));
        }
        return parts;
    }

    public static InputStream merge(List<InputStream> inputs, String locale) throws Exception {
        DocumentBuilderFactory dbf = createDocumentBuilderFactory();
        DocumentBuilder db = dbf.newDocumentBuilder();

        String lang = locale != null ? locale : "en";
        StringBuffer sb = new StringBuffer();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("\n");
        sb.append("<!DOCTYPE translationbundle SYSTEM \"translationbundle.dtd\">");
        sb.append("\n");
        sb.append("<translationbundle lang=\"" + lang + "\">");
        sb.append("\n");

        for (InputStream input : inputs) {
            Document doc = db.parse(input);
            doc.getDocumentElement().normalize();

            NodeList translationbundleNode = doc.getElementsByTagName("translationbundle");
            if (translationbundleNode.getLength() == 0) {
                continue;
            }

            Node selectedBundle = selectBundle(translationbundleNode, locale);
            Node langAttr = selectedBundle.getAttributes().getNamedItem("lang");
            if (langAttr != null) {
                lang = langAttr.getNodeValue();
            }
            appendTranslations(sb, selectedBundle);
        }

        sb.append("</translationbundle>");
        sb.append("\n");

        return new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendTranslations(StringBuffer sb, Node bundle) {
        NodeList children = bundle.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!"translation".equals(node.getNodeName())) {
                continue;
            }

            sb.append("  <translation id=\"");
            sb.append(node.getAttributes().getNamedItem("id").getNodeValue());
            sb.append("\"");

            if (node.getAttributes().getNamedItem("key") != null) {
                sb.append(" key=\"");
                sb.append(escape(node.getAttributes().getNamedItem("key").getNodeValue()));
                sb.append("\"");
            }
            sb.append(">");

            if (node.hasChildNodes()) {
                StringBuffer innerContent = new StringBuffer();
                for (int j = 0; j < node.getChildNodes().getLength(); j++) {
                    innerContent.append(getInnerContent(node.getChildNodes().item(j)));
                }
                String result = parse(innerContent.toString())
                        .stream()
                        .collect(Collectors.joining(""));
                sb.append(result);
            }

            sb.append("</translation>");
            sb.append("\n");
        }
    }

    private static DocumentBuilderFactory createDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setValidating(false);
        dbf.setFeature("http://xml.org/sax/features/namespaces", false);
        dbf.setFeature("http://xml.org/sax/features/validation", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return dbf;
    }

    private static Node selectBundle(NodeList bundles, String locale) {
        if (locale == null || bundles.getLength() == 1) {
            return bundles.item(0);
        }
        String normalized = locale.replace("-", "_");
        for (int i = 0; i < bundles.getLength(); i++) {
            Node bundle = bundles.item(i);
            Node langAttr = bundle.getAttributes().getNamedItem("lang");
            if (langAttr != null) {
                String lang = langAttr.getNodeValue();
                if (locale.equals(lang) || normalized.equals(lang)) {
                    return bundle;
                }
            }
        }
        return bundles.item(0);
    }

    static String escape(String part) {
        return part.replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("&", "&amp;");
    }
}
