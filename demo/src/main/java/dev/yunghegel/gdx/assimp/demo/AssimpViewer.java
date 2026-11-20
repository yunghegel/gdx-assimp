package dev.yunghegel.gdx.assimp.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.resolvers.AbsoluteFileHandleResolver;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.ScreenUtils;
import dev.yunghegel.gdx.assimp.GdxAssimp;

/** Minimal model viewer: {@code gradlew :demo:run -Pmodel=path/to/model.fbx} (a relative path is taken from the
 * repository root), or {@code gradlew :demo:run --args="/absolute/path/to/model.fbx"}.
 * Loads the model through the AssetManager (async) with the Assimp loader registered for all supported formats,
 * frames it with an orbit camera and plays its first animation, if any. */
public class AssimpViewer extends ApplicationAdapter {

	private final String modelPath;

	private AssetManager assets;
	private ModelBatch batch;
	private PerspectiveCamera camera;
	private CameraInputController cameraController;
	private Environment environment;
	private ModelInstance instance;
	private AnimationController animationController;
	private boolean loaded;

	public AssimpViewer (String modelPath) {
		this.modelPath = modelPath;
	}

	@Override
	public void create () {
		assets = new AssetManager(new AbsoluteFileHandleResolver());
		GdxAssimp.registerLoaders(assets, new AbsoluteFileHandleResolver());
		Gdx.app.log("AssimpViewer", "Assimp " + GdxAssimp.getAssimpVersion() + ", "
			+ GdxAssimp.getSupportedExtensions().size + " supported extensions");
		assets.load(modelPath, Model.class);

		batch = new ModelBatch();
		camera = new PerspectiveCamera(60f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		camera.position.set(3f, 3f, 3f);
		camera.lookAt(0f, 0f, 0f);
		camera.near = 0.01f;
		camera.far = 1000f;
		camera.update();
		cameraController = new CameraInputController(camera);
		Gdx.input.setInputProcessor(cameraController);

		environment = new Environment();
		environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
		environment.add(new DirectionalLight().set(Color.WHITE, new Vector3(-1f, -0.8f, -0.2f)));
	}

	@Override
	public void render () {
		if (!loaded && assets.update()) {
			loaded = true;
			Model model = assets.get(modelPath, Model.class);
			instance = new ModelInstance(model);

			BoundingBox bounds = instance.calculateBoundingBox(new BoundingBox());
			Vector3 center = bounds.getCenter(new Vector3());
			float extent = Math.max(0.001f, bounds.getDimensions(new Vector3()).len());
			camera.position.set(center).add(extent, extent * 0.6f, extent);
			camera.lookAt(center);
			camera.up.set(Vector3.Y);
			camera.update();
			cameraController.target.set(center);

			if (model.animations.size > 0) {
				animationController = new AnimationController(instance);
				animationController.setAnimation(model.animations.first().id, -1);
				Gdx.app.log("AssimpViewer", "Playing animation: " + model.animations.first().id);
			}
			Gdx.app.log("AssimpViewer", "Loaded " + modelPath + ": " + model.meshes.size + " meshes, "
				+ model.materials.size + " materials, " + model.animations.size + " animations");
			for (com.badlogic.gdx.graphics.g3d.Material material : model.materials) {
				StringBuilder attributes = new StringBuilder();
				for (com.badlogic.gdx.graphics.g3d.Attribute attribute : material)
					attributes.append(com.badlogic.gdx.graphics.g3d.Attribute.getAttributeAlias(attribute.type)).append(' ');
				Gdx.app.log("AssimpViewer", "Material '" + material.id + "': " + attributes);
			}
		}

		if (animationController != null) animationController.update(Gdx.graphics.getDeltaTime());

		ScreenUtils.clear(0.15f, 0.15f, 0.18f, 1f, true);
		Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
		if (instance != null) {
			cameraController.update();
			batch.begin(camera);
			batch.render(instance, environment);
			batch.end();
		}
	}

	@Override
	public void resize (int width, int height) {
		camera.viewportWidth = width;
		camera.viewportHeight = height;
		camera.update();
	}

	@Override
	public void dispose () {
		batch.dispose();
		assets.dispose();
	}

	public static void main (String[] args) {
		if (args.length != 1) {
			System.err.println("Usage: AssimpViewer <absolute path to model file>");
			System.exit(1);
		}
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("gdx-assimp viewer");
		config.setWindowedMode(1280, 800);
		config.useVsync(true);
		new Lwjgl3Application(new AssimpViewer(args[0]), config);
	}
}
