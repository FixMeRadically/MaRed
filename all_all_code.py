import os
import re

# Папка с исходниками
SOURCE_DIR = "src"
# Расширения файлов, которые нужно собрать
EXTENSIONS = (".py", ".js", ".ts", ".html", ".css", ".java", ".cpp", ".c", ".h", ".json")
# Итоговые файлы
OUTPUT_CODE = "all_code.txt"
OUTPUT_DEPS = "dependencies.txt"


def collect_files(source_dir, extensions):
    """Рекурсивно собирает все файлы с нужными расширениями из папки src."""
    result = []
    if not os.path.isdir(source_dir):
        print(f"Папка '{source_dir}' не найдена!")
        return result

    for root, dirs, files in os.walk(source_dir):
        dirs[:] = [d for d in dirs if d not in (".git", "__pycache__", "node_modules", ".venv", "venv")]
        for file in files:
            if file.lower().endswith(extensions) and file not in (OUTPUT_CODE, OUTPUT_DEPS):
                result.append(os.path.join(root, file))
    return result


def write_all_code(files):
    """Записывает содержимое всех файлов в all_code.txt."""
    with open(OUTPUT_CODE, "w", encoding="utf-8") as out:
        for file_path in files:
            out.write(f"{'=' * 80}\n")
            out.write(f"FILE: {os.path.abspath(file_path)}\n")
            out.write(f"{'=' * 80}\n")
            try:
                with open(file_path, "r", encoding="utf-8") as f:
                    out.write(f.read())
            except UnicodeDecodeError:
                out.write("[Ошибка: не удалось прочитать файл в UTF-8]\n")
            except Exception as e:
                out.write(f"[Ошибка чтения: {e}]\n")
            out.write("\n\n")


def extract_imports(file_path):
    """Извлекает список импортов/зависимостей из файла."""
    imports = []
    ext = os.path.splitext(file_path)[1].lower()

    try:
        with open(file_path, "r", encoding="utf-8") as f:
            content = f.read()
    except Exception:
        return imports

    if ext == ".py":
        # import xxx / from xxx import yyy
        for m in re.finditer(r"^\s*import\s+([\w\.\, ]+)", content, re.MULTILINE):
            for name in m.group(1).split(","):
                imports.append(("import", name.strip()))
        for m in re.finditer(r"^\s*from\s+([\w\.]+)\s+import\s+(.+)", content, re.MULTILINE):
            imports.append(("from", m.group(1).strip()))

    elif ext in (".js", ".ts", ".jsx", ".tsx"):
        # import ... from 'xxx' / require('xxx')
        for m in re.finditer(r"import\s+.*?\s+from\s+['\"]([^'\"]+)['\"]", content):
            imports.append(("import", m.group(1)))
        for m in re.finditer(r"import\s+['\"]([^'\"]+)['\"]", content):
            imports.append(("import", m.group(1)))
        for m in re.finditer(r"require\(\s*['\"]([^'\"]+)['\"]\s*\)", content):
            imports.append(("require", m.group(1)))

    elif ext == ".java":
        for m in re.finditer(r"^\s*import\s+([\w\.\*]+)\s*;", content, re.MULTILINE):
            imports.append(("import", m.group(1)))

    elif ext in (".c", ".cpp", ".h"):
        for m in re.finditer(r'^\s*#include\s+[<"]([^>"]+)[>"]', content, re.MULTILINE):
            imports.append(("include", m.group(1)))

    return imports


def resolve_local_dependency(import_name, all_files, source_dir):
    """
    Пытается определить, ссылается ли импорт на локальный файл из src.
    Возвращает путь к файлу или None.
    """
    # Нормализуем: убираем начальные точки, заменяем точки на /
    clean = import_name.lstrip(".").replace(".", "/")
    if not clean:
        return None

    candidates = []
    for f in all_files:
        rel = os.path.relpath(f, source_dir).replace("\\", "/")
        rel_no_ext = os.path.splitext(rel)[0]
        if rel_no_ext.endswith(clean):
            candidates.append(f)

    if candidates:
        # Берём самый короткий путь (наиболее вероятный)
        return min(candidates, key=len)
    return None


def write_dependencies(files):
    """Записывает граф зависимостей в dependencies.txt."""
    with open(OUTPUT_DEPS, "w", encoding="utf-8") as out:
        out.write("ГРАФ ЗАВИСИМОСТЕЙ ФАЙЛОВ\n")
        out.write("=" * 80 + "\n\n")

        for file_path in files:
            imports = extract_imports(file_path)

            out.write(f"{'-' * 80}\n")
            out.write(f"ФАЙЛ: {os.path.basename(file_path)}\n")
            out.write(f"ПУТЬ: {os.path.abspath(file_path)}\n")
            out.write(f"{'-' * 80}\n")

            if not imports:
                out.write("  (нет импортов)\n\n")
                continue

            local_deps = []
            external_deps = []

            for kind, name in imports:
                resolved = resolve_local_dependency(name, files, SOURCE_DIR)
                if resolved:
                    local_deps.append((kind, name, resolved))
                else:
                    external_deps.append((kind, name))

            if local_deps:
                out.write("  Зависит от (локальные файлы):\n")
                for kind, name, resolved in local_deps:
                    out.write(f"    → {os.path.basename(resolved)}\n")
                    out.write(f"        ({kind}: {name})\n")
                    out.write(f"        путь: {os.path.abspath(resolved)}\n")
                out.write("\n")

            if external_deps:
                out.write("  Внешние зависимости:\n")
                for kind, name in external_deps:
                    out.write(f"    - {name}  ({kind})\n")
                out.write("\n")

            out.write("\n")


def main():
    files = collect_files(SOURCE_DIR, EXTENSIONS)
    files.sort()

    write_all_code(files)
    write_dependencies(files)

    print(f"Готово! Собрано файлов: {len(files)}")
    print(f"  Код:          {os.path.abspath(OUTPUT_CODE)}")
    print(f"  Зависимости:  {os.path.abspath(OUTPUT_DEPS)}")


if __name__ == "__main__":
    main()