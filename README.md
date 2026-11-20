# gdx-assimp

A universal model importer for [LibGDX](https://libgdx.com/) built on the
[LWJGL 3 Assimp bindings](https://www.lwjgl.org/). It imports **any format Assimp supports**
(FBX, glTF/GLB, Collada/DAE, OBJ, 3DS, BLEND, STL, PLY, LWO, X, MD5, … ~70 extensions) and
converts it into LibGDX's own `ModelData` — the exact same intermediate representation
`G3dModelLoader` produces — so the result plugs directly into `Model`, `ModelInstance`,
`ModelBatch`, `AnimationController` and the standard LibGDX material system. No custom
shaders, no custom scene graph.

**What gets converted**

| Assimp | LibGDX |
|---|---|
| Meshes (positions, normals, colors, multiple UV sets, tangents/binormals) | `ModelMesh` + `VertexAttribute`s |
| Materials — full destructuring, see below | `Material` with stock + PBR attributes and the raw property bag |
| Textures, every stack (diffuse, specular, normal, bump, emissive, ambient, base color, metallic-roughness, occlusion, clearcoat, sheen, transmission, displacement, reflection) | `TextureAttribute` / `PBRTextureAttribute` with UV set, wrap modes, UV transforms |
| Embedded textures (GLB blobs, glTF data URIs, FBX embedded media) | decoded from memory, owned by the `Model` |
| Node hierarchy (with transforms decomposed to translation/rotation/scale) | `ModelNode` tree |
| Skinning (bones, ≤4 weights per vertex by default) | `ModelNodePart.bones` + `BoneWeight` attributes |
| Node animations (position/rotation/scale keys, ticks converted to seconds) | `ModelAnimation` |

## Material destructuring

Every material property Assimp parses is destructured into LibGDX material attributes, with the goal that an
implementation can build an intricate renderer covering full PBR and beyond on top of the standard
`Material`/`Attribute` system:

- **Stock attributes** keep working exactly as with `G3dModelLoader`: `ColorAttribute`
  (diffuse/ambient/specular/emissive/reflection), `FloatAttribute` (shininess, alpha test), `TextureAttribute`
  (diffuse/specular/normal/bump/ambient/emissive/reflection), `BlendingAttribute`, `IntAttribute.CullFace`
  (from `doubleSided`). The stock `DefaultShader` renders these out of the box.
- **`PBRColorAttribute`** — `BaseColorFactor` (incl. alpha), `SheenColorFactor`, `VolumeAttenuationColor`,
  `TransparentColor`.
- **`PBRFloatAttribute`** — `Metallic`, `Roughness`, `Glossiness`, `SpecularFactor`, `SheenRoughness`,
  `ClearcoatFactor`, `ClearcoatRoughness`, `TransmissionFactor`, `VolumeThickness`, `VolumeAttenuationDistance`,
  `IOR`, `EmissiveIntensity`, `Anisotropy`, `ShininessStrength`, `Reflectivity`, `TransparencyFactor`.
- **`PBRTextureAttribute`** — `BaseColor`, `MetallicRoughness` (combined glTF map, auto-detected), `Metalness`,
  `Roughness`, `Occlusion`, `Clearcoat`, `Sheen`, `Transmission`, `Displacement`. Subclass of `TextureAttribute`,
  carrying UV set index, wrap modes and the full UV transform including `rotationUV` (KHR_texture_transform).
- **Alpha modes** — glTF `MASK` becomes `FloatAttribute.AlphaTest` with the declared cutoff; `BLEND` becomes a
  `BlendingAttribute` (base color alpha × opacity).
- **`AssimpMaterialAttribute`** — attached to every material, carrying the complete raw
  [`AssimpMaterialInfo`](gdx-assimp/src/main/java/com/crownjames/gdxassimp/AssimpMaterialInfo.java): every
  `aiMaterialProperty` (typed access by key, see `AssimpMatKeys`) and every texture reference across all stacks,
  including extra layers and per-texture scalars (normal scale, occlusion strength, blend op, …). Anything Assimp
  can read, a renderer can query — even properties gdx-assimp has no dedicated attribute for:

```java
AssimpMaterialAttribute raw = material.get(AssimpMaterialAttribute.class, AssimpMaterialAttribute.AssimpMaterial);
float anisotropy = raw.info.getFloat(AssimpMatKeys.ANISOTROPY_FACTOR, 0f);
float normalScale = raw.info.property(AssimpMatKeys.TEX_SCALE, Assimp.aiTextureType_NORMALS, 0).asFloat(1f);
String alphaMode = raw.info.getString(AssimpMatKeys.GLTF_ALPHA_MODE);
```

KHR material extensions verified by the test suite: `KHR_materials_ior`, `KHR_materials_clearcoat`,
`KHR_materials_transmission`, `KHR_materials_sheen`, `KHR_materials_emissive_strength`, `KHR_materials_volume`,
`KHR_texture_transform`. Writing a shader that consumes these attributes (a `DefaultShader` subclass or a fully
custom `Shader`) is the renderer's job — gdx-assimp guarantees the data is present on the `Material`.

## Usage

### With an `AssetManager` (async)

```java
AssetManager assets = new AssetManager();
GdxAssimp.registerLoaders(assets);              // registers the loader for every Assimp extension

assets.load("models/ship.fbx", Model.class);    // parsed on the async loader thread
// ... later, e.g. in render():
if (assets.update()) {
    Model model = assets.get("models/ship.fbx", Model.class);
    instance = new ModelInstance(model);
}
```

The loader is a standard LibGDX `AsynchronousAssetLoader`: the Assimp import and the
`ModelData` conversion run on the AssetManager's loading thread, referenced texture files are
declared as dependencies (loaded and reference-counted by the manager), and only GL resource
creation happens on the render thread. Textures embedded in the model file are decoded from
memory and disposed together with the `Model`.

### Direct (synchronous)

```java
AssimpModelLoader loader = new AssimpModelLoader(new InternalFileHandleResolver());
Model model = loader.loadModel(Gdx.files.internal("models/ship.glb"));
```

You can also stop at the intermediate representation:

```java
ModelData data = loader.loadModelData(Gdx.files.internal("models/ship.glb"));
```

### Tuning the import

```java
AssimpLoaderParameters params = new AssimpLoaderParameters();
params.flipUVs = true;                 // LibGDX top-left UV origin (default true)
params.calcTangents = true;            // tangents/binormals for normal mapping
params.preTransformVertices = true;    // flatten static scenery into one node
params.normalGeneration = AssimpLoaderParameters.NormalGeneration.SMOOTH;
params.additionalFlags = Assimp.aiProcess_OptimizeMeshes;  // any extra aiProcess_* flags

assets.load("models/level.obj", Model.class, params);
```

Files are read through LibGDX `FileHandle`s via a custom Assimp file system
(`AssimpFileIO`), so multi-file formats (OBJ + MTL, glTF + .bin + textures) work from any
LibGDX file type — including assets packed inside jars.

## Project layout

- `gdx-assimp/` — the library (`com.crownjames.gdxassimp`), plus unit tests
- `demo/` — a minimal viewer: `./gradlew :demo:run --args="/absolute/path/to/model.fbx"`
  (orbit camera, plays the first animation)

## Dependencies

The library depends on `com.badlogicgames.gdx:gdx`, `org.lwjgl:lwjgl` and
`org.lwjgl:lwjgl-assimp`. Desktop applications must additionally provide the Assimp natives
for their platform, matching the LWJGL version of their `gdx-backend-lwjgl3`:

```groovy
implementation "org.lwjgl:lwjgl-assimp:3.3.3"
runtimeOnly "org.lwjgl:lwjgl-assimp:3.3.3:natives-macos-arm64"   // or natives-windows,
// natives-linux, natives-macos, natives-linux-arm64, natives-windows-arm64, ...
```

(LibGDX 1.13.1–1.14.0 use LWJGL 3.3.3.) This is a desktop (LWJGL3) library; Android/iOS/GWT
backends are not supported by Assimp's native binaries here — for those platforms, convert
to `.g3db` offline as usual.

## Notes & limitations

- Mesh indices are 16-bit (LibGDX `Mesh`); larger meshes are automatically split
  (`aiProcess_SplitLargeMeshes`, ≤ 65534 vertices per mesh).
- Bone weights are capped at 4 per vertex (renormalized by Assimp); raise
  `maxBoneWeights` only if your shader supports it.
- Node transforms are decomposed into translation/rotation/scale (LibGDX's node model);
  shear/negative-scale matrices won't survive decomposition.
- Vertex animations (morph targets) are not converted — LibGDX's model format has no
  equivalent; only node/skeletal animations are.
- The stock `DefaultShader` only consumes the classic attribute subset (it renders PBR models with the
  diffuse approximation Assimp also provides); the PBR attributes are there for your own shader.
- LibGDX's attribute registry is limited to 64 attribute types globally. gdx-assimp registers 30 on top of
  the ~23 LibGDX core uses; if you combine it with another library that registers many attribute types
  (e.g. gdx-gltf), watch out for exhaustion.
- Only layer 0 of each texture stack becomes an attribute; additional layers (and their blend ops/strengths)
  are accessible through `AssimpMaterialAttribute`.

## Building

```bash
./gradlew build          # library + tests
./gradlew :demo:run --args="/path/to/model.fbx"
```
