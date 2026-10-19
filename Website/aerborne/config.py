"""Site settings, read from environment variables.

On cPanel, set them under Setup Python App > your app > Environment variables, then restart.
Content (news, guides, companions, store items) is edited in the staff admin, not here.
"""
import os
import secrets

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _env(name, default=""):
    value = os.environ.get(name)
    if value is None:
        return default
    return value.strip()


def _env_int(name, default):
    try:
        return int(_env(name, str(default)))
    except ValueError:
        return default


def _env_bool(name, default):
    value = _env(name, "")
    if not value:
        return default
    return value.lower() in ("1", "true", "yes", "on")


def _secret_key(data_dir):
    """Use AERBORNE_SECRET_KEY, or create one random key and keep it in the data folder."""
    key = _env("AERBORNE_SECRET_KEY")
    if key:
        return key
    path = os.path.join(data_dir, "secret_key")
    try:
        with open(path) as fh:
            key = fh.read().strip()
        if key:
            return key
    except IOError:
        pass
    os.makedirs(data_dir, exist_ok=True)
    key = secrets.token_hex(32)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as fh:
        fh.write(key)
    return key


def load_config():
    data_dir = _env("AERBORNE_DATA_DIR", os.path.join(BASE_DIR, "instance"))
    return {
        # Storage. Keep the data folder outside public_html.
        "DATA_DIR": data_dir,
        "DATABASE": os.path.join(data_dir, "aerborne.sqlite3"),
        "UPLOAD_DIR": os.path.join(data_dir, "uploads"),
        "SECRET_KEY": _secret_key(data_dir),
        "MAX_CONTENT_LENGTH": 10 * 1024 * 1024,
        "UPLOAD_MAX_BYTES": 8 * 1024 * 1024,

        # Sessions are used by staff in the admin and by signed-in forum members.
        "SESSION_COOKIE_NAME": "aerborne_session",
        "SESSION_COOKIE_HTTPONLY": True,
        "SESSION_COOKIE_SAMESITE": "Lax",
        "SESSION_COOKIE_SECURE": _env_bool("AERBORNE_SECURE_COOKIES", True),
        "PERMANENT_SESSION_LIFETIME": 60 * 60 * 12,
        # One-time token that unlocks /admin/setup while no staff accounts exist.
        "SETUP_TOKEN": _env("AERBORNE_SETUP_TOKEN"),

        # Public identity.
        "SITE_NAME": _env("AERBORNE_SITE_NAME", "Aerborne"),
        "SITE_URL": _env("AERBORNE_SITE_URL").rstrip("/"),
        "TAGLINE": _env("AERBORNE_TAGLINE", "Small beginnings. Endless skies."),
        "SUPPORT_LINE": _env("AERBORNE_SUPPORT_LINE", "Your adventure begins above the clouds."),
        "MOTD_LINE_1": _env("AERBORNE_MOTD_LINE_1", "A Skyblock RPG Adventure"),
        "MOTD_LINE_2": _env("AERBORNE_MOTD_LINE_2",
                            "Build your island. Explore the skies. Discover magic."),

        # Connection details. Blank Java address shows "coming soon".
        "SERVER_ADDRESS": _env("AERBORNE_SERVER_ADDRESS", "play.aerborne.net"),
        "SERVER_PORT": _env_int("AERBORNE_SERVER_PORT", 25565),
        "MINECRAFT_VERSIONS": _env("AERBORNE_MINECRAFT_VERSIONS", "26.2"),
        # Aerborne is Java-only. Only set these once Bedrock access is verified end to end.
        "BEDROCK_ADDRESS": _env("AERBORNE_BEDROCK_ADDRESS"),
        "BEDROCK_PORT": _env_int("AERBORNE_BEDROCK_PORT", 19132),

        # The plugin's web API. Public reads need no token; linking accounts needs the private
        # token from plugins/NerdSMP/security/web-api-token.txt. Never put the token in templates.
        "API_URL": _env("AERBORNE_API_URL", "https://api.aerborne.net"),
        "API_TOKEN": _env("AERBORNE_API_TOKEN"),
        "API_TIMEOUT": _env_int("AERBORNE_API_TIMEOUT", 4),

        # Watchdog staff panel. The panel fetches website admins from /admin/api/panel-accounts using
        # this token (from plugins/NerdSMP/security/watchdog-website-token.txt). Blank turns sync off.
        "PANEL_URL": _env("AERBORNE_PANEL_URL", "https://panel.aerborne.net"),
        "PANEL_SYNC_TOKEN": _env("AERBORNE_PANEL_SYNC_TOKEN"),
        # The game server collects Arcade payouts with this token (plugins/NerdSMP/security/website-rewards-token.txt).
        "REWARDS_TOKEN": _env("AERBORNE_REWARDS_TOKEN"),

        # Live status checks.
        "STATUS_HOST": _env("AERBORNE_STATUS_HOST"),
        "STATUS_PORT": _env_int("AERBORNE_STATUS_PORT", 0),
        "STATUS_TIMEOUT": _env_int("AERBORNE_STATUS_TIMEOUT", 3),
        "STATUS_CACHE_SECONDS": _env_int("AERBORNE_STATUS_CACHE_SECONDS", 30),

        # Links. Blank hides them.
        "DISCORD_URL": _env("AERBORNE_DISCORD_URL"),
        "STORE_URL": _env("AERBORNE_STORE_URL"),
        "CONTACT_EMAIL": _env("AERBORNE_CONTACT_EMAIL"),
    }


def status_target(config):
    host = config.get("STATUS_HOST") or config.get("SERVER_ADDRESS")
    port = config.get("STATUS_PORT") or config.get("SERVER_PORT")
    if not host:
        return None
    if ":" in host and not host.startswith("["):
        name, _, maybe_port = host.rpartition(":")
        if maybe_port.isdigit():
            host, port = name, int(maybe_port)
    return host, port


def display_address(config):
    address = config.get("SERVER_ADDRESS")
    if address and config.get("SERVER_PORT") != 25565 and ":" not in address:
        address = "{}:{}".format(address, config["SERVER_PORT"])
    return address


def display_bedrock(config):
    address = config.get("BEDROCK_ADDRESS")
    if not address:
        return None
    return {"address": address, "port": config.get("BEDROCK_PORT") or 19132}
