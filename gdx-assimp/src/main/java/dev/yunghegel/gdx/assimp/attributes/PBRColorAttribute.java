package dev.yunghegel.gdx.assimp.attributes;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;

/** Color attribute types for PBR material factors beyond the classic diffuse/ambient/specular/emissive/reflection colors
 * (which keep using the stock {@link ColorAttribute} types). */
public class PBRColorAttribute extends ColorAttribute {

	/** glTF PBR base color factor, including alpha. */
	public final static String BaseColorFactorAlias = "baseColorFactor";
	public final static long BaseColorFactor = register(BaseColorFactorAlias);

	public final static String SheenColorFactorAlias = "sheenColorFactor";
	public final static long SheenColorFactor = register(SheenColorFactorAlias);

	/** KHR_materials_volume attenuation color. */
	public final static String VolumeAttenuationColorAlias = "volumeAttenuationColor";
	public final static long VolumeAttenuationColor = register(VolumeAttenuationColorAlias);

	/** Classic transparent color (AI_MATKEY_COLOR_TRANSPARENT). */
	public final static String TransparentColorAlias = "transparentColor";
	public final static long TransparentColor = register(TransparentColorAlias);

	static {
		Mask |= BaseColorFactor | SheenColorFactor | VolumeAttenuationColor | TransparentColor;
	}

	public PBRColorAttribute (long type, Color color) {
		super(type, color);
	}

	public PBRColorAttribute (long type, float r, float g, float b, float a) {
		super(type, r, g, b, a);
	}
}
