package dev.yunghegel.gdx.assimp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.loaders.resolvers.AbsoluteFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;

/** Tests the AssetManager-facing parts of {@link AssimpModelLoader} that do not require a GL context: dependency
 * declaration (async phase) and embedded texture extraction. */
public class AssetLoaderTest {

	@Test
	public void declaresExternalTexturesAsDependencies () {
		AssimpModelLoader loader = new AssimpModelLoader(new AbsoluteFileHandleResolver());
		FileHandle file = TestUtils.resource("models/cube.obj");

		Array<AssetDescriptor> dependencies = loader.getDependencies(file.path(), file, null);

		assertEquals(1, dependencies.size);
		AssetDescriptor dependency = dependencies.first();
		assertEquals(Texture.class, dependency.type);
		assertTrue("dependency should point at the texture next to the model: " + dependency.fileName,
			dependency.fileName.endsWith("models/cube.png"));
		// the referenced texture actually exists where the dependency says
		assertTrue(new java.io.File(dependency.fileName).exists());
	}

	@Test
	public void embeddedTexturesAreExtractedAndNotDeclaredAsDependencies () {
		AssimpModelLoader loader = new AssimpModelLoader(new AbsoluteFileHandleResolver());
		FileHandle file = TestUtils.resource("models/embedded.gltf");

		Array<AssetDescriptor> dependencies = loader.getDependencies(file.path(), file, null);
		assertEquals("embedded textures must not become AssetManager dependencies", 0, dependencies.size);

		AssimpModelData data = TestUtils.convert("models/embedded.gltf");
		assertEquals(1, data.embeddedTextures.size);

		// the material references the embedded texture by its synthetic name
		boolean referenced = false;
		for (ModelMaterial material : data.materials) {
			if (material.textures == null) continue;
			if (data.embeddedTextures.containsKey(material.textures.first().fileName)) referenced = true;
		}
		assertTrue("material should reference the embedded texture", referenced);

		// and it decodes into a pixmap (pure CPU, no GL needed)
		GdxNativesLoader.load();
		AssimpEmbeddedTexture embedded = data.embeddedTextures.values().next();
		assertTrue(embedded.compressed);
		Pixmap pixmap = embedded.createPixmap();
		assertEquals(4, pixmap.getWidth());
		assertEquals(4, pixmap.getHeight());
		pixmap.dispose();
	}

	@Test
	public void supportedExtensionsIncludeMajorFormats () {
		Array<String> extensions = GdxAssimp.getSupportedExtensions();
		assertTrue(extensions.contains("obj", false));
		assertTrue(extensions.contains("fbx", false));
		assertTrue(extensions.contains("gltf", false));
		assertTrue(extensions.contains("glb", false));
		assertTrue(extensions.contains("stl", false));
		assertTrue(extensions.contains("dae", false));
		assertFalse(extensions.contains("g3db", false));

		assertTrue(GdxAssimp.supportsExtension(".FBX"));
		assertNotNull(GdxAssimp.getAssimpVersion());
	}

	@Test(expected = AssimpException.class)
	public void failsWithMeaningfulExceptionOnGarbage () {
		AssimpModelLoader loader = new AssimpModelLoader(new AbsoluteFileHandleResolver());
		// an mtl alone is not a loadable model
		loader.loadModelData(TestUtils.resource("models/cube.mtl"), null);
	}
}
