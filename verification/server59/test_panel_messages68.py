"""Only allowlisted constant failure reasons can be published to the game UI."""
from pathlib import Path
import sys,unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.diagnostics63 import failure_data
from first58 import bridge67
class Messages68(unittest.TestCase):
 def test_known_changed_package_explains_retry(self):
  data=failure_data(ValueError('Prepared package changed'),{'action':'run','phase':'READY'})
  self.assertIn('пакет изменился',data['reason']);self.assertNotIn('справочник',data['reason'])
 def test_arbitrary_exception_text_is_not_exposed(self):
  secret='accessToken=DO_NOT_PUBLISH_this';data=failure_data(ValueError(secret),{'action':'run','phase':'READY'})
  self.assertNotIn(secret,str(data));self.assertNotIn('DO_NOT_PUBLISH',str(data))
 def test_dynamic_paths_are_not_echoed(self):
  data=failure_data(ValueError('file C:/Users/private/secret.jar failed'),{'action':'run','phase':'READY'})
  self.assertNotIn('secret.jar',str(data))
 def test_new_ui_reason_roundtrips_signed_unicode_channel(self):
  reason=failure_data(ValueError('MaRed itself cannot be replaced in a dev snapshot'),{'action':'run','phase':'READY'})['reason']
  self.assertEqual(bridge67.verify(b'k'*32,bridge67.sign(b'k'*32,{'failureReason':reason}))['failureReason'],reason)
 def test_world_save_failure_has_no_retry_old_world_advice(self):
  reason=failure_data(ValueError('Saved source receipt mismatch'),{'action':'run','phase':'RESTARTING'})['reason']
  self.assertIn('сохранения мира',reason);self.assertIn('не активируется',reason)
if __name__=='__main__':unittest.main()
