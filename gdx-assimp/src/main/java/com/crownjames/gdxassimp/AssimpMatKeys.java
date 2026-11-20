package com.crownjames.gdxassimp;

/** String keys of Assimp material properties ({@code AI_MATKEY_*} from assimp's material.h), for querying
 * {@link AssimpMaterialInfo}. Defined here as literals so every key is available regardless of which constants the LWJGL
 * binding version exposes. Keys starting with {@code $clr.} are colors (float[3] or float[4]), {@code $mat.} are scalars,
 * ints or strings, {@code $tex.} are per-texture properties (looked up with the texture's semantic/type and index). */
public final class AssimpMatKeys {

	private AssimpMatKeys () {
	}

	// --- Classic material model
	public static final String NAME = "?mat.name";
	public static final String COLOR_DIFFUSE = "$clr.diffuse";
	public static final String COLOR_AMBIENT = "$clr.ambient";
	public static final String COLOR_SPECULAR = "$clr.specular";
	public static final String COLOR_EMISSIVE = "$clr.emissive";
	public static final String COLOR_REFLECTIVE = "$clr.reflective";
	public static final String COLOR_TRANSPARENT = "$clr.transparent";
	public static final String OPACITY = "$mat.opacity";
	public static final String SHININESS = "$mat.shininess";
	public static final String SHININESS_STRENGTH = "$mat.shinpercent";
	public static final String REFLECTIVITY = "$mat.reflectivity";
	public static final String TRANSPARENCY_FACTOR = "$mat.transparencyfactor";
	public static final String BUMP_SCALING = "$mat.bumpscaling";
	public static final String TWO_SIDED = "$mat.twosided";
	public static final String SHADING_MODEL = "$mat.shadingm";
	public static final String ENABLE_WIREFRAME = "$mat.wireframe";
	public static final String BLEND_FUNC = "$mat.blend";
	public static final String REFRACTI = "$mat.refracti"; // index of refraction

	// --- PBR (metallic/roughness and specular/glossiness workflows)
	public static final String BASE_COLOR = "$clr.base";
	public static final String METALLIC_FACTOR = "$mat.metallicFactor";
	public static final String ROUGHNESS_FACTOR = "$mat.roughnessFactor";
	public static final String GLOSSINESS_FACTOR = "$mat.glossinessFactor";
	public static final String SPECULAR_FACTOR = "$mat.specularFactor";
	public static final String ANISOTROPY_FACTOR = "$mat.anisotropyFactor";
	public static final String EMISSIVE_INTENSITY = "$mat.emissiveIntensity";

	// --- glTF KHR material extensions
	public static final String SHEEN_COLOR_FACTOR = "$clr.sheen.factor";
	public static final String SHEEN_ROUGHNESS_FACTOR = "$mat.sheen.roughnessFactor";
	public static final String CLEARCOAT_FACTOR = "$mat.clearcoat.factor";
	public static final String CLEARCOAT_ROUGHNESS_FACTOR = "$mat.clearcoat.roughnessFactor";
	public static final String TRANSMISSION_FACTOR = "$mat.transmission.factor";
	public static final String VOLUME_THICKNESS_FACTOR = "$mat.volume.thicknessFactor";
	public static final String VOLUME_ATTENUATION_DISTANCE = "$mat.volume.attenuationDistance";
	/** Note: assimp 5.2.x stores this under {@code $mat.}, newer headers renamed it to {@code $clr.}; gdx-assimp queries
	 * should use this constant which matches the linked native library. */
	public static final String VOLUME_ATTENUATION_COLOR = "$mat.volume.attenuationColor";
	public static final String GLTF_ALPHA_MODE = "$mat.gltf.alphaMode"; // "OPAQUE" | "MASK" | "BLEND"
	public static final String GLTF_ALPHA_CUTOFF = "$mat.gltf.alphaCutoff";

	// --- Per-texture properties (query with the texture's semantic and index)
	public static final String TEX_FILE = "$tex.file";
	public static final String TEX_UV_SOURCE = "$tex.uvwsrc";
	public static final String TEX_MAPMODE_U = "$tex.mapmodeu";
	public static final String TEX_MAPMODE_V = "$tex.mapmodev";
	public static final String TEX_BLEND = "$tex.blend";
	public static final String TEX_OP = "$tex.op";
	public static final String TEX_UV_TRANSFORM = "$tex.uvtrafo"; // float[5]: translation.xy, scaling.xy, rotation
	public static final String TEX_MAPPING = "$tex.mapping";
	/** glTF normalTexture.scale (semantic = aiTextureType_NORMALS). */
	public static final String TEX_SCALE = "$tex.scale";
	/** glTF occlusionTexture.strength (semantic = aiTextureType_LIGHTMAP). */
	public static final String TEX_STRENGTH = "$tex.file.strength";
}
