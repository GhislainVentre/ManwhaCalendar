"""Écrit release.json, lu par l'appli pour proposer la mise à jour : numéro, nom et taille de la
version, et nouveautés des dernières versions (message du commit fusionné dans main).

Usage : release_json.py <message du commit> <release.json précédent> <APK> <sortie>
Variables : VERSION_CODE, VERSION_NAME.
"""
import json
import os
import re
import sys

KEEP = 20  # Versions gardées dans l'historique.
TRAILERS = re.compile(r"^(Co-Authored-By|Claude-Session|Signed-off-by):", re.IGNORECASE)


def notes_from_message(message):
    """Titre (sans « (#28) ») et corps du commit, lignes coupées à 72 colonnes recollées."""
    lines = [line.rstrip() for line in message.splitlines() if not TRAILERS.match(line.strip())]
    lines = [line for line in lines if not line.startswith(("Merge pull request", "Merge branch"))]
    while lines and not lines[0].strip():
        lines.pop(0)
    if not lines:
        return "", ""
    title = re.sub(r"\s*\(#\d+\)$", "", lines[0].strip())
    paragraphs = []
    current = None
    for line in lines[1:]:
        text = line.strip()
        if not text:
            current = None
            continue
        bullet = re.match(r"^[-*•]\s+(.*)", text)
        if bullet:
            current = ["• " + bullet.group(1)]
            paragraphs.append(current)
        elif current is None:
            current = [text]
            paragraphs.append(current)
        else:
            current.append(text)
    body = "\n".join(" ".join(p) for p in paragraphs)
    return title, body


def main():
    message_path, previous_path, apk_path, out_path = sys.argv[1:5]
    code = int(os.environ["VERSION_CODE"])
    name = os.environ["VERSION_NAME"]
    with open(message_path, encoding="utf-8") as f:
        title, body = notes_from_message(f.read())
    try:
        with open(previous_path, encoding="utf-8") as f:
            history = json.load(f).get("history", [])
    except (OSError, ValueError, AttributeError):
        history = []
    history = [h for h in history if isinstance(h, dict) and isinstance(h.get("versionCode"), int) and h["versionCode"] < code]
    if title:
        history.insert(0, {"versionCode": code, "versionName": name, "title": title, "body": body})
    release = {
        "versionCode": code,
        "versionName": name,
        "sizeBytes": os.path.getsize(apk_path),
        "history": history[:KEEP],
    }
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(release, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
