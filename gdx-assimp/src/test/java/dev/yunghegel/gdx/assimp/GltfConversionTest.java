package dev.yunghegel.gdx.assimp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.model.data.ModelAnimation;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMesh;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodeAnimation;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

public class GltfConversionTest {

	@Test
	public void convertsNodeAnimation () {
		AssimpModelData data = TestUtils.convert("models/animated.gltf");

		assertEquals(1, data.animations.size);
		ModelAnimation animation = data.animations.first();
		assertEquals("spin", animation.id);

		ModelNodeAnimation channel = null;
		for (ModelNodeAnimation candidate : animation.nodeAnimations)
			if ("spinner".equals(candidate.nodeId)) channel = candidate;
		assertNotNull("animation channel for node 'spinner' expected", channel);
		assertNotNull("rotation keyframes expected", channel.rotation);
		assertTrue(channel.rotation.size >= 2);

		// keytimes are seconds, first at 0, last at 1
		assertEquals(0f, channel.rotation.first().keytime, 0.001f);
		assertEquals(1f, channel.rotation.peek().keytime, 0.01f);

		// the animated node must exist in the hierarchy
		assertNotNull(TestUtils.findNode(data, node -> "spinner".equals(node.id)));
	}

	@Test
	public void convertsSkinnedMesh () {
		AssimpModelData data = TestUtils.convert("models/skinned.gltf");

		// mesh with bone weight attributes
		assertEquals(1, data.meshes.size);
		ModelMesh mesh = data.meshes.first();
		int boneWeightUnits = 0;
		for (VertexAttribute attribute : mesh.attributes)
			if (attribute.usage == Usage.BoneWeight) boneWeightUnits++;
		assertTrue("bone weight attributes expected", boneWeightUnits >= 1);
		assertTrue(boneWeightUnits <= 4);

		// the node part referencing the skinned mesh must carry the bone bind poses
		ModelNode meshNode = TestUtils.findNode(data,
			node -> node.parts != null && node.parts.length > 0 && node.parts[0].bones != null);
		assertNotNull("skinned node part expected", meshNode);
		assertEquals(2, meshNode.parts[0].bones.size);
		assertTrue(meshNode.parts[0].bones.containsKey("root_joint"));
		assertTrue(meshNode.parts[0].bones.containsKey("tip_joint"));

		// ModelData stores bind poses (Model re-inverts them); tip_joint sits at y=1
		Matrix4 tipBindPose = meshNode.parts[0].bones.get("tip_joint");
		Vector3 translation = tipBindPose.getTranslation(new Vector3());
		assertEquals(0f, translation.x, 0.001f);
		assertEquals(1f, translation.y, 0.001f);
		assertEquals(0f, translation.z, 0.001f);

		// joints must exist as nodes so animations/skinning can resolve them
		assertNotNull(TestUtils.findNode(data, node -> "root_joint".equals(node.id)));
		assertNotNull(TestUtils.findNode(data, node -> "tip_joint".equals(node.id)));

		// and the bend animation targets the tip joint
		assertEquals(1, data.animations.size);
		boolean animatesTip = false;
		for (ModelNodeAnimation channel : data.animations.first().nodeAnimations)
			if ("tip_joint".equals(channel.nodeId) && channel.rotation != null && channel.rotation.size >= 2)
				animatesTip = true;
		assertTrue("rotation animation on tip_joint expected", animatesTip);
	}
}
