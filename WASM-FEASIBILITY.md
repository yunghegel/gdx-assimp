# Assimp-in-the-browser: feasibility

Investigation into running gdx-assimp's importer on the web, given that the
current implementation binds to native Assimp through LWJGL JNI.

**Date:** 2026-08-01 · **Status:** research only, nothing implemented

---

## 1. Why the obvious routes don't work

GWT and TeaVM both compile Java to JavaScript/wasm. Neither can execute JNI.
There is no emulation for `org.lwjgl.assimp` — it relies on direct
`ByteBuffer`s, `MemoryUtil`, `PointerBuffer` and native function-pointer
callbacks, none of which have a browser analogue.

Choosing TeaVM over GWT does not change this. TeaVM's advantage is that it
consumes bytecode (so Kotlin works) rather than Java source; it is equally
unable to call native code.

So the native import path cannot be ported. Anything that works in a browser
has to call a *separately compiled* Assimp.

## 2. The viable route

Assimp has an existing Emscripten build: [`assimpjs`](https://github.com/kovacsv/assimpjs) (MIT).

The critical detail — and the thing that makes this far cheaper than it first
appears — is that **assimpjs does not expose `aiScene` structs to JavaScript.**
It exposes a *conversion* API:

```js
const fileList = new ajs.FileList();
fileList.AddFile('model.fbx', uint8array);
const result = ajs.ConvertFileList(fileList, 'assjson');
```

Output formats: `assjson`, `gltf`, `gltf2`, `glb`, `glb2`. Multi-file formats
(OBJ+MTL, textures) are handled either by adding every file upfront or via
`CheckFileExists` / `GetFileContent` callbacks for lazy resolution.

This means there is **no struct marshalling against the wasm heap** — the
browser side receives text/bytes and parses them in pure Java.

### Use `assjson`, not glTF

`assjson` is Assimp's own JSON serialisation of `aiScene`. It preserves the
**raw material property list** — which is exactly what `AssimpMaterialInfo` and
`AssimpMaterialProperty` already model.

Converting to glTF2 instead would flatten every material into glTF's PBR model
and discard the format-specific properties (FBX, Collada, 3DS) that the
full-material destructuring in this library exists to capture. Since that
destructuring is the project's bar, `assjson` is the only output format that
preserves the value proposition.

## 3. What this does and does not give you

**It does not make `gdx-assimp` GWT-compliant.** The published artifact stays
desktop-only. What you get is a *second importer backend* that produces the
same `AssimpModelData` from the same Assimp semantics, sharing the entire data
model and material layer. The API converges; the implementation does not.

Current code splits cleanly for this — 9 of 15 main sources have zero LWJGL
references:

| Fate | Sources |
|---|---|
| **Reused unchanged** | `AssimpModelData`, `AssimpMaterialInfo`, `AssimpMaterialProperty`, `AssimpEmbeddedTexture`, `AssimpMatKeys`, `AssimpTextureProvider`, `attributes/*` (4 files) |
| **Reused, needs a `.gwt.xml`** | `AssimpModelLoader` — only touches native code by delegating to the converter |
| **Rewritten against JSON** | `AssimpSceneConverter` (708 LOC) → new `AssimpJsonConverter`. Same output, same semantics, different input. libGDX's `JsonReader` is GWT-safe. |
| **Dropped** | `AssimpFileIO` (223 LOC of `AIFileIO` callbacks) — replaced by assimpjs's JS file callbacks |
| **Desktop-only** | `GdxAssimp` extension/version queries (`aiGetExtensionList`), `AssimpLoaderParameters` flag constants (values can be hardcoded) |

## 4. The real blocker: embedded textures

libGDX's GWT backend documents:

> Some Pixmap methods are not supported (eg. loading from binary data).

Embedded textures — GLB payloads, data URIs, FBX embedded media — are a
first-class feature here (`AssimpEmbeddedTexture`, `AssimpTextureProvider`), and
decoding them is precisely "Pixmap from binary data". This is also why
`gdx-gltf` doesn't support binary/embedded glTF on its HTML target.

There is a workaround, but it is GWT-specific code: hand the bytes to the
browser as a Blob, decode via `HTMLImageElement`/`createImageBitmap`, draw to a
canvas, and upload to a GL texture directly — bypassing `Pixmap` entirely. The
gdx GWT backend already does something equivalent for preloaded images. Expect
~150 LOC behind a GWT-only source path, and expect it to be the fiddliest part
of the project.

## 5. Secondary constraints

- **Payload.** `assimpjs.wasm` is 3.86 MB, plus 133 KB of JS loader
  (uncompressed; roughly 1.2–1.5 MB wasm over Brotli). Acceptable for a demo,
  worth a deliberate decision for a library.
- **Staleness.** assimpjs is at 0.0.10, last published **July 2023** — three
  years old, and it pins whatever Assimp version it was built against. Given
  that this project already documents Assimp 5.2.5 property quirks, *which*
  Assimp version the wasm carries matters. Building your own via
  `emcmake cmake` / `emmake make` is the safer long-term option and is a
  well-trodden path.
- **Async.** `ConvertFileList` is Promise-based and GWT has no threads, so the
  `AssetManager` integration cannot mirror the desktop async-loader flow. It
  needs its own load path.
- **No reflection, no threads** on GWT generally — neither is used in the pure
  layer today, so this is a constraint on new code rather than a migration cost.

## 6. Effort

Rough, focused-work estimate for a working demo:

| Work | Est. | Risk |
|---|---|---|
| Split pure layer into GWT-safe module + `.gwt.xml` | 0.5 d | low |
| `assjson` → `AssimpModelData` converter | 2–4 d | medium — assjson's schema is under-documented; plan to read Assimp's assjson exporter source |
| JSInterop bridge to assimpjs (load, FileList, callbacks) | 1 d | low–med |
| Async load path for GWT | 1 d | medium |
| Embedded-texture workaround | 1–2 d | **med–high** |
| Demo module + Pages CI | 0.5 d | low — pattern already proven in gbullet |
| *Optional:* own Emscripten Assimp build | +1–3 d | medium |

**Total: roughly 6–10 focused days**, call it 1.5–2 weeks realistically.

## 7. Recommendation

The route is real and the codebase is well-shaped for it — the clean pure/native
split and the fact that assimpjs hands back parseable text rather than structs
together remove most of the cost I'd have assumed.

Two things to decide before committing:

1. **Is a parallel browser backend worth it, versus pre-converting models
   offline?** Offline conversion (run the native importer in CI, ship G3DB)
   gets a live demo for well under a day, with no wasm payload and no embedded-
   texture problem. It just can't import user-supplied files at runtime.
2. **Is runtime import in the browser actually a product goal?** That is the
   only capability the wasm route buys. If the answer is yes, the 3.86 MB and
   the ~2 weeks are justified; if it's mainly about proving the library works on
   the web, option 1 is the better trade.

Suggested next step if proceeding: spike step 3 alone — load assimpjs from a
GWT page, convert a bundled `.fbx` to `assjson`, and dump the material property
list to the console. That validates the riskiest assumption (that assjson
preserves the properties the destructuring needs) in well under a day, before
any converter work.

---

## Sources

- [kovacsv/assimpjs](https://github.com/kovacsv/assimpjs) — Emscripten Assimp, MIT
- [Building Assimp for WebAssembly](https://gist.github.com/rdeepak2002/406009878163e9b7ea35211df569667a)
- [libGDX HTML5 Backend and GWT Specifics](https://libgdx.com/wiki/html5-backend-and-gwt-specifics) — Pixmap limitation
- [mgsx-dev/gdx-gltf](https://github.com/mgsx-dev/gdx-gltf) — precedent for the embedded-texture limitation
- [AssimpJS live demo](https://kovacsv.github.io/assimpjs/)
