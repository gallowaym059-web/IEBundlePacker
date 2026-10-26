"""Compatibility stub for UnityPy's optional native texture decoder.

The Android build intentionally doesn't ship this desktop-oriented extension.
UnityPy's non-texture bundle operations and Pillow-supported texture formats
remain usable. Unsupported compressed texture exports fail with a clear error.
"""

class _UnavailableDecoder:
    def __init__(self, name="texture decoder"):
        self._name = name

    def __getattr__(self, name):
        return _UnavailableDecoder(f"{self._name}.{name}")

    def __call__(self, *args, **kwargs):
        raise NotImplementedError(
            f"{self._name} requires UnityPy's optional native texture2ddecoder "
            "extension, which is not included in this Android build. You can "
            "still export the asset as raw data or replace the texture with a PNG."
        )


def __getattr__(name):
    if name.startswith("__"):
        raise AttributeError(name)
    return _UnavailableDecoder(f"texture2ddecoder.{name}")
