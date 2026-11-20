package com.crownjames.gdxassimp;

import com.badlogic.gdx.utils.GdxRuntimeException;

/** Thrown when Assimp fails to import a model or the imported scene cannot be converted. */
public class AssimpException extends GdxRuntimeException {

	public AssimpException (String message) {
		super(message);
	}

	public AssimpException (String message, Throwable cause) {
		super(message, cause);
	}
}
