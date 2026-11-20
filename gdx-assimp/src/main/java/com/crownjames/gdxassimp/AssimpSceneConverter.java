package com.crownjames.gdxassimp;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIAnimation;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMaterialProperty;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AINodeAnim;
import org.lwjgl.assimp.AIPropertyStore;
import org.lwjgl.assimp.AIQuatKey;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.AIVectorKey;
import org.lwjgl.assimp.AIVertexWeight;
import org.lwjgl.assimp.Assimp;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.g3d.model.data.ModelAnimation;
import com.badlogic.gdx.graphics.g3d.model.data.ModelData;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMesh;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMeshPart;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodeAnimation;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart;
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ArrayMap;
import com.badlogic.gdx.utils.ObjectSet;

/** Imports a model file with Assimp and converts the resulting {@code aiScene} into a LibGDX {@link ModelData} — the same
 * intermediate representation produced by {@code G3dModelLoader}, so the result plugs directly into
 * {@link com.badlogic.gdx.graphics.g3d.Model} and the LibGDX material system.
 * <p>
 * A converter instance is single-use per {@link #convert(FileHandle, AssimpLoaderParameters)} call and not thread safe; create
 * one per import (imports themselves may run concurrently on different instances, Assimp's import API is reentrant). */
public class AssimpSceneConverter {

	private AssimpModelData data;
	private FileHandle modelFile;
	private String textureFolder;
	private AssimpLoaderParameters params;

	/** Names already used for nodes, to keep node ids unique (LibGDX looks nodes up by id for bones and animations). */
	private final ObjectSet<String> usedNodeIds = new ObjectSet<String>();
	private final ObjectSet<String> usedMeshIds = new ObjectSet<String>();
	private final ObjectSet<String> usedMaterialIds = new ObjectSet<String>();

	/** Per assimp-mesh conversion results, indexed like {@code aiScene.mMeshes}. */
	private String[] meshPartIds;
	private String[] meshMaterialIds;
	private ArrayMap<String, Matrix4>[] meshBones;

	/** Imports {@code file} and converts it. Throws {@link AssimpException} if Assimp cannot read the file. */
	public AssimpModelData convert (FileHandle file, AssimpLoaderParameters parameters) {
		this.params = parameters == null ? new AssimpLoaderParameters() : parameters;
		this.modelFile = file;
		String parent = file.parent().path();
		this.textureFolder = parent.isEmpty() ? "" : parent + "/";

		AssimpFileIO fileIO = new AssimpFileIO(file);
		AIPropertyStore props = Assimp.aiCreatePropertyStore();
		if (props == null) throw new AssimpException("Unable to create Assimp property store");
		try {
			Assimp.aiSetImportPropertyInteger(props, Assimp.AI_CONFIG_PP_SLM_VERTEX_LIMIT, Math.min(params.maxVertices, 65534));
			Assimp.aiSetImportPropertyInteger(props, Assimp.AI_CONFIG_PP_LBW_MAX_WEIGHTS, params.maxBoneWeights);
			Assimp.aiSetImportPropertyInteger(props, Assimp.AI_CONFIG_PP_SBP_REMOVE,
				Assimp.aiPrimitiveType_POINT | Assimp.aiPrimitiveType_LINE);
			Assimp.aiSetImportPropertyInteger(props, Assimp.AI_CONFIG_IMPORT_FBX_PRESERVE_PIVOTS,
				params.collapseFbxPivots ? 0 : 1);

			AIScene scene = Assimp.aiImportFileExWithProperties(file.path().replace('\\', '/'), params.getPostProcessFlags(),
				fileIO.getFileIO(), props);
			if (scene == null) throw new AssimpException("Assimp failed to import '" + file + "': " + Assimp.aiGetErrorString());
			try {
				return convertScene(scene);
			} finally {
				Assimp.aiReleaseImport(scene);
			}
		} finally {
			Assimp.aiReleasePropertyStore(props);
			fileIO.dispose();
		}
	}

	/** Synthetic file name used to reference embedded texture {@code index} of {@code modelFile} from ModelData materials. */
	public static String embeddedName (FileHandle modelFile, int index) {
		return modelFile.path() + "#embedded_" + index;
	}

	@SuppressWarnings("unchecked")
	private AssimpModelData convertScene (AIScene scene) {
		data = new AssimpModelData();
		data.id = modelFile.path();
		data.version[0] = 0;
		data.version[1] = 1;

		int numMeshes = scene.mNumMeshes();
		meshPartIds = new String[numMeshes];
		meshMaterialIds = new String[numMeshes];
		meshBones = new ArrayMap[numMeshes];

		convertMaterials(scene);
		convertMeshes(scene);
		if (scene.mRootNode() != null) {
			ModelNode root = convertNode(scene.mRootNode());
			data.nodes.add(root);
		}
		convertAnimations(scene);
		return data;
	}

	// --- Materials ------------------------------------------------------------------------------------------------

	private void convertMaterials (AIScene scene) {
		int numMaterials = scene.mNumMaterials();
		PointerBuffer materials = scene.mMaterials();
		for (int i = 0; i < numMaterials; i++) {
			AIMaterial aiMaterial = AIMaterial.create(materials.get(i));
			data.materials.add(convertMaterial(scene, aiMaterial, i));
		}
	}

	private ModelMaterial convertMaterial (AIScene scene, AIMaterial aiMaterial, int index) {
		ModelMaterial material = new ModelMaterial();
		material.type = ModelMaterial.MaterialType.Phong;

		AIString name = AIString.calloc();
		try {
			String id = null;
			if (Assimp.aiGetMaterialString(aiMaterial, Assimp.AI_MATKEY_NAME, Assimp.aiTextureType_NONE, 0,
				name) == Assimp.aiReturn_SUCCESS) {
				id = name.dataString();
			}
			if (id == null || id.isEmpty()) id = "material_" + index;
			material.id = uniqueId(usedMaterialIds, id);
		} finally {
			name.free();
		}

		AIColor4D color = AIColor4D.calloc();
		try {
			material.diffuse = getColor(aiMaterial, Assimp.AI_MATKEY_COLOR_DIFFUSE, color);
			material.ambient = getColor(aiMaterial, Assimp.AI_MATKEY_COLOR_AMBIENT, color);
			material.specular = getColor(aiMaterial, Assimp.AI_MATKEY_COLOR_SPECULAR, color);
			material.emissive = getColor(aiMaterial, Assimp.AI_MATKEY_COLOR_EMISSIVE, color);
			material.reflection = getColor(aiMaterial, Assimp.AI_MATKEY_COLOR_REFLECTIVE, color);
		} finally {
			color.free();
		}
		if (material.diffuse == null) material.diffuse = new Color(Color.WHITE);

		float[] floatValue = new float[1];
		Float shininess = getFloat(aiMaterial, Assimp.AI_MATKEY_SHININESS, floatValue);
		if (shininess != null && shininess > 0) material.shininess = shininess;
		Float opacity = getFloat(aiMaterial, Assimp.AI_MATKEY_OPACITY, floatValue);
		material.opacity = opacity == null ? 1f : Math.max(0f, Math.min(1f, opacity));

		convertTextures(scene, aiMaterial, material, Assimp.aiTextureType_DIFFUSE, ModelTexture.USAGE_DIFFUSE);
		// glTF PBR base color; only map it to diffuse when there is no classic diffuse texture.
		if (material.textures == null || material.textures.size == 0)
			convertTextures(scene, aiMaterial, material, Assimp.aiTextureType_BASE_COLOR, ModelTexture.USAGE_DIFFUSE);
		convertTextures(scene, aiMaterial, material, Assimp.aiTextureType_SPECULAR, ModelTexture.USAGE_SPECULAR);
		convertTextures(scene, aiMaterial, material, Assimp.aiTextureType_AMBIENT, ModelTexture.USAGE_AMBIENT);
		convertTextures(scene, aiMaterial, material, Assimp.aiTextureType_EMISSIVE, ModelTexture.USAGE_EMISSIVE);
		convertTextures(scene, aiMaterial, material, Assimp.aiTextureType_NORMALS, ModelTexture.USAGE_NORMAL);
		convertTextures(scene, aiMaterial, material, Assimp.aiTextureType_HEIGHT, ModelTexture.USAGE_BUMP);
		convertTextures(scene, aiMaterial, material, Assimp.aiTextureType_SHININESS, ModelTexture.USAGE_SHININESS);
		convertTextures(scene, aiMaterial, material, Assimp.aiTextureType_OPACITY, ModelTexture.USAGE_TRANSPARENCY);
		convertTextures(scene, aiMaterial, material, Assimp.aiTextureType_REFLECTION, ModelTexture.USAGE_REFLECTION);

		data.extendedMaterials.put(material.id, convertMaterialInfo(scene, aiMaterial, material.id));
		return material;
	}

	/** All texture stacks to enumerate for the extended material info. */
	private static final int[] ALL_TEXTURE_TYPES = {Assimp.aiTextureType_DIFFUSE, Assimp.aiTextureType_SPECULAR,
		Assimp.aiTextureType_AMBIENT, Assimp.aiTextureType_EMISSIVE, Assimp.aiTextureType_HEIGHT, Assimp.aiTextureType_NORMALS,
		Assimp.aiTextureType_SHININESS, Assimp.aiTextureType_OPACITY, Assimp.aiTextureType_DISPLACEMENT,
		Assimp.aiTextureType_LIGHTMAP, Assimp.aiTextureType_REFLECTION, Assimp.aiTextureType_BASE_COLOR,
		Assimp.aiTextureType_NORMAL_CAMERA, Assimp.aiTextureType_EMISSION_COLOR, Assimp.aiTextureType_METALNESS,
		Assimp.aiTextureType_DIFFUSE_ROUGHNESS, Assimp.aiTextureType_AMBIENT_OCCLUSION, Assimp.aiTextureType_UNKNOWN,
		Assimp.aiTextureType_SHEEN, Assimp.aiTextureType_CLEARCOAT, Assimp.aiTextureType_TRANSMISSION};

	/** Fully destructures the material: every raw {@code aiMaterialProperty} plus resolved texture references for every
	 * texture stack, including UV set, wrap modes and UV transforms. */
	private AssimpMaterialInfo convertMaterialInfo (AIScene scene, AIMaterial aiMaterial, String id) {
		AssimpMaterialInfo info = new AssimpMaterialInfo();
		info.id = id;

		PointerBuffer propertyPointers = aiMaterial.mProperties();
		for (int i = 0; i < aiMaterial.mNumProperties(); i++) {
			AIMaterialProperty property = AIMaterialProperty.create(propertyPointers.get(i));
			java.nio.ByteBuffer valueBuffer = property.mData();
			byte[] value = new byte[valueBuffer.remaining()];
			valueBuffer.get(value);
			info.properties.add(new AssimpMaterialProperty(property.mKey().dataString(), property.mSemantic(),
				property.mIndex(), property.mType(), value));
		}

		for (int type : ALL_TEXTURE_TYPES) {
			int count = Assimp.aiGetMaterialTextureCount(aiMaterial, type);
			for (int i = 0; i < count; i++) {
				AIString path = AIString.calloc();
				try {
					if (Assimp.aiGetMaterialTexture(aiMaterial, type, i, path, (IntBuffer)null, null, null, null, null,
						null) != Assimp.aiReturn_SUCCESS) continue;
					String texturePath = path.dataString();
					if (texturePath == null || texturePath.isEmpty()) continue;
					String fileName = resolveTexturePath(scene, texturePath);
					if (fileName == null) continue;

					AssimpTextureRef ref = new AssimpTextureRef();
					ref.type = type;
					ref.index = i;
					ref.fileName = fileName;

					AssimpMaterialProperty uvSource = info.property(AssimpMatKeys.TEX_UV_SOURCE, type, i);
					if (uvSource != null) ref.uvIndex = uvSource.asInt(0);
					AssimpMaterialProperty mapModeU = info.property(AssimpMatKeys.TEX_MAPMODE_U, type, i);
					if (mapModeU != null) ref.mapModeU = mapModeU.asInt(ref.mapModeU);
					AssimpMaterialProperty mapModeV = info.property(AssimpMatKeys.TEX_MAPMODE_V, type, i);
					if (mapModeV != null) ref.mapModeV = mapModeV.asInt(ref.mapModeV);
					AssimpMaterialProperty blend = info.property(AssimpMatKeys.TEX_BLEND, type, i);
					if (blend != null) ref.blend = blend.asFloat(1f);
					AssimpMaterialProperty op = info.property(AssimpMatKeys.TEX_OP, type, i);
					if (op != null) ref.op = op.asInt(ref.op);
					AssimpMaterialProperty uvTransform = info.property(AssimpMatKeys.TEX_UV_TRANSFORM, type, i);
					if (uvTransform != null) {
						float[] values = uvTransform.asFloats();
						if (values.length >= 5) {
							ref.hasUvTransform = true;
							ref.uvTranslationU = values[0];
							ref.uvTranslationV = values[1];
							ref.uvScaleU = values[2];
							ref.uvScaleV = values[3];
							ref.uvRotation = values[4];
						}
					}
					info.textures.add(ref);
				} finally {
					path.free();
				}
			}
		}
		return info;
	}

	private Color getColor (AIMaterial material, String key, AIColor4D tmp) {
		if (Assimp.aiGetMaterialColor(material, key, Assimp.aiTextureType_NONE, 0, tmp) == Assimp.aiReturn_SUCCESS) {
			return new Color(tmp.r(), tmp.g(), tmp.b(), 1f);
		}
		return null;
	}

	private Float getFloat (AIMaterial material, String key, float[] tmp) {
		int[] count = {1};
		if (Assimp.aiGetMaterialFloatArray(material, key, Assimp.aiTextureType_NONE, 0, tmp,
			count) == Assimp.aiReturn_SUCCESS && count[0] >= 1) {
			return tmp[0];
		}
		return null;
	}

	private void convertTextures (AIScene scene, AIMaterial aiMaterial, ModelMaterial material, int type, int usage) {
		int count = Assimp.aiGetMaterialTextureCount(aiMaterial, type);
		for (int i = 0; i < count; i++) {
			AIString path = AIString.calloc();
			try {
				if (Assimp.aiGetMaterialTexture(aiMaterial, type, i, path, (IntBuffer)null, null, null, null, null,
					null) != Assimp.aiReturn_SUCCESS) continue;
				String texturePath = path.dataString();
				if (texturePath == null || texturePath.isEmpty()) continue;

				ModelTexture texture = new ModelTexture();
				texture.usage = usage;
				texture.id = material.id + "_" + usage + "_" + i;
				texture.fileName = resolveTexturePath(scene, texturePath);
				if (texture.fileName == null) continue;
				texture.uvTranslation = new Vector2(0f, 0f);
				texture.uvScaling = new Vector2(1f, 1f);

				if (material.textures == null) material.textures = new Array<ModelTexture>();
				material.textures.add(texture);
			} finally {
				path.free();
			}
		}
	}

	/** Turns an Assimp texture reference into either a synthetic embedded-texture name or a path relative to the model file
	 * (the same convention G3dModelLoader uses, so the AssetManager can resolve it). */
	private String resolveTexturePath (AIScene scene, String texturePath) {
		int embeddedIndex = findEmbeddedTexture(scene, texturePath);
		if (embeddedIndex >= 0) {
			String syntheticName = embeddedName(modelFile, embeddedIndex);
			if (!data.embeddedTextures.containsKey(syntheticName)) {
				AssimpEmbeddedTexture embedded = extractEmbeddedTexture(
					AITexture.create(scene.mTextures().get(embeddedIndex)));
				if (embedded == null) return null;
				data.embeddedTextures.put(syntheticName, embedded);
			}
			return syntheticName;
		}

		String normalized = texturePath.replace('\\', '/');
		// Absolute paths (often baked in by DCC tools) can't be resolved portably; fall back to the bare name.
		boolean absolute = normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*");
		if (absolute) normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
		while (normalized.startsWith("./"))
			normalized = normalized.substring(2);
		return textureFolder + normalized;
	}

	/** Returns the index into {@code aiScene.mTextures} the given texture reference points at, or -1 if it is a regular
	 * external file. Assimp references embedded textures either as "*&lt;index&gt;" or (glTF) by the image's original name. */
	private int findEmbeddedTexture (AIScene scene, String texturePath) {
		if (scene.mNumTextures() == 0) return -1;
		if (texturePath.startsWith("*")) {
			try {
				int index = Integer.parseInt(texturePath.substring(1));
				if (index >= 0 && index < scene.mNumTextures()) return index;
			} catch (NumberFormatException ignored) {
			}
			return -1;
		}
		PointerBuffer textures = scene.mTextures();
		for (int i = 0; i < scene.mNumTextures(); i++) {
			AITexture texture = AITexture.create(textures.get(i));
			String fileName = texture.mFilename().dataString();
			if (!fileName.isEmpty() && (fileName.equals(texturePath) || texturePath.endsWith("/" + fileName))) return i;
		}
		return -1;
	}

	private AssimpEmbeddedTexture extractEmbeddedTexture (AITexture texture) {
		String hint = texture.achFormatHintString();
		if (texture.mHeight() == 0) {
			// Compressed image data (png/jpg/...), mWidth is the byte size.
			java.nio.ByteBuffer buffer = texture.pcDataCompressed();
			byte[] bytes = new byte[buffer.remaining()];
			buffer.get(bytes);
			return new AssimpEmbeddedTexture(bytes, 0, 0, true, hint);
		}
		int width = texture.mWidth();
		int height = texture.mHeight();
		// Raw texels are BGRA8888; copied verbatim, AssimpEmbeddedTexture converts on decode.
		java.nio.ByteBuffer texels = org.lwjgl.system.MemoryUtil.memByteBuffer(texture.pcData().address(), width * height * 4);
		byte[] bytes = new byte[width * height * 4];
		texels.get(bytes);
		return new AssimpEmbeddedTexture(bytes, width, height, false, hint);
	}

	// --- Meshes ---------------------------------------------------------------------------------------------------

	private void convertMeshes (AIScene scene) {
		PointerBuffer meshes = scene.mMeshes();
		for (int i = 0; i < scene.mNumMeshes(); i++) {
			AIMesh aiMesh = AIMesh.create(meshes.get(i));
			convertMesh(scene, aiMesh, i);
		}
	}

	private void convertMesh (AIScene scene, AIMesh aiMesh, int index) {
		int numVertices = aiMesh.mNumVertices();
		String baseName = aiMesh.mName().dataString();
		if (baseName == null || baseName.isEmpty()) baseName = "mesh_" + index;
		String meshId = uniqueId(usedMeshIds, baseName);
		String partId = meshId + "_part";

		int materialIndex = aiMesh.mMaterialIndex();
		meshMaterialIds[index] = materialIndex >= 0 && materialIndex < data.materials.size
			? data.materials.get(materialIndex).id
			: data.materials.first().id;
		meshPartIds[index] = partId;

		// Bones: collect per-vertex (boneIndex, weight) pairs, ordered per mesh like the nodepart bone map below.
		int numBones = aiMesh.mNumBones();
		float[][] vertexWeights = null; // [vertex] -> pairs (index, weight)
		int weightsPerVertex = 0;
		if (numBones > 0) {
			ArrayMap<String, Matrix4> bones = new ArrayMap<String, Matrix4>();
			List<List<float[]>> perVertex = new ArrayList<List<float[]>>(numVertices);
			for (int v = 0; v < numVertices; v++)
				perVertex.add(null);
			PointerBuffer bonePointers = aiMesh.mBones();
			for (int b = 0; b < numBones; b++) {
				AIBone bone = AIBone.create(bonePointers.get(b));
				String boneName = bone.mName().dataString();
				// ModelData stores the bind pose; Model inverts it again to get the inverse bind matrix
				// Assimp provides directly as mOffsetMatrix.
				Matrix4 bindPose = toGdx(bone.mOffsetMatrix(), new Matrix4()).inv();
				bones.put(boneName, bindPose);
				AIVertexWeight.Buffer weights = bone.mWeights();
				for (int w = 0; w < bone.mNumWeights(); w++) {
					AIVertexWeight weight = weights.get(w);
					int vertexId = weight.mVertexId();
					if (weight.mWeight() <= 0f || vertexId >= numVertices) continue;
					List<float[]> list = perVertex.get(vertexId);
					if (list == null) {
						list = new ArrayList<float[]>(4);
						perVertex.set(vertexId, list);
					}
					list.add(new float[] {b, weight.mWeight()});
				}
			}
			meshBones[index] = bones;

			vertexWeights = new float[numVertices][];
			for (int v = 0; v < numVertices; v++) {
				List<float[]> list = perVertex.get(v);
				if (list == null) {
					vertexWeights[v] = new float[0];
					continue;
				}
				// Highest influence first, cap at maxBoneWeights (aiProcess_LimitBoneWeights already does
				// this and renormalizes; this is a safety net for custom flag setups).
				list.sort( (a, bPair) -> Float.compare(bPair[1], a[1]));
				int n = Math.min(list.size(), params.maxBoneWeights);
				float[] flat = new float[n * 2];
				for (int k = 0; k < n; k++) {
					flat[k * 2] = list.get(k)[0];
					flat[k * 2 + 1] = list.get(k)[1];
				}
				vertexWeights[v] = flat;
				weightsPerVertex = Math.max(weightsPerVertex, n);
			}
			if (weightsPerVertex == 0) {
				// Bones declared but no actual weights; drop skinning info.
				meshBones[index] = null;
				vertexWeights = null;
			}
		}

		// Vertex attributes, in a fixed order the vertex writer below mirrors exactly.
		AIVector3D.Buffer positions = aiMesh.mVertices();
		AIVector3D.Buffer normals = aiMesh.mNormals();
		AIVector3D.Buffer tangents = aiMesh.mTangents();
		AIVector3D.Buffer bitangents = aiMesh.mBitangents();
		AIColor4D.Buffer colors = aiMesh.mColors(0);

		List<AIVector3D.Buffer> uvSets = new ArrayList<AIVector3D.Buffer>();
		for (int i = 0; i < Assimp.AI_MAX_NUMBER_OF_TEXTURECOORDS; i++) {
			AIVector3D.Buffer uv = aiMesh.mTextureCoords(i);
			if (uv != null) uvSets.add(uv);
		}

		Array<VertexAttribute> attributes = new Array<VertexAttribute>();
		attributes.add(VertexAttribute.Position());
		if (normals != null) attributes.add(VertexAttribute.Normal());
		if (colors != null) attributes.add(VertexAttribute.ColorUnpacked());
		for (int i = 0; i < uvSets.size(); i++)
			attributes.add(VertexAttribute.TexCoords(i));
		boolean hasTangents = tangents != null && bitangents != null;
		if (hasTangents) {
			attributes.add(VertexAttribute.Tangent());
			attributes.add(VertexAttribute.Binormal());
		}
		for (int i = 0; i < weightsPerVertex; i++)
			attributes.add(VertexAttribute.BoneWeight(i));

		int floatsPerVertex = 0;
		for (VertexAttribute attribute : attributes)
			floatsPerVertex += attribute.numComponents;

		float[] vertices = new float[numVertices * floatsPerVertex];
		int out = 0;
		for (int v = 0; v < numVertices; v++) {
			AIVector3D position = positions.get(v);
			vertices[out++] = position.x();
			vertices[out++] = position.y();
			vertices[out++] = position.z();
			if (normals != null) {
				AIVector3D normal = normals.get(v);
				vertices[out++] = normal.x();
				vertices[out++] = normal.y();
				vertices[out++] = normal.z();
			}
			if (colors != null) {
				AIColor4D color = colors.get(v);
				vertices[out++] = color.r();
				vertices[out++] = color.g();
				vertices[out++] = color.b();
				vertices[out++] = color.a();
			}
			for (AIVector3D.Buffer uvSet : uvSets) {
				AIVector3D uv = uvSet.get(v);
				vertices[out++] = uv.x();
				vertices[out++] = uv.y();
			}
			if (hasTangents) {
				AIVector3D tangent = tangents.get(v);
				vertices[out++] = tangent.x();
				vertices[out++] = tangent.y();
				vertices[out++] = tangent.z();
				AIVector3D bitangent = bitangents.get(v);
				vertices[out++] = bitangent.x();
				vertices[out++] = bitangent.y();
				vertices[out++] = bitangent.z();
			}
			if (weightsPerVertex > 0) {
				float[] weights = vertexWeights[v];
				for (int k = 0; k < weightsPerVertex; k++) {
					if (k * 2 + 1 < weights.length) {
						vertices[out++] = weights[k * 2];
						vertices[out++] = weights[k * 2 + 1];
					} else {
						vertices[out++] = 0f;
						vertices[out++] = 0f;
					}
				}
			}
		}

		// Indices: after aiProcess_Triangulate/SortByPType only triangle faces remain.
		int numFaces = aiMesh.mNumFaces();
		AIFace.Buffer faces = aiMesh.mFaces();
		int numIndices = 0;
		for (int f = 0; f < numFaces; f++)
			if (faces.get(f).mNumIndices() == 3) numIndices += 3;
		short[] indices = new short[numIndices];
		int outIndex = 0;
		for (int f = 0; f < numFaces; f++) {
			AIFace face = faces.get(f);
			if (face.mNumIndices() != 3) continue;
			IntBuffer faceIndices = face.mIndices();
			indices[outIndex++] = (short)(faceIndices.get(0) & 0xFFFF);
			indices[outIndex++] = (short)(faceIndices.get(1) & 0xFFFF);
			indices[outIndex++] = (short)(faceIndices.get(2) & 0xFFFF);
		}

		ModelMeshPart part = new ModelMeshPart();
		part.id = partId;
		part.primitiveType = GL20.GL_TRIANGLES;
		part.indices = indices;

		ModelMesh mesh = new ModelMesh();
		mesh.id = meshId;
		mesh.attributes = new VertexAttribute[attributes.size];
		for (int i = 0; i < attributes.size; i++)
			mesh.attributes[i] = attributes.get(i);
		mesh.vertices = vertices;
		mesh.parts = new ModelMeshPart[] {part};
		data.addMesh(mesh);
	}

	// --- Nodes ----------------------------------------------------------------------------------------------------

	private ModelNode convertNode (AINode aiNode) {
		ModelNode node = new ModelNode();
		String name = aiNode.mName().dataString();
		if (name == null || name.isEmpty()) name = "node";
		node.id = uniqueId(usedNodeIds, name);

		Matrix4 transform = toGdx(aiNode.mTransformation(), new Matrix4());
		node.translation = transform.getTranslation(new Vector3());
		node.rotation = transform.getRotation(new Quaternion(), true).nor();
		node.scale = transform.getScale(new Vector3());

		int numMeshes = aiNode.mNumMeshes();
		if (numMeshes > 0) {
			IntBuffer meshIndices = aiNode.mMeshes();
			Array<ModelNodePart> parts = new Array<ModelNodePart>();
			for (int i = 0; i < numMeshes; i++) {
				int meshIndex = meshIndices.get(i);
				if (meshIndex < 0 || meshIndex >= meshPartIds.length || meshPartIds[meshIndex] == null) continue;
				ModelNodePart part = new ModelNodePart();
				part.meshPartId = meshPartIds[meshIndex];
				part.materialId = meshMaterialIds[meshIndex];
				if (meshBones[meshIndex] != null) {
					part.bones = new ArrayMap<String, Matrix4>();
					part.bones.putAll(meshBones[meshIndex]);
				}
				parts.add(part);
			}
			if (parts.size > 0) {
				node.parts = new ModelNodePart[parts.size];
				for (int i = 0; i < parts.size; i++)
					node.parts[i] = parts.get(i);
			}
		}

		int numChildren = aiNode.mNumChildren();
		if (numChildren > 0) {
			PointerBuffer children = aiNode.mChildren();
			node.children = new ModelNode[numChildren];
			for (int i = 0; i < numChildren; i++)
				node.children[i] = convertNode(AINode.create(children.get(i)));
		}
		return node;
	}

	// --- Animations -----------------------------------------------------------------------------------------------

	private void convertAnimations (AIScene scene) {
		int numAnimations = scene.mNumAnimations();
		if (numAnimations == 0) return;
		PointerBuffer animations = scene.mAnimations();
		for (int i = 0; i < numAnimations; i++) {
			AIAnimation aiAnimation = AIAnimation.create(animations.get(i));
			ModelAnimation animation = new ModelAnimation();
			String name = aiAnimation.mName().dataString();
			animation.id = name == null || name.isEmpty() ? "animation_" + i : name;

			double ticksPerSecond = aiAnimation.mTicksPerSecond();
			if (ticksPerSecond <= 0) ticksPerSecond = 25.0;

			PointerBuffer channels = aiAnimation.mChannels();
			for (int c = 0; c < aiAnimation.mNumChannels(); c++) {
				AINodeAnim channel = AINodeAnim.create(channels.get(c));
				ModelNodeAnimation nodeAnimation = new ModelNodeAnimation();
				nodeAnimation.nodeId = channel.mNodeName().dataString();

				int numPositionKeys = channel.mNumPositionKeys();
				if (numPositionKeys > 0) {
					nodeAnimation.translation = new Array<ModelNodeKeyframe<Vector3>>();
					AIVectorKey.Buffer keys = channel.mPositionKeys();
					for (int k = 0; k < numPositionKeys; k++) {
						AIVectorKey key = keys.get(k);
						ModelNodeKeyframe<Vector3> keyframe = new ModelNodeKeyframe<Vector3>();
						keyframe.keytime = (float)(key.mTime() / ticksPerSecond);
						keyframe.value = new Vector3(key.mValue().x(), key.mValue().y(), key.mValue().z());
						nodeAnimation.translation.add(keyframe);
					}
				}
				int numRotationKeys = channel.mNumRotationKeys();
				if (numRotationKeys > 0) {
					nodeAnimation.rotation = new Array<ModelNodeKeyframe<Quaternion>>();
					AIQuatKey.Buffer keys = channel.mRotationKeys();
					for (int k = 0; k < numRotationKeys; k++) {
						AIQuatKey key = keys.get(k);
						ModelNodeKeyframe<Quaternion> keyframe = new ModelNodeKeyframe<Quaternion>();
						keyframe.keytime = (float)(key.mTime() / ticksPerSecond);
						keyframe.value = new Quaternion(key.mValue().x(), key.mValue().y(), key.mValue().z(), key.mValue().w());
						nodeAnimation.rotation.add(keyframe);
					}
				}
				int numScalingKeys = channel.mNumScalingKeys();
				if (numScalingKeys > 0) {
					nodeAnimation.scaling = new Array<ModelNodeKeyframe<Vector3>>();
					AIVectorKey.Buffer keys = channel.mScalingKeys();
					for (int k = 0; k < numScalingKeys; k++) {
						AIVectorKey key = keys.get(k);
						ModelNodeKeyframe<Vector3> keyframe = new ModelNodeKeyframe<Vector3>();
						keyframe.keytime = (float)(key.mTime() / ticksPerSecond);
						keyframe.value = new Vector3(key.mValue().x(), key.mValue().y(), key.mValue().z());
						nodeAnimation.scaling.add(keyframe);
					}
				}
				animation.nodeAnimations.add(nodeAnimation);
			}
			data.animations.add(animation);
		}
	}

	// --- Helpers --------------------------------------------------------------------------------------------------

	/** Converts a row-major Assimp matrix to a column-major LibGDX matrix (same logical matrix). */
	public static Matrix4 toGdx (AIMatrix4x4 m, Matrix4 out) {
		float[] val = out.val;
		val[Matrix4.M00] = m.a1();
		val[Matrix4.M01] = m.a2();
		val[Matrix4.M02] = m.a3();
		val[Matrix4.M03] = m.a4();
		val[Matrix4.M10] = m.b1();
		val[Matrix4.M11] = m.b2();
		val[Matrix4.M12] = m.b3();
		val[Matrix4.M13] = m.b4();
		val[Matrix4.M20] = m.c1();
		val[Matrix4.M21] = m.c2();
		val[Matrix4.M22] = m.c3();
		val[Matrix4.M23] = m.c4();
		val[Matrix4.M30] = m.d1();
		val[Matrix4.M31] = m.d2();
		val[Matrix4.M32] = m.d3();
		val[Matrix4.M33] = m.d4();
		return out;
	}

	private static String uniqueId (ObjectSet<String> used, String candidate) {
		String id = candidate;
		int suffix = 2;
		while (!used.add(id))
			id = candidate + "_" + suffix++;
		return id;
	}
}
