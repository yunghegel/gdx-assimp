package com.crownjames.gdxassimp;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.assimp.AIFile;
import org.lwjgl.assimp.AIFileCloseProc;
import org.lwjgl.assimp.AIFileIO;
import org.lwjgl.assimp.AIFileOpenProc;
import org.lwjgl.assimp.AIFileReadProc;
import org.lwjgl.assimp.AIFileSeek;
import org.lwjgl.assimp.AIFileTellProc;
import org.lwjgl.assimp.AIFileWriteProc;
import org.lwjgl.assimp.AIFileFlushProc;
import org.lwjgl.assimp.Assimp;
import org.lwjgl.system.MemoryUtil;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;

import java.nio.ByteBuffer;

/** Bridges Assimp's virtual file system ({@code aiFileIO}) to LibGDX {@link FileHandle}s. This lets Assimp resolve secondary
 * files (OBJ .mtl libraries, glTF .bin buffers, textures probed during import, ...) through the same LibGDX file abstraction
 * that provided the primary model file — including files packed inside jars.
 * <p>
 * An instance is intended to be used for a single import and disposed afterwards. Not thread safe; use one instance per
 * import. */
public class AssimpFileIO implements Disposable {

	private static class OpenFile {
		final AIFile struct;
		final ByteBuffer data;
		long position;

		OpenFile (AIFile struct, ByteBuffer data) {
			this.struct = struct;
			this.data = data;
		}
	}

	private final FileHandle primaryFile;
	private final String primaryPath;
	private final Map<Long, OpenFile> openFiles = new HashMap<Long, OpenFile>();

	private final AIFileIO fileIO;
	private final AIFileOpenProc openProc;
	private final AIFileCloseProc closeProc;
	private final AIFileReadProc readProc;
	private final AIFileWriteProc writeProc;
	private final AIFileTellProc tellProc;
	private final AIFileTellProc fileSizeProc;
	private final AIFileSeek seekProc;
	private final AIFileFlushProc flushProc;

	public AssimpFileIO (FileHandle primaryFile) {
		this.primaryFile = primaryFile;
		this.primaryPath = normalize(primaryFile.path());

		readProc = new AIFileReadProc() {
			@Override
			public long invoke (long pFile, long pBuffer, long size, long count) {
				OpenFile file = openFiles.get(pFile);
				if (file == null || size <= 0 || count <= 0) return 0;
				long remaining = file.data.limit() - file.position;
				if (remaining <= 0) return 0;
				long bytes = Math.min(size * count, remaining);
				MemoryUtil.memCopy(MemoryUtil.memAddress0(file.data) + file.position, pBuffer, bytes);
				file.position += bytes;
				return bytes / size;
			}
		};
		writeProc = new AIFileWriteProc() {
			@Override
			public long invoke (long pFile, long pBuffer, long memB, long count) {
				return 0; // read-only file system
			}
		};
		tellProc = new AIFileTellProc() {
			@Override
			public long invoke (long pFile) {
				OpenFile file = openFiles.get(pFile);
				return file == null ? 0 : file.position;
			}
		};
		fileSizeProc = new AIFileTellProc() {
			@Override
			public long invoke (long pFile) {
				OpenFile file = openFiles.get(pFile);
				return file == null ? 0 : file.data.limit();
			}
		};
		seekProc = new AIFileSeek() {
			@Override
			public int invoke (long pFile, long offset, int origin) {
				OpenFile file = openFiles.get(pFile);
				if (file == null) return Assimp.aiReturn_FAILURE;
				long target;
				switch (origin) {
				case Assimp.aiOrigin_SET:
					target = offset;
					break;
				case Assimp.aiOrigin_CUR:
					target = file.position + offset;
					break;
				case Assimp.aiOrigin_END:
					target = file.data.limit() + offset;
					break;
				default:
					return Assimp.aiReturn_FAILURE;
				}
				if (target < 0 || target > file.data.limit()) return Assimp.aiReturn_FAILURE;
				file.position = target;
				return Assimp.aiReturn_SUCCESS;
			}
		};
		flushProc = new AIFileFlushProc() {
			@Override
			public void invoke (long pFile) {
			}
		};
		openProc = new AIFileOpenProc() {
			@Override
			public long invoke (long pFileIO, long fileName, long openMode) {
				String path = MemoryUtil.memUTF8(fileName);
				FileHandle handle = resolve(path);
				if (handle == null) return MemoryUtil.NULL;
				byte[] bytes;
				try {
					bytes = handle.readBytes();
				} catch (Exception e) {
					return MemoryUtil.NULL;
				}
				ByteBuffer data = MemoryUtil.memAlloc(Math.max(1, bytes.length));
				data.put(bytes);
				data.flip();
				AIFile aiFile = AIFile.calloc() //
					.ReadProc(readProc) //
					.WriteProc(writeProc) //
					.TellProc(tellProc) //
					.FileSizeProc(fileSizeProc) //
					.SeekProc(seekProc) //
					.FlushProc(flushProc);
				openFiles.put(aiFile.address(), new OpenFile(aiFile, data));
				return aiFile.address();
			}
		};
		closeProc = new AIFileCloseProc() {
			@Override
			public void invoke (long pFileIO, long pFile) {
				OpenFile file = openFiles.remove(pFile);
				if (file != null) {
					MemoryUtil.memFree(file.data);
					file.struct.free();
				}
			}
		};

		fileIO = AIFileIO.calloc() //
			.OpenProc(openProc) //
			.CloseProc(closeProc);
	}

	/** The {@code aiFileIO} struct to pass to {@code aiImportFileEx(WithProperties)}. */
	public AIFileIO getFileIO () {
		return fileIO;
	}

	/** Resolves a path requested by Assimp to a FileHandle, or null if it does not exist. Assimp constructs secondary paths by
	 * combining the directory of the primary path with relative file names, so requests arrive in the same "path space" as the
	 * primary file and can be resolved the same way. */
	protected FileHandle resolve (String requestedPath) {
		String path = normalize(requestedPath);
		if (path.equals(primaryPath)) return primaryFile;

		// Same file-type resolution as the primary file (internal, absolute, classpath, ...).
		if (Gdx.files != null) {
			try {
				FileHandle handle = Gdx.files.getFileHandle(path, primaryFile.type());
				if (handle.exists() && !handle.isDirectory()) return handle;
			} catch (Exception ignored) {
			}
		}

		// Plain file system fallback (also covers running without a LibGDX application, e.g. unit tests).
		FileHandle direct = new FileHandle(new File(path));
		if (direct.exists() && !direct.isDirectory()) return direct;

		// Last resort: look for the bare file name next to the primary file. Helps with absolute paths
		// baked into FBX/DAE files on the author's machine.
		String name = path.substring(path.lastIndexOf('/') + 1);
		if (!name.isEmpty()) {
			FileHandle sibling = primaryFile.parent().child(name);
			if (sibling.exists() && !sibling.isDirectory()) return sibling;
		}
		return null;
	}

	private static String normalize (String path) {
		return path.replace('\\', '/');
	}

	@Override
	public void dispose () {
		// Assimp normally closes everything it opens; clean up defensively regardless.
		for (OpenFile file : openFiles.values()) {
			MemoryUtil.memFree(file.data);
			file.struct.free();
		}
		openFiles.clear();
		fileIO.free();
		openProc.free();
		closeProc.free();
		readProc.free();
		writeProc.free();
		tellProc.free();
		fileSizeProc.free();
		seekProc.free();
		flushProc.free();
	}
}
