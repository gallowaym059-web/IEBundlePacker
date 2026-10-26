"""Compatibility stub for UnityPy's optional FMOD audio decoder."""

class _UnavailableAudioDecoder:
    def __init__(self, name="FMOD decoder"):
        self._name = name

    def __getattr__(self, name):
        return _UnavailableAudioDecoder(f"{self._name}.{name}")

    def __call__(self, *args, **kwargs):
        raise NotImplementedError(
            "FMOD-only audio decoding needs UnityPy's optional fmod_toolkit "
            "native dependency, which is not bundled in this Android build. "
            "Raw audio asset export is still available."
        )


def __getattr__(name):
    if name.startswith("__"):
        raise AttributeError(name)
    return _UnavailableAudioDecoder(f"fmod_toolkit.{name}")
