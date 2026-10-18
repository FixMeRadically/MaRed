#!/usr/bin/env python3
"""Local MaRed restart timing report; Python 3.9+, no external dependencies or network."""
from pathlib import Path
from collections import defaultdict
from statistics import median
import argparse
import html
import json
import math
import uuid

KINDS = {'initial': 'Первый запуск', 'restart': 'Перезапуск',
         'rollback': 'Восстановление', 'cds_fallback': 'CDS fallback', 'legacy': 'Старый журнал (05)'}
LABELS = {'shutdown': 'Закрытие старой игры', 'prepare': 'Подготовка команды (может идти параллельно)',
          'spawn': 'Установка / прогрев / запуск процесса', 'world_load': 'Загрузка мира',
          'player_restore': 'Восстановление игрока', 'mod_loaded': 'MaRed загружен: uptime JVM',
          'world_loading': 'Начало загрузки мира: uptime JVM', 'world_ready': 'Мир готов: uptime JVM',
          'player_restored': 'Игрок восстановлен: uptime JVM', 'return_total': 'От запроса до готового мира', 'title_ready':'Главное меню: uptime JVM', 'return_failed':'Возврат в мир отменён: uptime JVM'}
MILESTONES = {'mod_loaded', 'world_loading', 'world_ready', 'player_restored', 'title_ready', 'return_failed'}
LIMIT_BYTES = 64 * 1024 * 1024
LIMIT_EVENTS = 100_000


def uuid_text(value):
    try:
        return isinstance(value, str) and str(uuid.UUID(value)) == value
    except (ValueError, AttributeError):
        return False


def files_for(path):
    path = Path(path).expanduser().resolve()
    if path.is_file():
        return [path]
    for folder in (path, path/'config/mared/dev/logs', path/'run/client/config/mared/dev/logs'):
        files = [folder/name for name in ('timings.previous.jsonl', 'timings.jsonl') if (folder/name).is_file()]
        if files:
            return files
    raise ValueError('Журнал timings.jsonl не найден: ' + str(path))


def load(path):
    files = files_for(path)
    if sum(p.stat().st_size for p in files) > LIMIT_BYTES:
        raise ValueError('Журнал больше 64 МиБ; выберите меньший файл.')
    cycles = {}
    ignored = 0
    count = 0
    seen = set()
    for file in files:
        with file.open('rb') as stream:
            while True:
                line = stream.readline(65537)
                if not line:
                    break
                count += 1
                if count > LIMIT_EVENTS:
                    raise ValueError('Больше 100000 событий; выберите меньший файл.')
                if len(line) > 65536:
                    raise ValueError('Строка журнала больше 64 КиБ.')
                try:
                    e = json.loads(line)
                    if not isinstance(e, dict):
                        raise ValueError()
                    phase, launch = e.get('phase'), e.get('launch')
                    if phase not in LABELS or phase == 'return_total' or not uuid_text(launch):
                        raise ValueError()
                    for key in ('valueMs', 'cycleMs'):
                        if type(e.get(key)) is not int or not 0 <= e[key] <= (86_400_000 if key == 'valueMs' else 31_536_000_000):
                            raise ValueError()
                    expected = 'jvm_uptime' if phase in MILESTONES else 'duration'
                    if e.get('measure') != expected:
                        raise ValueError()
                    if 'cycleId' in e:
                        cycle, kind = e['cycleId'], e.get('cycleKind')
                        if not uuid_text(cycle) or kind not in KINDS or kind == 'legacy':
                            raise ValueError()
                    else:
                        cycle, kind = 'legacy:' + launch, 'legacy'
                    key = (cycle, launch, phase)
                    if key in seen:
                        continue
                    seen.add(key)
                    c = cycles.setdefault(cycle, {'kind': kind, 'events': {}, 'time': ''})
                    if c['kind'] != kind:
                        raise ValueError()
                    # Old logs also record terminal stops; no reliable cross-JVM cycle link exists there.
                    if kind == 'legacy' and phase == 'shutdown':
                        continue
                    if phase in c['events']:
                        raise ValueError()
                    c['events'][phase] = e
                    timestamp = e.get('time', '')
                    if isinstance(timestamp, str) and len(timestamp) <= 64 and not c['time']:
                        c['time'] = timestamp
                except (ValueError, TypeError, UnicodeError, KeyError):
                    ignored += 1
    return {'cycles': cycles, 'ignored': ignored, 'files': files}


def stats(values):
    values = sorted(values)
    return {'n': len(values), 'median': median(values), 'p95': values[max(0, math.ceil(.95 * len(values)) - 1)],
            'min': values[0], 'max': values[-1]}


def summary(data, kind):
    samples = defaultdict(list)
    selected = [c for c in data['cycles'].values() if c['kind'] == kind and c['events']]
    complete = 0
    for c in selected:
        events = c['events']
        for phase, e in events.items():
            samples[phase].append(e['valueMs'])
        needed = {'spawn', 'world_ready'} | ({'shutdown'} if kind == 'restart' else set())
        if kind != 'legacy' and needed.issubset(events):
            # A total is a recorded supervisor monotonic mark, never a sum of overlapping durations.
            ready = events['world_ready']['cycleMs']
            if ready >= events['spawn']['cycleMs']:
                complete += 1
                samples['return_total'].append(ready)
    return {phase: stats(values) for phase, values in samples.items()}, len(selected), complete


def milliseconds(value):
    return f'{value / 1000:.3f}'


def text_report(data, title):
    lines = [title, 'Все значения — секунды. P95: ближайший ранг; на малой выборке нестабилен.']
    for kind, label in KINDS.items():
        rows, count, complete = summary(data, kind)
        if not count:
            continue
        lines.append(f'\n{label}: циклов {count}, полный возврат измерен {complete}')
        for phase in LABELS:
            if phase in rows:
                s = rows[phase]
                lines.append(f"  {LABELS[phase]}: n={s['n']}, медиана={milliseconds(s['median'])}, P95={milliseconds(s['p95'])}")
    lines += [f"\nПропущено повреждённых/неподдерживаемых событий: {data['ignored']}",
              'Build и сохранение мира до принятия restart не входят в общий возврат.',
              'prepare может перекрываться с shutdown; world_load входит во время до world_ready. Не складывайте строки.',
              'world_ready — отметка клиента о наличии мира/игрока, а не о завершении всей отрисовки чанков.',
              'В старых журналах 05 циклы и общий возврат надёжно не восстанавливаются.']
    return '\n'.join(lines)


def html_report(current, baseline=None):
    esc = html.escape
    sections = []
    def table(headers, rows):
        return '<div class="scroll"><table><thead><tr>' + ''.join('<th>' + esc(str(x)) + '</th>' for x in headers) + '</tr></thead><tbody>' + ''.join('<tr>' + ''.join('<td>' + esc(str(x)) + '</td>' for x in row) + '</tr>' for row in rows) + '</tbody></table></div>'
    for kind, label in KINDS.items():
        rows, count, complete = summary(current, kind)
        previous, bcount, bcomplete = summary(baseline, kind) if baseline else ({}, 0, 0)
        if not count and not bcount:
            continue
        headers = ['Этап', 'N', 'Медиана, с', 'P95, с', 'Мин., с', 'Макс., с']
        if baseline:
            headers += ['N базы', 'Медиана базы, с', 'Изменение медианы']
        values = []
        for phase in LABELS:
            a, b = rows.get(phase), previous.get(phase)
            if not a and not b:
                continue
            row = [LABELS[phase]] + ([a['n']] + [milliseconds(a[x]) for x in ('median', 'p95', 'min', 'max')] if a else ['—'] * 5)
            if baseline:
                delta = f"{(a['median'] - b['median']) / b['median'] * 100:+.1f}%" if a and b and b['median'] else '—'
                row += [b['n'] if b else '—', milliseconds(b['median']) if b else '—', delta]
            values.append(row)
        sections.append('<section><h2>' + esc(label) + '</h2><p>Циклов: ' + str(count) + '; полный возврат: ' + str(complete) + (f'. База: {bcount}; полный возврат: {bcomplete}' if baseline else '') + '.</p>' + table(headers, values) + '</section>')
    history = []
    for key, cycle in list(current['cycles'].items())[-100:][::-1]:
        if not cycle['events']:
            continue
        events = cycle['events']
        ready = events.get('world_ready')
        required = {'spawn', 'world_ready'} | ({'shutdown'} if cycle['kind'] == 'restart' else set())
        complete = cycle['kind'] != 'legacy' and required.issubset(events) and ready['cycleMs'] >= events['spawn']['cycleMs']
        history.append([cycle['time'], KINDS[cycle['kind']], key[:8], 'Мир готов' if ready else 'Нет world_ready',
                        milliseconds(ready['cycleMs']) if complete else '—'])
    sections.append('<section><h2>Последние циклы (до 100)</h2>' + table(['Начало записей UTC', 'Тип', 'Цикл', 'Результат', 'Возврат, с'], history) + '</section>')
    notes = [
        'Общий возврат отсчитывается от принятого supervisor запроса до получения world_ready. Сборка и сохранение мира перед запросом сюда не входят.',
        'Подготовка может идти параллельно закрытию. world_load входит во время до world_ready; строки нельзя складывать.',
        'spawn измеряет установку, прогрев и запуск процесса. При Gradle это ещё не старт JVM Minecraft.',
        'world_ready сообщает о наличии мира и игрока на клиенте; все чанки ещё могут отрисовываться.',
        'Запуски, рестарты и восстановления сравниваются отдельно. Частичные циклы не входят в общий возврат; у каждого этапа свой N.',
        'P95 вычислен ближайшим рангом. На малой выборке он нестабилен; сравнивайте несколько одинаковых запусков одного мира.',
        'Файлы performance-05 показаны отдельно: общего cycleId в них нет, закрытие и общий возврат для них не реконструируются.',
        f"Пропущено событий: {current['ignored']}" + (f"; в базе: {baseline['ignored']}" if baseline else '') + '.',
    ]
    return '''<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>MaRed · время перезапуска</title><style>
:root{font-family:system-ui,sans-serif;color:#dfe8ee;background:#101820;color-scheme:dark}body{max-width:1180px;margin:auto;padding:32px 20px;line-height:1.6}h1{font-size:32px;margin-bottom:0}h2{font-size:22px}.sub{color:#9baebc}section{margin-top:28px;padding:20px;background:#192630;border:1px solid #304551;border-radius:14px}.scroll{overflow:auto}table{border-collapse:collapse;width:100%;font-size:14px}th,td{text-align:right;padding:10px;border-bottom:1px solid #304551;white-space:nowrap}th:first-child,td:first-child{text-align:left}th{color:#93d5ce}li{margin:8px 0}button{background:#29434b;border:1px solid #47636c;color:inherit;padding:8px 14px;border-radius:8px;cursor:pointer}p{margin:6px 0 16px}@media print{body{color:#111;background:white}section{background:white;border-color:#aaa}th{color:#111}button{display:none}}
</style><h1>MaRed · время перезапуска</h1><p class="sub">Локальный отчёт · секунды · без передачи данных</p><button onclick="window.print()">Печать / PDF</button>''' + ''.join(sections) + '<section><h2>Как читать</h2><ul>' + ''.join('<li>' + esc(n) + '</li>' for n in notes) + '</ul></section></html>'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('path', nargs='?', type=Path, default=Path.cwd(), help='MaRed project, gameDir, logs directory or one JSONL')
    parser.add_argument('--compare', type=Path, help='Baseline log file or directory (same world/settings)')
    parser.add_argument('--html', type=Path, help='Write standalone HTML report')
    args = parser.parse_args()
    try:
        current = load(args.path)
        baseline = load(args.compare) if args.compare else None
        if args.html:
            output = args.html.expanduser().absolute()
            if output.resolve() in [p.resolve() for data in [current, baseline] if data for p in data['files']]:
                raise ValueError('Отчёт не может перезаписать входной журнал.')
            output.write_text(html_report(current, baseline), encoding='utf-8')
        report = text_report(current, 'MaRed · время перезапуска')
        if baseline:
            report += '\n\n' + text_report(baseline, 'База для сравнения')
        try:
            print(report)
            if args.html:
                print('\nHTML: ' + str(args.html))
        except UnicodeEncodeError:
            import sys
            sys.stdout.buffer.write((report + '\n').encode(sys.stdout.encoding or 'utf-8', errors='replace'))
    except (OSError, ValueError) as error:
        parser.exit(1, str(error) + '\n')

if __name__ == '__main__':
    main()
