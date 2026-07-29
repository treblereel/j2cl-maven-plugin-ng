package org.treblereel.j2cl.plugin.tools;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.j2cl.transpiler.backend.wasm.ItableAllocator;
import com.google.j2cl.transpiler.backend.wasm.JsImportsGenerator;
import com.google.j2cl.transpiler.backend.wasm.SharedSnippet;
import com.google.j2cl.transpiler.backend.wasm.StringLiteralInfo;
import com.google.j2cl.transpiler.backend.wasm.Summary;
import com.google.j2cl.transpiler.backend.wasm.SystemPropertyInfo;
import com.google.j2cl.transpiler.backend.wasm.TypeInfo;
import org.treblereel.j2cl.plugin.log.BuildLog;

public class WasmBundler {

    private static final String FROM_JS_STRING =
            "$m_fromJsString__java_lang_String_NativeString__java_lang_String@java.lang.String";
    private static final String SYSTEM_PROPERTY_POOL = "javaemul.internal.SystemPropertyPool";

    private final BuildLog log;
    private final Map<String, String> defines;
    private final List<String> wasmEntryPoints;

    public WasmBundler(BuildLog log, Map<String, String> defines) {
        this(log, defines, List.of());
    }

    public WasmBundler(BuildLog log, Map<String, String> defines, List<String> wasmEntryPoints) {
        this.log = log;
        this.defines = defines;
        this.wasmEntryPoints = wasmEntryPoints;
    }

    public boolean bundle(List<Path> moduleDirs, Path outputWat, Path jsImportsOutput) {
        try {
            Map<Path, Summary> summaryByDir = new LinkedHashMap<>();
            for (Path dir : moduleDirs) {
                Path summaryPath = dir.resolve("summary.binpb");
                if (Files.exists(summaryPath)) {
                    try (InputStream is = new BufferedInputStream(Files.newInputStream(summaryPath))) {
                        summaryByDir.put(dir, Summary.parseFrom(is));
                    }
                }
            }

            List<Path> sortedDirs = sortModulesByTypeDependencies(moduleDirs, summaryByDir);

            List<Summary> summaries = new ArrayList<>();
            List<String> typesFragments = new ArrayList<>();
            List<String> contentsFragments = new ArrayList<>();
            List<String> importsFragments = new ArrayList<>();

            for (Path moduleDir : sortedDirs) {
                Summary s = summaryByDir.get(moduleDir);
                if (s != null) {
                    summaries.add(s);
                }

                Path typesPath = moduleDir.resolve("types.wat");
                if (Files.exists(typesPath)) {
                    typesFragments.add(Files.readString(typesPath));
                }

                Path contentsPath = moduleDir.resolve("contents.wat");
                if (Files.exists(contentsPath)) {
                    contentsFragments.add(Files.readString(contentsPath));
                }

                Path importsPath = moduleDir.resolve("imports.wat");
                if (Files.exists(importsPath)) {
                    importsFragments.add(Files.readString(importsPath));
                }
            }

            TypeGraph typeGraph = new TypeGraph(summaries);

            StringBuilder literalGlobals = new StringBuilder();
            StringBuilder literalMethods = new StringBuilder();
            synthesizeStringLiteralGetters(summaries, literalGlobals, literalMethods);
            synthesizeSystemPropertyGetters(summaries, literalGlobals, literalMethods);

            StringBuilder module = new StringBuilder();
            module.append("(module\n");

            for (String snippet : dedup(summaries, Summary::getNativeArrayTypeSnippetsList)) {
                module.append(snippet).append("\n");
            }

            module.append("(rec\n");
            for (String types : typesFragments) {
                module.append(types).append("\n");
            }
            for (String snippet : dedup(summaries, Summary::getTypeSnippetsList)) {
                module.append(snippet).append("\n");
            }
            module.append(typeGraph.getTopLevelItableStructDeclaration());
            for (TypeGraph.Type cls : typeGraph.getClasses()) {
                module.append(cls.getItableStructDeclaration());
            }
            module.append(")\n");

            for (String snippet : dedup(summaries, Summary::getWasmImportSnippetsList)) {
                module.append(snippet).append("\n");
            }

            for (String imports : importsFragments) {
                module.append(imports).append("\n");
            }

            module.append("(import \"WebAssembly\" \"JSTag\" (tag $exception.event (param externref)))\n");

            for (String contents : contentsFragments) {
                module.append(contents).append("\n");
            }

            for (String snippet : dedup(summaries, Summary::getGlobalSnippetsList)) {
                module.append(snippet).append("\n");
            }

            module.append(typeGraph.getEmptyItableDeclaration());
            for (TypeGraph.Type cls : typeGraph.getClasses()) {
                module.append(cls.getItableInitialization());
            }

            module.append(literalGlobals);
            module.append(literalMethods);

            module.append(typeGraph.getItableInterfaceGetters());

            if (!wasmEntryPoints.isEmpty()) {
                String exports = generateWasmExports(contentsFragments);
                module.append(exports);
            }

            module.append(")\n");

            Files.createDirectories(outputWat.getParent());
            Files.writeString(outputWat, module.toString());

            generateJsImports(summaries, jsImportsOutput);

            return true;
        } catch (Exception e) {
            log.error("WASM bundling failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void synthesizeStringLiteralGetters(List<Summary> summaries,
                                                 StringBuilder globals,
                                                 StringBuilder methods) {
        Set<String> emittedGetters = new LinkedHashSet<>();

        for (Summary s : summaries) {
            for (StringLiteralInfo literal : s.getStringLiteralsList()) {
                String methodName = literal.getMethodName();
                String enclosingType = literal.getEnclosingTypeName();
                String content = unescapeWtf16(literal.getContent());

                String holderFieldName = methodName.replace("$getString_", "$string_");
                String holderGlobalName = "$" + holderFieldName + "@wasm.stringLiteral.StringLiteralHolder";
                String holderFuncName = "$" + methodName + "__java_lang_String_<once>_@wasm.stringLiteral.StringLiteralHolder";
                String forwarderFuncName = "$" + methodName + "__java_lang_String_<once>_@" + enclosingType;

                if (emittedGetters.add(holderFuncName)) {
                    globals.append("(global ").append(holderGlobalName)
                            .append(" (mut (ref null $java.lang.String)) (ref.null $java.lang.String))\n");

                    methods.append("(func ").append(holderFuncName)
                            .append(" (result (ref null $java.lang.String))\n")
                            .append("  (if (i32.eqz (ref.is_null (global.get ").append(holderGlobalName).append(")))\n")
                            .append("    (then (return (global.get ").append(holderGlobalName).append(")))\n")
                            .append("  )\n")
                            .append("  (global.set ").append(holderGlobalName).append("\n")
                            .append("    (call ").append(FROM_JS_STRING)
                            .append(" (string.const ").append(watStringLiteral(content)).append("))\n")
                            .append("  )\n")
                            .append("  (return (global.get ").append(holderGlobalName).append("))\n")
                            .append(")\n");
                }

                methods.append("(func ").append(forwarderFuncName)
                        .append(" (result (ref null $java.lang.String))\n")
                        .append("  (return (call ").append(holderFuncName).append("))\n")
                        .append(")\n");
            }
        }
    }

    private void synthesizeSystemPropertyGetters(List<Summary> summaries,
                                                  StringBuilder globals,
                                                  StringBuilder methods) {
        Set<String> emittedProperties = new LinkedHashSet<>();

        for (Summary s : summaries) {
            for (SystemPropertyInfo prop : s.getSystemPropertiesList()) {
                String propertyKey = prop.getPropertyKey();
                if (!emittedProperties.add(propertyKey)) {
                    continue;
                }

                String funcName = "$$" + propertyKey + "__java_lang_String_<once>_@" + SYSTEM_PROPERTY_POOL;
                String value = defines.get(propertyKey);

                if (value != null) {
                    String fieldName = "$$prop_" + propertyKey.replace('.', '_') + "@" + SYSTEM_PROPERTY_POOL;

                    globals.append("(global ").append(fieldName)
                            .append(" (mut (ref null $java.lang.String)) (ref.null $java.lang.String))\n");

                    methods.append("(func ").append(funcName)
                            .append(" (result (ref null $java.lang.String))\n")
                            .append("  (if (i32.eqz (ref.is_null (global.get ").append(fieldName).append(")))\n")
                            .append("    (then (return (global.get ").append(fieldName).append(")))\n")
                            .append("  )\n")
                            .append("  (global.set ").append(fieldName).append("\n")
                            .append("    (call ").append(FROM_JS_STRING)
                            .append(" (string.const ").append(watStringLiteral(value)).append("))\n")
                            .append("  )\n")
                            .append("  (return (global.get ").append(fieldName).append("))\n")
                            .append(")\n");
                } else {
                    if (prop.getIsRequired()) {
                        log.error("No value found for required system property: " + propertyKey);
                    }
                    methods.append("(func ").append(funcName)
                            .append(" (result (ref null $java.lang.String))\n")
                            .append("  (ref.null $java.lang.String)\n")
                            .append(")\n");
                }
            }
        }
    }

    private static String watStringLiteral(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                sb.append("\\\"");
            } else if (c == '\\') {
                sb.append("\\\\");
            } else if (c >= 0x20 && c < 0x7f) {
                sb.append(c);
            } else {
                sb.append(String.format("\\u{%x}", (int) c));
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static String unescapeWtf16(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == 'u' && i + 5 < s.length()) {
                    String hex = s.substring(i + 2, i + 6);
                    try {
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 5;
                        continue;
                    } catch (NumberFormatException e) {
                        // fall through
                    }
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private List<String> dedup(List<Summary> summaries,
                               Function<Summary, List<SharedSnippet>> getter) {
        Map<String, String> seen = new LinkedHashMap<>();
        for (Summary s : summaries) {
            for (SharedSnippet snippet : getter.apply(s)) {
                seen.putIfAbsent(snippet.getKey(), snippet.getSnippet());
            }
        }
        return new ArrayList<>(seen.values());
    }

    private void generateJsImports(List<Summary> summaries, Path jsImportsOutput) throws IOException {
        Map<String, String> jsImportSnippets = new LinkedHashMap<>();
        List<String> jsImportRequires = new ArrayList<>();

        for (Summary s : summaries) {
            for (SharedSnippet snippet : s.getJsImportSnippetsList()) {
                jsImportSnippets.putIfAbsent(snippet.getKey(), snippet.getSnippet());
            }
            for (String req : s.getJsImportRequiresList()) {
                if (!jsImportRequires.contains(req)) {
                    jsImportRequires.add(req);
                }
            }
        }

        String content = JsImportsGenerator.generateOutputs(jsImportRequires, jsImportSnippets, false);

        Files.createDirectories(jsImportsOutput.getParent());
        Files.writeString(jsImportsOutput, content);
    }

    private String generateWasmExports(List<String> contentsFragments) {
        // Build entry point matchers from patterns like ".*_Adapter#.*"
        List<Pattern[]> matchers = new ArrayList<>();
        for (String ep : wasmEntryPoints) {
            int hash = ep.lastIndexOf('#');
            if (hash < 0) continue;
            String classPattern = ep.substring(0, hash);
            String methodPattern = ep.substring(hash + 1);
            String classRegex = classPattern.replace(".", "\\.").replace("\\.*", ".*");
            String methodRegex = methodPattern.replace(".", "\\.").replace("\\.*", ".*");
            matchers.add(new Pattern[]{Pattern.compile(classRegex), Pattern.compile(methodRegex)});
        }

        if (matchers.isEmpty()) return "";

        // WAT func declarations: (func $m_<methodName>__<sig>@<qualifiedClassName>
        Pattern funcPattern = Pattern.compile("\\(func (\\$m_([a-zA-Z0-9_]+)__[^@]+@([a-zA-Z0-9_.]+))\\b");

        StringBuilder exports = new StringBuilder();
        Set<String> exportedNames = new LinkedHashSet<>();

        for (String contents : contentsFragments) {
            Matcher m = funcPattern.matcher(contents);
            while (m.find()) {
                String watFuncName = m.group(1);
                String javaMethodName = m.group(2);
                String qualifiedClassName = m.group(3);

                for (Pattern[] ep : matchers) {
                    if (ep[0].matcher(qualifiedClassName).matches()
                            && ep[1].matcher(javaMethodName).matches()) {
                        if (exportedNames.add(javaMethodName)) {
                            exports.append("(export \"").append(javaMethodName)
                                    .append("\" (func ").append(watFuncName).append("))\n");
                            log.debug("WASM export: " + javaMethodName + " -> " + watFuncName);
                        }
                        break;
                    }
                }
            }
        }

        if (!exportedNames.isEmpty()) {
            log.info("Generated " + exportedNames.size() + " WASM exports");
        }

        return exports.toString();
    }

    private List<Path> sortModulesByTypeDependencies(List<Path> moduleDirs,
                                                       Map<Path, Summary> summaryByDir) {
        Map<String, Path> typeDefiningModule = new HashMap<>();
        Map<Path, Set<String>> definedTypes = new LinkedHashMap<>();

        for (var entry : summaryByDir.entrySet()) {
            Path dir = entry.getKey();
            Summary s = entry.getValue();
            Set<String> defined = new HashSet<>();
            for (TypeInfo t : s.getTypesList()) {
                String name = s.getTypeNames(t.getTypeId());
                defined.add(name);
                typeDefiningModule.put(name, dir);
            }
            for (TypeInfo t : s.getInterfacesList()) {
                String name = s.getTypeNames(t.getTypeId());
                defined.add(name);
                typeDefiningModule.put(name, dir);
            }
            definedTypes.put(dir, defined);
        }

        Map<Path, Set<Path>> deps = new LinkedHashMap<>();
        for (Path dir : moduleDirs) {
            deps.put(dir, new LinkedHashSet<>());
        }

        for (var entry : summaryByDir.entrySet()) {
            Path dir = entry.getKey();
            Summary s = entry.getValue();
            Set<String> defined = definedTypes.getOrDefault(dir, Set.of());

            for (TypeInfo t : s.getTypesList()) {
                addCrossModuleDeps(s, t, defined, dir, typeDefiningModule, deps);
            }
            for (TypeInfo t : s.getInterfacesList()) {
                addCrossModuleDeps(s, t, defined, dir, typeDefiningModule, deps);
            }
        }

        List<Path> sorted = new ArrayList<>();
        Set<Path> visited = new LinkedHashSet<>();
        Set<Path> visiting = new HashSet<>();
        for (Path dir : moduleDirs) {
            topoVisit(dir, deps, visited, visiting, sorted);
        }

        if (log != null) {
            log.debug("Module order after type-dependency sort:");
            for (Path p : sorted) {
                log.debug("  " + p.getFileName());
            }
        }

        return sorted;
    }

    private static final int NO_TYPE_INDEX = 0;

    private void addCrossModuleDeps(Summary s, TypeInfo t, Set<String> defined, Path dir,
                                     Map<String, Path> typeDefiningModule,
                                     Map<Path, Set<Path>> deps) {
        if (t.getExtendsType() != NO_TYPE_INDEX) {
            String superName = s.getTypeNames(t.getExtendsType());
            if (!defined.contains(superName)) {
                Path depDir = typeDefiningModule.get(superName);
                if (depDir != null && !depDir.equals(dir)) {
                    deps.computeIfAbsent(dir, k -> new LinkedHashSet<>()).add(depDir);
                }
            }
        }
        for (int ifaceId : t.getImplementsTypesList()) {
            String ifaceName = s.getTypeNames(ifaceId);
            if (!defined.contains(ifaceName)) {
                Path depDir = typeDefiningModule.get(ifaceName);
                if (depDir != null && !depDir.equals(dir)) {
                    deps.computeIfAbsent(dir, k -> new LinkedHashSet<>()).add(depDir);
                }
            }
        }
    }

    private static void topoVisit(Path node, Map<Path, Set<Path>> deps,
                                    Set<Path> visited, Set<Path> visiting, List<Path> sorted) {
        if (visited.contains(node)) return;
        if (visiting.contains(node)) return;
        visiting.add(node);
        Set<Path> nodeDeps = deps.get(node);
        if (nodeDeps != null) {
            for (Path dep : nodeDeps) {
                topoVisit(dep, deps, visited, visiting, sorted);
            }
        }
        visiting.remove(node);
        visited.add(node);
        sorted.add(node);
    }

    private static class TypeGraph {

        private static final int NO_TYPE_INDEX = 0;
        private final List<Type> classes = new ArrayList<>();
        private final List<Type> interfaces = new ArrayList<>();
        private final Map<String, Type> typesByName = new LinkedHashMap<>();
        private final ItableAllocator<String> itableAllocator;

        TypeGraph(List<Summary> summaries) {
            for (Summary s : summaries) {
                addToTypeGraph(s);
            }
            this.itableAllocator = createItableAllocator();
        }

        private ItableAllocator<String> createItableAllocator() {
            SetMultimap<String, String> implementedInterfaceNamesByTypeName =
                    LinkedHashMultimap.create();
            classes.forEach(c ->
                    c.getImplementedInterfaces().forEach(i ->
                            implementedInterfaceNamesByTypeName.put(c.name, i.name)));

            ImmutableMap.Builder<String, String> superInterfaceBuilder = ImmutableMap.builder();
            interfaces.stream()
                    .filter(i -> i.superType != null)
                    .forEach(i -> superInterfaceBuilder.put(i.name, i.superType.name));
            ImmutableMap<String, String> superInterfaceNamesByTypeName = superInterfaceBuilder
                    .buildKeepingLast();

            return new ItableAllocator<>(
                    classes.stream().map(t -> t.name).collect(ImmutableList.toImmutableList()),
                    implementedInterfaceNamesByTypeName::get,
                    superInterfaceNamesByTypeName::get);
        }

        private void addToTypeGraph(Summary summary) {
            for (TypeInfo interfaceInfo : summary.getInterfacesList()) {
                String interfaceName = summary.getTypeNames(interfaceInfo.getTypeId());
                Type interfaceType = new Type(interfaceName, false);
                if (interfaceInfo.getExtendsType() != NO_TYPE_INDEX) {
                    String superTypeName = summary.getTypeNames(interfaceInfo.getExtendsType());
                    interfaceType.superType = typesByName.get(superTypeName);
                }
                typesByName.put(interfaceName, interfaceType);
                interfaces.add(interfaceType);
            }

            for (TypeInfo typeInfo : summary.getTypesList()) {
                String name = summary.getTypeNames(typeInfo.getTypeId());
                Type type = typesByName.computeIfAbsent(name,
                        n -> new Type(n, typeInfo.getAbstract()));
                classes.add(type);
                if (typeInfo.getExtendsType() != NO_TYPE_INDEX) {
                    String superTypeName = summary.getTypeNames(typeInfo.getExtendsType());
                    type.superType = typesByName.get(superTypeName);
                }
                for (int interfaceId : typeInfo.getImplementsTypesList()) {
                    String interfaceName = summary.getTypeNames(interfaceId);
                    Type interfaceType = typesByName.get(interfaceName);
                    if (interfaceType != null) {
                        type.implementedInterfaces.add(interfaceType);
                    }
                }
            }
        }

        List<Type> getClasses() {
            return classes;
        }

        String getTopLevelItableStructDeclaration() {
            StringBuilder sb = new StringBuilder();
            sb.append("(type $itable (sub (struct\n");
            for (int i = 0; i < itableAllocator.getItableSize(); i++) {
                sb.append("  (field (ref null struct))\n");
            }
            sb.append(")))\n");
            return sb.toString();
        }

        String getEmptyItableDeclaration() {
            StringBuilder sb = new StringBuilder();
            sb.append("(global $itable.empty (ref $itable) (struct.new $itable \n");
            for (int i = 0; i < itableAllocator.getItableSize(); i++) {
                sb.append("(ref.null struct)\n");
            }
            sb.append("))\n");
            return sb.toString();
        }

        String getItableInterfaceGetters() {
            StringBuilder sb = new StringBuilder();
            for (Type i : interfaces) {
                int fieldIndex = itableAllocator.getItableFieldIndex(i.name);
                String methodName = "$get.itable." + i.name;
                sb.append(String.format(
                        "(func %s (param $object (ref null $java.lang.Object)) (result (ref null struct)) ",
                        methodName));
                if (fieldIndex == -1) {
                    sb.append("(ref.null struct)");
                } else {
                    sb.append(String.format(
                            "(struct.get $itable %d (struct.get $java.lang.Object $itable (local.get $object)))",
                            fieldIndex));
                }
                sb.append(")\n");
            }
            return sb.toString();
        }

        class Type {
            final String name;
            Type superType;
            final Set<Type> implementedInterfaces = new HashSet<>();
            final boolean isAbstract;

            Type(String name, boolean isAbstract) {
                this.name = name;
                this.isAbstract = isAbstract;
            }

            Set<Type> getImplementedInterfaces() {
                return implementedInterfaces;
            }

            boolean isSuperTypeOf(Type other) {
                if (other == null) return false;
                return this == other.superType || isSuperTypeOf(other.superType);
            }

            String getItableTypeName() {
                if (implementedInterfaces.isEmpty()) {
                    return "$itable";
                }
                return name + ".itable";
            }

            String getItableStructDeclaration() {
                if (implementedInterfaces.isEmpty()) {
                    return "";
                }
                String superItableTypeName = superType == null ? "$itable"
                        : superType.getItableTypeName();
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("(type %s.itable (sub %s (struct \n",
                        name, superItableTypeName));
                Type[] fieldTypes = getItableFieldTypes();
                for (Type fieldType : fieldTypes) {
                    sb.append(String.format("  (field (ref %s))\n",
                            fieldType != null ? (fieldType.name + ".vtable") : "null struct"));
                }
                sb.append(")))\n");
                return sb.toString();
            }

            String getItableInitialization() {
                if (isAbstract) {
                    return "";
                }
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("(global %s.itable (ref %s) ",
                        name, getItableTypeName()));
                if (implementedInterfaces.isEmpty()) {
                    sb.append("(global.get $itable.empty)");
                } else {
                    sb.append(String.format("(struct.new %s \n", getItableTypeName()));
                    for (Type fieldType : getItableFieldTypes()) {
                        sb.append(fieldType == null
                                ? "(ref.null struct)\n"
                                : String.format("(global.get %s.vtable@%s)\n",
                                        fieldType.name, this.name));
                    }
                    sb.append(")");
                }
                sb.append(")\n");
                return sb.toString();
            }

            private Type[] itableFieldTypesCache;

            Type[] getItableFieldTypes() {
                if (itableFieldTypesCache == null) {
                    itableFieldTypesCache = new Type[itableAllocator.getItableSize()];
                    for (Type i : implementedInterfaces) {
                        int itableIndex = itableAllocator.getItableFieldIndex(i.name);
                        if (itableIndex >= 0) {
                            if (itableFieldTypesCache[itableIndex] == null
                                    || itableFieldTypesCache[itableIndex].isSuperTypeOf(i)) {
                                itableFieldTypesCache[itableIndex] = i;
                            }
                        }
                    }
                }
                return itableFieldTypesCache;
            }
        }
    }
}
