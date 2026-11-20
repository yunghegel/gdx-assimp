package dev.yunghegel.gdx.assimp;

import org.lwjgl.assimp.Assimp;

/** A fully destructured texture reference from an Assimp material: which texture stack it belongs to
 * ({@code aiTextureType}), its index in that stack, the resolved file name (or synthetic embedded-texture name), the UV set
 * it samples, wrap modes, blend factor/operation, and the UV transform. */
public class AssimpTextureRef {

	/** The {@code aiTextureType} this texture belongs to (Assimp.aiTextureType_*). */
	public int type;
	/** Index within the texture stack of {@link #type}. Only index 0 of each stack is mapped to a material attribute;
	 * additional layers remain accessible here. */
	public int index;
	/** Path relative to the model file, or a synthetic embedded-texture name resolvable via
	 * {@link AssimpModelData#embeddedTextures}. */
	public String fileName;
	/** UV set index (TEXCOORD_n). */
	public int uvIndex;
	/** Wrap modes, values are Assimp.aiTextureMapMode_* (Wrap/Clamp/Mirror/Decal). */
	public int mapModeU = Assimp.aiTextureMapMode_Wrap, mapModeV = Assimp.aiTextureMapMode_Wrap;
	/** Strength/blend factor for this texture layer (defaults to 1). */
	public float blend = 1f;
	/** Blend operation with the previous layer, Assimp.aiTextureOp_*. */
	public int op = Assimp.aiTextureOp_Multiply;

	/** Whether the source specified a UV transform for this texture. */
	public boolean hasUvTransform;
	public float uvTranslationU, uvTranslationV;
	public float uvScaleU = 1f, uvScaleV = 1f;
	/** Rotation in radians, counter-clockwise. */
	public float uvRotation;

	@Override
	public String toString () {
		return "texture[type=" + type + ",index=" + index + ",file=" + fileName + ",uv=" + uvIndex + "]";
	}
}
