"""Image uploads: type checks, re-encoding and size variants.

Pillow is used when installed: it verifies the image, strips metadata and writes WebP
variants. Without it, only the file signature is checked and the file is stored as uploaded.
"""
import io
import os
import secrets

try:
    from PIL import Image
except ImportError:  # pragma: no cover - depends on the host
    Image = None

SIGNATURES = {
    "png": (b"\x89PNG\r\n\x1a\n", "image/png"),
    "jpg": (b"\xff\xd8\xff", "image/jpeg"),
    "gif": (b"GIF8", "image/gif"),
    "webp": (b"RIFF", "image/webp"),
}
EXTENSIONS = {"png": "png", "jpg": "jpg", "jpeg": "jpg", "gif": "gif", "webp": "webp"}
MAX_PIXELS = 40000000
FULL_WIDTH = 1600
THUMB_WIDTH = 480


class UploadError(Exception):
    pass


def _detect(head):
    for ext, (magic, mime) in SIGNATURES.items():
        if head.startswith(magic):
            if ext == "webp" and head[8:12] != b"WEBP":
                continue
            return ext, mime
    return None, None


def save_upload(storage, upload_dir, max_bytes):
    name = storage.filename or ""
    ext = EXTENSIONS.get(name.rsplit(".", 1)[-1].lower()) if "." in name else None
    if not ext:
        raise UploadError("Upload a PNG, JPG, GIF or WebP image.")
    data = storage.read(max_bytes + 1)
    if len(data) > max_bytes:
        raise UploadError("That image is over %d MB. Resize it and try again." % (max_bytes // 1048576))
    detected, mime = _detect(data[:16])
    if detected is None or detected != ext:
        raise UploadError("That file isn't the image type its name says. Export it again and retry.")

    os.makedirs(upload_dir, exist_ok=True)
    stem = secrets.token_hex(10)
    result = {"original_name": os.path.basename(name)[:200], "width": None, "height": None}

    if Image is not None and detected != "gif":
        try:
            Image.MAX_IMAGE_PIXELS = MAX_PIXELS
            probe = Image.open(io.BytesIO(data))
            probe.verify()
            img = Image.open(io.BytesIO(data))
            img.load()
        except Exception:
            raise UploadError("That image couldn't be read. Export it again and retry.")
        img = img.convert("RGBA") if img.mode in ("RGBA", "LA", "P") else img.convert("RGB")
        full = _resized(img, FULL_WIDTH)
        thumb = _resized(img, THUMB_WIDTH)
        filename, thumbname = stem + ".webp", stem + "-thumb.webp"
        full.save(os.path.join(upload_dir, filename), "WEBP", quality=84, method=6)
        thumb.save(os.path.join(upload_dir, thumbname), "WEBP", quality=80, method=6)
        result.update(filename=filename, thumb=thumbname, mime="image/webp",
                      width=full.width, height=full.height,
                      bytes=os.path.getsize(os.path.join(upload_dir, filename)))
    else:
        filename = "%s.%s" % (stem, detected)
        with open(os.path.join(upload_dir, filename), "wb") as fh:
            fh.write(data)
        result.update(filename=filename, thumb="", mime=mime, bytes=len(data))
    return result


def _resized(img, width):
    if img.width <= width:
        return img.copy()
    height = max(1, int(round(img.height * width / float(img.width))))
    return img.resize((width, height), Image.LANCZOS)


def delete_files(upload_dir, row):
    for name in (row["filename"], row["thumb"]):
        if name:
            path = os.path.join(upload_dir, name)
            if os.path.isfile(path):
                os.remove(path)
