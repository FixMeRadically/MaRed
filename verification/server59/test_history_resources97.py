from pathlib import Path
import json,re,unittest
ROOT=Path(__file__).resolve().parents[2]
def load(lang):
 def pairs(items):
  out={}
  for k,v in items:
   if k in out:raise ValueError('Duplicate language key: '+k)
   out[k]=v
  return out
 return json.loads((ROOT/'src/main/resources/assets/mared/lang'/f'{lang}.json').read_text(encoding='utf8'),object_pairs_hook=pairs)
class HistoryResources97(unittest.TestCase):
 def test_languages_have_matching_history_keys(self):
  ru=load('ru_ru');en=load('en_us');self.assertEqual({k for k in ru if k.startswith('mared.history97.')},{k for k in en if k.startswith('mared.history97.')})
 def test_placeholder_parity(self):
  ru=load('ru_ru');en=load('en_us')
  for k,v in ru.items():
   if k.startswith('mared.history97.'):self.assertEqual(re.findall(r'%(?:\d+\$)?[sd]',v),re.findall(r'%(?:\d+\$)?[sd]',en[k]),k)
 def test_buttons_and_recovery_have_text(self):
  for lang in ['ru_ru','en_us']:
   values=load(lang)
   for key in ['title','open','tab.0','tab.1','tab.2','refresh','copy','recovery','back','loading','read_failed','small']:
    self.assertTrue(values['mared.history97.'+key].strip())
 def test_no_tokens_or_absolute_user_paths_in_history_labels(self):
  for lang in ['ru_ru','en_us']:
   for k,v in load(lang).items():
    if k.startswith('mared.history97.'):self.assertNotIn('C:\\Users\\',v);self.assertNotIn('--accessToken',v);self.assertNotIn('GENESIS_SUPERVISOR_TOKEN',v)
if __name__=='__main__':unittest.main()
