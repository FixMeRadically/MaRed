from pathlib import Path
import tempfile,unittest,uuid,sys
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import automatic156 as auto
from first58.bridge67 import write
class Automatic156(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)/'owner';self.root.mkdir();self.key=b'k'*32;self.run=str(uuid.uuid4());self.job=str(uuid.uuid4());self.snapshot=self.root/'snapshot';self.snapshot.mkdir();self.client=self.root/'inputs';self.client.mkdir();self.seed=self.root/'releases/first/public'
  self.settings=dict(run=self.run,profile=str(self.root/'server'),port='25565',auth='false')
  for target,name,value in [(auto.base,'settings',(self.settings,self.key)),(auto.publication79,'available',None),(auto.publication79,'baseline',(self.seed,'first'))]:
   mock=patch.object(target,name,return_value=value);mock.start();self.addCleanup(mock.stop)
  self.calls=[]
  def release(project,root,action,*a,**kw):
   self.calls.append((action,kw))
   return {'draft':str(self.root/'drafts84/one')} if action=='script-prepare' else {'ticket':'a'*64} if action=='script-preview' else {'authored':kw['label'],'release':'release-id'}
  mock=patch.object(auto.publication84,'run',side_effect=release);mock.start();self.addCleanup(mock.stop)
 def ready(self):return auto.prepare(self.root,self.root,self.snapshot,self.client,self.job)
 def rollout(self,view=None):
  write(self.root/'publication75',self.key,dict(schema='75'))
  for target,name,value in [(auto.https78,'load',(None,None,{'enabled':'true'})),(auto.https78,'status',{'state':'RUNNING_TLS'}),(auto,'view',view or {'control':'{"phase":"PREPARING","release":"release-id","accepted":1,"prepared":1,"pending":0}'})]:
   mock=patch.object(target,name,return_value=value);mock.start();self.addCleanup(mock.stop)
 def enqueue(self,root,action,label,request=None):
  return request or 'stage'

 def test_interrupted_enqueue_is_recovered_without_replay(self):
  data=self.ready();self.rollout()
  nonce=str(uuid.uuid4())
  data.update(phase='COMMIT_PENDING',request=nonce)
  auto.store(self.root,self.key,self.job,data)
  write(self.root/'request74',self.key,dict(schema='75',run=self.run,generation='1',request=nonce,action='COMMIT',label=data['label']))
  with patch('first58.dedicated75.submit') as submit:
   self.assertEqual(auto.launch(self.root,self.root,self.job)['phase'],'COMMIT_QUEUED')
   submit.assert_not_called()

 def test_interrupted_enqueue_recovers_from_owner_receipt_after_queue_advanced(self):
  data=self.ready();self.rollout()
  nonce=str(uuid.uuid4())
  auto.store(self.root,self.key,self.job,dict(data,phase='COMMIT_PENDING',request=nonce))
  write(self.root/'state104',self.key,dict(run=self.run,request=nonce,requestResult='COMPLETED'))
  newer=dict(schema='75',run=self.run,generation='1',request=str(uuid.uuid4()),action='STATUS',label=data['label'])
  write(self.root/'request74',self.key,newer)
  with patch('first58.dedicated75.submit') as submit:
   self.assertEqual(auto.launch(self.root,self.root,self.job)['phase'],'COMMIT_QUEUED')
   submit.assert_not_called()

 def test_matching_queued_commit_rejection_is_returned(self):
  data=self.ready();self.rollout()
  nonce=str(uuid.uuid4())
  data.update(phase='COMMIT_QUEUED',request=nonce)
  auto.store(self.root,self.key,self.job,data)
  write(self.root/'state104',self.key,dict(run=self.run,request=nonce,requestResult='REJECTED',error='FOLDER118_PAIR_CHANGED'))
  with patch('first58.dedicated75.submit') as submit:
   with self.assertRaisesRegex(ValueError,'FOLDER118_PAIR_CHANGED'):
    auto.launch(self.root,self.root,self.job)
   submit.assert_not_called()

 def test_explicit_retry_of_rejected_commit_in_same_round(self):
  data=self.ready();self.rollout()
  old=str(uuid.uuid4())
  data.update(phase='COMMIT_QUEUED',request=old,stageRequest='stage-1')
  auto.store(self.root,self.key,self.job,data)
  write(self.root/'state104',self.key,dict(run=self.run,request=old,requestResult='REJECTED',error='FOLDER118_PAIR_CHANGED'))
  with patch('first58.dedicated75.submit',side_effect=self.enqueue) as submit:
   retry=auto.launch(self.root,self.root,self.job)
   self.assertEqual(retry['phase'],'COMMIT_QUEUED')
   self.assertNotEqual(retry['request'],old)
   self.assertEqual([call.args[1] for call in submit.call_args_list],['COMMIT'])

 def test_failed_commit_cannot_be_retried_even_if_view_is_old(self):
  data=self.ready();self.rollout()
  nonce=str(uuid.uuid4())
  data.update(phase='COMMIT_QUEUED',request=nonce,stageRequest='stage-1')
  auto.store(self.root,self.key,self.job,data)
  write(self.root/'state104',self.key,dict(run=self.run,request=nonce,requestResult='FAILED',error='FOLDER118_TRANSITION_REVIEW'))
  with patch('first58.dedicated75.submit') as submit:
   with self.assertRaisesRegex(ValueError,'FOLDER118_TRANSITION_REVIEW'):
    auto.launch(self.root,self.root,self.job)
   submit.assert_not_called()

 def test_label_unique(self):
  names={auto.label() for _ in range(100)};self.assertEqual(len(names),100);self.assertTrue(all(len(s)<=40 for s in names))
 def test_prepare_uses_exact_automatic_ticket(self):
  data=self.ready();self.assertEqual(data['phase'],'READY');self.assertEqual([x[0] for x in self.calls],['script-prepare','script-preview','script-author']);self.assertEqual(self.calls[-1][1]['ticket'],'a'*64);self.assertEqual(self.calls[-1][1]['fallback'],self.seed)
 def test_same_job_does_not_sign_twice(self):
  first=self.ready();second=self.ready();self.assertEqual(first,second);self.assertEqual(len(self.calls),3)
 def test_reused_job_other_snapshot_rejected(self):
  self.ready();other=self.root/'other';other.mkdir()
  with self.assertRaisesRegex(ValueError,'JOB_CHANGED'):auto.prepare(self.root,self.root,other,self.client,self.job)
 def test_capable_owner_continues_without_second_command(self):
  data=self.ready();self.rollout({'control':'{"autoStage157":true,"phase":"PREPARING"}'})
  def stage(root,action,label):
   self.assertEqual(action,'STAGE_AUTO')
   write(root/'rollout118',self.key,dict(schema='118',run=self.run,label=label,phase='COMMIT_SEEN'))
   return 'stage'
  with patch('first58.dedicated75.submit',side_effect=stage) as submit:
   self.assertEqual(auto.launch(self.root,self.root,self.job)['phase'],'COMMIT_QUEUED')
   submit.assert_called_once_with(self.root,'STAGE_AUTO',data['label'])

 def test_launch_uses_existing_consent_then_commit(self):
  self.ready();self.rollout()
  with patch('first58.dedicated75.submit',side_effect=self.enqueue) as submit:
   result=auto.launch(self.root,self.root,self.job);self.assertEqual(result['phase'],'COMMIT_QUEUED');self.assertEqual([c.args[1] for c in submit.call_args_list],['STAGE','COMMIT']);self.assertEqual(result['request'],submit.call_args.kwargs['request'])
 def test_refused_https_stage_can_be_retried(self):
  self.ready();self.rollout()
  write(self.root/'state104',self.key,dict(run=self.run,request='stage-1',requestResult='REJECTED',error='HTTPS78_CONNECTION_REFUSED'))
  with patch('first58.dedicated75.submit',return_value='stage-1'):
   with self.assertRaisesRegex(ValueError,'HTTPS78_CONNECTION_REFUSED'):auto.launch(self.root,self.root,self.job)
  self.assertEqual(auto.load(self.root,self.key,self.job)['phase'],'READY')
  with patch('first58.dedicated75.submit',side_effect=lambda root,action,label,request=None:request or 'stage-2') as submit:
   self.assertEqual(auto.launch(self.root,self.root,self.job)['phase'],'COMMIT_QUEUED')
   self.assertEqual([c.args[1] for c in submit.call_args_list],['STAGE','COMMIT'])
 def test_pre_enqueue_busy_can_retry_same_job(self):
  self.ready();self.rollout()
  with patch('first58.dedicated75.submit',side_effect=ValueError('DEDICATED75_REQUEST_PENDING')):
   with self.assertRaisesRegex(ValueError,'REQUEST_PENDING'):auto.launch(self.root,self.root,self.job)
  self.assertEqual(auto.load(self.root,self.key,self.job)['phase'],'READY')
  with patch('first58.dedicated75.submit',side_effect=self.enqueue):
   self.assertEqual(auto.launch(self.root,self.root,self.job)['phase'],'COMMIT_QUEUED')

 def test_uncertain_stage_without_receipt_does_not_commit(self):
  data=self.ready();self.rollout()
  auto.store(self.root,self.key,self.job,dict(data,phase='WAITING'))
  with patch('first58.dedicated75.submit') as submit:
   with self.assertRaisesRegex(ValueError,'REVIEW_INTERRUPTED'):auto.launch(self.root,self.root,self.job)
   submit.assert_not_called()

 def test_zero_job_id_refused(self):
  with self.assertRaisesRegex(ValueError,'AUTO156_JOB'):
   auto.job_path(self.root,str(uuid.UUID(int=0)))

 def test_empty_jar_refused_before_authoring(self):
  (self.client/'empty.jar').touch()
  with self.assertRaisesRegex(ValueError,'AUTO156_MOD_EMPTY'):
   auto.copy_mods(self.client,self.root/'copy')

 def test_aborted_participant_does_not_block_ready_participant(self):
  self.ready();self.rollout({'control':'{"phase":"PREPARING","release":"release-id","accepted":2,"aborted":1,"prepared":1,"pending":0}'})
  with patch('first58.dedicated75.submit',side_effect=self.enqueue):
   self.assertEqual(auto.launch(self.root,self.root,self.job)['phase'],'COMMIT_QUEUED')
 def test_terminal_rollout_beats_stale_preparing_view(self):
  data=self.ready();self.rollout()
  write(self.root/'rollout118',self.key,dict(schema='118',run=self.run,label=data['label'],phase='CANCELLED'))
  with patch('first58.dedicated75.submit',side_effect=self.enqueue) as submit:
   with self.assertRaisesRegex(ValueError,'AUTO156_ROUND_CANCELLED'):
    auto.launch(self.root,self.root,self.job)
  self.assertEqual([call.args[1] for call in submit.call_args_list],['STAGE'])
  self.assertEqual(auto.load(self.root,self.key,self.job)['phase'],'CANCELLED')

 def test_no_acceptance_requests_cancellation(self):
  self.ready();self.rollout({'control':'{"phase":"PREPARING","release":"release-id","accepted":0,"prepared":0,"pending":0}'})
  with patch('first58.dedicated75.submit',side_effect=['stage','cancel']) as submit:
   with self.assertRaisesRegex(ValueError,'AUTO156_NO_ACCEPTANCE'):auto.launch(self.root,self.root,self.job)
   self.assertEqual([c.args[1] for c in submit.call_args_list],['STAGE','CANCEL'])
  self.assertEqual(auto.load(self.root,self.key,self.job)['phase'],'CANCELLED')
 def test_commit_not_replayed(self):
  self.ready();self.rollout()
  with patch('first58.dedicated75.submit',side_effect=self.enqueue) as submit:
   auto.launch(self.root,self.root,self.job);auto.launch(self.root,self.root,self.job);self.assertEqual(submit.call_count,2)
 def test_wrong_round_never_committed(self):
  self.ready();self.rollout({'control':'{"phase":"PREPARING","release":"other","accepted":1,"prepared":1,"pending":0}'})
  with patch('first58.dedicated75.submit',side_effect=self.enqueue) as submit,patch.object(auto.time,'monotonic',side_effect=[0,0,2]),patch.object(auto.time,'sleep'):
   with self.assertRaisesRegex(ValueError,'WAITING_FOR_CONSENT'):auto.launch(self.root,self.root,self.job,timeout=1)
   self.assertEqual(submit.call_count,1)
 def test_unknown_commit_requires_operator_review(self):
  self.ready();self.rollout()
  with patch('first58.dedicated75.submit',side_effect=['stage',ValueError('TRANSPORT_UNKNOWN')]):
   with self.assertRaisesRegex(ValueError,'UNKNOWN'):auto.launch(self.root,self.root,self.job)
  with patch('first58.dedicated75.submit') as submit:
   with self.assertRaisesRegex(ValueError,'REVIEW_INTERRUPTED'):auto.launch(self.root,self.root,self.job)
   submit.assert_not_called()
 def test_stale_controller_run_rejected(self):
  self.ready();self.settings['run']=str(uuid.uuid4())
  with self.assertRaisesRegex(ValueError,'STALE_JOB'):auto.launch(self.root,self.root,self.job)
 def test_baseline_rejection_is_reported_from_owner_state(self):
  write(self.root/'view75',self.key,dict(schema='75',phase='ACTIVE_BASELINE'))
  write(self.root/'state104',self.key,dict(run=self.run,request='baseline',requestResult='REJECTED',error='PUBLICATION83_FIRST_REQUIRED'))
  with self.assertRaisesRegex(ValueError,'PUBLICATION83_FIRST_REQUIRED'):
   next(auto.wait_command(self.root,self.key,self.run,'baseline'))

 def test_copy_bytes(self):
  (self.client/'a.jar').write_bytes(b'jar');(self.client/'note.txt').write_text('ignored');out=self.root/'copy';receipt=auto.copy_mods(self.client,out);self.assertEqual((out/'a.jar').read_bytes(),b'jar');self.assertEqual(set(receipt),{'a.jar'});self.assertFalse((out/'note.txt').exists())
 def test_copy_empty_refused(self):
  with self.assertRaisesRegex(ValueError,'EMPTY_MOD_SET'):auto.copy_mods(self.client,self.root/'copy')
 def test_copy_into_source_refused(self):
  with self.assertRaisesRegex(ValueError,'MODS_SCOPE'):auto.copy_mods(self.client,self.client/'child')
 def test_copy_case_collision(self):
  # Model a case-sensitive source even when the host filesystem folds case.
  alternate=self.root/'alternate';alternate.mkdir()
  first=self.client/'a.jar';second=alternate/'A.jar'
  first.write_bytes(b'one');second.write_bytes(b'two')
  with patch.object(Path,'iterdir',return_value=iter((first,second))):
   with self.assertRaisesRegex(ValueError,'AUTO156_MOD_NAME'):
    auto.copy_mods(self.client,self.root/'copy')
 def test_copy_changed_while_reading(self):
  path=self.client/'a.jar';path.write_bytes(b'one');original=auto.sha
  def changed(target,limit):
   if target.parent==self.root/'copy':path.write_bytes(b'changed')
   return original(target,limit)
  with patch.object(auto,'sha',side_effect=changed):
   with self.assertRaisesRegex(ValueError,'INPUT_CHANGED'):auto.copy_mods(self.client,self.root/'copy')
 def test_initial_requires_transport_address_before_copy(self):
  with patch.object(auto.publication79,'baseline',side_effect=ValueError('PUBLICATION79_BASELINE_REQUIRED')),patch.object(auto.https78,'load',return_value=(None,None,None)):
   with self.assertRaisesRegex(ValueError,'DISTRIBUTION_SETUP_REQUIRED'):self.ready()
   self.assertFalse((self.root/'automatic156'/self.job).exists())
 def test_failed_initial_copy_can_retry_same_job(self):
  server=Path(self.settings['profile'])/'mods';server.mkdir(parents=True)
  (server/'s.jar').write_bytes(b'server')
  with patch.object(auto.publication79,'baseline',side_effect=ValueError('PUBLICATION79_BASELINE_REQUIRED')),patch.object(auto.https78,'load',return_value=(None,None,None)),patch.object(auto.publication83,'first',side_effect=[{'ticket':'b'*64},{'path':str(self.root/'releases/base')}]):
   with self.assertRaisesRegex(ValueError,'EMPTY_MOD_SET'):
    auto.prepare(self.root,self.root,self.snapshot,self.client,self.job,endpoint='https://localhost:8443/')
   self.assertEqual(list((self.root/'automatic156').glob('.import156-*')),[])
   (self.client/'c.jar').write_bytes(b'client')
   self.assertEqual(auto.prepare(self.root,self.root,self.snapshot,self.client,self.job,endpoint='https://localhost:8443/')['phase'],'READY')
 def test_initial_import_copies_sets_once_then_discards_staging(self):
  server=Path(self.settings['profile'])/'mods';server.mkdir(parents=True);(server/'s.jar').write_bytes(b'server');(self.client/'c.jar').write_bytes(b'client')
  with patch.object(auto.publication79,'baseline',side_effect=ValueError('PUBLICATION79_BASELINE_REQUIRED')),patch.object(auto.https78,'load',return_value=(None,None,None)),patch.object(auto.publication83,'first',side_effect=[{'ticket':'b'*64},{'path':str(self.root/'releases/base')}]) as first:
   result=auto.prepare(self.root,self.root,self.snapshot,self.client,self.job,endpoint='https://localhost:8443/')
   self.assertEqual(result['phase'],'READY');self.assertEqual(first.call_count,2);self.assertEqual(first.call_args.args[6],'localhost:25565');self.assertFalse((self.root/'automatic156'/self.job/'client').exists());self.assertFalse((self.root/'automatic156'/self.job/'server').exists())
 def test_single_side_is_preserved(self):
  auto.prepare(self.root,self.root,self.snapshot,self.client,self.job,side='SERVER');self.assertEqual(self.calls[0][1]['side'],'SERVER');self.assertEqual(auto.load(self.root,self.key,self.job)['side'],'SERVER')
 def test_same_job_cannot_change_side(self):
  self.ready()
  with self.assertRaisesRegex(ValueError,'JOB_CHANGED'):auto.prepare(self.root,self.root,self.snapshot,self.client,self.job,side='CLIENT')
 def test_failure_log_does_not_rewrite_commit_authority(self):
  data=self.ready();data['phase']='COMMIT_PENDING';auto.store(self.root,self.key,self.job,data);auto.failure(self.root,self.job,ValueError('UNKNOWN'))
  self.assertEqual(auto.load(self.root,self.key,self.job)['phase'],'COMMIT_PENDING');self.assertTrue((auto.job_path(self.root,self.job)/'error.json').is_file())
 def test_tls_setup_passes_java_and_generated_pair(self):
  self.settings['java']=str(self.root/'jdk/bin/java.exe');self.ready();self.rollout()
  generated={'cert':str(self.root/'server.pem'),'key':str(self.root/'private.pem')}
  with patch.object(auto.https78,'load',return_value=(None,None,None)),patch.object(auto.https78,'public_info',return_value={'endpoint':'https://localhost:8443/'}),patch.object(auto.publication83,'command',return_value=generated) as issue,patch.object(auto.https78,'save',autospec=True) as save,patch('first58.dedicated75.submit',side_effect=self.enqueue):
   result=auto.launch(self.root,self.root,self.job)
   self.assertEqual(result['phase'],'COMMIT_QUEUED');self.assertEqual(issue.call_args.args[2],'com.fixmer.genesis.technology.server.remote.TlsIdentity159');self.assertEqual(save.call_args.args[-1],Path(self.settings['java']));self.assertEqual(save.call_args.args[-3],Path(generated['key']));self.assertEqual(len(save.call_args.args),7)
 def test_tls_half_manual_pair_is_not_replaced(self):
  self.ready();self.rollout()
  with patch.object(auto.https78,'load',return_value=(None,None,None)):
   with self.assertRaisesRegex(ValueError,'CERT_PAIR_REQUIRED'):auto.launch(self.root,self.root,self.job,cert='provided.pem')
 def test_tls_listener_error_is_not_hidden_as_setup(self):
  self.ready();self.rollout()
  with patch.object(auto.https78,'status',return_value={'state':'FAILED','code':'HTTPS78_LISTEN_FAILED'}):
   with self.assertRaisesRegex(ValueError,'HTTPS78_LISTEN_FAILED'):auto.launch(self.root,self.root,self.job)
if __name__=='__main__':unittest.main()
