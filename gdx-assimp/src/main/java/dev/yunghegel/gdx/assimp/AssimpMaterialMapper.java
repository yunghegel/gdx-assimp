package dev.yunghegel.gdx.assimp;

import org.lwjgl.assimp.Assimp;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import dev.yunghegel.gdx.assimp.attributes.AssimpMaterialAttribute;
import dev.yunghegel.gdx.assimp.attributes.PBRColorAttribute;
import dev.yunghegel.gdx.assimp.attributes.PBRFloatAttribute;
import dev.yunghegel.gdx.assimp.attributes.PBRTextureAttribute;

/** Destructures an {@link AssimpMaterialInfo} into LibGDX material {@link com.badlogic.gdx.graphics.g3d.Attribute}s: the
 * full PBR set (base color, metallic/roughness, occlusion, emissive strength, clearcoat, sheen, transmission, volume, IOR,
 * anisotropy), classic Phong extras (shininess strength, reflectivity, transparency), alpha mode / cutoff, double-sided
 * culling, and every texture stack with UV set, wrap modes and UV transforms. The raw material is additionally attached as an
 * {@link AssimpMaterialAttribute}, so renderers can reach anything not covered by the mapped attributes. */
public final class AssimpMaterialMapper {

	private AssimpMaterialMapper () {
	}

	/** Resolves texture file names (or synthetic embedded names) to Texture instances. Must be called on the GL thread. May
	 * return null, in which case the texture attribute is skipped (the reference stays available in the info). */
	public interface TextureResolver {
		Texture load (String fileName);
	}

	/** Applies the extended materials of {@code data} to all materials of a constructed {@code model}. Textures are resolved
	 * through {@code provider} (deduplicated per file name); newly created textures that are not owned by {@code manager}
	 * (may be null) are registered as managed disposables of the model. Called automatically by
	 * {@link AssimpModelLoader}. */
	public static void applyToModel (Model model, AssimpModelData data, TextureProvider provider, final AssetManager manager) {
		if (data.extendedMaterials.size == 0) return;

		final ObjectMap<String, Texture> cache = new ObjectMap<String, Texture>();
		final Array<Texture> created = new Array<Texture>();
		TextureResolver resolver = new TextureResolver() {
			@Override
			public Texture load (String fileName) {
				Texture texture = cache.get(fileName);
				if (texture == null) {
					texture = provider.load(fileName);
					if (texture != null) {
						cache.put(fileName, texture);
						created.add(texture);
					}
				}
				return texture;
			}
		};

		for (Material material : model.materials) {
			AssimpMaterialInfo info = data.extendedMaterials.get(material.id);
			if (info != null) apply(material, info, resolver);
		}

		// Own every texture this pass created, unless the AssetManager owns it or the model already does.
		Iterable<Disposable> managed = model.getManagedDisposables();
		for (Texture texture : created) {
			if (manager != null && manager.containsAsset(texture)) continue;
			boolean alreadyManaged = false;
			for (Disposable disposable : managed)
				if (disposable == texture) alreadyManaged = true;
			if (!alreadyManaged) model.manageDisposable(texture);
		}
	}

	/** Destructures {@code info} into attributes on {@code material}. Public so custom pipelines can apply the mapping to
	 * their own materials. */
	public static void apply (Material material, AssimpMaterialInfo info, TextureResolver resolver) {
		material.set(new AssimpMaterialAttribute(info));

		applyColors(material, info);
		applyScalars(material, info);
		applyAlphaAndCulling(material, info);
		applyTextures(material, info, resolver);
	}

	private static void applyColors (Material material, AssimpMaterialInfo info) {
		Color baseColor = info.getColor(AssimpMatKeys.BASE_COLOR);
		if (baseColor != null) material.set(new PBRColorAttribute(PBRColorAttribute.BaseColorFactor, baseColor));
		Color sheenColor = info.getColor(AssimpMatKeys.SHEEN_COLOR_FACTOR);
		if (sheenColor != null) material.set(new PBRColorAttribute(PBRColorAttribute.SheenColorFactor, sheenColor));
		Color attenuationColor = info.getColor(AssimpMatKeys.VOLUME_ATTENUATION_COLOR);
		if (attenuationColor != null)
			material.set(new PBRColorAttribute(PBRColorAttribute.VolumeAttenuationColor, attenuationColor));
		Color transparentColor = info.getColor(AssimpMatKeys.COLOR_TRANSPARENT);
		if (transparentColor != null)
			material.set(new PBRColorAttribute(PBRColorAttribute.TransparentColor, transparentColor));
	}

	private static void applyScalars (Material material, AssimpMaterialInfo info) {
		setFloat(material, info, AssimpMatKeys.METALLIC_FACTOR, PBRFloatAttribute.Metallic);
		setFloat(material, info, AssimpMatKeys.ROUGHNESS_FACTOR, PBRFloatAttribute.Roughness);
		setFloat(material, info, AssimpMatKeys.GLOSSINESS_FACTOR, PBRFloatAttribute.Glossiness);
		setFloat(material, info, AssimpMatKeys.SPECULAR_FACTOR, PBRFloatAttribute.SpecularFactor);
		setFloat(material, info, AssimpMatKeys.SHEEN_ROUGHNESS_FACTOR, PBRFloatAttribute.SheenRoughness);
		setFloat(material, info, AssimpMatKeys.CLEARCOAT_FACTOR, PBRFloatAttribute.ClearcoatFactor);
		setFloat(material, info, AssimpMatKeys.CLEARCOAT_ROUGHNESS_FACTOR, PBRFloatAttribute.ClearcoatRoughness);
		setFloat(material, info, AssimpMatKeys.TRANSMISSION_FACTOR, PBRFloatAttribute.TransmissionFactor);
		setFloat(material, info, AssimpMatKeys.VOLUME_THICKNESS_FACTOR, PBRFloatAttribute.VolumeThickness);
		setFloat(material, info, AssimpMatKeys.VOLUME_ATTENUATION_DISTANCE, PBRFloatAttribute.VolumeAttenuationDistance);
		setFloat(material, info, AssimpMatKeys.REFRACTI, PBRFloatAttribute.IOR);
		setFloat(material, info, AssimpMatKeys.EMISSIVE_INTENSITY, PBRFloatAttribute.EmissiveIntensity);
		setFloat(material, info, AssimpMatKeys.ANISOTROPY_FACTOR, PBRFloatAttribute.Anisotropy);
		setFloat(material, info, AssimpMatKeys.SHININESS_STRENGTH, PBRFloatAttribute.ShininessStrength);
		setFloat(material, info, AssimpMatKeys.REFLECTIVITY, PBRFloatAttribute.Reflectivity);
		setFloat(material, info, AssimpMatKeys.TRANSPARENCY_FACTOR, PBRFloatAttribute.TransparencyFactor);
	}

	private static void setFloat (Material material, AssimpMaterialInfo info, String key, long type) {
		AssimpMaterialProperty property = info.property(key);
		if (property != null && property.count() >= 1) material.set(new PBRFloatAttribute(type, property.asFloat(0f)));
	}

	private static void applyAlphaAndCulling (Material material, AssimpMaterialInfo info) {
		String alphaMode = info.getString(AssimpMatKeys.GLTF_ALPHA_MODE);
		if ("MASK".equals(alphaMode)) {
			material.set(FloatAttribute.createAlphaTest(info.getFloat(AssimpMatKeys.GLTF_ALPHA_CUTOFF, 0.5f)));
		} else if ("BLEND".equals(alphaMode)) {
			float opacity = info.getFloat(AssimpMatKeys.OPACITY, 1f);
			Color baseColor = info.getColor(AssimpMatKeys.BASE_COLOR);
			if (baseColor != null) opacity *= baseColor.a;
			material.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, opacity));
		}
		if (info.getInt(AssimpMatKeys.TWO_SIDED, 0) != 0) {
			material.set(IntAttribute.createCullFace(GL20.GL_NONE));
		}
	}

	private static void applyTextures (Material material, AssimpMaterialInfo info, TextureResolver resolver) {
		if (resolver == null) return;

		// glTF-style combined metallic-roughness detection: assimp exposes the combined map under METALNESS and
		// DIFFUSE_ROUGHNESS (and legacy UNKNOWN) pointing at the same image.
		AssimpTextureRef metalness = info.texture(Assimp.aiTextureType_METALNESS);
		AssimpTextureRef roughness = info.texture(Assimp.aiTextureType_DIFFUSE_ROUGHNESS);
		boolean combinedMetallicRoughness = metalness != null && roughness != null
			&& metalness.fileName.equals(roughness.fileName) && metalness.uvIndex == roughness.uvIndex;
		boolean metallicRoughnessEmitted = false;

		long emitted = 0; // guard against double-set within this pass (first layer of each stack wins)
		for (AssimpTextureRef ref : info.textures) {
			if (ref.index != 0) continue; // additional layers stay accessible through the info

			long type;
			switch (ref.type) {
			case Assimp.aiTextureType_DIFFUSE:
				type = TextureAttribute.Diffuse;
				break;
			case Assimp.aiTextureType_SPECULAR:
				type = TextureAttribute.Specular;
				break;
			case Assimp.aiTextureType_AMBIENT:
				type = TextureAttribute.Ambient;
				break;
			case Assimp.aiTextureType_EMISSIVE:
			case Assimp.aiTextureType_EMISSION_COLOR:
				type = TextureAttribute.Emissive;
				break;
			case Assimp.aiTextureType_HEIGHT:
				type = TextureAttribute.Bump;
				break;
			case Assimp.aiTextureType_NORMALS:
			case Assimp.aiTextureType_NORMAL_CAMERA:
				type = TextureAttribute.Normal;
				break;
			case Assimp.aiTextureType_REFLECTION:
				type = TextureAttribute.Reflection;
				break;
			case Assimp.aiTextureType_BASE_COLOR:
				type = PBRTextureAttribute.BaseColor;
				break;
			case Assimp.aiTextureType_METALNESS:
				type = combinedMetallicRoughness ? PBRTextureAttribute.MetallicRoughness : PBRTextureAttribute.Metalness;
				if (combinedMetallicRoughness) metallicRoughnessEmitted = true;
				break;
			case Assimp.aiTextureType_DIFFUSE_ROUGHNESS:
				if (combinedMetallicRoughness) continue; // already emitted via METALNESS
				type = PBRTextureAttribute.Roughness;
				break;
			case Assimp.aiTextureType_AMBIENT_OCCLUSION:
			case Assimp.aiTextureType_LIGHTMAP:
				type = PBRTextureAttribute.Occlusion;
				break;
			case Assimp.aiTextureType_CLEARCOAT:
				type = PBRTextureAttribute.Clearcoat;
				break;
			case Assimp.aiTextureType_SHEEN:
				type = PBRTextureAttribute.Sheen;
				break;
			case Assimp.aiTextureType_TRANSMISSION:
				type = PBRTextureAttribute.Transmission;
				break;
			case Assimp.aiTextureType_DISPLACEMENT:
				type = PBRTextureAttribute.Displacement;
				break;
			case Assimp.aiTextureType_UNKNOWN:
				// Legacy glTF metallic-roughness slot; only claim it if nothing else did.
				if (metallicRoughnessEmitted || metalness != null || roughness != null) continue;
				type = PBRTextureAttribute.MetallicRoughness;
				metallicRoughnessEmitted = true;
				break;
			default:
				continue; // info-only semantics
			}
			if ((emitted & type) != 0) continue;

			// Reuse the texture the Model constructor already loaded for this slot, if any.
			TextureAttribute existing = material.get(TextureAttribute.class, type);
			Texture texture = existing != null && existing.textureDescription.texture != null
				? existing.textureDescription.texture
				: resolver.load(ref.fileName);
			if (texture == null) continue;

			PBRTextureAttribute attribute = new PBRTextureAttribute(type, texture);
			attribute.uvIndex = ref.uvIndex;
			attribute.textureDescription.uWrap = wrap(ref.mapModeU);
			attribute.textureDescription.vWrap = wrap(ref.mapModeV);
			if (ref.hasUvTransform) {
				attribute.offsetU = ref.uvTranslationU;
				attribute.offsetV = ref.uvTranslationV;
				attribute.scaleU = ref.uvScaleU;
				attribute.scaleV = ref.uvScaleV;
				attribute.rotationUV = ref.uvRotation;
			}
			material.set(attribute);
			emitted |= type;
		}
	}

	private static Texture.TextureWrap wrap (int aiMapMode) {
		switch (aiMapMode) {
		case Assimp.aiTextureMapMode_Clamp:
		case Assimp.aiTextureMapMode_Decal:
			return Texture.TextureWrap.ClampToEdge;
		case Assimp.aiTextureMapMode_Mirror:
			return Texture.TextureWrap.MirroredRepeat;
		default:
			return Texture.TextureWrap.Repeat;
		}
	}
}
