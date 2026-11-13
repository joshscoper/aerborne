"""The Aerborne client download: whichever jar staff last uploaded from the dashboard, or the one shipped in
static/downloads until then. Players always get it as aerborne-client.jar from /client/download, so the page never
needs a version number and replacing the jar is a single upload."""
import hashlib
import io
import json
import os
import zipfile
from pathlib import Path

from flask import current_app

MAX_BYTES = 9 * 1024 * 1024
MOD_ID = "aerborne_client"
_SUMS = {}


class ClientUploadError(ValueError):
    pass


def uploaded_path():
    return Path(current_app.config["DATA_DIR"]) / "client" / "aerborne-client.jar"


def current_path():
    """The jar players get: the uploaded one, else the newest one shipped with the site."""
    uploaded = uploaded_path()
    if uploaded.is_file():
        return uploaded
    folder = Path(current_app.static_folder) / "downloads"
    jars = sorted(folder.glob("aerborne-client*.jar"), key=lambda p: p.stat().st_mtime, reverse=True) if folder.is_dir() else []
    return jars[0] if jars else None


def info():
    """Size, SHA-256 and mod version of the jar players get, or None. Cached until the file changes."""
    path = current_path()
    if path is None:
        return None
    stat = path.stat()
    stamp = (str(path), stat.st_mtime, stat.st_size)
    if _SUMS.get("stamp") != stamp:
        data = path.read_bytes()
        _SUMS.update(stamp=stamp, sha256=hashlib.sha256(data).hexdigest(), version=_version(data))
    return dict(path=path, size=round(stat.st_size / 1_048_576, 1), sha256=_SUMS["sha256"], version=_SUMS["version"],
                uploaded=path == uploaded_path())


def _version(data):
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            return str(json.loads(jar.read("fabric.mod.json")).get("version", ""))
    except (zipfile.BadZipFile, KeyError, ValueError):
        return ""


def check(data):
    """Refuses anything that isn't an Aerborne client jar: wrong size, not a zip, or another mod entirely."""
    if not data:
        raise ClientUploadError("Choose the client jar to upload.")
    if len(data) > MAX_BYTES:
        raise ClientUploadError("That file is too large for a client jar.")
    if not data.startswith(b"PK"):
        raise ClientUploadError("That isn't a jar file.")
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            if jar.testzip() is not None:
                raise ClientUploadError("That jar is damaged.")
            meta = json.loads(jar.read("fabric.mod.json"))
    except (zipfile.BadZipFile, KeyError, ValueError):
        raise ClientUploadError("That isn't a Fabric mod jar.")
    if meta.get("id") != MOD_ID:
        raise ClientUploadError("That jar is a different mod (%s), not the Aerborne client." % str(meta.get("id", "unknown"))[:40])
    return str(meta.get("version", ""))


def store(data):
    """Saves a checked jar as the download, keeping the one it replaces as aerborne-client.previous.jar."""
    target = uploaded_path()
    target.parent.mkdir(parents=True, exist_ok=True)
    temp = target.with_suffix(".uploading")
    temp.write_bytes(data)
    if target.is_file():
        os.replace(target, target.with_name("aerborne-client.previous.jar"))
    os.replace(temp, target)
