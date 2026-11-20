package com.crownjames.gdxassimp;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider;

/** A {@link TextureProvider} that resolves the synthetic names of textures embedded in the model file against an
 * {@link AssimpModelData}, and delegates everything else (regular texture files) to a fallback provider. Must be used on the
 * GL thread, like any TextureProvider. */
public class AssimpTextureProvider implements TextureProvider {

	private final AssimpModelData data;
	private final TextureProvider fallback;
	private final boolean useMipMaps;
	private final com.badlogic.gdx.utils.ObjectMap<String, Texture> embeddedCache = new com.badlogic.gdx.utils.ObjectMap<String, Texture>();

	public AssimpTextureProvider (AssimpModelData data, TextureProvider fallback) {
		this(data, fallback, true);
	}

	public AssimpTextureProvider (AssimpModelData data, TextureProvider fallback, boolean useMipMaps) {
		this.data = data;
		this.fallback = fallback;
		this.useMipMaps = useMipMaps;
	}

	@Override
	public Texture load (String fileName) {
		AssimpEmbeddedTexture embedded = data.embeddedTextures.get(fileName);
		if (embedded == null) return fallback.load(fileName);
		Texture cached = embeddedCache.get(fileName);
		if (cached != null) return cached;
		Pixmap pixmap = embedded.createPixmap();
		try {
			Texture texture = new Texture(pixmap, useMipMaps);
			texture.setFilter(useMipMaps ? Texture.TextureFilter.MipMapLinearLinear : Texture.TextureFilter.Linear,
				Texture.TextureFilter.Linear);
			texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
			embeddedCache.put(fileName, texture);
			return texture;
		} finally {
			pixmap.dispose();
		}
	}
}
