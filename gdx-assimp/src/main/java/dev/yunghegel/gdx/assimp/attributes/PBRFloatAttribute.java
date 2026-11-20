package dev.yunghegel.gdx.assimp.attributes;

import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;

/** Scalar attribute types for PBR material factors and other numeric material properties. Classic shininess and alpha-test
 * keep using the stock {@link FloatAttribute} types. */
public class PBRFloatAttribute extends FloatAttribute {

	public final static String MetallicAlias = "metallic";
	public final static long Metallic = register(MetallicAlias);

	public final static String RoughnessAlias = "roughness";
	public final static long Roughness = register(RoughnessAlias);

	/** Specular/glossiness workflow glossiness factor. */
	public final static String GlossinessAlias = "glossiness";
	public final static long Glossiness = register(GlossinessAlias);

	/** KHR_materials_specular strength factor. */
	public final static String SpecularFactorAlias = "specularFactor";
	public final static long SpecularFactor = register(SpecularFactorAlias);

	public final static String SheenRoughnessAlias = "sheenRoughness";
	public final static long SheenRoughness = register(SheenRoughnessAlias);

	public final static String ClearcoatFactorAlias = "clearcoatFactor";
	public final static long ClearcoatFactor = register(ClearcoatFactorAlias);

	public final static String ClearcoatRoughnessAlias = "clearcoatRoughness";
	public final static long ClearcoatRoughness = register(ClearcoatRoughnessAlias);

	public final static String TransmissionFactorAlias = "transmissionFactor";
	public final static long TransmissionFactor = register(TransmissionFactorAlias);

	/** KHR_materials_volume thickness factor. */
	public final static String VolumeThicknessAlias = "volumeThickness";
	public final static long VolumeThickness = register(VolumeThicknessAlias);

	/** KHR_materials_volume attenuation distance. */
	public final static String VolumeAttenuationDistanceAlias = "volumeAttenuationDistance";
	public final static long VolumeAttenuationDistance = register(VolumeAttenuationDistanceAlias);

	/** Index of refraction (KHR_materials_ior / AI_MATKEY_REFRACTI). */
	public final static String IORAlias = "ior";
	public final static long IOR = register(IORAlias);

	/** KHR_materials_emissive_strength. */
	public final static String EmissiveIntensityAlias = "emissiveIntensity";
	public final static long EmissiveIntensity = register(EmissiveIntensityAlias);

	public final static String AnisotropyAlias = "anisotropy";
	public final static long Anisotropy = register(AnisotropyAlias);

	/** Classic AI_MATKEY_SHININESS_STRENGTH. */
	public final static String ShininessStrengthAlias = "shininessStrength";
	public final static long ShininessStrength = register(ShininessStrengthAlias);

	/** Classic AI_MATKEY_REFLECTIVITY. */
	public final static String ReflectivityAlias = "reflectivity";
	public final static long Reflectivity = register(ReflectivityAlias);

	/** Classic AI_MATKEY_TRANSPARENCYFACTOR. */
	public final static String TransparencyFactorAlias = "transparencyFactor";
	public final static long TransparencyFactor = register(TransparencyFactorAlias);

	public PBRFloatAttribute (long type, float value) {
		super(type, value);
	}
}
