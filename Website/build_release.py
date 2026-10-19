"""Build the cPanel upload bundle without local accounts, secrets or test data."""
import os
import zipfile


def build():
    root = os.path.dirname(os.path.abspath(__file__))
    target = os.path.join(os.path.dirname(root), "Website.zip")
    files = ["passenger_wsgi.py", "manage.py", "requirements.txt", "README.md", "CONTENT_REVIEW.md", "build_release.py"]
    for directory, folders, names in os.walk(os.path.join(root, "aerborne")):
        folders[:] = sorted(name for name in folders if name != "__pycache__")
        for name in sorted(names):
            if not name.endswith((".pyc", ".pyo")):
                files.append(os.path.relpath(os.path.join(directory, name), root))
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        for relative in files:
            archive.write(os.path.join(root, relative), relative.replace(os.sep, "/"))
    with zipfile.ZipFile(target) as archive:
        if archive.testzip() is not None:
            raise RuntimeError("Release archive failed verification")
    print("Built {} ({} files)".format(target, len(files)))


if __name__ == "__main__":
    build()
