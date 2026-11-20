package dev.yunghegel.gdx.assimp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.lwjgl.assimp.Assimp;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.loaders.resolvers.AbsoluteFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.utils.Array;
import dev.yunghegel.gdx.assimp.attributes.AssimpMaterialAttribute;
import dev.yunghegel.gdx.assimp.attributes.PBRColorAttribute;
import dev.yunghegel.gdx.assimp.attributes.PBRFloatAttribute;

/** Verifies the full destructuring of PBR material properties (metallic/roughness workflow plus KHR extensions) and their
 * mapping into LibGDX material attributes. */
public class PbrMaterialTest {

	private static final float EPS = 0.001f;

	private AssimpMaterialInfo fullPbr () {
		AssimpModelData data = TestUtils.convert("models/pbr.gltf");
		AssimpMaterialInfo info = data.extendedMaterials.get("FullPBR");
		assertNotNull("extended material info expected for FullPBR", info);
		return info;
	}

	@Test
	public void destructuresAllPbrScalarsAndColors () {
		AssimpMaterialInfo info = fullPbr();

		// metallic/roughness core
		assertEquals(0.7f, info.getFloat(AssimpMatKeys.METALLIC_FACTOR, -1f), EPS);
		assertEquals(0.3f, info.getFloat(AssimpMatKeys.ROUGHNESS_FACTOR, -1f), EPS);
		Color baseColor = info.getColor(AssimpMatKeys.BASE_COLOR);
		assertNotNull(baseColor);
		assertEquals(0.9f, baseColor.r, EPS);
		assertEquals(0.5f, baseColor.g, EPS);
		assertEquals(0.25f, baseColor.b, EPS);
		assertEquals(0.8f, baseColor.a, EPS);

		// KHR extensions
		assertEquals(1.4f, info.getFloat(AssimpMatKeys.REFRACTI, -1f), EPS);
		assertEquals(0.8f, info.getFloat(AssimpMatKeys.CLEARCOAT_FACTOR, -1f), EPS);
		assertEquals(0.2f, info.getFloat(AssimpMatKeys.CLEARCOAT_ROUGHNESS_FACTOR, -1f), EPS);
		assertEquals(0.6f, info.getFloat(AssimpMatKeys.TRANSMISSION_FACTOR, -1f), EPS);
		assertEquals(0.5f, info.getFloat(AssimpMatKeys.SHEEN_ROUGHNESS_FACTOR, -1f), EPS);
		assertEquals(3.0f, info.getFloat(AssimpMatKeys.EMISSIVE_INTENSITY, -1f), EPS);
		assertEquals(0.7f, info.getFloat(AssimpMatKeys.VOLUME_THICKNESS_FACTOR, -1f), EPS);
		assertEquals(2.5f, info.getFloat(AssimpMatKeys.VOLUME_ATTENUATION_DISTANCE, -1f), EPS);
		Color sheen = info.getColor(AssimpMatKeys.SHEEN_COLOR_FACTOR);
		assertNotNull(sheen);
		assertEquals(0.2f, sheen.r, EPS);
		assertEquals(0.3f, sheen.g, EPS);
		assertEquals(0.4f, sheen.b, EPS);
		Color attenuation = info.getColor(AssimpMatKeys.VOLUME_ATTENUATION_COLOR);
		assertNotNull(attenuation);
		assertEquals(0.6f, attenuation.r, EPS);

		// alpha and sidedness (twosided is stored by assimp as a 1-byte buffer property)
		assertEquals("MASK", info.getString(AssimpMatKeys.GLTF_ALPHA_MODE));
		assertEquals(0.4f, info.getFloat(AssimpMatKeys.GLTF_ALPHA_CUTOFF, -1f), EPS);
		assertEquals(1, info.getInt(AssimpMatKeys.TWO_SIDED, 0));
	}

	@Test
	public void destructuresTextureStacksWithUvTransformsAndWrapModes () {
		AssimpMaterialInfo info = fullPbr();

		AssimpTextureRef baseColor = info.texture(Assimp.aiTextureType_BASE_COLOR);
		assertNotNull("base color texture expected", baseColor);
		assertTrue(baseColor.fileName.endsWith("models/pbr.png"));
		assertEquals(0, baseColor.uvIndex);
		// KHR_texture_transform (assimp pre-bakes the rotation pivot into the offset; scale/rotation pass through)
		assertTrue("uv transform expected on base color texture", baseColor.hasUvTransform);
		assertEquals(2.0f, baseColor.uvScaleU, EPS);
		assertEquals(3.0f, baseColor.uvScaleV, EPS);
		assertEquals(0.5f, baseColor.uvRotation, EPS);
		// sampler: wrapS CLAMP_TO_EDGE, wrapT MIRRORED_REPEAT
		assertEquals(Assimp.aiTextureMapMode_Clamp, baseColor.mapModeU);
		assertEquals(Assimp.aiTextureMapMode_Mirror, baseColor.mapModeV);

		// every declared stack is present
		assertNotNull(info.texture(Assimp.aiTextureType_NORMALS));
		assertNotNull("occlusion (imported as LIGHTMAP) expected", info.texture(Assimp.aiTextureType_LIGHTMAP));
		assertNotNull(info.texture(Assimp.aiTextureType_EMISSIVE));
		AssimpTextureRef metalness = info.texture(Assimp.aiTextureType_METALNESS);
		AssimpTextureRef roughness = info.texture(Assimp.aiTextureType_DIFFUSE_ROUGHNESS);
		assertNotNull(metalness);
		assertNotNull(roughness);
		assertEquals("combined metallic-roughness map", metalness.fileName, roughness.fileName);

		// per-texture scalar properties stay accessible through the raw property bag
		AssimpMaterialProperty normalScale = info.property(AssimpMatKeys.TEX_SCALE, Assimp.aiTextureType_NORMALS, 0);
		assertNotNull(normalScale);
		assertEquals(0.8f, normalScale.asFloat(-1f), EPS);
		AssimpMaterialProperty occlusionStrength = info.property(AssimpMatKeys.TEX_STRENGTH, Assimp.aiTextureType_LIGHTMAP, 0);
		assertNotNull(occlusionStrength);
		assertEquals(0.9f, occlusionStrength.asFloat(-1f), EPS);
	}

	@Test
	public void mapsScalarsToMaterialAttributes () {
		AssimpMaterialInfo info = fullPbr();

		Material material = new Material();
		// null-returning resolver: texture attributes are skipped, everything else must still map (no GL needed)
		AssimpMaterialMapper.apply(material, info, new AssimpMaterialMapper.TextureResolver() {
			@Override
			public com.badlogic.gdx.graphics.Texture load (String fileName) {
				return null;
			}
		});

		PBRFloatAttribute metallic = material.get(PBRFloatAttribute.class, PBRFloatAttribute.Metallic);
		assertNotNull(metallic);
		assertEquals(0.7f, metallic.value, EPS);
		PBRFloatAttribute roughness = material.get(PBRFloatAttribute.class, PBRFloatAttribute.Roughness);
		assertNotNull(roughness);
		assertEquals(0.3f, roughness.value, EPS);
		PBRFloatAttribute ior = material.get(PBRFloatAttribute.class, PBRFloatAttribute.IOR);
		assertNotNull(ior);
		assertEquals(1.4f, ior.value, EPS);
		PBRFloatAttribute clearcoat = material.get(PBRFloatAttribute.class, PBRFloatAttribute.ClearcoatFactor);
		assertNotNull(clearcoat);
		assertEquals(0.8f, clearcoat.value, EPS);
		PBRFloatAttribute transmission = material.get(PBRFloatAttribute.class, PBRFloatAttribute.TransmissionFactor);
		assertNotNull(transmission);
		assertEquals(0.6f, transmission.value, EPS);
		PBRFloatAttribute emissiveIntensity = material.get(PBRFloatAttribute.class, PBRFloatAttribute.EmissiveIntensity);
		assertNotNull(emissiveIntensity);
		assertEquals(3.0f, emissiveIntensity.value, EPS);

		PBRColorAttribute baseColor = material.get(PBRColorAttribute.class, PBRColorAttribute.BaseColorFactor);
		assertNotNull(baseColor);
		assertEquals(0.9f, baseColor.color.r, EPS);
		assertEquals(0.8f, baseColor.color.a, EPS);
		PBRColorAttribute sheenColor = material.get(PBRColorAttribute.class, PBRColorAttribute.SheenColorFactor);
		assertNotNull(sheenColor);
		assertEquals(0.3f, sheenColor.color.g, EPS);

		// alphaMode MASK -> alpha test with the declared cutoff
		FloatAttribute alphaTest = material.get(FloatAttribute.class, FloatAttribute.AlphaTest);
		assertNotNull(alphaTest);
		assertEquals(0.4f, alphaTest.value, EPS);

		// doubleSided -> disable culling
		IntAttribute cullFace = material.get(IntAttribute.class, IntAttribute.CullFace);
		assertNotNull(cullFace);
		assertEquals(GL20.GL_NONE, cullFace.value);

		// the complete raw material rides along on the Material
		AssimpMaterialAttribute raw = material.get(AssimpMaterialAttribute.class, AssimpMaterialAttribute.AssimpMaterial);
		assertNotNull(raw);
		assertEquals(0.9f, raw.info.property(AssimpMatKeys.TEX_STRENGTH, Assimp.aiTextureType_LIGHTMAP, 0).asFloat(-1f), EPS);
	}

	@Test
	public void extendedTexturesBecomeAssetDependenciesOnce () {
		AssimpModelLoader loader = new AssimpModelLoader(new AbsoluteFileHandleResolver());
		FileHandle file = TestUtils.resource("models/pbr.gltf");

		Array<AssetDescriptor> dependencies = loader.getDependencies(file.path(), file, null);

		// pbr.png feeds eight texture stacks but must be declared exactly once
		assertEquals(1, dependencies.size);
		assertTrue(dependencies.first().fileName.endsWith("models/pbr.png"));
	}

	@Test
	public void plainMaterialsHaveNoSpuriousPbrAttributes () {
		AssimpModelData data = TestUtils.convert("models/cube.obj");
		AssimpMaterialInfo info = data.extendedMaterials.get("RedPlastic");
		assertNotNull(info);

		Material material = new Material();
		AssimpMaterialMapper.apply(material, info, null);

		assertNull(material.get(PBRColorAttribute.class, PBRColorAttribute.BaseColorFactor));
		assertNull(material.get(FloatAttribute.class, FloatAttribute.AlphaTest));
		// classic keys still destructure
		assertEquals(64f, info.getFloat(AssimpMatKeys.SHININESS, -1f), EPS);
	}
}
