package dev.yunghegel.gdx.assimp;

import org.lwjgl.assimp.Assimp;

import com.badlogic.gdx.assets.loaders.ModelLoader;

/** Parameters controlling how Assimp imports and post-processes a model before it is converted to LibGDX
 * {@link com.badlogic.gdx.graphics.g3d.model.data.ModelData}. Also usable as
 * {@link com.badlogic.gdx.assets.AssetLoaderParameters} when loading through an
 * {@link com.badlogic.gdx.assets.AssetManager}. */
public class AssimpLoaderParameters extends ModelLoader.ModelParameters {

	/** How normals are generated for meshes that do not contain any. */
	public enum NormalGeneration {
		/** Leave meshes without normals as they are. */
		NONE,
		/** One normal per face (faceted look). */
		FLAT,
		/** Smoothed per-vertex normals. */
		SMOOTH
	}

	/** Flip the V texture coordinate. LibGDX uses a top-left UV origin (images are loaded top-down), while most model formats
	 * assume OpenGL's bottom-left origin, so this defaults to true — the same convention the stock LibGDX ObjLoader uses. */
	public boolean flipUVs = true;

	/** Generate normals for meshes that have none. Defaults to {@link NormalGeneration#SMOOTH}. */
	public NormalGeneration normalGeneration = NormalGeneration.SMOOTH;

	/** Compute per-vertex tangents and binormals (needed for normal mapping). Defaults to false. */
	public boolean calcTangents = false;

	/** Collapse the whole scene graph and bake all node transforms into the vertices. Produces a single flat model — useful for
	 * static scenery, but destroys the node hierarchy and drops animations. Defaults to false. */
	public boolean preTransformVertices = false;

	/** Maximum number of bone weights per vertex. LibGDX's DefaultShader supports up to 4 without configuration changes. */
	public int maxBoneWeights = 4;

	/** Meshes with more vertices than this are split so they stay addressable with the 16 bit indices LibGDX meshes use. */
	public int maxVertices = 65534;

	/** Remove point and line primitives, keeping only triangles. Defaults to true, since LibGDX ModelData mesh parts produced by
	 * this importer are rendered as GL_TRIANGLES. */
	public boolean removePointsAndLines = true;

	/** Collapse FBX pivot/helper nodes ($AssimpFbx$ chains) into their parent transforms. Defaults to true. */
	public boolean collapseFbxPivots = true;

	/** Additional {@code aiProcess_*} flags OR-ed on top of the flags derived from the fields above. */
	public int additionalFlags = 0;

	/** If not -1, this exact flag mask is passed to Assimp and every other post-processing field above (except properties like
	 * {@link #maxBoneWeights}) is ignored. For full manual control. */
	public int overrideFlags = -1;

	/** Computes the {@code aiProcess_*} flag mask for this configuration. */
	public int getPostProcessFlags () {
		if (overrideFlags != -1) return overrideFlags;
		int flags = Assimp.aiProcess_Triangulate //
			| Assimp.aiProcess_JoinIdenticalVertices //
			| Assimp.aiProcess_LimitBoneWeights //
			| Assimp.aiProcess_SplitLargeMeshes //
			| Assimp.aiProcess_ValidateDataStructure;
		if (removePointsAndLines) flags |= Assimp.aiProcess_SortByPType | Assimp.aiProcess_FindDegenerates;
		if (flipUVs) flags |= Assimp.aiProcess_FlipUVs;
		if (normalGeneration == NormalGeneration.SMOOTH) flags |= Assimp.aiProcess_GenSmoothNormals;
		if (normalGeneration == NormalGeneration.FLAT) flags |= Assimp.aiProcess_GenNormals;
		if (calcTangents) flags |= Assimp.aiProcess_CalcTangentSpace;
		if (preTransformVertices) flags |= Assimp.aiProcess_PreTransformVertices;
		return flags | additionalFlags;
	}
}
