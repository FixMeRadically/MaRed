"""One-use private handoff from Minecraft.getUser(); never logs account credentials."""
from pathlib import Path
import re,time,hashlib,uuid
from .common import safe,json_read
from .wizard_model import SetupError,account_id

FIELDS={'schema','pid','profile','account','name','accessToken','userType','issuedAt'}
AUTH_OPTIONS={'--uuid','--username','--accessToken','--userType'}

def consume(project, path, pid, profile, account, now=None, allow_offline=False):
    project=safe(project);path=safe(path);profile=safe(profile)
    if path.parent!=project or path.name!='client-session61.json':
        raise SetupError('Неправильное расположение данных текущей сессии. Открой мастер из игры заново.')
    claimed=path.with_name('.session61-'+uuid.uuid4().hex)
    try:
        path.rename(claimed)
    except FileNotFoundError as error:
        raise SetupError('Данные текущей сессии уже использованы или недоступны. Открой мастер заново.') from error
    try:
        data=json_read(claimed)
    finally:
        claimed.unlink(missing_ok=True)
    if not isinstance(data,dict) or data.get('schema') not in {61,62} or set(data)!=(FIELDS if data.get('schema')==61 else FIELDS|{'devMods','cwd'}):
        raise SetupError('Неподдерживаемые данные сессии. Открой мастер заново.')
    if type(data['pid']) is not int or data['pid']!=pid or type(data['issuedAt']) is not int:
        raise SetupError('Данные сессии относятся к другому запуску.')
    age=(time.time()*1000 if now is None else now)-data['issuedAt']
    if not 0<=age<=120000:
        raise SetupError('Данные сессии устарели. Закрой мастер и открой его из игры заново.')
    if not isinstance(data['profile'],str) or not Path(data['profile']).is_absolute() or safe(data['profile'])!=profile:
        raise SetupError('Профиль данных сессии отличается от запущенной игры.')
    if account_id(data['account'])!=account_id(account):
        raise SetupError('Аккаунт данных сессии отличается от запущенной игры.')
    if not isinstance(data['name'],str) or not re.fullmatch(r'[A-Za-z0-9_]{1,16}',data['name']):
        raise SetupError('Не удалось получить имя текущего аккаунта.')
    token=data['accessToken']
    if not isinstance(token,str) or len(token)>8192 or any(ord(c)<33 or ord(c)>126 for c in token):
        raise SetupError('Некорректный формат данных сессии.')
    if not allow_offline and (not token or token.lower() in {'0','null','none','offline','invalid'} or '${' in token):
        raise SetupError('В текущей сессии Minecraft нет пригодного online-токена. Для запуска из VSCode выбери локальный offline-тест.')
    if data['userType'] not in ({'msa','mojang','legacy'} if allow_offline else {'msa','mojang'}):
        raise SetupError('Для online-теста требуется online-сессия Minecraft.')
    if data['schema']==62:
        if not isinstance(data['devMods'],str) or len(data['devMods'])>32768 or not isinstance(data['cwd'],str) or not Path(data['cwd']).is_absolute():
            raise SetupError('Неправильные данные dev-запуска.')
    return data

def apply(argv, session):
    result=[];i=0
    while i<len(argv):
        value=argv[i];key=value.split('=',1)[0]
        if key not in AUTH_OPTIONS:
            result.append(value);i+=1;continue
        if '=' in value:
            supplied=value.split('=',1)[1];i+=1
        else:
            if i+1>=len(argv):raise SetupError('Неполный аргумент авторизации в команде клиента.')
            supplied=argv[i+1];i+=2
        if key=='--uuid' and account_id(supplied)!=account_id(session['account']):
            raise SetupError('UUID команды клиента отличается от текущей сессии.')
    for key,value in [('--uuid',session['account']),('--username',session['name']),('--accessToken',session['accessToken']),('--userType',session['userType'])]:
        result.extend((key,value))
    return result

def offline_uuid(name):
    if not re.fullmatch(r'[A-Za-z0-9_]{1,16}',name):raise SetupError('Некорректное имя игрока для offline-теста.')
    return str(uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:'+name).encode('utf-8'),usedforsecurity=False).digest(),version=3))

def for_mode(argv,session,mode):
    if mode not in {'ONLINE','LOCAL_OFFLINE'}:raise SetupError('Неизвестный режим авторизации.')
    if mode=='ONLINE':
        token=session['accessToken']
        if not token or token.lower() in {'0','null','none','offline','invalid'} or '${' in token or session['userType'] not in {'msa','mojang'}:
            raise SetupError('Для online-теста нужен вход через online-аккаунт. Для VSCode выбери локальный offline-тест.')
        return apply(argv,session),session['account']
    # First validate any original UUID against the actual current session.
    result=apply(argv,session)
    account=offline_uuid(session['name'])
    for key,value in [('--uuid',account),('--accessToken','0'),('--userType','legacy')]:
        result[result.index(key)+1]=value
    return result,account
