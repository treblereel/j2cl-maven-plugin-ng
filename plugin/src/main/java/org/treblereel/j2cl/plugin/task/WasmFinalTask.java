package org.treblereel.j2cl.plugin.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DependencyOptions;
import com.google.javascript.jscomp.SourceFile;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.tools.ClosureCompiler;
import org.treblereel.j2cl.plugin.tools.ClosureLibrary;
import org.treblereel.j2cl.plugin.utils.FileUtils;

public class WasmFinalTask extends TaskInput {

    public WasmFinalTask(Dependency dep, BuildContext buildContext, BuildLog buildLog) {
        super(dep, buildContext, buildLog);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.FINAL_TASK;
    }

    @Override
    public void process() {
        TaskOutput optimized = input(dependency, OutputTypes.WASM_OPTIMIZED);
        TaskOutput bundled = input(dependency, OutputTypes.WASM_BUNDLED);

        Path webappDirectory = Paths.get(buildContext.getConfig().webappDirectory());
        String scriptFilename = buildContext.getConfig().initialScriptFilename();
        String baseName = scriptFilename.replace(".js", "");

        Path outputDir = webappDirectory.resolve(Paths.get(baseName).getParent() != null
                ? Paths.get(baseName).getParent() : Paths.get(""));

        try {
            Files.createDirectories(outputDir);

            copyFromOutput(optimized, baseName + ".wasm", webappDirectory);
            copyFromOutput(optimized, baseName + ".wasm.map", webappDirectory);
            copyFromOutput(optimized, baseName + ".symbols", webappDirectory);

            Path importsFile = bundled.paths().get(0).resolve("imports.js.txt");
            Path targetImports = webappDirectory.resolve(baseName + ".imports.js");
            Files.createDirectories(targetImports.getParent());
            writeImports(importsFile, targetImports);

            generateModuleLoader(webappDirectory, baseName);

            copyPublicResources(webappDirectory);

        } catch (IOException e) {
            throw new RuntimeException("Failed to assemble WASM output for " + dependency.key(), e);
        }
    }

    private void copyFromOutput(TaskOutput output, String fileName, Path targetDir) throws IOException {
        for (Path outputDir : output.paths()) {
            Path source = outputDir.resolve(fileName);
            if (Files.exists(source)) {
                Path target = targetDir.resolve(fileName);
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        }
    }

    private void writeImports(Path importsFile, Path target) throws IOException {
        List<SourceFile> inputs = new ArrayList<>();
        ClosureLibrary.get().forEach((name, code) -> inputs.add(SourceFile.fromCode(name, code)));
        inputs.addAll(FileUtils.readJsZip(buildContext.getConfig().getWasmJreJsZip().toPath(), StandardCharsets.UTF_8));
        TaskOutput self = input(dependency, OutputTypes.UNZIPPED_DEPENDENCIES);
        TaskOutput deps = input(getFlattenDependencies(), OutputTypes.UNZIPPED_DEPENDENCIES);
        Stream.concat(self.filter(ClosureTask.PLAIN_JS_SOURCES).files().stream(),
                deps.filter(ClosureTask.PLAIN_JS_SOURCES).files().stream())
                .forEach(file -> inputs.add(SourceFile.fromFile(file.getAbsolutePath().toString())));
        inputs.add(SourceFile.fromCode("wasm-imports.js",
                "goog.module('j2cl.wasm.imports'); goog.module.declareLegacyNamespace();\n"
                        + Files.readString(importsFile) + "\nexports = getImports;\n"));

        CompilerOptions options = new CompilerOptions();
        CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        options.setClosurePass(true);
        options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);
        options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_2017);
        options.setDependencyOptions(DependencyOptions.sortOnly());
        Compiler compiler = new ClosureCompiler();
        var result = compiler.compile(CommandLineRunner.getDefaultExterns(), inputs, options);
        if (!result.success) {
            result.errors.forEach(error -> logger.error(error.toString()));
            throw new IllegalStateException("Failed to compile WASM imports for " + dependency.key());
        }
        Files.writeString(target, compiler.toSource() + "\nexport const getImports = j2cl.wasm.imports;\n");
    }

    private void generateModuleLoader(Path webappDirectory, String baseName) throws IOException {
        String wasmFileName = Paths.get(baseName).getFileName() + ".wasm";
        String importsFileName = Paths.get(baseName).getFileName() + ".imports.js";

        String moduleJs = "import { getImports } from './" + importsFileName + "';\n"
                + "\n"
                + "let _instance;\n"
                + "\n"
                + "export async function instantiateStreaming(wasmUrl) {\n"
                + "  if (_instance) return _instance;\n"
                + "  const response = typeof wasmUrl === 'string' || wasmUrl instanceof URL ? fetch(wasmUrl) : wasmUrl;\n"
                + "  const { instance } = await WebAssembly.instantiateStreaming(response, getImports(), {\n"
                + "    builtins: ['js-string'],\n"
                + "    importedStringConstants: \"'\"\n"
                + "  });\n"
                + "  _instance = instance;\n"
                + "  return instance;\n"
                + "}\n"
                + "\n"
                + "export async function instantiate() {\n"
                + "  return instantiateStreaming(new URL('./" + wasmFileName + "', import.meta.url));\n"
                + "}\n";

        Path moduleFile = webappDirectory.resolve(baseName + ".module.js");
        Files.createDirectories(moduleFile.getParent());
        Files.writeString(moduleFile, moduleJs);
    }

    private void copyPublicResources(Path outputDir) {
        TaskOutput selfUnzipped = input(dependency, OutputTypes.UNZIPPED_DEPENDENCIES);
        TaskOutput depsUnzipped = input(getFlattenDependencies(), OutputTypes.UNZIPPED_DEPENDENCIES);

        PathMatcher inMetaInfResources = path -> path.startsWith(Paths.get("META-INF", "resources"));
        PathMatcher inPublic = path -> {
            for (Path part : path) {
                if (part.toString().equals("public")) return true;
            }
            return false;
        };

        List<FileEntry> resources = Stream.concat(
                selfUnzipped.filter(inMetaInfResources, inPublic).files().stream(),
                depsUnzipped.filter(inMetaInfResources, inPublic).files().stream()
        ).toList();

        for (FileEntry resource : resources) {
            try {
                Path outPath = outputDir.resolve(extractPublicResourcePath(resource.getSourcePath()));
                Files.createDirectories(outPath.getParent());
                Files.copy(resource.getAbsolutePath(), outPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Unable to copy resource: " + resource.getSourcePath(), e);
            }
        }
    }

    private Path extractPublicResourcePath(Path p) {
        int publicIndex = -1;
        for (int i = 0; i < p.getNameCount(); i++) {
            if (p.getName(i).toString().equals("public")) {
                publicIndex = i;
                break;
            }
        }
        if (publicIndex >= 0 && publicIndex + 1 < p.getNameCount()) {
            return p.subpath(publicIndex + 1, p.getNameCount());
        }
        if (p.startsWith(Paths.get("META-INF", "resources"))) {
            return p.subpath(2, p.getNameCount());
        }
        return p;
    }
}
