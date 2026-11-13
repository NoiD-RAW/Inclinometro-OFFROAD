"""Compile the production native sources with small, platform-independent fixtures."""
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "modules/inclinometro-core"


def run_swift(sources, directory, args=()):
    executable = directory / "swift-tests"
    subprocess.run(["swiftc", "-module-cache-path", str(directory / "swift-cache"),
                    *map(str, sources), "-o", str(executable)], check=True)
    subprocess.run([str(executable), *map(str, args)], check=True)


def run_kotlin(sources, main_class, directory, args=()):
    """Use kotlinc, or the compiler already downloaded by the Android build."""
    classes = directory / "kotlin-classes"
    classes.mkdir()
    java = shutil.which("java")
    bundled = Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java")
    if bundled.exists() and not os.environ.get("JAVA_HOME"):
        java = str(bundled)
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"

    def jar(group, artifact, version):
        candidates = list((cache / group / artifact / version).glob("*/*.jar"))
        if not candidates:
            raise RuntimeError(f"Missing {artifact}:{version}; run an Android Gradle build first.")
        return candidates[0]

    poms = list((cache / "org.jetbrains.kotlin/kotlin-compiler-embeddable").glob("*/*/*.pom"))
    if not poms or not java:
        raise RuntimeError("A JDK and cached Android Kotlin compiler are required; run an Android build first.")
    pom = max(poms, key=lambda p: tuple(int(v) for v in p.parents[1].name.split(".") if v.isdigit()))
    version = pom.parents[1].name
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    dependencies = ET.parse(pom).findall("m:dependencies/m:dependency", ns)
    jars = [jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", version)]
    for dep in dependencies:
        jars.append(jar(*(dep.findtext(f"m:{key}", namespaces=ns) for key in ("groupId", "artifactId", "version"))))
    annotations = list((cache / "org.jetbrains/annotations").glob("*/*/*.jar"))
    if annotations:
        jars.append(annotations[-1])
    classpath = os.pathsep.join(map(str, jars))
    subprocess.run([java, "-cp", classpath, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                    "-no-stdlib", "-no-reflect", "-classpath", classpath, "-d", str(classes),
                    *map(str, sources)], check=True)
    subprocess.run([java, "-cp", str(classes) + os.pathsep + classpath, main_class, *map(str, args)], check=True)
