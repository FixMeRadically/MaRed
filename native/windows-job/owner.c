#include "job.h"
#include "owner_protocol.h"
#include "owner.h"
#include <stdio.h>
#include <string.h>
#include <inttypes.h>
static int input_line(char *line,size_t capacity,DWORD timeout) {
    size_t used=0;ULONGLONG start=GetTickCount64();
    for(;;) {
        DWORD count=0,read=0;unsigned char byte;
        if(gc_expired(GetTickCount64(),start,timeout))return 0;
        if(!PeekNamedPipe(GetStdHandle(STD_INPUT_HANDLE),NULL,0,NULL,&count,NULL))return 0;
        if(!count) {Sleep(5);continue;}
        if(!ReadFile(GetStdHandle(STD_INPUT_HANDLE),&byte,1,&read,NULL)||read!=1)return 0;
        if(byte=='\n') {line[used]=0;return 1;}
        if(byte<32 || byte>126 || used+1>=capacity)return 0;
        line[used++]=(char)byte;
    }
}
static int publish(const char *line) {
    size_t length=strlen(line);DWORD written=0;
    /* Raw pipe bytes: CRT text stdout would expand LF to CRLF on Windows. */
    return length<512 && WriteFile(GetStdHandle(STD_OUTPUT_HANDLE),line,(DWORD)length,&written,NULL) && written==(DWORD)length;
}
int go_owner(const wchar_t *module) {
    gc_job job;go_fence fence;go_command command;char line[GO_LINE],reply[512];
    const char *phase="NEW";DWORD active=0,code=0;int forced=0,opened=0,exit_result=70;
    memset(&fence,0,sizeof(fence));memset(&job,0,sizeof(job));reply[0]=0;
    for(;;) {
        int accepted;
        /* Pipe EOF, malformed input and lease loss all dispose this diagnostic job.
         * No EMPTY receipt is fabricated when the Java owner disappears. */
        if(!input_line(line,sizeof(line),fence.bound?30000:10000))break;
        if(!go_parse(line,strlen(line),&command))break;
        accepted=go_accept(&fence,&command);if(accepted<0)break;
        if(accepted==2) {if(!publish(reply))break;continue;}
        if(command.verb==GO_HELLO) {
            if(!gc_job_open(&job))break;
            opened=1;
        }else {
            if(!gc_job_active(&job,&active))break;
            if(job.process && active==0) {
                if(WaitForSingleObject(job.process,0)!=WAIT_OBJECT_0 || !GetExitCodeProcess(job.process,&code))break;
                phase="EMPTY";
            }
            if(command.verb==GO_START) {
                gc_record record;
                if(strcmp(phase,"NEW") || job.process)break;
                if(!gc_job_spawn_fixture(&job,module,L"controlled") || !gc_job_resume(&job) ||
                    !gc_job_record(&job,&record,7000) || record.kind!=GC_ROOT || record.value!=job.pid)break;
                phase="RUNNING";
            }else if(command.verb==GO_STOP) {
                if(!strcmp(phase,"EMPTY")) { /* Already exited before STOP; observe only. */ }
                else if(strcmp(phase,"RUNNING"))break;
                else
                /* This is the controlled fixture's graceful stdin command, not TerminateJobObject. */
                {if(!gc_job_send(&job,'S'))break;phase="STOPPING";}
            }else if(command.verb==GO_FORCE) {
                if(strcmp(phase,"EMPTY")) {
                    if(strcmp(phase,"RUNNING") && strcmp(phase,"STOPPING"))break;
                    if(!gc_job_terminate(&job,124))break;
                    forced=1;phase="STOPPING";
                }
            }else if(command.verb==GO_CLOSE) {
                if(strcmp(phase,"NEW") && strcmp(phase,"EMPTY"))break;
                phase="CLOSED";
            }
        }
        if(!gc_job_active(&job,&active))break;
        if(job.process && active==0 && strcmp(phase,"CLOSED")) {
            if(WaitForSingleObject(job.process,0)!=WAIT_OBJECT_0 || !GetExitCodeProcess(job.process,&code))break;
            phase="EMPTY";
        }
        if(snprintf(reply,sizeof(reply),"EVENT 44 %s %" PRIu64 " %s %" PRIu64 " %s %lu %lu %lu %d\n",
            fence.binding.run,fence.binding.epoch,fence.binding.nonce,command.seq,phase,
            (unsigned long)job.pid,(unsigned long)active,(unsigned long)code,forced)<0)break;
        if(!publish(reply))break;
        if(!strcmp(phase,"CLOSED")) {exit_result=0;break;}
    }
    if(opened)gc_job_dispose(&job);
    return exit_result;
}
