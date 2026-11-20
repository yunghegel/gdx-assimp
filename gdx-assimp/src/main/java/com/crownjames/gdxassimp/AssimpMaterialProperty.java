package com.crownjames.gdxassimp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.badlogic.gdx.graphics.Color;

/** One raw Assimp material property ({@code aiMaterialProperty}): a key, a semantic (the {@code aiTextureType} it belongs to,
 * or 0 for non-texture properties), a texture index, a type tag and the raw value bytes. Every property Assimp parsed from the
 * source file is preserved, so renderers can access material data beyond the attributes gdx-assimp maps by default. */
public class AssimpMaterialProperty {

	/** Value type tags, matching assimp's aiPropertyTypeInfo. */
	public static final int TYPE_FLOAT = 1;
	public static final int TYPE_DOUBLE = 2;
	public static final int TYPE_STRING = 3;
	public static final int TYPE_INTEGER = 4;
	public static final int TYPE_BUFFER = 5;

	/** Property key, see {@link AssimpMatKeys}. */
	public final String key;
	/** For {@code $tex.*} properties, the {@code aiTextureType} this property belongs to; 0 otherwise. */
	public final int semantic;
	/** For {@code $tex.*} properties, the index in that texture stack; 0 otherwise. */
	public final int index;
	/** One of the TYPE_* constants. */
	public final int type;
	/** Raw value bytes in native byte order. */
	public final byte[] data;

	public AssimpMaterialProperty (String key, int semantic, int index, int type, byte[] data) {
		this.key = key;
		this.semantic = semantic;
		this.index = index;
		this.type = type;
		this.data = data;
	}

	private ByteBuffer buffer () {
		return ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
	}

	/** The number of scalar elements for FLOAT/DOUBLE/INTEGER properties. BUFFER properties (assimp stores some booleans and
	 * enums this way) count as 4-byte ints when a multiple of 4 bytes, or a single element for shorter payloads. */
	public int count () {
		switch (type) {
		case TYPE_FLOAT:
		case TYPE_INTEGER:
			return data.length / 4;
		case TYPE_DOUBLE:
			return data.length / 8;
		case TYPE_BUFFER:
			return data.length % 4 == 0 ? data.length / 4 : (data.length > 0 ? 1 : 0);
		default:
			return 0;
		}
	}

	public float[] asFloats () {
		if (type == TYPE_BUFFER) {
			int[] ints = asInts();
			float[] result = new float[ints.length];
			for (int i = 0; i < ints.length; i++)
				result[i] = ints[i];
			return result;
		}
		int n = count();
		float[] result = new float[n];
		ByteBuffer buffer = buffer();
		for (int i = 0; i < n; i++)
			result[i] = type == TYPE_DOUBLE ? (float)buffer.getDouble(i * 8)
				: type == TYPE_INTEGER ? buffer.getInt(i * 4) : buffer.getFloat(i * 4);
		return result;
	}

	public int[] asInts () {
		int n = count();
		int[] result = new int[n];
		ByteBuffer buffer = buffer();
		for (int i = 0; i < n; i++) {
			if (type == TYPE_DOUBLE)
				result[i] = (int)buffer.getDouble(i * 8);
			else if (type == TYPE_FLOAT)
				result[i] = (int)buffer.getFloat(i * 4);
			else if (type == TYPE_BUFFER && data.length % 4 != 0)
				result[i] = data[0] & 0xFF; // single-byte boolean, e.g. $mat.twosided
			else
				result[i] = buffer.getInt(i * 4);
		}
		return result;
	}

	public float asFloat (float defaultValue) {
		return count() >= 1 ? asFloats()[0] : defaultValue;
	}

	public int asInt (int defaultValue) {
		return count() >= 1 ? asInts()[0] : defaultValue;
	}

	/** Decodes an aiString property (4 byte length prefix + UTF-8 bytes + NUL). Null for non-string properties. */
	public String asString () {
		if (type != TYPE_STRING || data.length < 5) return null;
		int length = buffer().getInt(0);
		length = Math.max(0, Math.min(length, data.length - 4));
		return new String(data, 4, length, java.nio.charset.StandardCharsets.UTF_8);
	}

	/** Interprets a float[3]/float[4] property as a color (alpha defaults to 1). Null if fewer than 3 components. */
	public Color asColor () {
		float[] values = asFloats();
		if (values.length < 3) return null;
		return new Color(values[0], values[1], values[2], values.length >= 4 ? values[3] : 1f);
	}

	@Override
	public String toString () {
		return key + "[sem=" + semantic + ",idx=" + index + ",type=" + type + ",bytes=" + data.length + "]";
	}
}
