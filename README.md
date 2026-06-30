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

### Project Structure

```
j2cl-maven-plugin-ng/
├── plugin/          # Maven plugin implementation
│   └── src/
│       ├── main/    # Plugin goals, build pipeline, task execution
│       └── test/    # Unit tests
├── tests/           # Integration test suite (19 test modules)
│   ├── hello-world-single/          # Basic single-module compilation
│   ├── hello-world-reactor/         # Multi-module reactor build
│   ├── hello-world-super-source/    # GWT super-source mechanism
│   ├── hello-world-web-components/  # Elemental2 DOM APIs
│   ├── simple-htmlunit-test/        # HtmlUnit-based test execution
│   ├── translationsfile/            # XTB translation bundles
│   ├── annotation-processor-in-reactor/      # APT in reactor
│   ├── annotation-processor-in-reactor-args/ # APT with arguments
│   ├── transitive-dependencies/     # Transitive dependency resolution
│   └── ...
└── pom.xml          # Parent POM
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
