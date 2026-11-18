"""Bounded support report. Never serializes argv, env, locals or arbitrary exception text."""
import json,re
from pathlib import Path
from .common import atomic,safe

# Exact constant messages only; dynamic exception strings may contain credentials.
REASONS = {
 'Client files changed during snapshot': 'Файлы клиента изменились во время подготовки. Закончи сборку/запись файлов и повтори подготовку; закрывать игру не нужно.',
 'Source client did not exit normally': 'Исходный клиент не завершился за 120 секунд; новая игра не запускалась.',
 'Panel confirmation expired': 'Время ожидания кнопки перезапуска истекло. Можно подготовить новый опыт.',
 'Panel commit expired': 'Игра не подтвердила переход к перезапуску.',
 'NEW disjoint client snapshot required': 'Папка копии клиента должна быть новой и находиться отдельно от исходного профиля.',
 'Dev legacy classpath file limit': 'Файл classpath отсутствует или превышает допустимый размер.',
 'Dev legacy classpath entry limit': 'Файл classpath пуст или содержит слишком много строк.',
 'Invalid dev legacy classpath entry': 'Некорректная строка classpath. Подготовь новый опыт после патча64.',
 'Native argv': 'Команда не соответствует ограничениям Windows runner (число аргументов или управляющие символы).',
 'Windows command length': 'Команда превышает предел длины Windows.',
 'Only explicit Java executable': 'Нужен существующий прямой путь к java.exe.',
 'Environment': 'Некорректный формат переменных окружения запуска.',
 'Launch command redirects profile or loads an external agent': 'Параметры запуска перенаправляют профиль или содержат неподдерживаемый Java agent.',
 'Hidden gameDir in argfile is not supported': 'В Java @argfile найден скрытый gameDir; команда не была полностью раскрыта.',
 'Launch argfile size limit': 'Java @argfile превышает допустимый размер.',
 'Generation changed before launch': 'Набор JAR тестовой копии изменился после подготовки.',
 'Generation seal mismatch': 'Подпись тестовой копии не совпадает с ожидаемым поколением.',
 'Frozen dev snapshot changed': 'Снимок dev-классов изменился после подготовки.',
 'Cannot wrap a nested Java argument file': 'Длинная команда клиента содержит вложенный @argfile.',
 'Game runtime rejected expected generation or inventory': 'Игровой адаптер отклонил версию, поколение или набор модов; нужен журнал соответствующей игры.',
 'Managed game exited with an error; no update activation': 'Управляемая игра завершилась с ошибкой или запись её журнала не удалась.',
 'Game exited before runtime acknowledgement': 'Игра завершилась до подтверждения загрузки.',
 'Game did not confirm loaded jars and versions': 'Игра не подтвердила загруженные версии и моды за отведённое время.',
 'JSON file limit': 'Файл конфигурации отсутствует или превышает допустимый размер.',
 'Record authentication': 'Проверка подписи состояния не прошла.',
 'Record canonical form': 'Некорректный формат подписанного состояния.',
 'Stale/wrong-role record': 'Получено состояние другого запуска или другой стороны.',
 'Linked/aliased path refused': 'Обнаружена ссылка или перенаправление пути вместо обычной папки.',
 'Pilot cancelled': 'Остановка запрошена пользователем.',
 'Client closed before consent': 'Клиент закрыт до подтверждения обновления.',
 'Actual first launch requires Windows x64': 'Для игрового стенда требуется Windows x64.',
 'This pilot root has already been run; do not replay an unknown update': 'Этот стенд уже запускался. Подготовь новый опыт в мастере.',
 'Package/permission verification failed; no game profile was installed': 'Java-ядро отклонило пакет или права доступа; новые профили не активированы.',
 'Restart cover did not acknowledge presentation': 'Окно заставки не подтвердило показ или скрытие.',
 'World save/process-tree stop was not confirmed': 'Сохранение мира и остановка дерева процессов не подтверждены.',
 'Game exited before requested checkpoint; update outcome not confirmed': 'Игра закрылась до ожидаемого подтверждения.',
}
ACTIONS = {
 'load_state':'чтение состояния стенда', 'validate_args':'проверка параметров запуска',
 'verify_generation':'проверка тестовой копии', 'dev_snapshot':'проверка снимка dev-классов',
 'prepare_command':'подготовка Java-команды', 'spawn':'создание процесса',
 'await_ready':'ожидание подтверждения игры', 'job_health':'проверка управляемого процесса',
 'runtime_status':'проверка ответа игрового адаптера', 'join':'подключение клиента',
 'stop':'сохранение и остановка', 'package_verify':'проверка пакета',
 'run':'работа контроллера', 'cleanup':'завершение процессов',
}

def identifier(value):
 return value if isinstance(value,str) and re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]{0,79}',value) else 'unknown'

class PilotFailure(RuntimeError):
 def __init__(self,display):
  super().__init__(display);self.display=display

def failure_data(error,context):
 # Traceback frame metadata only. Source text and local variable values are excluded.
 frames=[]
 tb=error.__traceback__
 while tb:
  name=Path(tb.tb_frame.f_code.co_filename).name
  if re.fullmatch(r'[A-Za-z0-9_]{1,80}\.py',name):
   frames.append({'file':name,'function':identifier(tb.tb_frame.f_code.co_name),'line':tb.tb_lineno})
  tb=tb.tb_next
 chain=[];current=error;seen=set()
 while current is not None and len(chain)<4 and id(current) not in seen:
  seen.add(id(current));chain.append(identifier(type(current).__name__));current=current.__cause__ or current.__context__
 raw=error.args[0] if len(error.args)==1 and isinstance(error.args[0],str) else None
 reason=REASONS.get(raw,'Причина не входит в безопасный справочник; место ошибки сохранено в frames.')
 role=context.get('role');role=role if role in ('server','client') else None
 generation=context.get('generation');generation=generation if type(generation) is int and generation in (0,1) else None
 result={'error':identifier(type(error).__name__),'reason':reason,'action':context.get('action') if context.get('action') in ACTIONS else 'run','role':role,'generation':generation,'last_phase':identifier(context.get('phase')),'frames':frames[-12:],'exception_types':chain}
 for key in ('errno','winerror'):
  value=getattr(error,key,None)
  if type(value) is int:result[key]=value
 return result

class Diagnostics:
 def __init__(self,root,attempt):
  self.root=safe(root);self.attempt=attempt;self.context={'phase':'BOOTSTRAP','action':'load_state'};self.events=[];self.write_failed=False;self.processes={}
 def step(self,action,role=None,generation=None,phase=None):
  context={'phase':phase or self.context['phase'],'action':action,'role':role,'generation':generation}
  if context==self.context and self.events:return
  self.context=context
  self.events.append(dict(self.context));self.events=self.events[-96:]
  try:atomic(self.root/'controller-events63.json',json.dumps({'schema':63,'attempt':self.attempt,'events':self.events},ensure_ascii=False,indent=2).encode('utf-8'))
  except (OSError,ValueError):self.write_failed=True
 def process(self,role,generation,state):
  entry={'role':role,'generation':generation}
  for field in ('active','exitCode'):
   if type(state.get(field)) is int:entry[field]=state[field]
  for field in ('rootExited','logFailed'):
   if type(state.get(field)) is bool:entry[field]=state[field]
  self.processes[(role,generation)]=entry
 def failure(self,error,config=None):
  data=failure_data(error,self.context)
  # Only expected filenames, never enumerate private configuration files.
  logs=[]
  for role in ('server','client'):
   for gen in (0,1):
    name=f'{role}-{gen}-{self.attempt}.log'
    try:exists=safe(self.root/name).is_file()
    except (OSError,ValueError):exists=False
    logs.append({'file':name,'exists':exists})
  data.update({'schema':63,'phase':'FAILED','attempt':self.attempt,'scope':'ISOLATED_ONE_PC_PILOT','remoteUpdater':False,'liveInstallAuthority':False,'worldDowngradeAttempted':False,'logs':logs,'processes':list(self.processes.values()),'event_write_failed':self.write_failed})
  run=(config or {}).get('run')
  if isinstance(run,str) and re.fullmatch('[0-9a-f-]{36}',run):data['run']=run
  written=True
  try:atomic(self.root/'report.json',json.dumps(data,ensure_ascii=False,indent=2).encode('utf-8'))
  except (OSError,ValueError):written=False
  who={'client':'клиент','server':'сервер'}.get(data['role'],'контроллер')
  display=f"Стенд остановлен: {who}, {ACTIONS[data['action']]}.\n{data['reason']}\nОшибка: {data['error']}."
  if data['frames']:
   at=data['frames'][-1];display+=f"\nМесто: {at['file']}:{at['line']} ({at['function']})."
  display+='\nПришли report.json из папки:\n'+str(self.root) if written else '\nНе удалось записать report.json. Скопируй это сообщение.'
  return PilotFailure(display)
