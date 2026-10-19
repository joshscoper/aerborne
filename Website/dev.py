"""Local development server with browser refresh for source, template and asset edits."""
import argparse
import hashlib
import json
import os

from flask import Response, jsonify

from aerborne import create_app

ROOT = os.path.dirname(os.path.abspath(__file__))
SOURCE = os.path.join(ROOT, "aerborne")


def source_version():
    digest = hashlib.sha256()
    for directory, folders, files in os.walk(SOURCE):
        folders[:] = sorted(folder for folder in folders if folder != "__pycache__")
        for name in sorted(files):
            path = os.path.join(directory, name)
            try:
                stat = os.stat(path)
            except OSError:
                continue
            digest.update((os.path.relpath(path, SOURCE) + ":" + str(stat.st_mtime_ns) + ":" + str(stat.st_size)).encode("utf-8"))
    return digest.hexdigest()[:20]


def development_app():
    app = create_app({"TEMPLATES_AUTO_RELOAD": True, "SEND_FILE_MAX_AGE_DEFAULT": 0,
                      "SESSION_COOKIE_SECURE": False})

    @app.route("/__dev/version")
    def version():
        return jsonify(version=source_version())

    @app.route("/__dev/reload.js")
    def reload_script():
        return Response("""(function () {
  var version = document.currentScript.getAttribute('data-version');
  var checking = false;
  setInterval(function () {
    if (checking || document.hidden) return;
    checking = true;
    fetch('/__dev/version', {cache: 'no-store'}).then(function (r) {
      if (!r.ok) throw new Error('Restarting');
      return r.json();
    }).then(function (data) {
      if (data.version !== version) location.reload();
    }).catch(function () {}).then(function () { checking = false; });
  }, 1000);
})();""", mimetype="application/javascript")

    @app.after_request
    def refresh_browser(response):
        response.headers["Cache-Control"] = "no-store"
        if response.mimetype == "text/html" and not response.direct_passthrough:
            script = '<script src="/__dev/reload.js" data-version=' + json.dumps(source_version()) + ' defer></script>'
            response.set_data(response.get_data(as_text=True).replace("</body>", script + "</body>"))
        return response

    return app


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=5050)
    args = parser.parse_args()
    development_app().run(host="127.0.0.1", port=args.port, debug=True,
                          use_debugger=False, use_reloader=True)
