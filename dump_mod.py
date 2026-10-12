from pathlib import Path

# Папки, которые нужно обойти (относительно корня мода)
TARGET_FOLDERS = ["technologies", "src", "verification"]

# Расширения, которые считаем текстовыми (можно дополнять)
TEXT_EXTENSIONS = {
    ".java", ".py", ".txt", ".md", ".json", ".xml", ".yaml", ".yml",
    ".gradle", ".properties", ".toml", ".cfg", ".ini", ".html", ".css",
    ".js", ".ts", ".mr", ".lang", ".mf", ".kts",
    ".bat", ".sh", ".cmd",
}

# Файлы без расширения, которые стоит читать
NO_EXTENSION_FILES = {"gradlew", "gradlew.bat"}

OUTPUT_FILE = "mod_dump.txt"


def read_text(path: Path) -> str:
    for encoding in ("utf-8", "cp1251", "latin-1"):
        try:
            return path.read_text(encoding=encoding)
        except (UnicodeDecodeError, LookupError):
            continue
        except Exception:
            return ""
    return ""


def main():
    mod_root = Path(input("Путь к папке мода: ").strip().strip('"'))

    with open(OUTPUT_FILE, "w", encoding="utf-8") as out:
        for folder in TARGET_FOLDERS:
            base = mod_root / folder
            if not base.is_dir():
                continue

            for file in sorted(base.rglob("*")):
                if not file.is_file():
                    continue

                # Пропускаем служебные папки сборки
                parts = file.relative_to(base).parts
                if any(p in {"build", "bin", "out", ".gradle", ".genesis-build"} for p in parts):
                    continue

                ext = file.suffix.lower()
                is_text = ext in TEXT_EXTENSIONS or file.name in NO_EXTENSION_FILES
                if not is_text:
                    continue

                try:
                    relative = file.relative_to(mod_root)
                except ValueError:
                    relative = file

                out.write(f"===== {relative} =====\n")
                content = read_text(file)
                out.write(content)
                if content and not content.endswith("\n"):
                    out.write("\n")
                out.write("\n")

    print(f"Готово: {OUTPUT_FILE}")


if __name__ == "__main__":
    main()