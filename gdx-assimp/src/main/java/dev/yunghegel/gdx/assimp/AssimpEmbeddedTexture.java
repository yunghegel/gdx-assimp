package dev.yunghegel.gdx.assimp;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;

/** A texture embedded inside a model file (e.g. GLB binary blobs, data-URI images in glTF, embedded FBX media). Holds either
 * compressed image bytes (png/jpg/...) or raw BGRA8888 texel data, and can decode itself into a {@link Pixmap}. */
public class AssimpEmbeddedTexture {

	/** Compressed image bytes if {@link #compressed}, otherwise raw BGRA8888 texels (width * height * 4 bytes). */
	public final byte[] data;
	/** Only meaningful when {@link #compressed} is false. */
	public final int width, height;
	public final boolean compressed;
	/** Assimp's format hint, e.g. "png", "jpg". May be empty. */
	public final String formatHint;

	public AssimpEmbeddedTexture (byte[] data, int width, int height, boolean compressed, String formatHint) {
		this.data = data;
		this.width = width;
		this.height = height;
		this.compressed = compressed;
		this.formatHint = formatHint;
	}

	/** Decodes this embedded texture into a new Pixmap. The caller is responsible for disposing it. */
	public Pixmap createPixmap () {
		if (compressed) {
			return new Pixmap(data, 0, data.length);
		}
		Pixmap pixmap = new Pixmap(width, height, Format.RGBA8888);
		java.nio.ByteBuffer pixels = pixmap.getPixels();
		pixels.position(0);
		for (int i = 0, n = width * height * 4; i < n; i += 4) {
			byte b = data[i];
			byte g = data[i + 1];
			byte r = data[i + 2];
			byte a = data[i + 3];
			pixels.put(r).put(g).put(b).put(a);
		}
		pixels.position(0);
		return pixmap;
	}
}
