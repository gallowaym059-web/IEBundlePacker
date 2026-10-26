"""Compatibility stub for UnityPy's optional TPK decoder."""

class _UnavailableTPK:
    def __init__(self, name="TPK decoder"):
        self._name = name

    def __getattr__(self, name):
        return _UnavailableTPK(f"{self._name}.{name}")

    def __call__(self, *args, **kwargs):
        raise NotImplementedError(
            "This asset uses the optional TPK decoder, which is not bundled "
            "in the Android build. Other supported asset types can still be read."
        )


def __getattr__(name):
    if name.startswith("__"):
        raise AttributeError(name)
    return _UnavailableTPK(f"tpk_ar.{name}")
