"""Compatibility stub for UnityPy's optional native ASTC codec.

Texture replacement is written as RGBA32 and doesn't need ASTC encoding.
ASTC PNG decoding/export is unavailable in this build.
"""

class _UnavailableCodec:
    def __init__(self, name="ASTC codec"):
        self._name = name

    def __getattr__(self, name):
        return _UnavailableCodec(f"{self._name}.{name}")

    def __call__(self, *args, **kwargs):
        raise NotImplementedError(
            f"{self._name} requires UnityPy's optional native astc_encoder "
            "extension, which is not included in this Android build. Replace "
            "the texture with a PNG or export its raw data instead."
        )


def __getattr__(name):
    if name.startswith("__"):
        raise AttributeError(name)
    return _UnavailableCodec(f"astc_encoder.{name}")
