package dev.yunghegel.gdx.assimp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMesh;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode;
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture;

public class ObjConversionTest {

	@Test
	public void convertsCubeWithMaterialAndTexture () {
		AssimpModelData data = TestUtils.convert("models/cube.obj");

		assertEquals(1, data.meshes.size);
		ModelMesh mesh = data.meshes.first();

		boolean hasPosition = false, hasNormal = false, hasUv = false;
		int floatsPerVertex = 0;
		for (VertexAttribute attribute : mesh.attributes) {
			floatsPerVertex += attribute.numComponents;
			if (attribute.usage == Usage.Position) hasPosition = true;
			if (attribute.usage == Usage.Normal) hasNormal = true;
			if (attribute.usage == Usage.TextureCoordinates) hasUv = true;
		}
		assertTrue("position attribute expected", hasPosition);
		assertTrue("normal attribute expected (generated if missing)", hasNormal);
		assertTrue("texture coordinates expected", hasUv);

		// 12 triangles
		assertEquals(36, mesh.parts[0].indices.length);
		assertEquals(0, mesh.vertices.length % floatsPerVertex);

		// material from cube.mtl
		ModelMaterial material = null;
		for (ModelMaterial candidate : data.materials)
			if ("RedPlastic".equals(candidate.id)) material = candidate;
		assertNotNull("RedPlastic material expected", material);
		assertEquals(0.8f, material.diffuse.r, 0.01f);
		assertEquals(0.1f, material.diffuse.g, 0.01f);
		assertEquals(0.1f, material.diffuse.b, 0.01f);
		assertTrue("shininess expected", material.shininess > 0f);

		assertNotNull("diffuse texture expected", material.textures);
		ModelTexture texture = material.textures.first();
		assertEquals(ModelTexture.USAGE_DIFFUSE, texture.usage);
		assertTrue("texture path should be relative to the model file: " + texture.fileName,
			texture.fileName.endsWith("models/cube.png"));

		// node referencing the mesh part with the material
		ModelNode meshNode = TestUtils.findNode(data, node -> node.parts != null && node.parts.length > 0);
		assertNotNull("a node with parts expected", meshNode);
		assertEquals(mesh.parts[0].id, meshNode.parts[0].meshPartId);
		assertEquals("RedPlastic", meshNode.parts[0].materialId);
	}

	@Test
	public void convertsAsciiStl () {
		AssimpModelData data = TestUtils.convert("models/tri.stl");
		assertEquals(1, data.meshes.size);
		ModelMesh mesh = data.meshes.first();
		assertEquals(3, mesh.parts[0].indices.length);
		boolean hasNormal = false;
		for (VertexAttribute attribute : mesh.attributes)
			if (attribute.usage == Usage.Normal) hasNormal = true;
		assertTrue(hasNormal);
		// STL has no materials; a default one must still be present and referenced.
		assertTrue(data.materials.size >= 1);
		ModelNode meshNode = TestUtils.findNode(data, node -> node.parts != null && node.parts.length > 0);
		assertNotNull(meshNode);
		assertNotNull(meshNode.parts[0].materialId);
	}
}
