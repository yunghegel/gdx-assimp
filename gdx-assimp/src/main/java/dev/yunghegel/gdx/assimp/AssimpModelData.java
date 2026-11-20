package dev.yunghegel.gdx.assimp;

import com.badlogic.gdx.graphics.g3d.model.data.ModelData;
import com.badlogic.gdx.utils.ObjectMap;
import dev.yunghegel.gdx.assimp.attributes.AssimpMaterialAttribute;

/** {@link ModelData} produced by the Assimp importer. Identical to plain ModelData for the LibGDX model pipeline, but
 * additionally carries textures that were embedded in the source file. Embedded textures are referenced from
 * {@code ModelMaterial.textures[i].fileName} using synthetic names (see {@link AssimpSceneConverter#embeddedName}); the
 * {@link AssimpTextureProvider} resolves those names against {@link #embeddedTextures} instead of the file system. */
public class AssimpModelData extends ModelData {

	/** Synthetic file name -> embedded texture payload. Empty for models without embedded textures. */
	public final ObjectMap<String, AssimpEmbeddedTexture> embeddedTextures = new ObjectMap<String, AssimpEmbeddedTexture>();

	/** Material id -> fully destructured Assimp material (every raw property and texture reference). Used by
	 * {@link AssimpMaterialMapper} to apply PBR and extended attributes after Model construction, and attached to each
	 * Material as an {@link AssimpMaterialAttribute} for renderer access. */
	public final ObjectMap<String, AssimpMaterialInfo> extendedMaterials = new ObjectMap<String, AssimpMaterialInfo>();
}
