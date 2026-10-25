"""UnityPy backend for IEBundlePacker.

Supported operations are intentionally explicit: list assets, export Texture2D PNG,
export Mesh OBJ when UnityPy exposes it, export type-tree/raw data, replace Texture2D
from PNG, and serialize the modified environment. Encrypted bundles are unsupported.
"""
import json
import os
import traceback
import uuid

import UnityPy
from PIL import Image

_SESSIONS = {}


def _json(value):
    return json.dumps(value, ensure_ascii=False)


def _object_name(obj, data):
    name = getattr(data, "name", None)
    if name:
        return str(name)
    return f"{obj.type.name}_{obj.path_id}"


def _obj_info(obj):
    try:
        data = obj.read()
        name = _object_name(obj, data)
    except Exception:
        name = f"{obj.type.name}_{obj.path_id}"
    try:
        size = int(getattr(obj, "byte_size", 0) or 0)
    except Exception:
        size = 0
    return {"name": name, "type": str(obj.type.name), "id": str(obj.path_id), "sizeBytes": size}


def open_bundle(path):
    """Open a bundle from a local path and return JSON session + asset rows."""
    env = UnityPy.load(path)
    session_id = str(uuid.uuid4())
    objects = list(env.objects)
    _SESSIONS[session_id] = {"env": env, "path": path, "objects": {str(o.path_id): o for o in objects}}
    rows = [_obj_info(obj) for obj in objects]
    return _json({"ok": True, "session": session_id, "count": len(rows), "assets": rows,
                  "message": f"Loaded {len(rows)} objects"})


def _get(session, path_id):
    s = _SESSIONS.get(session)
    if not s:
        raise ValueError("This bundle session is no longer open. Reopen the bundle.")
    obj = s["objects"].get(str(path_id))
    if obj is None:
        raise ValueError("Asset path ID not found")
    return s, obj


def export_asset(session, path_id, output_path, format_name):
    s, obj = _get(session, path_id)
    data = obj.read()
    typ = str(obj.type.name)
    fmt = str(format_name).lower()
    if typ == "Texture2D" and fmt == "png":
        image = data.image
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
            payload = {"error": str(e), "type": typ, "path_id": str(path_id)}
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2, default=str)
    elif fmt == "raw":
        raw = obj.get_raw_data()
        with open(output_path, "wb") as f:
            f.write(raw)
    else:
        raise ValueError(f"Export format {fmt} is not supported for {typ}.")
    return _json({"ok": True, "path": output_path})


def replace_texture(session, path_id, png_path):
    s, obj = _get(session, path_id)
    if str(obj.type.name) != "Texture2D":
        raise ValueError("Only Texture2D assets can be replaced with PNG.")
    data = obj.read()
    with Image.open(png_path) as source:
        image = source.convert("RGBA")
    old = getattr(data, "image", None)
    if old is not None and old.size != image.size:
        raise ValueError(f"Image dimensions must match the original ({old.width}x{old.height}).")
    data.image = image
    data.save()
    return _json({"ok": True, "message": "Texture replaced in memory. Use SAVE AS to write the bundle."})


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
    return _json({"ready": True, "message": "UnityPy backend loaded"})
