#include "job.h"
#include <wchar.h>
#include <stdlib.h>
#include <string.h>
static void close_handle(HANDLE *handle) {
    if(*handle && *handle!=INVALID_HANDLE_VALUE) CloseHandle(*handle);
    *handle=NULL;
}
static int failure(gc_job *state,DWORD error) {state->error=error?error:ERROR_INVALID_DATA;return 0;}
int gc_job_open(gc_job *state) {
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits;
    memset(state,0,sizeof(*state));memset(&limits,0,sizeof(limits));
    state->job=CreateJobObjectW(NULL,NULL);
    if(!state->job) return failure(state,GetLastError());
    limits.BasicLimitInformation.LimitFlags=JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    if(!SetInformationJobObject(state->job,JobObjectExtendedLimitInformation,&limits,(DWORD)sizeof(limits))) {
        DWORD error=GetLastError();gc_job_dispose(state);return failure(state,error);
    }
    return 1;
}
static int allowed(const wchar_t *name) {
    return wcscmp(name,L"controlled")==0 || wcscmp(name,L"idle")==0 || wcscmp(name,L"tree")==0 ||
        wcscmp(name,L"root-exit")==0 || wcscmp(name,L"breakaway")==0 ||
        wcscmp(name,L"owner")==0;
}
int gc_job_spawn_command(gc_job *state,const wchar_t *module,wchar_t *command,const wchar_t *cwd,void *environment) {
    HANDLE input_read=NULL,output_write=NULL,handles[2];
    SECURITY_ATTRIBUTES security={sizeof(SECURITY_ATTRIBUTES),NULL,TRUE};
    STARTUPINFOEXW startup;PROCESS_INFORMATION process;SIZE_T bytes=0;
    DWORD error=ERROR_INVALID_PARAMETER;
    int created=0,assigned=0;
    memset(&startup,0,sizeof(startup));memset(&process,0,sizeof(process));
    if(!state->job || state->process || !module || !command || wcschr(module,L'"'))
        return failure(state,ERROR_INVALID_PARAMETER);
    if(!CreatePipe(&input_read,&state->input,&security,0)) {error=GetLastError();goto done;}
    if(!SetHandleInformation(state->input,HANDLE_FLAG_INHERIT,0)) {error=GetLastError();goto done;}
    if(!CreatePipe(&state->output,&output_write,&security,0)) {error=GetLastError();goto done;}
    if(!SetHandleInformation(state->output,HANDLE_FLAG_INHERIT,0)) {error=GetLastError();goto done;}
    /* Only child's stdin/stdout are inherited. In particular never inherit job. */
    handles[0]=input_read;handles[1]=output_write;
    startup.StartupInfo.cb=(DWORD)sizeof(startup);startup.StartupInfo.dwFlags=STARTF_USESTDHANDLES;
    startup.StartupInfo.hStdInput=input_read;startup.StartupInfo.hStdOutput=output_write;
    startup.StartupInfo.hStdError=output_write;
    InitializeProcThreadAttributeList(NULL,2,0,&bytes);
    if(!bytes) {error=GetLastError();goto done;}
    startup.lpAttributeList=(LPPROC_THREAD_ATTRIBUTE_LIST)malloc(bytes);
    if(!startup.lpAttributeList) {error=ERROR_NOT_ENOUGH_MEMORY;goto done;}
    if(!InitializeProcThreadAttributeList(startup.lpAttributeList,2,0,&bytes)) {
        error=GetLastError();free(startup.lpAttributeList);startup.lpAttributeList=NULL;goto done;
    }
    if(!UpdateProcThreadAttribute(startup.lpAttributeList,0,PROC_THREAD_ATTRIBUTE_HANDLE_LIST,
        handles,sizeof(handles),NULL,NULL)) {error=GetLastError();goto done;}
    /* Associate at creation, not in a later AssignProcessToJobObject gap. */
    if(!UpdateProcThreadAttribute(startup.lpAttributeList,0,PROC_THREAD_ATTRIBUTE_JOB_LIST,
        &state->job,sizeof(state->job),NULL,NULL)) {error=GetLastError();goto done;}
    if(!CreateProcessW(module,command,NULL,NULL,TRUE,
        CREATE_SUSPENDED|CREATE_NO_WINDOW|EXTENDED_STARTUPINFO_PRESENT|(environment?CREATE_UNICODE_ENVIRONMENT:0),environment,cwd,&startup.StartupInfo,&process)) {
        error=GetLastError();goto done;
    }
    created=1;
    if(!gc_job_contains(state,process.hProcess)) {error=state->error;goto done;}
    assigned=1;state->process=process.hProcess;state->thread=process.hThread;state->pid=process.dwProcessId;
 done:
    if(startup.lpAttributeList) {DeleteProcThreadAttributeList(startup.lpAttributeList);free(startup.lpAttributeList);}
    close_handle(&input_read);close_handle(&output_write);
    if(!assigned) {
        if(created) {
            /* The primary thread has never run. Do not resume on assignment failure. */
            if(!TerminateProcess(process.hProcess,125)) error=GetLastError();
            if(WaitForSingleObject(process.hProcess,5000)!=WAIT_OBJECT_0) error=ERROR_TIMEOUT;
            CloseHandle(process.hThread);CloseHandle(process.hProcess);
        }
        close_handle(&state->input);close_handle(&state->output);return failure(state,error);
    }
    return 1;
}
int gc_job_spawn_fixture(gc_job *state,const wchar_t *module,const wchar_t *fixture) {
    wchar_t *command;size_t length;int result;
    if(!module || !fixture || !allowed(fixture) || wcschr(module,L'"'))return failure(state,ERROR_INVALID_PARAMETER);
    length=wcslen(module)+wcslen(fixture)+32;
    if(length>32760)return failure(state,ERROR_FILENAME_EXCED_RANGE);
    command=(wchar_t *)calloc(length,sizeof(wchar_t));
    if(!command)return failure(state,ERROR_NOT_ENOUGH_MEMORY);
    if(swprintf(command,length,L"\"%ls\" --fixture %ls",module,fixture)<0){free(command);return failure(state,ERROR_INVALID_PARAMETER);}
    result=gc_job_spawn_command(state,module,command,NULL,NULL);free(command);return result;
}
int gc_job_resume(gc_job *state) {
    if(!state->job || !state->thread || state->resumed) return failure(state,ERROR_INVALID_STATE);
    DWORD previous=ResumeThread(state->thread);
    if(previous==(DWORD)-1) return failure(state,GetLastError());
    if(previous!=1) return failure(state,ERROR_INVALID_STATE);
    state->resumed=1;close_handle(&state->thread);return 1;
}
int gc_job_active(gc_job *state,DWORD *active) {
    JOBOBJECT_BASIC_ACCOUNTING_INFORMATION info;
    if(!state->job || !active) return failure(state,ERROR_INVALID_HANDLE);
    if(!QueryInformationJobObject(state->job,JobObjectBasicAccountingInformation,&info,(DWORD)sizeof(info),NULL))
        return failure(state,GetLastError());
    *active=info.ActiveProcesses;return 1;
}
int gc_job_contains(gc_job *state,HANDLE process) {
    BOOL inside=FALSE;
    if(!state->job || !process) return failure(state,ERROR_INVALID_HANDLE);
    if(!IsProcessInJob(process,state->job,&inside)) return failure(state,GetLastError());
    return inside?1:failure(state,ERROR_ACCESS_DENIED);
}
int gc_job_limits(gc_job *state,DWORD *flags) {
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION info;
    if(!state->job || !flags) return failure(state,ERROR_INVALID_HANDLE);
    if(!QueryInformationJobObject(state->job,JobObjectExtendedLimitInformation,&info,(DWORD)sizeof(info),NULL))
        return failure(state,GetLastError());
    *flags=info.BasicLimitInformation.LimitFlags;return 1;
}
int gc_job_record(gc_job *state,gc_record *record,DWORD timeout_ms) {
    ULONGLONG start=GetTickCount64();
    while(!gc_expired(GetTickCount64(),start,timeout_ms)) {
        DWORD available=0,read=0;unsigned char byte;int result;
        if(!PeekNamedPipe(state->output,NULL,0,NULL,&available,NULL)) return failure(state,GetLastError());
        if(!available) {Sleep(5);continue;}
        if(!ReadFile(state->output,&byte,1,&read,NULL) || read!=1) return failure(state,GetLastError());
        result=gc_feed(&state->reader,byte,record);
        if(result<0) return failure(state,ERROR_INVALID_DATA);
        if(result==1) return 1;
    }
    return failure(state,ERROR_TIMEOUT);
}
int gc_job_send(gc_job *state,unsigned char byte) {
    DWORD written=0;
    if(!WriteFile(state->input,&byte,1,&written,NULL) || written!=1) return failure(state,GetLastError());
    return 1;
}
int gc_job_terminate(gc_job *state,DWORD code) {
    if(!state->job) return failure(state,ERROR_INVALID_HANDLE);
    if(!TerminateJobObject(state->job,code)) return failure(state,GetLastError());
    return 1;
}
int gc_job_wait_empty(gc_job *state,DWORD timeout_ms) {
    ULONGLONG start=GetTickCount64();DWORD count;
    do {
        if(!gc_job_active(state,&count)) return 0;
        if(count==0) return 1;
        Sleep(5);
    } while(!gc_expired(GetTickCount64(),start,timeout_ms));
    return failure(state,ERROR_TIMEOUT);
}
void gc_job_close_handle(gc_job *state) {close_handle(&state->job);}
void gc_job_dispose(gc_job *state) {
    gc_job_close_handle(state);
    if(state->process) WaitForSingleObject(state->process,5000);
    close_handle(&state->thread);close_handle(&state->process);
    close_handle(&state->input);close_handle(&state->output);
}
