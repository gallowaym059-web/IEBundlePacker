"""Compatibility stub for UnityPy's optional ETC/BC texture compressor."""

class _UnavailableCodec:
    def __init__(self, name="ETC/BC compressor"):
        self._name = name

    def __getattr__(self, name):
        return _UnavailableCodec(f"{self._name}.{name}")

    def __call__(self, *args, **kwargs):
        raise NotImplementedError(
            f"{self._name} requires UnityPy's optional native etcpak extension. "
            "This build replaces textures in RGBA32 format instead."
        )


def __getattr__(name):
    if name.startswith("__"):
        raise AttributeError(name)
    return _UnavailableCodec(f"etcpak.{name}")
