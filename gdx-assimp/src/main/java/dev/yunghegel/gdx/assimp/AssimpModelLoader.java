package dev.yunghegel.gdx.assimp;

import java.util.Iterator;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.ModelLoader;
import com.badlogic.gdx.assets.loaders.TextureLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.data.ModelData;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial;
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture;
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;

/** Loads any model format supported by Assimp (FBX, glTF/GLB, DAE/Collada, OBJ, 3DS, BLEND, STL, PLY, ...) as a LibGDX
 * {@link Model}.
 * <p>
 * As a subclass of {@link ModelLoader} this is an {@code AsynchronousAssetLoader}: when used with an {@link AssetManager},
 * the Assimp import and ModelData conversion run on the manager's async loading thread, and only the GL resource creation
 * (meshes, textures) happens on the render thread in {@code loadSync}. Referenced texture files are declared as dependencies
 * so the AssetManager loads and reference-counts them; textures embedded in the model file (GLB, data URIs, FBX media) are
 * decoded from memory and owned (disposed) by the Model itself.
 * <p>
 * It can also be used directly, without an AssetManager:
 *
 * <pre>
 * AssimpModelLoader loader = new AssimpModelLoader(new InternalFileHandleResolver());
 * Model model = loader.loadModel(Gdx.files.internal("models/ship.fbx"));
 * </pre>
 *
 * To register it with an AssetManager for every Assimp-supported extension, see
 * {@link GdxAssimp#registerLoaders(AssetManager, FileHandleResolver)}. */
public class AssimpModelLoader extends ModelLoader<AssimpLoaderParameters> {

	private final AssimpLoaderParameters defaultAssimpParameters = new AssimpLoaderParameters();

	public AssimpModelLoader (FileHandleResolver resolver) {
		super(resolver);
	}

	@Override
	public ModelData loadModelData (FileHandle fileHandle, AssimpLoaderParameters parameters) {
		return new AssimpSceneConverter().convert(fileHandle, parameters == null ? defaultAssimpParameters : parameters);
	}

	/** Loads a model directly (synchronously, no AssetManager). Embedded textures are resolved from memory; regular texture
	 * references are loaded through the given provider. PBR and extended material attributes are applied via
	 * {@link AssimpMaterialMapper}. */
	@Override
	public Model loadModel (final FileHandle fileHandle, TextureProvider textureProvider, AssimpLoaderParameters parameters) {
		final ModelData data = loadModelData(fileHandle, parameters);
		if (data == null) return null;
		TextureProvider provider = wrapProvider(data, textureProvider);
		Model model = new Model(data, provider);
		if (data instanceof AssimpModelData) AssimpMaterialMapper.applyToModel(model, (AssimpModelData)data, provider, null);
		return model;
	}

	private static TextureProvider wrapProvider (ModelData data, TextureProvider provider) {
		if (data instanceof AssimpModelData && ((AssimpModelData)data).embeddedTextures.size > 0)
			return new AssimpTextureProvider((AssimpModelData)data, provider);
		return provider;
	}

	/** Called on the AssetManager's async thread: performs the Assimp import + conversion and declares the external texture
	 * files the model references as dependencies. Embedded textures are excluded — they are not loadable assets. */
	@Override
	public Array<AssetDescriptor> getDependencies (String fileName, FileHandle file, AssimpLoaderParameters parameters) {
		final Array<AssetDescriptor> dependencies = new Array<AssetDescriptor>();
		ModelData data = loadModelData(file, parameters);
		if (data == null) return dependencies;

		ObjectMap.Entry<String, ModelData> item = new ObjectMap.Entry<String, ModelData>();
		item.key = fileName;
		item.value = data;
		synchronized (items) {
			items.add(item);
		}

		TextureLoader.TextureParameter textureParameter = parameters != null ? parameters.textureParameter
			: defaultAssimpParameters.textureParameter;
		ObjectMap<String, AssimpEmbeddedTexture> embedded = data instanceof AssimpModelData
			? ((AssimpModelData)data).embeddedTextures
			: null;

		ObjectSet<String> seen = new ObjectSet<String>();
		for (ModelMaterial material : data.materials) {
			if (material.textures == null) continue;
			for (ModelTexture texture : material.textures) {
				if (embedded != null && embedded.containsKey(texture.fileName)) continue;
				if (!seen.add(texture.fileName)) continue;
				dependencies.add(new AssetDescriptor<Texture>(texture.fileName, Texture.class, textureParameter));
			}
		}
		// Extended materials can reference texture stacks the classic material model has no slot for
		// (metallic-roughness, occlusion, clearcoat, ...); those must be loaded as dependencies too.
		if (data instanceof AssimpModelData) {
			for (ObjectMap.Entry<String, AssimpMaterialInfo> entry : ((AssimpModelData)data).extendedMaterials) {
				for (AssimpTextureRef ref : entry.value.textures) {
					if (embedded != null && embedded.containsKey(ref.fileName)) continue;
					if (!seen.add(ref.fileName)) continue;
					dependencies.add(new AssetDescriptor<Texture>(ref.fileName, Texture.class, textureParameter));
				}
			}
		}
		return dependencies;
	}

	/** Called on the render thread once all dependencies are loaded: builds the Model (GL resources). Textures owned by the
	 * AssetManager are removed from the model's managed disposables so reference counting works; textures created from
	 * embedded data stay managed and are disposed together with the Model. */
	@Override
	public Model loadSync (AssetManager manager, String fileName, FileHandle file, AssimpLoaderParameters parameters) {
		ModelData data = null;
		synchronized (items) {
			for (int i = 0; i < items.size; i++) {
				if (items.get(i).key.equals(fileName)) {
					data = items.get(i).value;
					items.removeIndex(i);
					break;
				}
			}
		}
		if (data == null) return null;

		TextureProvider provider = wrapProvider(data, new TextureProvider.AssetTextureProvider(manager));
		final Model result = new Model(data, provider);

		Iterator<Disposable> disposables = result.getManagedDisposables().iterator();
		while (disposables.hasNext()) {
			Disposable disposable = disposables.next();
			if (disposable instanceof Texture && manager.containsAsset(disposable)) {
				disposables.remove();
			}
		}

		if (data instanceof AssimpModelData)
			AssimpMaterialMapper.applyToModel(result, (AssimpModelData)data, provider, manager);
		return result;
	}
}
