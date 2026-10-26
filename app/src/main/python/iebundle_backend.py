"""UnityPy backend for IEBundlePacker.

Supported operations are intentionally explicit: list assets, export Texture2D PNG,
export Mesh OBJ when UnityPy exposes it, export type-tree/raw data, replace Texture2D
from PNG, and serialize the modified environment. Encrypted bundles are unsupported.
"""
import json
import os

UNITYPY_SOURCE_COMMIT = "43a8d0e014969a8b152590cd152a0e1badc3d35a"
import uuid



def _load_unitypy():
    """Load UnityPy only when bundle processing is requested.

    UnityPy's pure-Python source is vendored by the Android workflow. Its
    optional UnityPyBoost accelerator may be absent; upstream has a Python fallback.
    """
    try:
        import UnityPy
        return UnityPy
    except Exception as exc:
        raise RuntimeError(
            "UnityPy failed to load. Build through the included GitHub Actions workflow "
            "so the pinned UnityPy source is added before packaging. Details: " + str(exc)
        ) from exc

_SESSIONS = {}


def _json(value):
    return json.dumps(value, ensure_ascii=False)


def _object_name(obj, data):
    name = getattr(data, "name", None)
    if name:
        return str(name)
    return f"{obj.type.name}_{obj.path_id}"


def _obj_info(obj, handle):
    try:
        data = obj.read()
        name = _object_name(obj, data)
    except Exception:
        name = f"{obj.type.name}_{obj.path_id}"
    try:
        size = int(getattr(obj, "byte_size", 0) or 0)
    except Exception:
        size = 0
    return {"name": name, "type": str(obj.type.name), "id": str(obj.path_id), "handle": str(handle), "sizeBytes": size}


def open_bundle(path):
    """Open a bundle from a local path and return JSON session + asset rows."""
    UnityPy = _load_unitypy()
    env = UnityPy.load(path)
    session_id = str(uuid.uuid4())
    objects = list(env.objects)
    # path_id values are only unique within a SerializedFile. Use a session-local
    # handle for actions while continuing to display Unity's actual path ID.
    object_map = {}
    rows = []
    for index, obj in enumerate(objects):
        handle = str(index)
        object_map[handle] = obj
        rows.append(_obj_info(obj, handle))
    _SESSIONS[session_id] = {"env": env, "path": path, "objects": object_map}
    return _json({"ok": True, "session": session_id, "count": len(rows), "assets": rows,
                  "message": f"Loaded {len(rows)} objects"})


def _get(session, handle):
    s = _SESSIONS.get(session)
    if not s:
        raise ValueError("This bundle session is no longer open. Reopen the bundle.")
    obj = s["objects"].get(str(handle))
    if obj is None:
        raise ValueError("Asset handle not found. Reopen the bundle and try again.")
    return s, obj


def export_asset(session, handle, output_path, format_name):
    s, obj = _get(session, handle)
    typ = str(obj.type.name)
    fmt = str(format_name).lower()

    # Raw export must not require parsing the Unity object/type tree. This makes
    # it the recovery path for unsupported or malformed asset types.
    if fmt == "raw":
        raw = obj.get_raw_data()
        with open(output_path, "wb") as f:
            f.write(raw)
        return _json({"ok": True, "path": output_path, "bytes": len(raw)})

    data = obj.read()
    if typ == "Texture2D" and fmt == "png":
        try:
            image = data.image
        except NotImplementedError as exc:
            raise ValueError(str(exc)) from exc
        if image is None:
            raise ValueError("UnityPy could not decode this texture format on this device.")
        image.save(output_path, format="PNG")
    elif typ == "Mesh" and fmt == "obj":
        exported = None
        for candidate in (data, obj):
            try:
                exported = candidate.export()
                if exported:
                    break
            except Exception:
                pass
        if not exported:
            raise ValueError("OBJ export is unavailable for this mesh/version.")
        if isinstance(exported, bytes):
            with open(output_path, "wb") as f:
                f.write(exported)
        else:
            with open(output_path, "w", encoding="utf-8") as f:
                f.write(str(exported))
    elif fmt == "json":
        try:
            payload = obj.read_typetree()
        except Exception as e:
            payload = {"error": str(e), "type": typ, "path_id": str(obj.path_id)}
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2, default=str)
    else:
        raise ValueError(f"Export format {fmt} is not supported for {typ}.")
    return _json({"ok": True, "path": output_path})


def replace_texture(session, handle, png_path):
    s, obj = _get(session, handle)
    if str(obj.type.name) != "Texture2D":
        raise ValueError("Only Texture2D assets can be replaced with PNG.")
    data = obj.read()
    try:
        from PIL import Image
    except Exception as exc:
        raise RuntimeError(
            "PNG replacement is unavailable in this build because Pillow is not packaged."
        ) from exc
    with Image.open(png_path) as source:
        image = source.convert("RGBA")
    original_width = int(getattr(data, "m_Width", 0) or 0)
    original_height = int(getattr(data, "m_Height", 0) or 0)
    if original_width > 0 and original_height > 0 and image.size != (original_width, original_height):
        raise ValueError(
            f"Image dimensions must match the original ({original_width}x{original_height}). "
            "This prevents accidental atlas resizing and keeps references intact."
        )

    # Do not access data.image here: doing so forces a decode of the original
    # texture, which can be ETC/ASTC/crunched and require native decoder modules.
    # Re-encode replacement PNG pixels as Unity's uncompressed RGBA32 format.
    # This keeps replacement functional even when the source texture uses a
    # compressed format which this Android build cannot preview.
    try:
        from UnityPy.enums import TextureFormat
        data.set_image(image, target_format=TextureFormat.RGBA32)
    except Exception as exc:
        raise RuntimeError(
            "Could not encode the replacement PNG as RGBA32. Confirm this is a "
            "Texture2D asset and try a standard PNG. Details: " + str(exc)
        ) from exc
    data.save()
    return _json({"ok": True, "message": "Texture replaced as RGBA32 in memory. Use SAVE AS to write the bundle."})


def save_bundle(session, output_path):
    s = _SESSIONS.get(session)
    if not s:
        raise ValueError("No open bundle session")
    content = s["env"].file.save()
    if isinstance(content, str):
        content = content.encode("utf-8")
    with open(output_path, "wb") as f:
        f.write(content)
    return _json({"ok": True, "path": output_path, "bytes": len(content)})


def close_bundle(session):
    _SESSIONS.pop(session, None)
    return _json({"ok": True})


def backend_status():
    """Report actual backend capabilities without pretending optional codecs exist."""
    checks = {}
    errors = []
    try:
        import UnityPy
        checks["unitypy"] = True
        version = getattr(UnityPy, "__version__", "unknown")
    except Exception as exc:
        checks["unitypy"] = False
        version = "unavailable"
        errors.append("UnityPy import failed: " + str(exc))

    for key, module_name in (("lz4", "lz4.block"), ("brotli", "brotli"), ("pillow", "PIL.Image"),
                             ("attrs", "attrs"), ("fsspec", "fsspec")):
        try:
            __import__(module_name)
            checks[key] = True
        except Exception as exc:
            checks[key] = False
            errors.append(f"Missing dependency {module_name}: {exc}")

    ready = bool(checks.get("unitypy")) and all(checks.get(k, False) for k in ("lz4", "brotli", "pillow", "attrs", "fsspec"))
    message = (
        "UnityPy core ready: bundle inspection, JSON/raw export, Save As, and PNG replacement as RGBA32 are enabled. "
        "PNG preview/export of some ETC/ASTC/crunched formats and FMOD-only audio decoding need optional native codecs; "
        "unsupported formats will show a specific error."
        if ready else "AssetBundle backend is not ready. " + "; ".join(errors)
    )
    return _json({"ready": ready, "unitypyAvailable": bool(checks.get("unitypy")), "version": version,
                  "dependencies": checks, "nativeTextureCodecs": False, "message": message})
