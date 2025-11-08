package org.treblereel.j2cl.plugin.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;

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
            if (Files.exists(importsFile)) {
                Path targetImports = webappDirectory.resolve(baseName + ".imports.js");
                Files.createDirectories(targetImports.getParent());
                Files.copy(importsFile, targetImports, StandardCopyOption.REPLACE_EXISTING);
            }

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

    private void generateModuleLoader(Path webappDirectory, String baseName) throws IOException {
        String wasmFileName = Paths.get(baseName).getFileName() + ".wasm";
        String importsFileName = Paths.get(baseName).getFileName() + ".imports.js";

        String moduleJs = "import { instantiate as _instantiate } from './" + importsFileName + "';\n"
                + "\n"
                + "let _instance;\n"
                + "\n"
                + "export async function instantiateStreaming(wasmUrl) {\n"
                + "  if (_instance) return _instance;\n"
                + "  const response = typeof wasmUrl === 'string' ? fetch(wasmUrl) : wasmUrl;\n"
                + "  const { instance } = await WebAssembly.instantiateStreaming(response, {\n"
                + "    builtins: ['js-string'],\n"
                + "    importedStringConstants: \"'\"\n"
                + "  });\n"
                + "  _instance = instance;\n"
                + "  return instance;\n"
                + "}\n"
                + "\n"
                + "export async function instantiate() {\n"
                + "  return instantiateStreaming('./" + wasmFileName + "');\n"
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
