"""Same exact-artifact HMAC receipt as Java Approval120. Call only after explicit review/consent."""
from pathlib import Path
import secrets,time,uuid,re
from .common import safe,atomic
from .bridge67 import read,sign

def transaction_id(transaction):
 if str(uuid.UUID(transaction))!=transaction:raise ValueError('APPROVAL120_ID')

def approval_key(path):
 with safe(path).open('rb') as stream:secret=stream.read(33)
 if len(secret)!=32:raise ValueError('APPROVAL120_KEY')
 return secret

def issue(root,purpose,transaction,artifact):
 root=safe(root);transaction_id(transaction)
 if not re.fullmatch('[A-Z][A-Z0-9_]{0,31}',purpose) or not re.fullmatch('[a-f0-9]{64}',artifact):raise ValueError('APPROVAL120_INPUT')
 root.mkdir(exist_ok=True);key=safe(root/'approval120.key')
 if not key.exists():atomic(key,secrets.token_bytes(32),new=True)
 secret=approval_key(key)
 folder=safe(root/'approvals120');folder.mkdir(exist_ok=True);target=safe(folder/transaction)
 payload=sign(secret,dict(schema='120',scope=str(root),purpose=purpose,transaction=transaction,artifact=artifact,issued=str(int(time.time()*1000))))
 try:atomic(target,payload,new=True)
 except FileExistsError as error:raise ValueError('APPROVAL120_ALREADY_ISSUED') from error
 return transaction

def verify(root,purpose,transaction,artifact):
 root=safe(root);transaction_id(transaction);d=read(root/'approvals120'/transaction,approval_key(root/'approval120.key'))
 if set(d)!={'schema','scope','purpose','transaction','artifact','issued'} or any(d[k]!=v for k,v in dict(schema='120',scope=str(root),purpose=purpose,transaction=transaction,artifact=artifact).items()) or not re.fullmatch('[1-9][0-9]{11,16}',d['issued']):raise ValueError('APPROVAL120_REQUIRED_OR_CHANGED')
 return d
