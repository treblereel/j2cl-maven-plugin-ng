package org.example.tools;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class J2CLModuleParser {

    private static final DocumentBuilder db;

    static {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            dbf.setValidating(false);
            dbf.setFeature("http://xml.org/sax/features/namespaces", false);
            dbf.setFeature("http://xml.org/sax/features/validation", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            db = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Path> getSuperSourcePaths(List<Path> sourceRoots) {
        Set<Path> superSourcePaths = new LinkedHashSet<>();
        for (Path sourceRoot : sourceRoots) {
            superSourcePaths.addAll(getJ2clModuleSuperSourcePaths(sourceRoot));
            superSourcePaths.addAll(getGwtModuleSuperSourcePaths(sourceRoot));
        }
        return superSourcePaths.stream()
                .sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                .toList();
    }

    private static List<Path> getJ2clModuleSuperSourcePaths(Path sourceRoot) {
        Path modulePath = sourceRoot.resolve("META-INF").resolve("module.j2cl.xml");
        return parseSuperSourcePaths(modulePath, Paths.get(""));
    }

    private static List<Path> getGwtModuleSuperSourcePaths(Path sourceRoot) {
        if (!Files.isDirectory(sourceRoot)) {
            return List.of();
        }
        List<Path> superSourcePaths = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(path -> path.getFileName().toString().endsWith(".gwt.xml"))
                    .forEach(path -> {
                        Path modulePackage = sourceRoot.relativize(path).getParent();
                        if (modulePackage == null) {
                            modulePackage = Paths.get("");
                        }
                        superSourcePaths.addAll(parseSuperSourcePaths(path, modulePackage));
                    });
        } catch (IOException ignored) {
            return List.of();
        }
        return superSourcePaths;
    }

    private static List<Path> parseSuperSourcePaths(Path modulePath, Path modulePackage) {
        Document doc;
        try {
            synchronized (db) {
                doc = db.parse(modulePath.toFile());
            }
        } catch (SAXException | IOException e) {
            return List.of();
        }
        doc.getDocumentElement().normalize();
        List<Path> superSourcePaths = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagName("super-source");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            Node pathAttribute = node.getAttributes().getNamedItem("path");
            if (pathAttribute != null) {
                superSourcePaths.add(modulePackage.resolve(pathAttribute.getNodeValue()).normalize());
            }
        }
        return superSourcePaths;
    }
}
