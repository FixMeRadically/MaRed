"""One-use private handoff from Minecraft.getUser(); never logs account credentials."""
from pathlib import Path
import re,time
from .common import safe,json_read
from .wizard_model import SetupError,account_id

FIELDS={'schema','pid','profile','account','name','accessToken','userType','issuedAt'}
AUTH_OPTIONS={'--uuid','--username','--accessToken','--userType'}

def consume(project, path, pid, profile, account, now=None):
    project=safe(project);path=safe(path);profile=safe(profile)
    if path.parent!=project or path.name!='client-session61.json':
        raise SetupError('Неправильное расположение данных текущей сессии. Открой мастер из игры заново.')
    try:
        data=json_read(path)
    finally:
        path.unlink(missing_ok=True)
    if not isinstance(data,dict) or set(data)!=FIELDS or data.get('schema')!=61:
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
    if not isinstance(token,str) or not token or token.lower() in {'0','null','none','offline','invalid'} or len(token)>8192 or any(ord(c)<33 or ord(c)>126 for c in token) or '${' in token:
        raise SetupError('В текущей сессии Minecraft нет пригодного online-токена. Войди в Minecraft через online-аккаунт и открой мастер снова. Сам токен присылать не нужно.')
    if data['userType'] not in {'msa','mojang'}:
        raise SetupError('Для первого теста требуется online-сессия Minecraft.')
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
