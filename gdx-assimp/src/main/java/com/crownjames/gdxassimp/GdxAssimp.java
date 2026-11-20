package com.crownjames.gdxassimp;

import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.Assimp;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.utils.Array;

/** Entry-point utilities for the gdx-assimp importer. */
public final class GdxAssimp {

	private GdxAssimp () {
	}

	/** Returns the lower-case file extensions (without dot) the linked Assimp build can import, e.g. "fbx", "gltf", "obj". */
	public static Array<String> getSupportedExtensions () {
		AIString aiString = AIString.calloc();
		try {
			Assimp.aiGetExtensionList(aiString);
			// Format: "*.3ds;*.obj;*.fbx;..."
			Array<String> extensions = new Array<String>();
			for (String token : aiString.dataString().split(";")) {
				token = token.trim();
				if (token.startsWith("*.")) token = token.substring(2);
				if (!token.isEmpty()) extensions.add(token.toLowerCase());
			}
			return extensions;
		} finally {
			aiString.free();
		}
	}

	/** Whether Assimp can import files with the given extension (with or without leading dot, case-insensitive). */
	public static boolean supportsExtension (String extension) {
		if (extension.startsWith(".")) extension = extension.substring(1);
		return getSupportedExtensions().contains(extension.toLowerCase(), false);
	}

	/** The version of the linked Assimp native library, e.g. "5.3.1". */
	public static String getAssimpVersion () {
		return Assimp.aiGetVersionMajor() + "." + Assimp.aiGetVersionMinor() + "." + Assimp.aiGetVersionPatch();
	}

	/** Registers a single {@link AssimpModelLoader} (resolving internal files) with the manager for every file extension
	 * Assimp supports. Returns the registered loader. */
	public static AssimpModelLoader registerLoaders (AssetManager manager) {
		return registerLoaders(manager, new InternalFileHandleResolver());
	}

	/** Registers a single {@link AssimpModelLoader} with the manager for every file extension Assimp supports.
	 * <p>
	 * LibGDX's built-in loaders for ".g3db" and ".g3dj" are untouched (Assimp does not handle those formats). The stock
	 * ObjLoader registration for ".obj" is replaced by the Assimp loader, which handles OBJ far more completely. */
	public static AssimpModelLoader registerLoaders (AssetManager manager, FileHandleResolver resolver) {
		AssimpModelLoader loader = new AssimpModelLoader(resolver);
		for (String extension : getSupportedExtensions()) {
			manager.setLoader(Model.class, "." + extension, loader);
		}
		return loader;
	}
}
