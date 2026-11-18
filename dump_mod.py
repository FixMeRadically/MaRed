from pathlib import Path
import os
import tempfile

TARGET_FOLDERS = ["technologies", "src", "verification"]
TEXT_EXTENSIONS = {
    ".java", ".py", ".txt", ".md", ".json", ".xml", ".yaml", ".yml",
    ".gradle", ".properties", ".toml", ".cfg", ".ini", ".html", ".css",
    ".js", ".ts", ".mr", ".lang", ".mf", ".kts", ".bat", ".sh", ".cmd",
}
NO_EXTENSION_FILES = {"gradlew", "gradlew.bat"}
OUTPUT_FILE = "mod_dump.txt"


def read_text(path: Path) -> str:
    for encoding in ("utf-8", "cp1251", "latin-1"):
        try:
            return path.read_text(encoding=encoding)
        except (UnicodeDecodeError, LookupError):
            continue
    raise UnicodeError(f"Не удалось прочитать текст: {path}")


def main():
    entered = input("Путь к папке мода: ").strip().strip('"')
    if not entered:
        raise SystemExit("Путь к папке мода не указан.")
    mod_root = Path(entered)
    if not mod_root.is_dir():
        raise SystemExit(f"Папка мода не найдена: {mod_root}")
    bases = [mod_root / folder for folder in TARGET_FOLDERS
             if (mod_root / folder).is_dir()]
    if not bases:
        raise SystemExit("В папке не найдены technologies, src или verification.")
    destination = Path(OUTPUT_FILE).absolute()
    fd, temporary_name = tempfile.mkstemp(prefix=".mod-dump-", suffix=".tmp",
                                          dir=destination.parent)
    temporary = Path(temporary_name)
    count = 0
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as out:
            for base in bases:
                for file in sorted(base.rglob("*")):
                    if not file.is_file():
                        continue
                    parts = file.relative_to(base).parts
                    if any(p in {"build", "bin", "out", ".gradle", ".genesis-build"}
                           for p in parts):
                        continue
                    if (file.suffix.lower() not in TEXT_EXTENSIONS
                            and file.name not in NO_EXTENSION_FILES):
                        continue
                    content = read_text(file)
                    relative = file.relative_to(mod_root)
                    out.write(f"===== {relative} =====\n")
                    out.write(content)
                    if content and not content.endswith("\n"):
                        out.write("\n")
                    out.write("\n")
                    count += 1
            out.flush()
            os.fsync(out.fileno())
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)
    print(f"Готово: {OUTPUT_FILE}; файлов: {count}")


if __name__ == "__main__":
    main()
