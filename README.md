[![GitHub license](https://img.shields.io/github/license/treblereel/j2cl-maven-plugin-ng)](https://github.com/treblereel/j2cl-maven-plugin-ng/blob/main/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/org.treblereel.j2cl.plugin/j2cl-maven-plugin)](https://central.sonatype.com/artifact/org.treblereel.j2cl.plugin/j2cl-maven-plugin)
![Gitter](https://img.shields.io/gitter/room/vertispan/j2cl)
[![Java CI with Maven](https://github.com/treblereel/j2cl-maven-plugin-ng/actions/workflows/maven.yml/badge.svg)](https://github.com/treblereel/j2cl-maven-plugin-ng/actions/workflows/maven.yml)

# j2cl-maven-plugin

A Maven plugin for transpiling Java code to JavaScript using [J2CL](https://github.com/nicka-kie/nicka-nicka-nicka) (Java to Closure Compiler) and optimizing it with [Google Closure Compiler](https://developers.google.com/closure/compiler).

## Coordinates

```xml
<groupId>org.treblereel.j2cl.plugin</groupId>
<artifactId>j2cl-maven-plugin</artifactId>
<version>1.0-SNAPSHOT</version>
```

## Requirements

- Java 21+
- Maven 3.9.11+

---

## Key Terms

| Term | Description |
|------|-------------|
| **J2CL** | Java to Closure Compiler — a transpiler that converts Java code into JavaScript compatible with Google Closure Compiler |
| **Closure Compiler** | Google's JavaScript compiler that performs optimization, minification, and dead-code elimination |
| **Transpilation** | The process of converting Java sources into JavaScript code via J2CL |
| **Stripped sources** | Java sources with `@GwtIncompatible`-annotated members removed |
| **Bytecode** | Java `.class` files compiled via Turbine (a fast header compiler) |
| **Transpiled JS** | JavaScript code produced by J2CL transpilation |
| **Optimized JS** | JavaScript that has been optimized by Closure Compiler |
| **Bundled JS** | JavaScript bundled separately per dependency |
| **Native JS** | Hand-written JavaScript files (`.native.js`) that complement Java classes |
| **JsInterop** | Annotations (`@JsType`, `@JsMethod`, `@JsProperty`) for Java-JavaScript interoperability |
| **Super-source** | A GWT mechanism for substituting Java classes with platform-specific implementations |
| **Defines** | Closure Compiler compile-time constants (`@define`), set at build time |
| **Externs** | Type declarations for external JS libraries used by Closure Compiler |
| **XTB** | XML Translation Bundle — a translation file format for internationalization |
| **Reactor dependency** | A module from the current Maven reactor (multi-module build) |
| **JAR dependency** | An external dependency from a Maven repository |
| **WASM** | WebAssembly — a binary instruction format; the plugin can compile Java to WASM instead of JavaScript |
| **WAT** | WebAssembly Text format — human-readable form of WASM, produced by the J2CL transpiler |
| **Binaryen / wasm-opt** | WebAssembly optimizer that converts WAT to optimized `.wasm` binary |
| **WASM Entry Point** | A `public static` Java method exported from the WASM module, callable from JavaScript |
| **wasm:js-string** | A WebAssembly builtin module for string operations, provided by the browser engine |
| **--closed-world** | A wasm-opt flag that restricts types at the module boundary to primitives |

---

## Goals

### `compile`

Transpiles Java code to JavaScript and runs Closure Compiler optimization.

- **Phase**: `compile`
- **Dependency resolution**: `COMPILE_PLUS_RUNTIME`

```xml
<execution>
    <id>compile</id>
    <goals>
        <goal>compile</goal>
    </goals>
</execution>
```

### `test`

Transpiles test Java sources to JavaScript and runs them in a headless browser.

- **Phase**: `test`
- **Dependency resolution**: `TEST`
- Discovers tests via `@J2clTestInput`
- Compiles each test separately with Closure Compiler
- Generates an HTML harness and runs it in HtmlUnit or Chrome

```xml
<execution>
    <id>test</id>
    <goals>
        <goal>test</goal>
    </goals>
</execution>
```

### `watch`

Watch mode — monitors source files for changes and automatically rebuilds the project.

- **Phase**: not bound to any phase (invoked manually)
- **Dependency resolution**: `COMPILE_PLUS_RUNTIME`
- Watches all source directories, including reactor modules
- 200ms debounce before triggering a rebuild
- Uses `SIMPLE_OPTIMIZATIONS` and enables sourcemaps by default

```bash
mvn j2cl:watch
```

---

## Build Pipeline

The plugin processes each dependency (including the project itself) through a chain of tasks:

```
Sources
  └─> UnzipTask         — extract JAR/ZIP dependencies
  └─> ByteCodeTask      — compile Java → bytecode (via Turbine)
  └─> StripSourcesTask  — remove @GwtIncompatible members
  └─> J2CLTask          — transpile Java → JavaScript (J2CL)
  └─> ClosureTask       — optimize with Closure Compiler
  └─> FinalTask         — copy output to the webapp directory
```

When `compilationLevel=BUNDLE_JAR`, `ClosureTask` → `FinalTask` is replaced with:

```
  └─> ClosureBundleTask — bundle JS per dependency
  └─> BundleJarTask     — generate a loader for all bundles
```

When `backend=WASM`, the pipeline switches to:

```
Sources
  └─> UnzipTask           — extract JAR/ZIP dependencies
  └─> ByteCodeTask        — compile Java → bytecode (via Turbine)
  └─> StripSourcesTask    — remove @GwtIncompatible members
  └─> J2CLTask (WASM)     — transpile Java → WAT (WebAssembly Text)
  └─> WasmBundlerTask     — merge WAT modules, generate exports/imports
  └─> BinaryenTask        — optimize WAT → .wasm binary (via wasm-opt)
  └─> WasmFinalTask       — assemble output (.wasm, imports.js, public/ resources)
```

Tasks run in parallel on virtual threads (Java 21+). Results are cached — subsequent builds skip tasks that have a `.success` marker.

---

## Configuration Parameters

### Core Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `compilationLevel` | `ADVANCED_OPTIMIZATIONS` | Closure Compiler optimization level |
| `initialScriptFilename` | `${project.artifactId}/${project.artifactId}.js` | Output JS filename |
| `webappDirectory` | `${project.build.directory}/${project.build.finalName}` | Output directory for build artifacts |
| `enableSourcemaps` | `false` | Generate source maps |
| `languageOut` | `ECMASCRIPT_2017` | Target ECMAScript version |
| `checkAssertions` | `false` | Keep `assert` statements in the output JS |
| `env` | `BROWSER` | Closure Compiler environment (`BROWSER` / `NODEJS`) |
| `rewritePolyfills` | `false` | Rewrite ES6 library calls to use polyfills |
| `defines` | _(empty)_ | Compile-time `@define` variables (key-value pairs) |
| `translationsFile` | _(none)_ | XTB translation file for internationalization |
| `annotationProcessorsArgs` | _(empty)_ | Arguments for annotation processors (key-value pairs) |

### Compilation Levels (`compilationLevel`)

| Level | Description |
|-------|-------------|
| `ADVANCED_OPTIMIZATIONS` | Aggressive optimization: renaming, dead-code elimination. For production |
| `SIMPLE_OPTIMIZATIONS` | Optimizations that don't change the public API |
| `WHITESPACE_ONLY` | Only removes whitespace and comments |
| `BUNDLE` | Concatenates files, prepends defines, provides sourcemaps |
| `BUNDLE_JAR` | Caches bundles per dependency. Enables incremental builds |

### `test` Parameters

| Parameter | Default | Property | Description |
|-----------|---------|----------|-------------|
| `compilationLevel` | `SIMPLE_OPTIMIZATIONS` | `compilationLevel` | Optimization level for tests |
| `languageOut` | `ECMASCRIPT5` | `languageOut` | Target ECMAScript version |
| `webdriver` | `htmlunit` | — | Browser driver: `htmlunit` or `chrome` |
| `checkAssertions` | `true` | — | Enable assertion checks |
| `testTimeout` | `60` | `j2cl.test.timeout` | Test timeout in seconds |
| `skipTests` | `false` | `skipTests` | Skip test execution (compilation still runs) |
| `skip` | `false` | `maven.test.skip` | Skip the test goal entirely |

### `watch` Parameters

| Parameter | Default | Property | Description |
|-----------|---------|----------|-------------|
| `watchCompilationLevel` | `SIMPLE_OPTIMIZATIONS` | `j2cl.watch.compilationLevel` | Optimization level in watch mode |
| `watchEnableSourcemaps` | `true` | `j2cl.watch.enableSourcemaps` | Enable sourcemaps in watch mode |

---

## Examples

### Minimal Configuration

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.treblereel.j2cl.plugin</groupId>
            <artifactId>j2cl-maven-plugin</artifactId>
            <version>1.0-SNAPSHOT</version>
            <executions>
                <execution>
                    <id>compile</id>
                    <goals>
                        <goal>compile</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Production Build with Defines

```xml
<plugin>
    <groupId>org.treblereel.j2cl.plugin</groupId>
    <artifactId>j2cl-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <id>compile</id>
            <goals>
                <goal>compile</goal>
            </goals>
            <configuration>
                <compilationLevel>ADVANCED_OPTIMIZATIONS</compilationLevel>
                <initialScriptFilename>myapp/app.js</initialScriptFilename>
                <webappDirectory>${project.build.directory}/dist</webappDirectory>
                <languageOut>ECMASCRIPT_2020</languageOut>
                <defines>
                    <app.version>${project.version}</app.version>
                    <app.debug>false</app.debug>
                </defines>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Compilation + Tests

```xml
<dependencies>
    <dependency>
        <groupId>com.google.jsinterop</groupId>
        <artifactId>jsinterop-annotations</artifactId>
        <version>2.1.0</version>
    </dependency>
    <dependency>
        <groupId>org.kie.j2cl.tools</groupId>
        <artifactId>junit-annotations</artifactId>
        <version>v20260527-1</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.kie.j2cl.tools</groupId>
        <artifactId>junit-emul</artifactId>
        <version>v20260527-1</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.kie.j2cl.tools</groupId>
        <artifactId>gwttestcase-emul</artifactId>
        <version>v20260527-1</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.treblereel.j2cl.plugin</groupId>
            <artifactId>j2cl-maven-plugin</artifactId>
            <version>1.0-SNAPSHOT</version>
            <executions>
                <execution>
                    <id>compile</id>
                    <goals>
                        <goal>compile</goal>
                    </goals>
                </execution>
                <execution>
                    <id>test</id>
                    <goals>
                        <goal>test</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Internationalization (XTB)

```xml
<execution>
    <id>compile</id>
    <goals>
        <goal>compile</goal>
    </goals>
    <configuration>
        <translationsFile>
            <file>${project.basedir}/translations_fr.xtb</file>
        </translationsFile>
        <defines>
            <goog.LOCALE>fr</goog.LOCALE>
        </defines>
    </configuration>
</execution>
```

Auto-detect XTB file by locale:

```xml
<translationsFile>
    <auto>true</auto>
</translationsFile>
<defines>
    <goog.LOCALE>de</goog.LOCALE>
</defines>
```

### Annotation Processors with Arguments

```xml
<configuration>
    <annotationProcessorsArgs>
        <optionName>my-option</optionName>
    </annotationProcessorsArgs>
</configuration>
```

### JsInterop — Java Class Example

```java
package example;

import jsinterop.annotations.JsType;

@JsType
public class HelloWorld {

    public static String getHelloWorld() {
        return "Hello from Java!";
    }
}
```

After the build, the method is accessible from JavaScript:

```javascript
console.log(example.HelloWorld.getHelloWorld()); // "Hello from Java!"
```

---

## WASM Backend

The plugin supports compiling Java to **WebAssembly** (WASM) as an alternative to the default Closure Compiler JavaScript backend. Set `<backend>WASM</backend>` and declare entry points to export.

### Build Pipeline (WASM)

```
Sources
  └─> UnzipTask           — extract JAR/ZIP dependencies
  └─> ByteCodeTask        — compile Java → bytecode (via Turbine)
  └─> StripSourcesTask    — remove @GwtIncompatible members
  └─> J2CLTask (WASM)     — transpile Java → WAT (WebAssembly Text)
  └─> WasmBundlerTask     — merge WAT modules, generate exports and imports
  └─> BinaryenTask        — optimize WAT → .wasm binary (via wasm-opt)
  └─> WasmFinalTask       — copy .wasm, imports.js, module.js to webapp directory
                             and copy public/ resources to the output root
```

### WASM Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `backend` | `CLOSURE` | Set to `WASM` for WebAssembly output |
| `compilationLevel` | `ADVANCED_OPTIMIZATIONS` | Use `BUNDLE` for WASM |
| `wasmEntryPoints` | _(empty)_ | List of `qualified.ClassName#methodName` patterns to export |

### Entry Points

Entry points define which Java methods become WASM exports, callable from JavaScript. Each entry point follows the format `package.ClassName#methodName`:

```xml
<wasmEntryPoints>
    <wasmEntryPoint>example.wasm.Calculator#add</wasmEntryPoint>
    <wasmEntryPoint>example.wasm.Calculator#multiply</wasmEntryPoint>
</wasmEntryPoints>
```

**Constraints:**
- Exported methods must be `public static`
- Parameter and return types must be **primitives** (`int`, `long`, `boolean`, `double`) — `String` and object types cannot cross the WASM export boundary due to `--closed-world` optimization
- Wildcard `.*` is supported: `example.wasm.Calculator#*` exports all public static methods

### Minimal WASM Example

**pom.xml:**

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.treblereel.j2cl.plugin</groupId>
            <artifactId>j2cl-maven-plugin</artifactId>
            <version>0.24</version>
            <executions>
                <execution>
                    <id>compile</id>
                    <goals><goal>compile</goal></goals>
                </execution>
            </executions>
            <configuration>
                <backend>WASM</backend>
                <compilationLevel>BUNDLE</compilationLevel>
                <wasmEntryPoints>
                    <wasmEntryPoint>example.wasm.Calculator#add</wasmEntryPoint>
                    <wasmEntryPoint>example.wasm.Calculator#multiply</wasmEntryPoint>
                </wasmEntryPoints>
            </configuration>
        </plugin>
    </plugins>
</build>
```

**Java code:**

```java
package example.wasm;

public class Calculator {
    public static int add(int a, int b) {
        return a + b;
    }
    public static int multiply(int a, int b) {
        return a * b;
    }
}
```

### Output Structure

After `mvn compile`, the output goes to `target/${project.build.finalName}/`:

```
target/my-project-1.0/
├── index.html                         ← from src/main/java/.../public/
└── my-project/
    ├── my-project.wasm                ← compiled WASM binary
    ├── my-project.wasm.map            ← source maps
    ├── my-project.imports.js          ← generated import bindings (Closure style)
    ├── my-project.module.js           ← ES module loader
    └── my-project.symbols             ← debug symbols
```

Static resources (HTML, CSS, images) should be placed in a `public/` directory inside your Java source tree (e.g. `src/main/java/example/wasm/public/index.html`). The plugin copies `public/` contents to the output root.

### Loading WASM from HTML

The generated `imports.js` uses Closure Library (`goog.require`). For standalone use without Closure Library, provide imports inline:

```html
<script>
async function loadWasm() {
    const importObj = {
        'WebAssembly': WebAssembly,
        'imports': {
            // JRE support (required by j2cl runtime)
            'Error': Error,
            'Error.constructor': () => new Error(),
            'j2wasm.ExceptionUtils.getJavaThrowable': (e) => e?.['__j2wasm$exception'] ?? null,
            'j2wasm.ExceptionUtils.setJavaThrowable': (e, t) => {
                if (e instanceof Object) { try { e['__j2wasm$exception'] = t; } catch(ex) {} }
            },
            'j2wasm.ExceptionUtils.isError': (obj) => obj instanceof Error,
            'j2wasm.EqualityUtils.isSame': (a, b) => Object.is(a, b) || (a == null && b == null),
            // ... more imports depending on what Java APIs you use
        }
    };

    // builtins and importedStringConstants are compile options (3rd argument)
    const compileOpts = { builtins: ['js-string'], importedStringConstants: "'" };
    const { instance } = await WebAssembly.instantiateStreaming(
        fetch('./my-project/my-project.wasm'), importObj, compileOpts
    );

    // Call exported Java methods
    console.log(instance.exports.add(2, 3));       // 5
    console.log(instance.exports.multiply(4, 7));  // 28
}
loadWasm();
</script>
```

**Important:** `builtins` and `importedStringConstants` must be passed as the **third argument** (compile options) to `WebAssembly.instantiateStreaming`, not mixed into the import object. The `wasm:js-string` builtin module is provided by the browser engine (Chrome 130+, Firefox 128+, Safari 18.2+) and handles string operations directly in the WASM runtime without JS boundary crossing.

### WASM → JS Callbacks

Use `@JsMethod(namespace = JsPackage.GLOBAL)` to declare JavaScript functions that WASM can call. These become WASM imports that you provide in the import object:

```java
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;

public class Bridge {

    // Declare JS functions callable from WASM
    @JsMethod(namespace = JsPackage.GLOBAL, name = "onResult")
    private static native void onResult(int value);

    // Exported entry point that calls back into JS
    public static int computeAndNotify(int a, int b) {
        int result = a + b;
        onResult(result);  // calls JS function
        return result;
    }
}
```

Provide the callback in the import object:

```js
'imports': {
    'onResult': (value) => document.getElementById('result').textContent = value,
    // ... other imports
}
```

Callback parameters must be **primitives** — `String` and object types cannot cross the WASM→JS boundary in exported/imported functions.

### WASM DOM Access

WASM code can access browser DOM APIs directly via `@JsType(isNative = true)` bindings. This avoids writing JS glue — the WASM module calls DOM methods through its import table:

```java
import jsinterop.annotations.*;

@JsType(isNative = true, name = "Document", namespace = JsPackage.GLOBAL)
public class JsDocument {
    @JsMethod
    public native JsElement createElement(String tagName);

    @JsMethod
    public native JsElement getElementById(String id);
}

@JsType(isNative = true, name = "Element", namespace = JsPackage.GLOBAL)
public class JsElement {
    @JsProperty
    public native void setInnerText(String text);

    @JsMethod
    public native void appendChild(JsElement child);

    @JsProperty
    public native JsStyle getStyle();
}
```

Use from a WASM entry point:

```java
public class DomDemo {
    @JsProperty(namespace = JsPackage.GLOBAL, name = "document")
    private static native JsDocument getDocument();

    public static int createElements(int count) {
        JsDocument doc = getDocument();
        JsElement container = doc.getElementById("container");
        for (int i = 0; i < count; i++) {
            JsElement div = doc.createElement("div");
            div.setInnerText("Element #" + (i + 1));
            container.appendChild(div);
        }
        return count;
    }
}
```

The corresponding DOM imports must be provided:

```js
'imports': {
    'get document': () => document,
    'Document.createElement$1': (doc, tag) => doc.createElement(tag),
    'Document.getElementById$1': (doc, id) => doc.getElementById(id),
    'Element.appendChild$1': (el, child) => el.appendChild(child),
    'set Element.innerText': (el, text) => { el.innerText = text; },
    'get Element.style': (el) => el.style,
    // ...
}
```

**Note:** The import names follow j2cl conventions — instance methods get a `$N` suffix (arity), properties use `get`/`set` prefix. Check the generated `.imports.js` file for exact names.

### WASM Tests

WASM tests use the same `test` goal with `<backend>WASM</backend>`:

```xml
<configuration>
    <backend>WASM</backend>
    <compilationLevel>BUNDLE</compilationLevel>
</configuration>
<executions>
    <execution>
        <id>compile</id>
        <goals><goal>compile</goal></goals>
    </execution>
    <execution>
        <id>test</id>
        <goals><goal>test</goal></goals>
    </execution>
</executions>
```

Test classes use the standard `@J2clTestInput` annotation and JUnit 4 API, same as JavaScript backend tests.

### Browser Requirements

| Feature | Chrome | Firefox | Safari |
|---------|--------|---------|--------|
| WebAssembly GC | 119+ | 120+ | 18.2+ |
| JS String Builtins | 130+ | 128+ | 18.2+ |

The compiled WASM uses the GC proposal (struct/array types) and JS String Builtins (`wasm:js-string` module). Both are required.

---

## Dependency Replacement

The plugin automatically replaces certain dependencies for J2CL compatibility:

| Original | Replacement |
|----------|-------------|
| `com.google.jsinterop:base` | `org.kie.j2cl.tools.jsinterop:jsinterop-base:1.1.1` |
| `org.gwtproject:gwt-user` | _(excluded)_ |
| `org.gwtproject:gwt-dev` | _(excluded)_ |
| `org.gwtproject:gwt-servlet` | _(excluded)_ |
| `com.google.gwt:gwt-user` | _(excluded)_ |
| `com.google.gwt:gwt-dev` | _(excluded)_ |
| `com.google.gwt:gwt-servlet` | _(excluded)_ |

---

## Maven Commands

```bash
# Compile
mvn compile

# Compile + run tests
mvn test

# Watch mode (continuous rebuild)
mvn j2cl:watch
sad
# Skip tests
mvn compile -DskipTests

# Change optimization level
mvn compile -DcompilationLevel=BUNDLE

# Change target ECMAScript version
mvn compile -DlanguageOut=ECMASCRIPT_2020
```

---

## Building from Source

### Prerequisites

- JDK 21 or later
- Maven 3.9.11 or later

### Build the Plugin

```bash
git clone https://github.com/treblereel/j2cl-maven-plugin-ng.git
cd j2cl-maven-plugin-ng

# Build the plugin and install it to local Maven repository
mvn clean install -pl plugin

# Build everything including integration tests
mvn clean install
```

### Run Integration Tests Only

```bash
mvn clean verify -pl tests -am
```

---

## Contributing

Contributions are welcome! Here's how to get started:

1. **Fork** the repository on GitHub.
2. **Create a branch** for your feature or bug fix:
   ```bash
   git checkout -b feature/my-feature
   ```
3. **Make your changes** and make sure the build passes:
   ```bash
   mvn clean install
   ```
4. **Add integration tests** if your change affects plugin behavior — place them under `tests/`.
5. **Commit** your changes with a clear message describing what was changed and why.
6. **Push** your branch and open a **Pull Request** against `main`.

### Code Quality

The build enforces code quality automatically via Checkstyle (runs at `validate` phase):

- **No tab characters** — use spaces only.
- **No star imports** — all imports must be explicit.
- **No unused or redundant imports**.
- **Import ordering**: `java.*` → `jakarta/javax.*` → third-party → `static`, alphabetically sorted within each group, blank lines between groups.
- **Braces required** for `if`, `else`, `for`, `while`, `do`.
- **`equals()` and `hashCode()` must be overridden together**.

The Maven Enforcer plugin also validates:
- Maven 3.9.0+
- JDK 21+
- No duplicate dependency versions in POM

### Guidelines

- Follow existing code style — Checkstyle will enforce the basics.
- Keep PRs focused — one feature or fix per PR.
- Integration tests are preferred over unit tests for plugin behavior.
- Make sure all existing tests pass before submitting.

### Reporting Issues

If you find a bug or have a feature request, please [open an issue](https://github.com/treblereel/j2cl-maven-plugin-ng/issues) on GitHub with:

- Steps to reproduce (for bugs)
- Expected vs. actual behavior
- Plugin version and Java/Maven versions

---

## Releasing

To publish a release to Maven Central:

```bash
mvn clean deploy -Prelease
```

The `release` profile activates:
- Source JAR attachment
- Javadoc JAR generation
- GPG artifact signing
- Publishing to Maven Central via Sonatype

---

## License

This project is licensed under the [Apache License 2.0](LICENSE).
