#include "job.h"
#include "capsule.h"
#include "owner_protocol.h"
#include "java_owner.h"
#include <bcrypt.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include <inttypes.h>
#define LOG_CAP 1048576u
#define PIN_LIMIT (32u*1024u*1024u)
static DWORD failure_code(void){DWORD error=GetLastError();return error?error:ERROR_INVALID_DATA;}
static int digest(const unsigned char *data,DWORD size,unsigned char out[32]){
 return BCryptHash(BCRYPT_SHA256_ALG_HANDLE,NULL,0,(unsigned char *)data,size,out,32)>=0;
}
static int hex(wchar_t c){return c>=L'0'&&c<=L'9'?(int)(c-L'0'):c>=L'a'&&c<=L'f'?(int)(c-L'a'+10):-1;}
static int path_ok(const wchar_t *path){
 size_t i,start=3,n=wcslen(path);wchar_t *copy;int ok=1;
 if(n<4||n>4096||!((path[0]>=L'A'&&path[0]<=L'Z')||(path[0]>=L'a'&&path[0]<=L'z'))||path[1]!=L':'||path[2]!=L'\\')return 0;
 copy=(wchar_t *)calloc(n+1,sizeof(wchar_t));if(!copy)return 0;memcpy(copy,path,(n+1)*sizeof(wchar_t));
 for(i=3;i<=n&&ok;i++){
  if(i<n&&(path[i]<32||path[i]==L'/'||path[i]==L'"'||path[i]==L':'||path[i]==L'*'||path[i]==L'?')){ok=0;break;}
  if(i==n||path[i]==L'\\'){
   DWORD attrs;wchar_t saved=copy[i];
   if(i==start||copy[i-1]==L'.'||copy[i-1]==L' '){ok=0;break;}
   copy[i]=0;attrs=GetFileAttributesW(copy);copy[i]=saved;
   if(attrs==INVALID_FILE_ATTRIBUTES||(attrs&FILE_ATTRIBUTE_REPARSE_POINT))ok=0;
   start=i+1;
  }
 }
 free(copy);return ok;
}
/* Share-read only: ordinary writes/deletes of pinned files fail while owner lives. */
static HANDLE read_locked(const wchar_t *path,DWORD limit,unsigned char **data,DWORD *size){
 HANDLE file;LARGE_INTEGER length;DWORD n=0;*data=NULL;*size=0;
 if(!path_ok(path)||(GetFileAttributesW(path)&FILE_ATTRIBUTE_DIRECTORY))return INVALID_HANDLE_VALUE;
 file=CreateFileW(path,GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,FILE_FLAG_OPEN_REPARSE_POINT,NULL);
 if(file==INVALID_HANDLE_VALUE)return file;
 if(!GetFileSizeEx(file,&length)||length.QuadPart<0||length.QuadPart>limit){CloseHandle(file);return INVALID_HANDLE_VALUE;}
 *size=(DWORD)length.QuadPart;*data=(unsigned char *)malloc((size_t)*size+1);
 if(!*data||!ReadFile(file,*data,*size,&n,NULL)||n!=*size){free(*data);*data=NULL;CloseHandle(file);return INVALID_HANDLE_VALUE;}
 return file;
}
static wchar_t *wide(jc_text text){
 uint32_t i;wchar_t *out=(wchar_t *)calloc((size_t)text.units+1,sizeof(wchar_t));
 if(out)for(i=0;i<text.units;i++)out[i]=(wchar_t)((uint16_t)text.bytes[i*2]|((uint16_t)text.bytes[i*2+1]<<8));
 return out;
}
static wchar_t *wide_units(const uint16_t *text,size_t count){size_t i;wchar_t *out=(wchar_t *)calloc(count+1,sizeof(wchar_t));if(out)for(i=0;i<count;i++)out[i]=(wchar_t)text[i];return out;}
static int emit(const char *line){DWORD n=0;size_t length=strlen(line);return length<512&&WriteFile(GetStdHandle(STD_OUTPUT_HANDLE),line,(DWORD)length,&n,NULL)&&n==(DWORD)length;}
static int parse46(char *line,go_command *out){
 if(!strncmp(line,"HELLO 46 ",9))line[7]='4';else if(!strncmp(line,"COMMAND 46 ",11))line[9]='4';else return 0;
 return go_parse(line,strlen(line),out);
}
static void drain(gc_job *job,HANDLE log,DWORD *logged,int *failed){
 unsigned round;for(round=0;round<16;round++){
  unsigned char buffer[4096];DWORD available=0,n=0,written=0,keep;
  if(!job->output||!PeekNamedPipe(job->output,NULL,0,NULL,&available,NULL)||!available)return;
  if(available>sizeof(buffer))available=(DWORD)sizeof(buffer);
  if(!ReadFile(job->output,buffer,available,&n,NULL)||!n)return;
  keep=*logged<LOG_CAP?LOG_CAP-*logged:0;if(keep>n)keep=n;
  if(keep&&!*failed){if(!WriteFile(log,buffer,keep,&written,NULL)||written!=keep)*failed=1;else *logged+=keep;}
 }
}
typedef struct {int known,root;DWORD active,code;} observation;
/* root -1=unconfirmed, 0=observed exited/no child, 1=running. */
static observation observe(gc_job *job,int opened,DWORD *error){
 observation o={1,0,0,0};
 if(opened&&!gc_job_active(job,&o.active)){o.known=0;o.active=UINT32_MAX;*error=job->error;}
 if(job->process){
  DWORD waited=WaitForSingleObject(job->process,0);
  if(waited==WAIT_TIMEOUT)o.root=1;
  else if(waited!=WAIT_OBJECT_0||!GetExitCodeProcess(job->process,&o.code)){o.root=-1;*error=failure_code();}
 }
 return o;
}
static int empty(observation o){return o.known&&o.active==0&&o.root==0;}
int java_owner46(const wchar_t *file,const wchar_t *sha){
 unsigned char *bytes=NULL,expected[32],actual[32];DWORD size=0,error=0,logged=0;
 HANDLE capsule=INVALID_HANDLE_VALUE,pins[JC_MAX_PINS],log=INVALID_HANDLE_VALUE,receipt=INVALID_HANDLE_VALUE;
 jc_capsule plan;gc_job job;go_fence fence;jp_policy policy;observation seen={1,0,0,0};
 wchar_t *module=NULL,*cwd=NULL,*cmd=NULL,*env=NULL,*base=NULL,*log_path=NULL,*receipt_path=NULL;
 uint16_t *units=NULL;size_t count,i;uint64_t pinned=0,last_input=GetTickCount64(),force_at=0;
 int result=75,opened=0,bound=0,lost=0,log_failed=0,close_requested=0,lifecycle50=0;
 char input[GO_LINE],reply[512],cached[512],sha_ascii[65];size_t used=0;
 memset(&job,0,sizeof(job));memset(&fence,0,sizeof(fence));memset(&policy,0,sizeof(policy));cached[0]=0;
 for(i=0;i<JC_MAX_PINS;i++)pins[i]=INVALID_HANDLE_VALUE;
 if(wcslen(sha)!=64)goto cleanup;
 for(i=0;i<32;i++){int a=hex(sha[i*2]),b=hex(sha[i*2+1]);if(a<0||b<0)goto cleanup;expected[i]=(unsigned char)((a<<4)|b);sha_ascii[2*i]=(char)sha[2*i];sha_ascii[2*i+1]=(char)sha[2*i+1];}sha_ascii[64]=0;
 capsule=read_locked(file,JC_MAX_BYTES,&bytes,&size);
 if(capsule==INVALID_HANDLE_VALUE||!digest(bytes,size,actual)||memcmp(actual,expected,32)||!jc_decode(bytes,size,&plan))goto cleanup;
 /* Two fixed diagnostics only. Kind2 remains rejected before Job/child creation. */
 if(plan.kind!=1||plan.argc!=5||plan.pinc!=2||!jc_equal_ascii(plan.args[0],"-cp"))goto cleanup;
 lifecycle50=jc_equal_ascii(plan.args[2],"com.fixmer.genesis.technology.server.process.ServerNativeLifecycleProbe")&&jc_equal_ascii(plan.args[4],"--genesis-native-lifecycle50");
 if(lifecycle50){if(!jc_equal_ascii(plan.args[3],"normal")&&!jc_equal_ascii(plan.args[3],"save-fail")&&!jc_equal_ascii(plan.args[3],"stop-crash")&&!jc_equal_ascii(plan.args[3],"stall"))goto cleanup;}
 else if(!jc_equal_ascii(plan.args[2],"com.fixmer.genesis.technology.server.process.ServerNativeJavaProbe")||!jc_equal_ascii(plan.args[4],"--genesis-native-probe46")||(!jc_equal_ascii(plan.args[3],"normal")&&!jc_equal_ascii(plan.args[3],"ignore")&&!jc_equal_ascii(plan.args[3],"flood")&&!jc_equal_ascii(plan.args[3],"root-exit")))goto cleanup;
 module=wide(plan.module);cwd=wide(plan.cwd);
 if(!module||!cwd||!path_ok(module)||!path_ok(cwd)||!(GetFileAttributesW(cwd)&FILE_ATTRIBUTE_DIRECTORY))goto cleanup;
 {size_t n=wcslen(module);static const wchar_t tail[]=L"\\java.exe";if(n<9)goto cleanup;for(i=0;i<9;i++){wchar_t c=module[n-9+i];if(c>=L'A'&&c<=L'Z')c=(wchar_t)(c-L'A'+L'a');if(c!=tail[i])goto cleanup;}}
 {wchar_t *classes=wide(plan.args[1]),*class_file=wide(plan.files[1]);size_t length=wcslen(cwd)+128;wchar_t *expected_path=(wchar_t *)calloc(length,sizeof(wchar_t));int ok=0;
  if(classes&&class_file&&expected_path&&!wcschr(cwd,L';')){
   if(lifecycle50){if(swprintf(expected_path,length,L"%ls\\probe50.jar",cwd)>=0&&wcscmp(classes,expected_path)==0&&wcscmp(class_file,expected_path)==0)ok=1;}
   else if(swprintf(expected_path,length,L"%ls\\classes",cwd)>=0&&wcscmp(classes,expected_path)==0&&swprintf(expected_path,length,L"%ls\\classes\\com\\fixmer\\genesis\\technology\\server\\process\\ServerNativeJavaProbe.class",cwd)>=0&&wcscmp(class_file,expected_path)==0)ok=1;
  }
  free(classes);free(class_file);free(expected_path);if(!ok)goto cleanup;
 }
 if(lifecycle50){
  size_t length=wcslen(cwd)+128;wchar_t *parent=(wchar_t *)calloc(length,sizeof(wchar_t)),*expected_run=(wchar_t *)calloc(length,sizeof(wchar_t)),run_uuid[37];int supervised=0,id=0,directory=0,ok=0;
  for(i=0;i<36;i++){run_uuid[i]=(wchar_t)(unsigned char)plan.run[i];}run_uuid[36]=0;
  if(parent&&expected_run&&plan.envc==9){wchar_t *slash;wcscpy(parent,cwd);slash=wcsrchr(parent,L'\\');if(slash){*slash=0;if(swprintf(expected_run,length,L"%ls\\state\\runs\\%ls",parent,run_uuid)>=0){
   for(i=0;i<plan.envc;i++){
    if(jc_equal_ascii(plan.names[i],"GENESIS_SERVER_SUPERVISED"))supervised=jc_equal_ascii(plan.values[i],"1");
    if(jc_equal_ascii(plan.names[i],"GENESIS_SERVER_RUN_ID"))id=jc_equal_ascii(plan.values[i],plan.run);
    if(jc_equal_ascii(plan.names[i],"GENESIS_SERVER_RUN_DIR")){wchar_t *value=wide(plan.values[i]);directory=value&&wcscmp(value,expected_run)==0&&path_ok(value)&&(GetFileAttributesW(value)&FILE_ATTRIBUTE_DIRECTORY);free(value);}
   }
   ok=supervised&&id&&directory;
  }}}
  free(parent);free(expected_run);if(!ok)goto cleanup;
 }
 for(i=0;i<plan.pinc;i++){
  wchar_t *path=wide(plan.files[i]);unsigned char *data=NULL;DWORD n=0;
  if(!path)goto cleanup;
  pins[i]=read_locked(path,i==0?PIN_LIMIT:1048576u,&data,&n);free(path);pinned+=n;
  if(pins[i]==INVALID_HANDLE_VALUE||pinned>36u*1024u*1024u||!digest(data,n,actual)||memcmp(actual,plan.hashes[i],32)){free(data);goto cleanup;}
  free(data);
 }
 units=(uint16_t *)calloc(JC_COMMAND_UNITS+2,sizeof(uint16_t));if(!units)goto cleanup;
 count=jc_command(&plan,units,JC_COMMAND_UNITS+1);if(!count||(cmd=wide_units(units,count))==NULL)goto cleanup;
 count=jc_environment(&plan,units,JC_COMMAND_UNITS+1);if(!count||(env=wide_units(units,count))==NULL)goto cleanup;
 base=(wchar_t *)calloc(wcslen(file)+32,sizeof(wchar_t));log_path=(wchar_t *)calloc(wcslen(file)+64,sizeof(wchar_t));receipt_path=(wchar_t *)calloc(wcslen(file)+64,sizeof(wchar_t));
 if(!base||!log_path||!receipt_path)goto cleanup;
 wcscpy(base,file);{wchar_t *slash=wcsrchr(base,L'\\');if(!slash)goto cleanup;*slash=0;}
 if(swprintf(log_path,wcslen(file)+64,L"%ls\\child-output.log",base)<0||swprintf(receipt_path,wcslen(file)+64,L"%ls\\native-exit.json",base)<0)goto cleanup;
 log=CreateFileW(log_path,GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);if(log==INVALID_HANDLE_VALUE)goto cleanup;
 receipt=CreateFileW(receipt_path,GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);if(receipt==INVALID_HANDLE_VALUE)goto cleanup;
 policy.grace_ms=plan.grace_ms;policy.orphan_force=plan.orphan_force;
 for(;;){
  DWORD available=0,n=0;uint64_t now=GetTickCount64();int got=0,stop=0,force=0;unsigned actions,budget=512;const char *phase="NEW";
  drain(&job,log,&logged,&log_failed);seen=observe(&job,opened,&error);
  while(!got&&!lost&&budget--){
   unsigned char ch;
   if(!PeekNamedPipe(GetStdHandle(STD_INPUT_HANDLE),NULL,0,NULL,&available,NULL)){lost=1;break;}
   if(!available)break;
   if(!ReadFile(GetStdHandle(STD_INPUT_HANDLE),&ch,1,&n,NULL)||n!=1){lost=1;break;}
   if(ch=='\n'){input[used]=0;used=0;got=1;}
   else if(ch<32||ch>126||used+1>=sizeof(input))lost=1;else input[used++]=(char)ch;
  }
  if((uint64_t)(now-last_input)>=(bound?plan.lease_ms:10000u))lost=1;
  if(got&&!lost){go_command command;int accepted;
   if(!parse46(input,&command)||strcmp(command.run,plan.run)||strcmp(command.nonce,plan.nonce)||command.epoch!=plan.epoch)lost=1;
   else if((accepted=go_accept(&fence,&command))<0)lost=1;
   else if(accepted==2){last_input=now;if(!emit(cached))lost=1;got=0;}
   else{
    last_input=now;
    if(command.verb==GO_HELLO){if(!gc_job_open(&job)){error=job.error;lost=1;}else{opened=1;bound=1;}}
    else if(command.verb==GO_START){
     if(job.process||!bound||!gc_job_spawn_command(&job,module,cmd,cwd,env)){error=job.error?job.error:ERROR_INVALID_STATE;lost=1;}
     else if(!gc_job_resume(&job)){error=job.error;lost=1;/* Probe-only startup failure: explicitly abort, never graceful. */policy.force_attempted=1;if(gc_job_terminate(&job,125)){policy.force_sent=1;force_at=now;}}
    }else if(command.verb==GO_STOP){if(!job.process){error=ERROR_INVALID_STATE;lost=1;}else stop=1;}
    else if(command.verb==GO_FORCE){if(!job.process){error=ERROR_INVALID_STATE;lost=1;}else force=1;}
    else if(command.verb==GO_CLOSE){if(!empty(seen)){error=ERROR_INVALID_STATE;lost=1;}else close_requested=1;}
   }
  }
  seen=observe(&job,opened,&error);
  actions=jp_step(&policy,now,lost,seen.root!=0,seen.known,seen.active,stop,force);
  if(actions&JP_STOP){DWORD written=0;if(WriteFile(job.input,"stop\n",5,&written,NULL)&&written==5)policy.stop_sent=1;else error=failure_code();}
  if(actions&JP_FORCE){if(gc_job_terminate(&job,124)){policy.force_sent=1;force_at=now;}else error=job.error;}
  seen=observe(&job,opened,&error);
  if(error||!seen.known||seen.root<0)phase="UNKNOWN";
  else if(job.process&&empty(seen))phase="EMPTY";
  else if(job.process&&seen.root==0)phase="DRAINING";
  else if(policy.force_sent&&(uint64_t)(now-force_at)>=plan.force_ms)phase="FORCE_TIMEOUT";
  else if(policy.requested&&(uint64_t)(now-policy.stopped_at)>=plan.grace_ms)phase="STOP_TIMEOUT";
  else if(policy.requested)phase="STOPPING";
  else if(job.process)phase="RUNNING";
  if(close_requested)phase="CLOSED";
  if(got&&!lost){snprintf(reply,sizeof(reply),"EVENT 46 %s %" PRIu64 " %s %" PRIu64 " %s %lu %lu %lu %d %d %d %d %lu\n",plan.run,plan.epoch,plan.nonce,fence.seq,phase,(unsigned long)job.pid,(unsigned long)seen.active,(unsigned long)seen.code,policy.force_sent,policy.stop_sent,policy.owner_lost,log_failed,(unsigned long)error);strcpy(cached,reply);if(!emit(reply))lost=1;}
  if((close_requested||((actions&JP_EXIT)&&empty(seen)))&&empty(seen)){result=error?77:0;break;}
  Sleep(1);
 }
 if(receipt!=INVALID_HANDLE_VALUE){char record[640];DWORD written=0;int len;
  drain(&job,log,&logged,&log_failed);
  len=snprintf(record,sizeof(record),"{\"schema\":46,\"run\":\"%s\",\"epoch\":%" PRIu64 ",\"capsuleSha256\":\"%s\",\"pid\":%lu,\"active\":%lu,\"exitCode\":%lu,\"stopSent\":%s,\"forced\":%s,\"ownerLost\":%s,\"logFailed\":%s,\"nativeError\":%lu,\"installAuthority\":false}\n",plan.run,plan.epoch,sha_ascii,(unsigned long)job.pid,(unsigned long)seen.active,(unsigned long)seen.code,policy.stop_sent?"true":"false",policy.force_sent?"true":"false",policy.owner_lost?"true":"false",log_failed?"true":"false",(unsigned long)error);
  if(len<0||len>=(int)sizeof(record)||!WriteFile(receipt,record,(DWORD)len,&written,NULL)||written!=(DWORD)len||!FlushFileBuffers(receipt))result=76;
 }
 cleanup:
 /* All running-child paths reach cleanup only after confirmed root exit+Job empty. */
 if(opened)gc_job_dispose(&job);
 for(i=0;i<JC_MAX_PINS;i++)if(pins[i]!=INVALID_HANDLE_VALUE)CloseHandle(pins[i]);
 if(capsule!=INVALID_HANDLE_VALUE)CloseHandle(capsule);
 if(log!=INVALID_HANDLE_VALUE)CloseHandle(log);
 if(receipt!=INVALID_HANDLE_VALUE)CloseHandle(receipt);
 free(bytes);free(module);free(cwd);free(cmd);free(env);free(base);free(log_path);free(receipt_path);free(units);return result;
}
