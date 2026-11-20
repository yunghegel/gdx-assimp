package dev.yunghegel.gdx.assimp;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import dev.yunghegel.gdx.assimp.attributes.AssimpMaterialAttribute;

/** The complete destructured contents of one Assimp material: every raw property (see {@link AssimpMaterialProperty}) and
 * every texture reference (see {@link AssimpTextureRef}), independent of what gdx-assimp maps to LibGDX attributes by
 * default. Attached to each converted {@link com.badlogic.gdx.graphics.g3d.Material} as an
 * {@link AssimpMaterialAttribute}, so a custom renderer can query any material property
 * at render/setup time. */
public class AssimpMaterialInfo {

	/** Material id, equal to the {@code Material}/{@code ModelMaterial} id. */
	public String id;
	/** All raw properties Assimp parsed for this material. */
	public final Array<AssimpMaterialProperty> properties = new Array<AssimpMaterialProperty>();
	/** All texture references, across every texture stack. */
	public final Array<AssimpTextureRef> textures = new Array<AssimpTextureRef>();

	/** First property with the given key (any semantic), or null. */
	public AssimpMaterialProperty property (String key) {
		for (AssimpMaterialProperty property : properties)
			if (property.key.equals(key)) return property;
		return null;
	}

	/** Property with the given key, texture semantic and index, or null. */
	public AssimpMaterialProperty property (String key, int semantic, int index) {
		for (AssimpMaterialProperty property : properties)
			if (property.semantic == semantic && property.index == index && property.key.equals(key)) return property;
		return null;
	}

	public boolean has (String key) {
		return property(key) != null;
	}

	public float getFloat (String key, float defaultValue) {
		AssimpMaterialProperty property = property(key);
		return property == null ? defaultValue : property.asFloat(defaultValue);
	}

	public int getInt (String key, int defaultValue) {
		AssimpMaterialProperty property = property(key);
		return property == null ? defaultValue : property.asInt(defaultValue);
	}

	public String getString (String key) {
		AssimpMaterialProperty property = property(key);
		return property == null ? null : property.asString();
	}

	/** Color for the given key, or null if absent/not a color. */
	public Color getColor (String key) {
		AssimpMaterialProperty property = property(key);
		return property == null ? null : property.asColor();
	}

	/** First texture reference of the given {@code aiTextureType}, or null. */
	public AssimpTextureRef texture (int type) {
		for (AssimpTextureRef ref : textures)
			if (ref.type == type && ref.index == 0) return ref;
		return null;
	}
}
