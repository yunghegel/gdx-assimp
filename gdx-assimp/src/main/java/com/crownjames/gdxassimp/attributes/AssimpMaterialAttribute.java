package com.crownjames.gdxassimp.attributes;

import com.badlogic.gdx.graphics.g3d.Attribute;
import com.crownjames.gdxassimp.AssimpMaterialInfo;

/** Carries the complete destructured Assimp material ({@link AssimpMaterialInfo}) on a converted
 * {@link com.badlogic.gdx.graphics.g3d.Material}. Renderers that need material data beyond the mapped attributes — custom
 * scalar properties, extra texture layers, shading model hints, format-specific keys — can fetch it with:
 *
 * <pre>
 * AssimpMaterialAttribute raw = material.get(AssimpMaterialAttribute.class, AssimpMaterialAttribute.AssimpMaterial);
 * if (raw != null) {
 * 	float anisotropy = raw.info.getFloat(AssimpMatKeys.ANISOTROPY_FACTOR, 0f);
 * }
 * </pre> */
public class AssimpMaterialAttribute extends Attribute {

	public final static String AssimpMaterialAlias = "assimpMaterial";
	public final static long AssimpMaterial = register(AssimpMaterialAlias);

	public final AssimpMaterialInfo info;

	public AssimpMaterialAttribute (AssimpMaterialInfo info) {
		super(AssimpMaterial);
		this.info = info;
	}

	@Override
	public Attribute copy () {
		return new AssimpMaterialAttribute(info);
	}

	@Override
	public int compareTo (Attribute o) {
		if (type != o.type) return type < o.type ? -1 : 1;
		AssimpMaterialInfo otherInfo = ((AssimpMaterialAttribute)o).info;
		if (info == otherInfo) return 0;
		return System.identityHashCode(info) < System.identityHashCode(otherInfo) ? -1 : 1;
	}

	@Override
	public int hashCode () {
		return 947 * super.hashCode() + System.identityHashCode(info);
	}
}
