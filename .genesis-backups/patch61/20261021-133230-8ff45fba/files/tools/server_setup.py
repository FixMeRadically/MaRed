#!/usr/bin/env python3
"""Genesis59: graphical setup for the existing one-PC controller. No hand-written JSON."""
from pathlib import Path
import argparse
import os
import queue
import subprocess
import sys
import threading
from first58.common import safe
from first58.capture import read_client
from first58.wizard_model import ClientLease, SetupError, client_details, plan, prepare, workspace_parent, replacement_inventory, changes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('project', type=Path)
    parser.add_argument('--client-pid', type=int, required=True)
    parser.add_argument('--client-profile', type=Path)
    parser.add_argument('--account', required=True)
    parser.add_argument('--neoforge', required=True)
    args = parser.parse_args()
    import tkinter as tk
    from tkinter import ttk, filedialog, messagebox
    app = tk.Tk()
    app.title('Genesis — настройка тестового сервера')
    app.geometry('800x710')
    app.minsize(700, 650)
    project = safe(args.project)
    messages = queue.Queue()
    state = {'busy': False, 'client': None, 'lease': None, 'root': None, 'reports': None, 'controller': None, 'replacement': ''}
    server = tk.StringVar(value=str(Path.home() / 'Desktop/server'))
    next_server = tk.StringVar()
    next_client = tk.StringVar()
    port = tk.StringVar(value='27690')
    stopped = tk.BooleanVar()
    status = tk.StringVar(value='Получаем настройки текущего клиента…')
    client_label = tk.StringVar(value='Профиль клиента определяется автоматически')
    frame = ttk.Frame(app, padding=18)
    frame.pack(fill='both', expand=True)
    frame.columnconfigure(0, weight=1)
    ttk.Label(frame, text='Тест обновления на этом компьютере', font=('Segoe UI', 16, 'bold')).grid(row=0, column=0, columnspan=2, sticky='w', pady=(0, 8))
    intro = ttk.Label(frame, text='Genesis создаст отдельные копии клиента и сервера. Исходный мир останется в своей папке.\nУдалённые серверы и несколько игроков пока не поддерживаются.', wraplength=680)
    intro.grid(row=1, column=0, columnspan=2, sticky='w', pady=(0, 14))
    controls = []
    def directory_row(row, title, variable):
        ttk.Label(frame, text=title).grid(row=row, column=0, columnspan=2, sticky='w', pady=(7, 3))
        entry = ttk.Entry(frame, textvariable=variable)
        entry.grid(row=row + 1, column=0, sticky='ew')
        def browse():
            selected = filedialog.askdirectory(parent=app, title=title, mustexist=True)
            if selected:
                variable.set(selected)
        button = ttk.Button(frame, text='Выбрать…', command=browse)
        button.grid(row=row + 1, column=1, padx=(8, 0))
        controls.extend((entry, button))
    directory_row(2, 'Папка установленного NeoForge-сервера', server)
    ttk.Label(frame, textvariable=client_label, wraplength=680).grid(row=4, column=0, columnspan=2, sticky='w', pady=(12, 4))
    directory_row(5, 'Полный следующий набор серверных модов (необязательно)', next_server)
    directory_row(7, 'Полный следующий набор клиентских модов (необязательно)', next_client)
    ttk.Label(frame, text='Для простого обновления выбери один JAR кнопкой ниже. Без выбранного JAR и папок проверяется перезапуск с текущими модами.\nЕсли выбираешь папку, она должна содержать ВСЕ моды нужной стороны, включая MaRed.', wraplength=680).grid(row=9, column=0, columnspan=2, sticky='w', pady=(8, 8))
    ports = ttk.Frame(frame)
    ports.grid(row=10, column=0, columnspan=2, sticky='w')
    ttk.Label(ports, text='Порт тестовой копии:').pack(side='left')
    port_entry = ttk.Entry(ports, textvariable=port, width=8)
    port_entry.pack(side='left', padx=8)
    controls.append(port_entry)
    updates = ttk.Frame(frame)
    updates.grid(row=11, column=0, columnspan=2, sticky='w', pady=(8, 0))
    side = tk.StringVar(value='Клиент и сервер')
    def choose_jar():
        path = filedialog.askopenfilename(parent=app, title='Собранный JAR твоего мода', filetypes=[('Minecraft mod', '*.jar')])
        if path:
            state['replacement'] = path
            next_server.set('')
            next_client.set('')
            status.set('Для обновления выбран ' + Path(path).name + '. Остальные моды будут скопированы автоматически.')
            invalidate()
    jar_button = ttk.Button(updates, text='Выбрать JAR обновления…', command=choose_jar)
    jar_button.pack(side='left', padx=5)
    side_box = ttk.Combobox(updates, textvariable=side, values=['Клиент и сервер', 'Только клиент', 'Только сервер'], state='readonly', width=18)
    side_box.pack(side='left')
    clear_jar = ttk.Button(updates, text='Сбросить JAR', command=lambda: (state.update(replacement=''), invalidate(), status.set('JAR сброшен. Можно проверить перезапуск с текущими модами.')))
    clear_jar.pack(side='left', padx=4)
    controls.extend((jar_button, side_box, clear_jar))
    checkbox = ttk.Checkbutton(frame, variable=stopped, text='Я закрыл исходную игру и остановил сервер командой stop')
    checkbox.grid(row=12, column=0, columnspan=2, sticky='w', pady=12)
    controls.append(checkbox)
    summary = tk.Text(frame, height=5, wrap='word', state='disabled', font=('Segoe UI', 10))
    summary.grid(row=13, column=0, columnspan=2, sticky='nsew')
    frame.rowconfigure(13, weight=1)
    ttk.Label(frame, textvariable=status, wraplength=680).grid(row=14, column=0, columnspan=2, sticky='w', pady=10)
    buttons = ttk.Frame(frame)
    buttons.grid(row=15, column=0, columnspan=2, sticky='ew')
    def refresh():
        prepare_button.configure(state='normal' if state['client'] and stopped.get() and not state['busy'] and not state['controller'] else 'disabled')
        launch_button.configure(state='normal' if state['root'] and not state['busy'] and not state['controller'] else 'disabled')
        folder_button.configure(state='normal' if state['reports'] else 'disabled')
        for widget in controls:
            widget.configure(state='disabled' if state['busy'] or state['controller'] else 'readonly' if widget is side_box else 'normal')
    def invalidate(*unused):
        if not state['busy'] and not state['controller']:
            state['root'] = None
        refresh()
    side.trace_add('write', invalidate)
    def background(operation, done):
        state['busy'] = True
        refresh()
        def work():
            try:
                messages.put(('success', done, operation()))
            except (ValueError, OSError, RuntimeError, TimeoutError) as error:
                # Never print captured argv or an exception containing a subprocess command.
                text = str(error) if isinstance(error, (SetupError, ValueError)) else 'Операция не завершилась (' + type(error).__name__ + '). Проверь Java 21, доступ к папкам и наличие свободного места.'
                messages.put(('error', None, text))
            except Exception as error:
                messages.put(('error', None, 'Не удалось выполнить настройку (' + type(error).__name__ + '). Исходные профили не изменены.'))
        threading.Thread(target=work, daemon=True, name='genesis-server-setup').start()
    def captured(result):
        state['client'], state['lease'] = result
        client_label.set('Клиент: ' + result[0]['profile'] + '\nMinecraft 1.21.1 • NeoForge ' + result[0]['neoforge'])
        status.set('Теперь закрой исходную игру, останови сервер командой stop и отметь флажок выше. Мастер оставь открытым.')
        refresh()
    def get_client():
        lease = ClientLease(args.client_pid)
        try:
            argv, profile = read_client(args.client_pid, args.client_profile)
            if lease.closed():
                raise SetupError('Игра закрылась до получения настроек. Открой мастер из игры заново.')
            return client_details(argv, profile, args.account, args.neoforge), lease
        except BaseException:
            lease.close()
            raise
    def prepare_clicked():
        if not stopped.get() or state['busy'] or not state['client']:
            return
        try:
            selected_port = int(port.get())
        except ValueError:
            messagebox.showerror('Genesis', 'В поле порта нужно число от 1024 до 65535.', parent=app)
            return
        values = server.get(), next_server.get(), next_client.get(), selected_port
        replacement = state['replacement']
        roles = ('client', 'server') if side.get() == 'Клиент и сервер' else ('client',) if side.get() == 'Только клиент' else ('server',)
        client = state['client']
        state['root'] = None
        status.set('Проверяем наборы модов и готовим отдельные копии. Это может занять несколько минут…')
        def operation():
            if not state['lease'].closed():
                raise SetupError('Исходная игра ещё открыта. Закрой её обычным способом; мастер оставь открытым.')
            spec, delta = plan(values[0], client, values[1], values[2], values[3])
            replacements = {}
            if replacement:
                if values[1] or values[2]:
                    raise SetupError('Выбери один способ: JAR обновления или полные следующие наборы модов.')
                for role in roles:
                    before, after = replacement_inventory(spec['next' + role.title() + 'Mods'], replacement)
                    delta[role] = changes(before, after)
                    replacements[role] = replacement
            root = prepare(project, spec, workspace_parent(), replacements=replacements)
            return root, delta
        def prepared(result):
            state['root'] = result[0]
            state['reports'] = result[0]
            lines = []
            for role, title in [('server', 'Сервер'), ('client', 'Клиент')]:
                delta = result[1][role]
                lines.append(title + ': добавлено ' + str(len(delta['added'])) + ', изменено ' + str(len(delta['changed'])) + ', удалено ' + str(len(delta['removed'])))
                if delta['removed']:
                    lines.append('Удаляются только в тестовой копии: ' + ', '.join(delta['removed']))
            if not any(any(d.values()) for d in result[1].values()):
                lines.append('Наборы одинаковы: проверяется перезапуск, а не изменение функциональности модов.')
            summary.configure(state='normal')
            summary.delete('1.0', 'end')
            summary.insert('1.0', '\n'.join(lines))
            summary.configure(state='disabled')
            status.set('Готово. Нажми «Запустить тест». Копии: ' + str(result[0]))
            refresh()
        background(operation, prepared)
    def launch_clicked():
        if not state['root'] or state['busy'] or state['controller']:
            return
        root = state['root']
        def operation():
            from first58.setup import doctor
            from first_launch import ownership
            with ownership(root):
                doctor(project, root)
            return subprocess.Popen([sys.executable, str(project / 'tools/first_launch.py'), str(project), 'run', str(root)], creationflags=subprocess.CREATE_NEW_CONSOLE)
        def launched(process):
            state['controller'] = process
            status.set('Тест запущен. Окно контроллера оставь открытым. В игре согласись на обновление в течение 30 секунд.')
            refresh()
        status.set('Проверяем Java, подписи и управление процессами Windows…')
        background(operation, launched)
    prepare_button = ttk.Button(buttons, text='Проверить и подготовить', command=prepare_clicked)
    prepare_button.pack(side='left')
    launch_button = ttk.Button(buttons, text='Запустить тест', command=launch_clicked)
    launch_button.pack(side='left', padx=8)
    folder_button = ttk.Button(buttons, text='Открыть отчёты', command=lambda: os.startfile(str(state['reports'])))
    folder_button.pack(side='right')
    for variable in (server, next_server, next_client, port):
        variable.trace_add('write', invalidate)
    stopped.trace_add('write', lambda *unused: refresh())
    def close():
        if state['busy']:
            messagebox.showinfo('Genesis', 'Дождись завершения текущей подготовки или проверки.', parent=app)
            return
        if state['lease']:
            state['lease'].close()
        app.destroy()
    app.protocol('WM_DELETE_WINDOW', close)
    def pump():
        try:
            while True:
                kind, callback, payload = messages.get_nowait()
                state['busy'] = False
                if kind == 'error':
                    status.set(payload)
                    messagebox.showerror('Genesis — настройка остановлена', payload, parent=app)
                else:
                    callback(payload)
                refresh()
        except queue.Empty:
            pass
        if state['controller'] and state['controller'].poll() is not None:
            state['controller'] = None
            state['root'] = None  # Never replay a previously started root.
            status.set('Контроллер завершился. Для новой попытки подготовь новые копии. Отчёты остаются в папке теста.')
            refresh()
        app.after(150, pump)
    background(get_client, captured)
    pump()
    app.mainloop()

if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Tokens are never part of the process command or diagnostic output.
        print('Genesis server setup could not start (' + type(error).__name__ + '). Python with tkinter and Windows x64 are required.', file=sys.stderr)
        raise SystemExit(2)
