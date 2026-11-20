#!/usr/bin/env bash
# scaffold-project.sh — scaffold (or retrofit) a multi-module Gradle library
# wired for Maven Central, optionally with a GWT/TeaVM web demo on GitHub Pages.
#
# Generates the publishing stack:
#   * root build.gradle with com.gradleup.nmcp Central Portal aggregation
#   * maven-publish + in-memory PGP signing per published module
#   * LocalBuild repository for dry-running signatures
#   * PUBLISHING.md runbook and a GitHub Actions release workflow
#
# and, when --web is gwt or teavm:
#   * a demo/{core,desktop,html|teavm} module set
#   * .gwt.xml module descriptors wired the way gbullet's are
#   * a deploy-demo.yml workflow publishing the compiled demo to GitHub Pages
#
# Two modes:
#   scaffold  (default)   create a new project directory
#   retrofit  (--into D)  write the build/CI stack into an existing project,
#                         backing up anything it would overwrite
#
# Run with no flags for interactive prompts, or pass flags for one-liner use.
#
# NOTE ON VERSIONS: defaults are pinned to combinations known to work together
# (GWT 2.11.0 + libgdx 1.13.1 is what gbullet ships). Newer is not always
# compatible — gdx-backend-gwt tracks specific GWT versions. Override with
# --gwt-version / --teavm-version / --gdx-version as needed.

set -euo pipefail
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

usage() {
    cat <<'EOF'
Usage: scaffold-project.sh [options]
Interactive when run bare; any flag you pass skips its prompt.

 Project
  -n, --name NAME          project name (required with -y); root becomes NAME-parent
  -d, --dir DIR            parent directory to create the project in   [.]
      --into DIR           RETROFIT an existing project in DIR instead of
                           creating a new one (implies the dir already exists)
  -g, --group GROUP        maven groupId                               [dev.yunghegel]
  -p, --package PKG        base java package            [GROUP.NAME-without-punctuation]
  -v, --version VERSION    initial version                             [1.0.0]
  -m, --modules a,b        comma-separated published modules           [NAME]
      --desc TEXT          project description (used in POMs)
      --license ID         zlib | apache-2.0 | mit                     [apache-2.0]

 Toolchain
      --java N             toolchain Java version                      [17]
      --release N          bytecode target (options.release)           [8]
      --layout ID          maven (src/main/java) | flat (src/)         [maven]
  -k, --kotlin BOOL        apply Kotlin plugin (yes/no)                [no]
      --kotlin-version V   Kotlin plugin version                       [2.0.21]
      --gdx BOOL           depend on libgdx (yes/no)                   [yes]
      --gdx-version V      libgdx version                              [1.13.1]

 Web target
  -w, --web ID             gwt | teavm | none                          [none]
      --gwt-version V      GWT version (gwt backend)                   [2.11.0]
      --gwt-plugin V       org.docstr gwt-gradle-plugin version        [1.1.31]
      --teavm-version V    TeaVM version, or gdx-teavm version if --gdx yes
      --demo BOOL          generate demo/{core,desktop,web} modules    [yes if --web]
      --demo-name NAME     name of the demo module tree                [demo]
      --pages BOOL         generate the GitHub Pages deploy workflow   [yes if --web]

 Repo metadata
      --gh-owner OWNER     GitHub owner for POM/scm URLs               [yunghegel]
      --gh-repo REPO       GitHub repo name                            [NAME]
      --dev-id ID          POM developer id                            [gh-owner]
      --dev-name NAME      POM developer name                          [Jamie Crown]

 Behaviour
      --wrapper-from DIR   copy Gradle wrapper from this project       [script's own dir]
      --no-git             skip git init
  -f, --force              overwrite without making .bak copies (retrofit)
      --dry-run            print what would be written, change nothing
  -y, --yes                non-interactive; use defaults for anything not passed
  -h, --help               this text

Examples
  # libgdx library + GWT demo on Pages, like gbullet
  scaffold-project.sh -n mylib -w gwt -y

  # plain Java library, no libgdx, no web
  scaffold-project.sh -n corelib --gdx no -w none -y

  # Kotlin library targeting the browser via TeaVM
  scaffold-project.sh -n klib -k yes --gdx no -w teavm -y

  # wire an existing repo for Central + Pages without touching its sources
  scaffold-project.sh --into ~/dev/gdx-assimp -n gdx-assimp \
      -g com.crownjames.gdxassimp -m gdx-assimp -w gwt
EOF
    exit "${1:-0}"
}

NAME='' DIR='.' INTO='' GROUP='' PKG='' VERSION='' MODULES='' DESC='' LICENSE=''
JAVA_VERSION='' RELEASE='' LAYOUT='' KOTLIN='' KOTLIN_VERSION='' GDX='' GDX_VERSION=''
WEB='' GWT_VERSION='' GWT_PLUGIN='' TEAVM_VERSION='' DEMO='' DEMO_NAME='' PAGES=''
GH_OWNER='' GH_REPO='' DEV_ID='' DEV_NAME='' WRAPPER_FROM='' GIT_INIT='yes'
NON_INTERACTIVE=no FORCE=no DRY_RUN=no

while [[ $# -gt 0 ]]; do
    case $1 in
        -n|--name)        NAME=$2; shift 2 ;;
        -d|--dir)         DIR=$2; shift 2 ;;
        --into)           INTO=$2; shift 2 ;;
        -g|--group)       GROUP=$2; shift 2 ;;
        -p|--package)     PKG=$2; shift 2 ;;
        -v|--version)     VERSION=$2; shift 2 ;;
        -m|--modules)     MODULES=$2; shift 2 ;;
        --desc)           DESC=$2; shift 2 ;;
        --license)        LICENSE=$2; shift 2 ;;
        --java)           JAVA_VERSION=$2; shift 2 ;;
        --release)        RELEASE=$2; shift 2 ;;
        --layout)         LAYOUT=$2; shift 2 ;;
        -k|--kotlin)      KOTLIN=$2; shift 2 ;;
        --kotlin-version) KOTLIN_VERSION=$2; shift 2 ;;
        --gdx)            GDX=$2; shift 2 ;;
        --gdx-version)    GDX_VERSION=$2; shift 2 ;;
        -w|--web)         WEB=$2; shift 2 ;;
        --gwt-version)    GWT_VERSION=$2; shift 2 ;;
        --gwt-plugin)     GWT_PLUGIN=$2; shift 2 ;;
        --teavm-version)  TEAVM_VERSION=$2; shift 2 ;;
        --demo)           DEMO=$2; shift 2 ;;
        --demo-name)      DEMO_NAME=$2; shift 2 ;;
        --pages)          PAGES=$2; shift 2 ;;
        --gh-owner)       GH_OWNER=$2; shift 2 ;;
        --gh-repo)        GH_REPO=$2; shift 2 ;;
        --dev-id)         DEV_ID=$2; shift 2 ;;
        --dev-name)       DEV_NAME=$2; shift 2 ;;
        --wrapper-from)   WRAPPER_FROM=$2; shift 2 ;;
        --no-git)         GIT_INIT=no; shift ;;
        -f|--force)       FORCE=yes; shift ;;
        --dry-run)        DRY_RUN=yes; shift ;;
        -y|--yes)         NON_INTERACTIVE=yes; shift ;;
        -h|--help)        usage ;;
        *) echo "unknown option: $1" >&2; usage 1 ;;
    esac
done

# ---------------------------------------------------------------- helpers

# ask VAR "prompt" default — prompt only if VAR is empty and we are interactive
ask() {
    local var=$1 prompt=$2 def=${3-}
    [[ -n ${!var} ]] && return 0
    if [[ $NON_INTERACTIVE == yes ]]; then
        printf -v "$var" '%s' "$def"
    else
        local val
        read -rp "$prompt${def:+ [$def]}: " val
        printf -v "$var" '%s' "${val:-$def}"
    fi
}

norm_bool() {  # echoes yes/no
    case $(echo "$1" | tr '[:upper:]' '[:lower:]') in
        y|yes|true|1) echo yes ;;
        *)            echo no ;;
    esac
}

lower() { echo "$1" | tr '[:upper:]' '[:lower:]'; }

die() { echo "error: $*" >&2; exit 1; }

# CamelCase from a dashed/underscored name: gdx-assimp -> GdxAssimp
camel() {
    local out='' part
    IFS='-_. ' read -ra parts <<< "$1"
    for part in "${parts[@]}"; do
        [[ -z $part ]] && continue
        out+="$(tr '[:lower:]' '[:upper:]' <<< "${part:0:1}")${part:1}"
    done
    echo "$out"
}

WRITTEN=() SKIPPED=() BACKED_UP=() CONFLICTS=()

# emit PATH  — content on stdin. Honours --dry-run, backs up in retrofit mode.
emit() {
    local path=$1 content
    content=$(cat)
    if [[ $DRY_RUN == yes ]]; then
        echo "  would write: ${path#"$TARGET"/}"
        WRITTEN+=("$path")
        return 0
    fi
    mkdir -p "$(dirname "$path")"
    if [[ -e $path ]]; then
        if [[ $FORCE == yes ]]; then
            :
        else
            cp "$path" "$path.bak"
            BACKED_UP+=("$path")
        fi
    fi
    printf '%s\n' "$content" > "$path"
    WRITTEN+=("$path")
}

# emit_new PATH — like emit, but never clobbers an existing file (source stubs)
emit_new() {
    local path=$1 content
    content=$(cat)
    if [[ -e $path ]]; then
        SKIPPED+=("$path")
        return 0
    fi
    if [[ $DRY_RUN == yes ]]; then
        echo "  would create: ${path#"$TARGET"/}"
        WRITTEN+=("$path")
        return 0
    fi
    mkdir -p "$(dirname "$path")"
    printf '%s\n' "$content" > "$path"
    WRITTEN+=("$path")
}

mkdirp() { [[ $DRY_RUN == yes ]] || mkdir -p "$@"; }

# emit_keep PATH — replace when scaffolding, preserve when retrofitting.
# An existing project's module scripts carry real dependency declarations that
# a generated stub would destroy.
emit_keep() {
    if [[ $MODE == retrofit ]]; then emit_new "$1"; else emit "$1"; fi
}

# ---------------------------------------------------------------- prompts

MODE=scaffold
if [[ -n $INTO ]]; then
    MODE=retrofit
    [[ -d $INTO ]] || die "--into '$INTO' is not a directory"
fi

if [[ $MODE == retrofit ]]; then
    DEFAULT_NAME=$(basename "$(cd "$INTO" && pwd)")
    ask NAME "Project name" "$DEFAULT_NAME"
else
    ask NAME "Project name (e.g. gbullet)"
fi
[[ -n $NAME ]] || die "project name is required"
[[ $NAME =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || die "invalid project name '$NAME'"

[[ $MODE == scaffold ]] && ask DIR "Parent directory" "."
ask GROUP        "Maven groupId"                       "dev.yunghegel"
DEFAULT_PKG="$GROUP.$(echo "$NAME" | tr -d '._-' | tr '[:upper:]' '[:lower:]')"
ask PKG          "Base java package"                   "$DEFAULT_PKG"
ask VERSION      "Initial version"                     "1.0.0"
ask MODULES      "Published modules (comma-separated)" "$NAME"
ask DESC         "Project description"                 "$NAME library."
ask LICENSE      "License (zlib / apache-2.0 / mit)"   "apache-2.0"
ask JAVA_VERSION "Java toolchain version"              "17"
ask RELEASE      "Bytecode target (options.release)"   "8"
ask LAYOUT       "Source layout (maven / flat)"        "maven"

ask KOTLIN "Kotlin support? (yes/no)" "no"
KOTLIN=$(norm_bool "$KOTLIN")
[[ $KOTLIN == yes ]] && ask KOTLIN_VERSION "Kotlin version" "2.0.21"

ask GDX "Depend on libgdx? (yes/no)" "yes"
GDX=$(norm_bool "$GDX")
[[ $GDX == yes ]] && ask GDX_VERSION "libgdx version" "1.13.1"

ask WEB "Web target (gwt / teavm / none)" "none"
WEB=$(lower "$WEB")

case $WEB in
    gwt)
        [[ $KOTLIN == yes ]] && cat >&2 <<'WARN'
warning: GWT compiles Java *source*; it cannot consume Kotlin. A Kotlin
         library will not be usable from the generated GWT demo. Use
         --web teavm for Kotlin.
WARN
        ask GWT_VERSION "GWT version"                    "2.11.0"
        ask GWT_PLUGIN  "gwt-gradle-plugin version"      "1.1.31"
        ;;
    teavm)
        if [[ $GDX == yes ]]; then
            ask TEAVM_VERSION "gdx-teavm version"        "1.2.0"
        else
            ask TEAVM_VERSION "TeaVM version"            "0.12.0"
        fi
        ;;
    none) ;;
    *) die "unknown --web '$WEB' (gwt, teavm, none)" ;;
esac

if [[ $WEB == none ]]; then
    DEMO=${DEMO:-no}; PAGES=${PAGES:-no}
else
    ask DEMO  "Generate demo modules? (yes/no)"       "yes"
    ask PAGES "Generate Pages deploy workflow? (yes/no)" "yes"
fi
DEMO=$(norm_bool "$DEMO"); PAGES=$(norm_bool "$PAGES")
ask DEMO_NAME "Demo module tree name" "demo"

ask GH_OWNER "GitHub owner"       "yunghegel"
ask GH_REPO  "GitHub repo"        "$NAME"
ask DEV_ID   "POM developer id"   "$GH_OWNER"
ask DEV_NAME "POM developer name" "Jamie Crown"

case $(lower "$LICENSE") in
    zlib)               LICENSE_NAME='zlib License';                    LICENSE_URL='https://opensource.org/licenses/Zlib' ;;
    apache*|apache-2.0) LICENSE_NAME='The Apache License, Version 2.0'; LICENSE_URL='https://www.apache.org/licenses/LICENSE-2.0.txt' ;;
    mit)                LICENSE_NAME='MIT License';                     LICENSE_URL='https://opensource.org/licenses/MIT' ;;
    *) die "unknown license '$LICENSE' (zlib, apache-2.0, mit)" ;;
esac

case $(lower "$LAYOUT") in
    maven) SRC_MAIN='src/main/java'; SRC_TEST='src/test/java'; SRC_RES='src/main/resources'; ASSET_DIR='src/main/resources' ;;
    flat)  SRC_MAIN='src';           SRC_TEST='test';          SRC_RES='resources';          ASSET_DIR='assets' ;;
    *) die "unknown --layout '$LAYOUT' (maven, flat)" ;;
esac

if (( RELEASE <= 8 )); then KOTLIN_JVM_TARGET='1.8'; else KOTLIN_JVM_TARGET=$RELEASE; fi

[[ $WEB != none && $GDX == no && $DEMO == yes && $KOTLIN == no ]] && true  # supported: plain GWT/TeaVM demo

if [[ $MODE == retrofit ]]; then
    TARGET=$(cd "$INTO" && pwd)
else
    TARGET="$DIR/$NAME"
    [[ -e $TARGET ]] && die "$TARGET already exists (use --into to retrofit it)"
fi

IFS=',' read -ra MODULE_LIST <<< "$MODULES"
for i in "${!MODULE_LIST[@]}"; do
    MODULE_LIST[$i]=$(echo "${MODULE_LIST[$i]}" | tr -d ' ')
done
[[ ${#MODULE_LIST[@]} -gt 0 ]] || die "at least one module is required"

PRIMARY=${MODULE_LIST[0]}
GROUP_PATH=${GROUP//./\/}
PKG_PATH=${PKG//./\/}
PKG_LEAF=${PKG##*.}
PKG_PARENT=${PKG%.*}
PKG_PARENT_PATH=${PKG_PARENT//./\/}
GWT_MODULE_NAME=$(camel "$NAME")

echo
echo "${MODE^} $TARGET"
echo "  group=$GROUP package=$PKG version=$VERSION"
echo "  modules=${MODULE_LIST[*]} layout=$LAYOUT license=$LICENSE_NAME"
echo "  java=$JAVA_VERSION release=$RELEASE kotlin=$KOTLIN gdx=$GDX web=$WEB demo=$DEMO pages=$PAGES"
[[ $DRY_RUN == yes ]] && echo "  (dry run — nothing will be written)"
echo

mkdirp "$TARGET/.github/workflows"

# ---------------------------------------------------------------- settings.gradle
DEMO_INCLUDES=()
if [[ $DEMO == yes ]]; then
    DEMO_INCLUDES+=("$DEMO_NAME:core" "$DEMO_NAME:desktop")
    [[ $WEB == gwt ]]   && DEMO_INCLUDES+=("$DEMO_NAME:html")
    [[ $WEB == teavm ]] && DEMO_INCLUDES+=("$DEMO_NAME:teavm")
fi

if [[ $MODE == retrofit && -f $TARGET/settings.gradle ]]; then
    # merge: keep rootProject.name and existing includes, add only what's missing
    if [[ $DRY_RUN == yes ]]; then
        for inc in "${MODULE_LIST[@]}" "${DEMO_INCLUDES[@]}"; do
            grep -q "include '$inc'" "$TARGET/settings.gradle" || echo "  would add to settings.gradle: include '$inc'"
        done
    else
        if [[ $WEB == teavm ]] && ! grep -q 'pluginManagement' "$TARGET/settings.gradle"; then
            CONFLICTS+=("settings.gradle has no pluginManagement block.
    Add this as the FIRST block in the file — TeaVM's plugin marker is on Maven
    Central and the Gradle Plugin Portal alone cannot resolve it:
        pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }")
        fi
        added=no
        for inc in "${MODULE_LIST[@]}" "${DEMO_INCLUDES[@]}"; do
            if ! grep -q "include '$inc'" "$TARGET/settings.gradle"; then
                [[ $added == no ]] && cp "$TARGET/settings.gradle" "$TARGET/settings.gradle.bak" && BACKED_UP+=("$TARGET/settings.gradle") && added=yes
                echo "include '$inc'" >> "$TARGET/settings.gradle"
            fi
        done
    fi
else
    {
        if [[ $WEB == teavm ]]; then
            # gdx-teavm (and TeaVM) publish their plugin markers to Maven
            # Central; the Gradle Plugin Portal alone cannot resolve them.
            echo "pluginManagement {"
            echo "    repositories {"
            echo "        mavenCentral()"
            echo "        gradlePluginPortal()"
            echo "    }"
            echo "}"
            echo
        fi
        echo "rootProject.name = '${NAME}-parent'"
        echo
        for m in "${MODULE_LIST[@]}"; do echo "include '$m'"; done
        [[ ${#DEMO_INCLUDES[@]} -gt 0 ]] && echo
        for inc in "${DEMO_INCLUDES[@]}"; do echo "include '$inc'"; done
    } | emit "$TARGET/settings.gradle"
fi

# ---------------------------------------------------------------- gradle.properties
# The properties this build needs, as bare key=value pairs.
gradle_props() {
    echo "group=$GROUP"
    echo "version=$VERSION"
    [[ $GDX == yes ]]    && echo "gdxVersion=$GDX_VERSION"
    [[ $WEB == gwt ]]    && echo "gwtVersion=$GWT_VERSION"
    [[ $WEB == teavm ]]  && echo "teavmVersion=$TEAVM_VERSION"
    [[ $KOTLIN == yes ]] && echo "kotlinVersion=$KOTLIN_VERSION"
    echo "org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8"
    echo "org.gradle.daemon=true"
    echo "org.gradle.configureondemand=false"
    return 0
}

if [[ $MODE == retrofit && -f $TARGET/gradle.properties ]]; then
    # An existing project's properties are authoritative — only add what is
    # missing, so things like lwjglVersion or a newer gdxVersion survive.
    MISSING=()
    while IFS= read -r line; do
        key=${line%%=*}
        grep -q "^[[:space:]]*${key}[[:space:]]*=" "$TARGET/gradle.properties" || MISSING+=("$line")
    done < <(gradle_props)
    if [[ ${#MISSING[@]} -gt 0 ]]; then
        if [[ $DRY_RUN == yes ]]; then
            for line in "${MISSING[@]}"; do echo "  would add to gradle.properties: $line"; done
        else
            cp "$TARGET/gradle.properties" "$TARGET/gradle.properties.bak"
            BACKED_UP+=("$TARGET/gradle.properties")
            {
                echo
                echo "# added by scaffold-project.sh"
                for line in "${MISSING[@]}"; do echo "$line"; done
            } >> "$TARGET/gradle.properties"
        fi
    fi
else
    {
        echo "group=$GROUP"
        echo "version=$VERSION"
        echo
        [[ $GDX == yes ]]    && echo "gdxVersion=$GDX_VERSION"
        [[ $WEB == gwt ]]    && echo "gwtVersion=$GWT_VERSION"
        [[ $WEB == teavm ]]  && echo "teavmVersion=$TEAVM_VERSION"
        [[ $KOTLIN == yes ]] && echo "kotlinVersion=$KOTLIN_VERSION"
        echo
        echo "org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8"
        echo "org.gradle.daemon=true"
        echo "org.gradle.configureondemand=false"
    } | emit "$TARGET/gradle.properties"
fi

# ---------------------------------------------------------------- root build.gradle
NMCP_DEPS='' CONFIGURE_LIST=''
for m in "${MODULE_LIST[@]}"; do
    NMCP_DEPS+="    nmcpAggregation project(':$m')"$'\n'
    CONFIGURE_LIST+="project(':$m'), "
done
CONFIGURE_LIST=${CONFIGURE_LIST%, }

KOTLIN_ROOT_PLUGIN='' KOTLIN_APPLY='' KOTLIN_SS_MAIN='' KOTLIN_SS_TEST='' KOTLIN_COMPILE=''
if [[ $KOTLIN == yes ]]; then
    KOTLIN_ROOT_PLUGIN="
    id 'org.jetbrains.kotlin.jvm' version '$KOTLIN_VERSION' apply false"
    KOTLIN_APPLY="
    apply plugin: 'org.jetbrains.kotlin.jvm'"
    KOTLIN_SS_MAIN="
            kotlin {
                srcDirs = ['$SRC_MAIN']
            }"
    KOTLIN_SS_TEST="
            kotlin {
                srcDirs = ['$SRC_TEST']
            }"
    KOTLIN_COMPILE="
    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
        compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget('$KOTLIN_JVM_TARGET')
    }
"
fi

# The GWT compiler reads .java sources, so published jars must carry them.
JAR_SOURCES=''
if [[ $WEB == gwt ]]; then
    JAR_SOURCES="
    // GWT compiles from source: ship .java (and .gwt.xml) inside the runtime jar
    // so downstream GWT projects can inherit the module without a -sources dep.
    jar {
        from sourceSets.main.allJava
    }
"
fi

cat <<EOF | emit "$TARGET/build.gradle"
plugins {
    id 'com.gradleup.nmcp.aggregation' version '1.6.1'${KOTLIN_ROOT_PLUGIN}
}

allprojects {
    group = '$GROUP'
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}

nmcpAggregation {
    centralPortal {
        username = providers.gradleProperty('centralPortalUsername')
        password = providers.gradleProperty('centralPortalPassword')
        publishingType = 'AUTOMATIC'
    }
}

dependencies {
$NMCP_DEPS}

configure([$CONFIGURE_LIST]) {
    apply plugin: 'java-library'${KOTLIN_APPLY}
    apply plugin: 'maven-publish'
    apply plugin: 'signing'
    apply plugin: 'com.gradleup.nmcp'

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of($JAVA_VERSION)
        }
        withSourcesJar()
        withJavadocJar()
    }

    compileJava {
        options.release = $RELEASE
        options.encoding = 'UTF-8'
    }
${KOTLIN_COMPILE}
    sourceSets {
        main {
            java {
                srcDirs = ['$SRC_MAIN']
            }${KOTLIN_SS_MAIN}
            resources {
                srcDirs = ['$SRC_RES']
            }
        }
        test {
            java {
                srcDirs = ['$SRC_TEST']
            }${KOTLIN_SS_TEST}
        }
    }

    javadoc {
        options.encoding = 'UTF-8'
        options.addStringOption('Xdoclint:none', '-quiet')
        failOnError = false
    }

    // shared srcDirs mean the same file can reach a Jar task more than once
    tasks.withType(Jar).configureEach {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
$JAR_SOURCES
    publishing {
        publications {
            mavenJava(MavenPublication) {
                from components.java
                pom {
                    name = project.name
                    description = project.description ?: '$DESC'
                    url = 'https://github.com/$GH_OWNER/$GH_REPO'
                    licenses {
                        license {
                            name = '$LICENSE_NAME'
                            url = '$LICENSE_URL'
                        }
                    }
                    developers {
                        developer {
                            id = '$DEV_ID'
                            name = '$DEV_NAME'
                        }
                    }
                    scm {
                        connection = 'scm:git:https://github.com/$GH_OWNER/$GH_REPO.git'
                        developerConnection = 'scm:git:ssh://git@github.com/$GH_OWNER/$GH_REPO.git'
                        url = 'https://github.com/$GH_OWNER/$GH_REPO'
                    }
                }
            }
        }
        repositories {
            maven {
                name = 'LocalBuild'
                url = rootProject.layout.buildDirectory.dir('repo')
            }
        }
    }

    signing {
        def signingKey = findProperty('signingInMemoryKey')
        def signingPassword = findProperty('signingInMemoryKeyPassword')
        if (signingKey != null) {
            useInMemoryPgpKeys(signingKey.toString(), signingPassword?.toString() ?: '')
            sign publishing.publications.mavenJava
        }
    }
}
EOF

# ---------------------------------------------------------------- library modules
for m in "${MODULE_LIST[@]}"; do
    mkdirp "$TARGET/$m/$SRC_MAIN" "$TARGET/$m/$SRC_TEST" "$TARGET/$m/$SRC_RES"
    # git does not track empty directories
    if [[ $DRY_RUN == no ]]; then
        for d in "$SRC_MAIN" "$SRC_TEST"; do
            [[ -n $(ls -A "$TARGET/$m/$d" 2>/dev/null) ]] || touch "$TARGET/$m/$d/.gitkeep"
        done
    fi

    GDX_DEP=''
    [[ $GDX == yes ]] && GDX_DEP="    api \"com.badlogicgames.gdx:gdx:\$gdxVersion\"
"
    cat <<EOF | emit_keep "$TARGET/$m/build.gradle"
description = '$m module of $NAME.'

dependencies {
${GDX_DEP}    testImplementation platform('org.junit:junit-bom:5.10.2')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
    testLogging {
        events 'passed', 'failed', 'skipped'
        exceptionFormat 'full'
    }
}
EOF

    # A retrofitted module may already declare its own publication; the root
    # build.gradle now owns 'mavenJava', and Gradle refuses two components in
    # one publication. Flag it rather than rewriting the user's build script.
    if [[ $MODE == retrofit && -f $TARGET/$m/build.gradle ]]; then
        if grep -qE "maven-publish|publishing[[:space:]]*\{" "$TARGET/$m/build.gradle"; then
            CONFLICTS+=("$m/build.gradle declares its own maven-publish/publishing block.
    Remove it — the root build.gradle now applies maven-publish and defines the
    'mavenJava' publication for this module. Leaving both fails the build with
    \"Maven publication 'mavenJava' cannot include multiple components\".")
        fi
    fi

    # GWT module descriptor for the library, mirroring gbullet's layout:
    # the .gwt.xml sits in the PARENT package and opens the leaf package.
    if [[ $WEB == gwt ]]; then
        GDX_INHERIT=''
        [[ $GDX == yes ]] && GDX_INHERIT="
  <inherits name='com.badlogic.gdx'/>"
        cat <<EOF | emit "$TARGET/$m/$SRC_RES/$PKG_PARENT_PATH/$GWT_MODULE_NAME.gwt.xml"
<module>
  <inherits name='com.google.gwt.core.Core'/>$GDX_INHERIT

  <!-- Emulation overrides for JDK classes GWT does not ship go here:
       create $SRC_RES/$PKG_PARENT_PATH/super/ and uncomment.
  <super-source path='super'/> -->

  <source path='$PKG_LEAF'/>
</module>
EOF
    fi
done

# ---------------------------------------------------------------- demo modules
if [[ $DEMO == yes ]]; then
    DEMO_PKG="$PKG.$DEMO_NAME"
    DEMO_PKG_PATH="$PKG_PATH/$DEMO_NAME"
    LIB_DEP="project(':$PRIMARY')"

    # Demo modules are not published, so the root `configure([...])` block does
    # not touch them — each applies its own plugins and toolchain.
    DEMO_APPLY="apply plugin: 'java-library'"
    [[ $KOTLIN == yes ]] && DEMO_APPLY="$DEMO_APPLY
apply plugin: 'org.jetbrains.kotlin.jvm'"

    TEAVM_KOTLIN_PLUGIN=''
    [[ $KOTLIN == yes ]] && TEAVM_KOTLIN_PLUGIN="
    id 'org.jetbrains.kotlin.jvm'"

    DEMO_JAVA_CFG="java {
    toolchain {
        languageVersion = JavaLanguageVersion.of($JAVA_VERSION)
    }
}

compileJava {
    options.release = $RELEASE
    options.encoding = 'UTF-8'
}"

    # ---- demo:core
    if [[ $GDX == yes ]]; then
        cat <<EOF | emit "$TARGET/$DEMO_NAME/core/build.gradle"
$DEMO_APPLY

description = 'Shared demo logic for $NAME (renders on desktop and in the browser).'

$DEMO_JAVA_CFG

dependencies {
    api $LIB_DEP
    api "com.badlogicgames.gdx:gdx:\$gdxVersion"
}
EOF
        cat <<EOF | emit_new "$TARGET/$DEMO_NAME/core/src/main/java/$DEMO_PKG_PATH/DemoApp.java"
package $DEMO_PKG;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

/** Shared demo, run by every launcher. Keep this GWT-safe: no reflection,
 *  no threads, no java.io, no Pixmap-from-binary-data. */
public class DemoApp extends ApplicationAdapter {

	@Override
	public void create () {
		Gdx.app.log("$NAME", "demo started");
	}

	@Override
	public void render () {
		Gdx.gl.glClearColor(0.09f, 0.09f, 0.11f, 1f);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
	}
}
EOF
    else
        cat <<EOF | emit "$TARGET/$DEMO_NAME/core/build.gradle"
$DEMO_APPLY

description = 'Shared demo logic for $NAME.'

$DEMO_JAVA_CFG

dependencies {
    api $LIB_DEP
}
EOF
        cat <<EOF | emit_new "$TARGET/$DEMO_NAME/core/src/main/java/$DEMO_PKG_PATH/DemoApp.java"
package $DEMO_PKG;

/** Shared demo entry point, called by every launcher. */
public class DemoApp {

	public static String run () {
		return "$NAME demo";
	}
}
EOF
    fi

    if [[ $WEB == gwt ]]; then
        cat <<EOF | emit "$TARGET/$DEMO_NAME/core/src/main/resources/$PKG_PATH/Demo.gwt.xml"
<module>
  <inherits name='$PKG_PARENT.$GWT_MODULE_NAME'/>

  <source path='$DEMO_NAME'/>
</module>
EOF
    fi

    # ---- demo:desktop
    if [[ $GDX == yes ]]; then
        cat <<EOF | emit "$TARGET/$DEMO_NAME/desktop/build.gradle"
$DEMO_APPLY
apply plugin: 'application'

description = 'Desktop launcher for the $NAME demo.'

$DEMO_JAVA_CFG

application {
    mainClass = '$DEMO_PKG.desktop.DesktopLauncher'
}

dependencies {
    implementation project(':$DEMO_NAME:core')
    implementation "com.badlogicgames.gdx:gdx-backend-lwjgl3:\$gdxVersion"
    runtimeOnly "com.badlogicgames.gdx:gdx-platform:\$gdxVersion:natives-desktop"
}

run {
    workingDir = rootProject.file('$DEMO_NAME/assets')
    setIgnoreExitValue(true)
    // LWJGL3 requires the GLFW event loop on the first thread on macOS.
    if (System.getProperty('os.name').toLowerCase().contains('mac')) {
        jvmArgs += '-XstartOnFirstThread'
    }
}
EOF
        cat <<EOF | emit_new "$TARGET/$DEMO_NAME/desktop/src/main/java/$DEMO_PKG_PATH/desktop/DesktopLauncher.java"
package $DEMO_PKG.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import $DEMO_PKG.DemoApp;

public class DesktopLauncher {

	public static void main (String[] args) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("$NAME demo");
		config.setWindowedMode(1280, 720);
		new Lwjgl3Application(new DemoApp(), config);
	}
}
EOF
    else
        cat <<EOF | emit "$TARGET/$DEMO_NAME/desktop/build.gradle"
$DEMO_APPLY
apply plugin: 'application'

description = 'Desktop launcher for the $NAME demo.'

$DEMO_JAVA_CFG

application {
    mainClass = '$DEMO_PKG.desktop.DesktopLauncher'
}

dependencies {
    implementation project(':$DEMO_NAME:core')
}
EOF
        cat <<EOF | emit_new "$TARGET/$DEMO_NAME/desktop/src/main/java/$DEMO_PKG_PATH/desktop/DesktopLauncher.java"
package $DEMO_PKG.desktop;

import $DEMO_PKG.DemoApp;

public class DesktopLauncher {

	public static void main (String[] args) {
		System.out.println(DemoApp.run());
	}
}
EOF
    fi

    # ---- demo:html (GWT)
    if [[ $WEB == gwt ]]; then
        GWT_SRC_PROJECTS="':$PRIMARY', ':$DEMO_NAME:core'"
        for m in "${MODULE_LIST[@]:1}"; do GWT_SRC_PROJECTS="$GWT_SRC_PROJECTS, ':$m'"; done

        if [[ $GDX == yes ]]; then
            GWT_DEPS="    implementation project(':$DEMO_NAME:core')
    implementation \"com.badlogicgames.gdx:gdx-backend-gwt:\$gdxVersion\"
    implementation \"com.badlogicgames.gdx:gdx:\$gdxVersion:sources\"
    implementation \"com.badlogicgames.gdx:gdx-backend-gwt:\$gdxVersion:sources\""
        else
            GWT_DEPS="    implementation project(':$DEMO_NAME:core')
    implementation \"org.gwtproject:gwt-user:\$gwtVersion\"
    implementation \"org.gwtproject:gwt-dev:\$gwtVersion\""
        fi

        WAR_FROM=''
        CLEAN_WAR=''
        if [[ $GDX == yes ]]; then
            WAR_FROM="
    // the gdx GWT backend's preloader writes the asset bundle into war/
    from file('war')"
            CLEAN_WAR="
clean.doLast {
    delete file('war')
}"
        fi

        cat <<EOF | emit "$TARGET/$DEMO_NAME/html/build.gradle"
buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath 'org.docstr:gwt-gradle-plugin:$GWT_PLUGIN'
    }
}

apply plugin: 'gwt-compiler'

description = 'GWT/HTML launcher for the $NAME demo.'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of($JAVA_VERSION)
    }
}

dependencies {
$GWT_DEPS
}

gwt {
    gwtVersion = project.property('gwtVersion')
    maxHeapSize = '2G'
    minHeapSize = '1G'

    src = files(file('src/main/java'), file('src/main/resources'))
    modules '$DEMO_PKG.GdxDefinition'
    devModules '$DEMO_PKG.GdxDefinitionSuperdev'

    compiler.strict = true
    compiler.disableCastChecking = true
}

// The GWT compiler works from .java sources, so hand it the source (and
// .gwt.xml resource) directories of the upstream project modules.
task addSource {
    doLast {
        [$GWT_SRC_PROJECTS].each { name ->
            def p = project(name)
            sourceSets.main.compileClasspath += files(p.sourceSets.main.allJava.srcDirs)
            sourceSets.main.compileClasspath += files(p.sourceSets.main.resources.srcDirs)
        }
    }
}
tasks.compileGwt.dependsOn(addSource)
tasks.draftCompileGwt.dependsOn(addSource)
tasks.checkGwt.dependsOn(addSource)

// Assembles the deployable site: compiled GWT output plus the static shell.
task dist(type: Copy, dependsOn: compileGwt) {
    from file('src/main/webapp')
    from(file("\$buildDir/gwt/out")) {
        exclude 'WEB-INF'
    }$WAR_FROM
    into file("\$buildDir/dist")
}
$CLEAN_WAR
EOF

        if [[ $GDX == yes ]]; then
            cat <<EOF | emit "$TARGET/$DEMO_NAME/html/src/main/resources/$DEMO_PKG_PATH/GdxDefinition.gwt.xml"
<module rename-to="html">
  <inherits name='com.badlogic.gdx.backends.gdx_backends_gwt'/>
  <inherits name='$PKG.Demo'/>

  <entry-point class='$DEMO_PKG.html.HtmlLauncher'/>

  <source path='html'/>

  <set-configuration-property name="gdx.assetpath" value="../assets"/>
  <set-configuration-property name="xsiframe.failIfScriptTag" value="FALSE"/>
</module>
EOF
            cat <<EOF | emit_new "$TARGET/$DEMO_NAME/html/src/main/java/$DEMO_PKG_PATH/html/HtmlLauncher.java"
package $DEMO_PKG.html;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;

import $DEMO_PKG.DemoApp;

public class HtmlLauncher extends GwtApplication {

	@Override
	public GwtApplicationConfiguration getConfig () {
		GwtApplicationConfiguration config = new GwtApplicationConfiguration(true);
		config.padVertical = 0;
		config.padHorizontal = 0;
		return config;
	}

	@Override
	public ApplicationListener createApplicationListener () {
		return new DemoApp();
	}
}
EOF
        else
            cat <<EOF | emit "$TARGET/$DEMO_NAME/html/src/main/resources/$DEMO_PKG_PATH/GdxDefinition.gwt.xml"
<module rename-to="html">
  <inherits name='com.google.gwt.user.User'/>
  <inherits name='$PKG.Demo'/>

  <entry-point class='$DEMO_PKG.html.HtmlLauncher'/>

  <source path='html'/>
</module>
EOF
            cat <<EOF | emit_new "$TARGET/$DEMO_NAME/html/src/main/java/$DEMO_PKG_PATH/html/HtmlLauncher.java"
package $DEMO_PKG.html;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.dom.client.Document;

import $DEMO_PKG.DemoApp;

public class HtmlLauncher implements EntryPoint {

	@Override
	public void onModuleLoad () {
		Document.get().getBody().setInnerText(DemoApp.run());
	}
}
EOF
        fi

        cat <<EOF | emit "$TARGET/$DEMO_NAME/html/src/main/resources/$DEMO_PKG_PATH/GdxDefinitionSuperdev.gwt.xml"
<module rename-to="html">
  <inherits name='$DEMO_PKG.GdxDefinition'/>

  <collapse-all-properties/>

  <add-linker name="xsiframe"/>
  <set-configuration-property name="devModeRedirectEnabled" value="true"/>
</module>
EOF

        cat <<EOF | emit_new "$TARGET/$DEMO_NAME/html/src/main/webapp/index.html"
<!doctype html>
<html>
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>$NAME demo</title>
	<style>
		html, body { margin: 0; padding: 0; height: 100%; background: #17171b; color: #e8e8ea;
			font: 14px/1.5 system-ui, sans-serif; }
		#embed-html { width: 100%; height: 100%; }
	</style>
</head>
<body>
	<div id="embed-html"></div>
	<script src="html/html.nocache.js"></script>
</body>
</html>
EOF
    fi

    # ---- demo:teavm
    if [[ $WEB == teavm ]]; then
        if [[ $GDX == yes ]]; then
            cat <<EOF | emit "$TARGET/$DEMO_NAME/teavm/build.gradle"
// gdx-teavm runs libgdx in the browser via TeaVM. Unlike GWT it consumes
// bytecode, so Kotlin sources are fine here.
//
// VERIFY: the gdx-teavm DSL moves between releases — check the task name and
// extension against https://github.com/xpenatan/gdx-teavm for \$teavmVersion.
plugins {
    id 'java-library'$TEAVM_KOTLIN_PLUGIN
    id 'com.github.xpenatan.gdx-teavm' version "\$teavmVersion"
}

description = 'TeaVM/web launcher for the $NAME demo.'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of($JAVA_VERSION)
    }
}

dependencies {
    implementation project(':$DEMO_NAME:core')
    implementation "com.github.xpenatan.gdx-teavm:backend-teavm:\$teavmVersion"
}

teavm {
    mainClass = '$DEMO_PKG.teavm.TeaVMLauncher'
}
EOF
            cat <<EOF | emit_new "$TARGET/$DEMO_NAME/teavm/src/main/java/$DEMO_PKG_PATH/teavm/TeaVMLauncher.java"
package $DEMO_PKG.teavm;

import com.github.xpenatan.gdx.backends.teavm.TeaApplication;
import com.github.xpenatan.gdx.backends.teavm.TeaApplicationConfiguration;

import $DEMO_PKG.DemoApp;

public class TeaVMLauncher {

	public static void main (String[] args) {
		TeaApplicationConfiguration config = new TeaApplicationConfiguration("canvas");
		config.width = 0;   // 0 = fill the window
		config.height = 0;
		new TeaApplication(new DemoApp(), config);
	}
}
EOF
        else
            cat <<EOF | emit "$TARGET/$DEMO_NAME/teavm/build.gradle"
plugins {
    id 'java-library'$TEAVM_KOTLIN_PLUGIN
    id 'org.teavm' version "\$teavmVersion"
}

description = 'TeaVM/web launcher for the $NAME demo.'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of($JAVA_VERSION)
    }
}

dependencies {
    implementation project(':$DEMO_NAME:core')
}

teavm {
    js {
        addedToWebApp = true
        targetFileName = 'teavm.js'
        mainClass = '$DEMO_PKG.teavm.TeaVMLauncher'
    }
}

// Assembles the deployable site: generated JS plus the static shell.
task dist(type: Copy, dependsOn: tasks.named('generateJavaScript')) {
    from file('src/main/webapp')
    from file("\$buildDir/generated/teavm/js")
    into file("\$buildDir/dist")
}
EOF
            cat <<EOF | emit_new "$TARGET/$DEMO_NAME/teavm/src/main/java/$DEMO_PKG_PATH/teavm/TeaVMLauncher.java"
package $DEMO_PKG.teavm;

import $DEMO_PKG.DemoApp;

public class TeaVMLauncher {

	public static void main (String[] args) {
		System.out.println(DemoApp.run());
	}
}
EOF
        fi

        if [[ $GDX == yes ]]; then
            TEAVM_BODY='	<canvas id="canvas"></canvas>'
        else
            TEAVM_BODY='	<div id="out"></div>'
        fi
        cat <<EOF | emit_new "$TARGET/$DEMO_NAME/teavm/src/main/webapp/index.html"
<!doctype html>
<html>
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>$NAME demo</title>
	<style>
		html, body { margin: 0; padding: 0; height: 100%; background: #17171b; color: #e8e8ea;
			font: 14px/1.5 system-ui, sans-serif; }
		canvas { display: block; width: 100%; height: 100%; }
		#out { padding: 2rem; white-space: pre-wrap; }
	</style>
</head>
<body>
$TEAVM_BODY
	<script type="text/javascript" src="teavm.js"></script>
	<script type="text/javascript">main();</script>
</body>
</html>
EOF
    fi

    # ---- shared demo assets (gdx preloader reads demo/assets)
    if [[ $GDX == yes ]]; then
        mkdirp "$TARGET/$DEMO_NAME/assets"
        [[ $DRY_RUN == yes ]] || touch "$TARGET/$DEMO_NAME/assets/.gitkeep"
    fi
fi

# ---------------------------------------------------------------- publish workflow
cat <<'YML' | emit "$TARGET/.github/workflows/publish-maven-central.yml"
# Publishes the library modules to Maven Central (Central Portal).
# Triggered by publishing a GitHub release, or manually from the Actions tab.
#
# Required repository secrets (Settings -> Secrets and variables -> Actions):
#   CENTRAL_PORTAL_USERNAME  token username from central.sonatype.com/account
#   CENTRAL_PORTAL_PASSWORD  token password from central.sonatype.com/account
#   SIGNING_KEY              ASCII-armored PGP private key block
#   SIGNING_KEY_PASSWORD     passphrase of that key
name: Publish to Maven Central

on:
  release:
    types: [published]
  workflow_dispatch:

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - uses: gradle/actions/setup-gradle@v4

      - name: Run tests
        run: ./gradlew test

      - name: Publish to Central Portal
        # ORG_GRADLE_PROJECT_* env vars surface as Gradle project properties,
        # matching the names the build script reads.
        env:
          ORG_GRADLE_PROJECT_centralPortalUsername: ${{ secrets.CENTRAL_PORTAL_USERNAME }}
          ORG_GRADLE_PROJECT_centralPortalPassword: ${{ secrets.CENTRAL_PORTAL_PASSWORD }}
          ORG_GRADLE_PROJECT_signingInMemoryKey: ${{ secrets.SIGNING_KEY }}
          ORG_GRADLE_PROJECT_signingInMemoryKeyPassword: ${{ secrets.SIGNING_KEY_PASSWORD }}
        run: ./gradlew publishAggregationToCentralPortal
YML

# ---------------------------------------------------------------- pages workflow
if [[ $PAGES == yes ]]; then
    case $WEB in
        gwt)
            BUILD_TASK=":$DEMO_NAME:html:dist"
            ARTIFACT_PATH="$DEMO_NAME/html/build/dist"
            WEB_LABEL="GWT"
            ;;
        teavm)
            if [[ $GDX == yes ]]; then
                BUILD_TASK=":$DEMO_NAME:teavm:buildJavaScript"
                ARTIFACT_PATH="$DEMO_NAME/teavm/build/dist/webapp"
            else
                BUILD_TASK=":$DEMO_NAME:teavm:dist"
                ARTIFACT_PATH="$DEMO_NAME/teavm/build/dist"
            fi
            WEB_LABEL="TeaVM"
            ;;
    esac

    cat <<EOF | emit "$TARGET/.github/workflows/deploy-demo.yml"
# Builds the $WEB_LABEL demo and publishes it to GitHub Pages.
# Runs on every push to main, so the live demo always tracks the repository.
#
# One-time setup: Settings -> Pages -> Source: "GitHub Actions".
name: Deploy demo to GitHub Pages

on:
  push:
    branches: [main]
  # allow manual redeploys from the Actions tab
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

# never run two deploys at once; let an in-flight deploy finish
concurrency:
  group: pages
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '$JAVA_VERSION'

      - uses: gradle/actions/setup-gradle@v4

      - name: Run tests
        run: ./gradlew test

      - name: Build $WEB_LABEL demo
        run: ./gradlew $BUILD_TASK

      - uses: actions/upload-pages-artifact@v3
        with:
          path: $ARTIFACT_PATH

  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: \${{ steps.deployment.outputs.page_url }}
    steps:
      - id: deployment
        uses: actions/deploy-pages@v4
EOF
fi

# ---------------------------------------------------------------- PUBLISHING.md
cat <<MD | emit "$TARGET/PUBLISHING.md"
# Publishing to Maven Central

One-time setup for signing and deploying releases through the
[Central Portal](https://central.sonatype.com). The build reads four Gradle
properties; everything below exists to produce them:

| property | what it is |
|---|---|
| \`centralPortalUsername\` | token username from central.sonatype.com |
| \`centralPortalPassword\` | token password from central.sonatype.com |
| \`signingInMemoryKey\` | ASCII-armored PGP private key block |
| \`signingInMemoryKeyPassword\` | that key's passphrase |

## 1. GPG key

Generate (skip if you already have one — check \`gpg --list-secret-keys\`):

\`\`\`bash
gpg --gen-key
\`\`\`

Find the key id (the part after \`ed25519/\` or \`rsa3072/\`):

\`\`\`bash
gpg --list-secret-keys --keyid-format long
\`\`\`

Publish the **public** half to a keyserver — Central verifies signatures
against it:

\`\`\`bash
gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>
\`\`\`

Export the **private** key (goes into the properties/secrets, never the repo):

\`\`\`bash
gpg --armor --export-secret-keys <KEYID> > private-key.asc
\`\`\`

## 2. Local credentials (~/.gradle/gradle.properties)

Properties files need the key on one line with \`\\n\` escapes:

\`\`\`bash
python3 -c "print('signingInMemoryKey=' + open('private-key.asc').read().replace(chr(10), '\\\\n'))"
\`\`\`

Then \`~/.gradle/gradle.properties\` (create if missing) contains:

\`\`\`properties
centralPortalUsername=<token username>
centralPortalPassword=<token password>
signingInMemoryKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\\n\\nlIYEak...
signingInMemoryKeyPassword=<passphrase>
\`\`\`

The portal token comes from central.sonatype.com → account icon →
*View Account* → *Generate User Token*.
Afterward, delete \`private-key.asc\` from disk.

## 3. GitHub Actions secrets

For the \`publish-maven-central.yml\` workflow, add repository secrets
(Settings → Secrets and variables → Actions):

| secret | content |
|---|---|
| \`CENTRAL_PORTAL_USERNAME\` | token username |
| \`CENTRAL_PORTAL_PASSWORD\` | token password |
| \`SIGNING_KEY\` | the armored private key, pasted as-is (multi-line is fine) |
| \`SIGNING_KEY_PASSWORD\` | passphrase |

## 4. Releasing

- CI: publish a GitHub release → the workflow signs, uploads and releases.
- Local: \`./gradlew publishAggregationToCentralPortal\`

Dry-run the signing locally without uploading:

\`\`\`bash
./gradlew publishAllPublicationsToLocalBuildRepository
ls build/repo/$GROUP_PATH/$PRIMARY/*/
\`\`\`

Expect a \`.asc\` next to every artifact.
MD

# ---------------------------------------------------------------- misc files
if [[ $MODE == scaffold || ! -f $TARGET/.gitignore ]]; then
    cat <<'EOF' | emit "$TARGET/.gitignore"
.gradle/
build/
.idea/
*.iml
out/
.DS_Store
private-key.asc

# GWT
war/
gwt-unitCache/
.apt_generated/
EOF
fi

if [[ $MODE == scaffold ]]; then
    {
        echo "# $NAME"
        echo
        echo "$DESC"
        echo
        echo '## Modules'
        echo
        for m in "${MODULE_LIST[@]}"; do echo "- \`$m\`"; done
        if [[ $DEMO == yes ]]; then
            echo
            echo '## Demo'
            echo
            echo "- \`$DEMO_NAME:core\` — shared demo logic"
            echo "- \`$DEMO_NAME:desktop\` — \`./gradlew :$DEMO_NAME:desktop:run\`"
            case $WEB in
                gwt)   echo "- \`$DEMO_NAME:html\` — \`./gradlew :$DEMO_NAME:html:dist\`" ;;
                teavm) echo "- \`$DEMO_NAME:teavm\` — TeaVM/web build" ;;
            esac
        fi
        echo
        echo '## Publishing'
        echo
        echo 'See [PUBLISHING.md](PUBLISHING.md).'
    } | emit "$TARGET/README.md"
fi

# ---------------------------------------------------------------- gradle wrapper
if [[ $DRY_RUN == no ]]; then
    if [[ ! -f $TARGET/gradlew ]]; then
        [[ -z $WRAPPER_FROM && -f $SCRIPT_DIR/gradlew ]] && WRAPPER_FROM=$SCRIPT_DIR
        if [[ -n $WRAPPER_FROM && -f $WRAPPER_FROM/gradlew ]]; then
            cp "$WRAPPER_FROM/gradlew" "$TARGET/gradlew"
            cp "$WRAPPER_FROM/gradlew.bat" "$TARGET/gradlew.bat" 2>/dev/null || true
            mkdir -p "$TARGET/gradle/wrapper"
            cp "$WRAPPER_FROM"/gradle/wrapper/gradle-wrapper.jar \
               "$WRAPPER_FROM"/gradle/wrapper/gradle-wrapper.properties "$TARGET/gradle/wrapper/" 2>/dev/null || true
            chmod +x "$TARGET/gradlew"
        elif command -v gradle >/dev/null 2>&1; then
            (cd "$TARGET" && gradle wrapper -q)
        else
            echo "note: no Gradle wrapper source found — run 'gradle wrapper' in $TARGET yourself"
        fi
    fi

    if [[ $GIT_INIT == yes && $MODE == scaffold ]]; then
        git -C "$TARGET" init -q
    fi
fi

# ---------------------------------------------------------------- summary
echo "Done: $TARGET"
if [[ ${#BACKED_UP[@]} -gt 0 ]]; then
    echo
    echo "Backed up (originals kept as .bak):"
    for f in "${BACKED_UP[@]}"; do echo "  ${f#"$TARGET"/}"; done
fi
if [[ ${#SKIPPED[@]} -gt 0 ]]; then
    echo
    echo "Left alone (already existed):"
    for f in "${SKIPPED[@]}"; do echo "  ${f#"$TARGET"/}"; done
fi

if [[ ${#CONFLICTS[@]} -gt 0 ]]; then
    echo
    echo "ACTION REQUIRED:"
    for c in "${CONFLICTS[@]}"; do echo "  - $c"; done
fi

echo
echo "Next steps:"
i=1
echo "  $((i++)). cd $TARGET && ./gradlew build"
if [[ $DEMO == yes ]]; then
    case $WEB in
        gwt)   echo "  $((i++)). ./gradlew :$DEMO_NAME:html:dist   # then open $DEMO_NAME/html/build/dist/index.html" ;;
        teavm) echo "  $((i++)). build the TeaVM demo (see $DEMO_NAME/teavm/build.gradle)" ;;
    esac
fi
echo "  $((i++)). Follow PUBLISHING.md to set up Central Portal credentials + signing"
echo "  $((i++)). Dry-run: ./gradlew publishAllPublicationsToLocalBuildRepository"
if [[ $PAGES == yes ]]; then
    echo "  $((i++)). Enable Pages: repo Settings -> Pages -> Source: GitHub Actions"
fi
