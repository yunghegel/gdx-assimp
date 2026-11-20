package com.crownjames.gdxassimp;

import java.io.File;
import java.net.URL;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g3d.model.data.ModelData;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode;

public final class TestUtils {

	private TestUtils () {
	}

	/** Resolves a test resource (copied by Gradle into build/resources/test) to an absolute FileHandle. */
	public static FileHandle resource (String path) {
		URL url = TestUtils.class.getClassLoader().getResource(path);
		if (url == null) throw new IllegalStateException("Missing test resource: " + path);
		try {
			return new FileHandle(new File(url.toURI()));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static AssimpModelData convert (String path) {
		return convert(path, null);
	}

	public static AssimpModelData convert (String path, AssimpLoaderParameters params) {
		return new AssimpSceneConverter().convert(resource(path), params);
	}

	/** Depth-first search for a node satisfying the predicate. */
	public static ModelNode findNode (ModelData data, NodePredicate predicate) {
		for (ModelNode node : data.nodes) {
			ModelNode found = findNode(node, predicate);
			if (found != null) return found;
		}
		return null;
	}

	private static ModelNode findNode (ModelNode node, NodePredicate predicate) {
		if (predicate.test(node)) return node;
		if (node.children != null) {
			for (ModelNode child : node.children) {
				ModelNode found = findNode(child, predicate);
				if (found != null) return found;
			}
		}
		return null;
	}

	public interface NodePredicate {
		boolean test (ModelNode node);
	}
}
