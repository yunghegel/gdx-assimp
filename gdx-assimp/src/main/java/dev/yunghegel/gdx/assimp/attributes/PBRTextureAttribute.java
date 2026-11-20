package dev.yunghegel.gdx.assimp.attributes;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor;
import com.badlogic.gdx.utils.NumberUtils;

/** Texture attribute types for PBR and other texture semantics that stock LibGDX {@link TextureAttribute} does not cover.
 * Being a TextureAttribute subclass, instances also work everywhere a TextureAttribute does (texture binding, uv offset/scale,
 * uv set index), and additionally carry a UV rotation (radians, from Assimp's per-texture UV transform).
 * <p>
 * Classic semantics (diffuse, specular, normal, bump, ambient, emissive, reflection) keep using the stock
 * {@link TextureAttribute} types; these are only the additional ones. A shader implementing full PBR should check for both. */
public class PBRTextureAttribute extends TextureAttribute {

	public final static String BaseColorAlias = "baseColorTexture";
	public final static long BaseColor = register(BaseColorAlias);

	/** Combined metallic-roughness map (glTF convention: metalness in B, roughness in G). */
	public final static String MetallicRoughnessAlias = "metallicRoughnessTexture";
	public final static long MetallicRoughness = register(MetallicRoughnessAlias);

	/** Standalone metalness map (formats that keep metalness and roughness separate). */
	public final static String MetalnessAlias = "metalnessTexture";
	public final static long Metalness = register(MetalnessAlias);

	/** Standalone roughness map. */
	public final static String RoughnessAlias = "roughnessTexture";
	public final static long Roughness = register(RoughnessAlias);

	/** Ambient occlusion map (covers both aiTextureType_AMBIENT_OCCLUSION and aiTextureType_LIGHTMAP). */
	public final static String OcclusionAlias = "occlusionTexture";
	public final static long Occlusion = register(OcclusionAlias);

	public final static String ClearcoatAlias = "clearcoatTexture";
	public final static long Clearcoat = register(ClearcoatAlias);

	public final static String SheenAlias = "sheenTexture";
	public final static long Sheen = register(SheenAlias);

	public final static String TransmissionAlias = "transmissionTexture";
	public final static long Transmission = register(TransmissionAlias);

	public final static String DisplacementAlias = "displacementTexture";
	public final static long Displacement = register(DisplacementAlias);

	static {
		Mask |= BaseColor | MetallicRoughness | Metalness | Roughness | Occlusion | Clearcoat | Sheen | Transmission
			| Displacement;
	}

	/** UV rotation in radians (counter-clockwise around the UV origin), from the source file's texture transform. Stock
	 * TextureAttribute only models offset and scale; shaders supporting rotated UV transforms should read this. */
	public float rotationUV = 0f;

	public PBRTextureAttribute (long type, Texture texture) {
		super(type, texture);
	}

	public PBRTextureAttribute (long type, TextureDescriptor<Texture> textureDescription) {
		super(type, textureDescription);
	}

	public PBRTextureAttribute (PBRTextureAttribute copyFrom) {
		super(copyFrom);
		this.rotationUV = copyFrom.rotationUV;
	}

	@Override
	public Attribute copy () {
		return new PBRTextureAttribute(this);
	}

	@Override
	public int compareTo (Attribute o) {
		int result = super.compareTo(o);
		if (result != 0) return result;
		float otherRotation = o instanceof PBRTextureAttribute ? ((PBRTextureAttribute)o).rotationUV : 0f;
		return Float.compare(rotationUV, otherRotation);
	}

	@Override
	public int hashCode () {
		int result = super.hashCode();
		result = 991 * result + NumberUtils.floatToRawIntBits(rotationUV);
		return result;
	}
}
