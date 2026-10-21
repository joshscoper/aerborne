"""Keep rank branding while giving names and labels readable foregrounds."""


def _rgb(color):
    return tuple(int(color[i:i + 2], 16) for i in (1, 3, 5))


def _luminance(rgb):
    channels = [c / 255 for c in rgb]
    linear = [c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4 for c in channels]
    return sum(c * weight for c, weight in zip(linear, (.2126, .7152, .0722)))


def contrast(foreground, background):
    a, b = sorted((_luminance(_rgb(foreground)), _luminance(_rgb(background))))
    return (b + .05) / (a + .05)


def readable_name(color, background, toward):
    """Find the smallest tint/shade that clears normal-text contrast."""
    start, end = _rgb(color), _rgb(toward)
    for step in range(101):
        rgb = tuple(round(a + (b - a) * step / 100) for a, b in zip(start, end))
        candidate = "#%02X%02X%02X" % rgb
        if contrast(candidate, background) >= 4.5:
            return candidate
    return toward


def rank_palette(color, label_color):
    if contrast(label_color, color) < 4.5:
        label_color = max(("#FFFFFF", "#000000"), key=lambda c: contrast(c, color))
    return {
        "ink": label_color,
        "name": readable_name(color, "#334869", "#FFFFFF"),
        "name_light": readable_name(color, "#E8F1FB", "#111B2E"),
    }
