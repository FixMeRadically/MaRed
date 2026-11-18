"""Offer time diagnostics and historical ancestors; actual archive recovery has Java/file integration checks."""
from pathlib import Path
import sys,unittest
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import https78 as h
import test_bootstrap76 as fixture

class Expiry80(unittest.TestCase):
 def test_offer_time_is_distinct_from_tls_certificate_failure(self):
  with patch.object(h,'core_classpath',return_value='core.jar'),patch.object(h.subprocess,'run',return_value=Mock(returncode=2,stderr='HTTPS78_OFFER_TIME')):
   with self.assertRaisesRegex(ValueError,'^HTTPS78_OFFER_TIME$'):h.public_info(Path('.'),'java',Path('public'))
 def test_arbitrary_stderr_is_not_exposed(self):
  with patch.object(h,'core_classpath',return_value='core.jar'),patch.object(h.subprocess,'run',return_value=Mock(returncode=2,stderr='private/path/or/token')):
   with self.assertRaisesRegex(ValueError,'^HTTPS78_PUBLIC_REJECTED$'):h.public_info(Path('.'),'java',Path('public'))
 def test_successful_public_verification_is_preserved(self):
  with patch.object(h,'core_classpath',return_value='core.jar'),patch.object(h.subprocess,'run',return_value=Mock(returncode=0,stdout='{"release":"valid"}')):
   self.assertEqual(h.public_info(Path('.'),'java',Path('public')),dict(release='valid'))
 def successor(self):
  f=fixture.Successor76();f.setUp();self.addCleanup(f.doCleanups);return f
 def test_expired_verified_ancestor_can_link_a_new_fresh_candidate(self):
  f=self.successor();valid=dict(f.owner.tool.return_value)
  def verify(action,*args):
   if action=='verify':raise ValueError('expired')
   self.assertEqual(action,'verify-history');return valid
  f.owner.tool.side_effect=verify
  self.assertTrue(f.owner.successor(f.info))
 def test_historical_verifier_failure_does_not_skip_missing_parent(self):
  f=self.successor();f.owner.tool.side_effect=ValueError('bad signature')
  self.assertFalse(f.owner.successor(f.info))
if __name__=='__main__':unittest.main()
