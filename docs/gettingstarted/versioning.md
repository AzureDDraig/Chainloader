# Versioning

ChainLoader handles mods declaring version dependencies from different ecosystems. Fabric mods declare SemVer strings (often with operators like `^` and `~` in `fabric.mod.json`), while Forge mods declare Maven-style version ranges in `mods.toml`. This document specifies how ChainLoader parses, compares, and translates these version rules using its internal `Version` and `VersionRequirement` engine.

---

## 1. Version Parsing and Comparison

Versions are parsed using the static `UnifiedDependencyResolver.Version.parse(String)` method. This parser is designed to be lightweight and dependency-free so that it can run during the earliest stages of the modloader bootstrap.

### 1.1 Parsing Logic
1. **Cleaning**: Trims whitespace and strips any leading `v` or `V` characters (e.g. `v1.19.2` -> `1.19.2`).
2. **Prerelease Split**: Locates the first hyphen `-`. Everything before is parsed as the main version; everything after is treated as the prerelease qualifier (e.g., `1.19.2-beta.1` -> main: `1.19.2`, prerelease: `beta.1`).
3. **Digit Splitting**: The main portion is split by dots `.`. The first three parts are parsed as integers mapping to `major`, `minor`, and `patch`. If parts are missing, they default to `0` (e.g., `1.19` -> `major=1`, `minor=19`, `patch=0`).

### 1.2 Comparison Rules
The `Version` class implements `Comparable<Version>`. When comparing two versions, the following precedence rules apply:
1. Compare `major` version numbers.
2. Compare `minor` version numbers.
3. Compare `patch` version numbers.
4. Compare `prerelease` qualifiers lexicographically.
   - **Crucial Rule**: A release version (empty prerelease string) always takes precedence over a prerelease version (e.g. `1.19.2` > `1.19.2-beta.1`).

---

## 2. SemVer Range Constraints

SemVer range constraints are commonly found in Fabric mods. When `VersionRequirement` encounters standard space-separated or comma-separated tokens, it parses them using the following rules:

| Operator | Rule Definition | Example Constraint | Matching Versions |
| :--- | :--- | :--- | :--- |
| **Exact** | Compares exact digits or matches prefixes | `1.19.2` or `1.19` | `1.19.2` (exact); `1.19.0`, `1.19.4` (prefix) |
| `=` | Exact version match | `=1.19.2` | `1.19.2` only |
| `>` | Strictly greater than | `>1.19.2` | `1.19.3`, `1.20.0` |
| `>=` | Greater than or equal to | `>=1.19.2` | `1.19.2`, `1.19.4` |
| `<` | Strictly less than | `<1.19.2` | `1.19.1`, `1.18.0` |
| `<=` | Less than or equal to | `<=1.19.2` | `1.19.2`, `1.19.0` |
| `~` | Tilde constraint (restricts minor bump) | `~1.19.0` | `>=1.19.0` and `<1.20.0` |
| `^` | Caret constraint (restricts major bump) | `^1.19.2` | `>=1.19.2` and `<2.0.0` |
| `*` or empty | Matches any version | `*` or `""` | Any version |

*Note: For plain version tokens without operators, a prefix match is performed. For example, `1.19` matches `1.19.0` and `1.19.2`, but not `1.20.0`.*

---

## 3. Maven Boundary Constraints

Maven boundary constraints are commonly found in Forge `mods.toml` files. A Maven range is recognized by starting with bracket `[` or parenthesis `(`.

### 3.1 Range Syntax
* **Inclusive Boundary**: Defined using square brackets `[` and `]`.
* **Exclusive Boundary**: Defined using parentheses `(` and `)`.

### 3.2 Parsing Mechanics
The parser splits the string by comma `,` inside the boundaries:
* **Single Version Exact**: `[1.19]` matches exact version `1.19.0` only.
* **Double Boundary**:
  - `[1.19,1.20)` matches versions `>=1.19.0` and `<1.20.0`.
  - `(1.19,1.20]` matches versions `>1.19.0` and `<=1.20.0`.
* **Open Boundary**:
  - `[1.19,)` matches any version `>=1.19.0`.
  - `(,1.20]` matches any version `<=1.20.0`.

These parsing rules ensure that dependencies declared in legacy Forge formats map cleanly into the unified resolver without causing false mismatch errors.
