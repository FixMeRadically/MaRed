#include "job.h"
#include "owner.h"
#include "java_owner.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
/* Deliberately diagnostics-only. No arbitrary command, game path, token or install API. */
static wchar_t module_path[32768];
static int emit(gc_kind kind,DWORD value) {
    static const char *names[]={"", "ROOT", "LEAF", "OWNER", "DENIED"};
    char line[64];DWORD written=0;int n=snprintf(line,sizeof(line),"%s %lu\n",names[kind],(unsigned long)value);
    return n>0 && n<(int)sizeof(line) && WriteFile(GetStdHandle(STD_OUTPUT_HANDLE),line,(DWORD)n,&written,NULL) && written==(DWORD)n;
}
/* Fixtures have no files/network/game actions. The outer selftest job owns every
 * normal descendant, including an inner owner's fixture child. */
static int ordinary_child(const wchar_t *name,DWORD flags,PROCESS_INFORMATION *process) {
    STARTUPINFOW startup;wchar_t command[32832];
    memset(&startup,0,sizeof(startup));memset(process,0,sizeof(*process));
    startup.cb=(DWORD)sizeof(startup);startup.dwFlags=STARTF_USESTDHANDLES;
    startup.hStdInput=GetStdHandle(STD_INPUT_HANDLE);startup.hStdOutput=GetStdHandle(STD_OUTPUT_HANDLE);
    startup.hStdError=GetStdHandle(STD_ERROR_HANDLE);
    if(swprintf(command,sizeof(command)/sizeof(command[0]),L"\"%ls\" --fixture %ls",module_path,name)<0) return 0;
    return CreateProcessW(module_path,command,NULL,NULL,TRUE,flags|CREATE_NO_WINDOW,NULL,NULL,&startup,process)!=0;
}
static int fixture(const wchar_t *name) {
    PROCESS_INFORMATION child;gc_job inner;
    if(wcscmp(name,L"leaf")==0) {
        if(!emit(GC_LEAF,GetCurrentProcessId())) return 110;
        Sleep(INFINITE);return 111;
    }
    if(wcscmp(name,L"controlled")==0) {
        DWORD read=0;unsigned char command;
        if(!emit(GC_ROOT,GetCurrentProcessId())) return 110;
        if(!ReadFile(GetStdHandle(STD_INPUT_HANDLE),&command,1,&read,NULL) || read!=1 || command!='S') return 120;
        return 0;
    }
    if(wcscmp(name,L"idle")==0) {
        if(!emit(GC_ROOT,GetCurrentProcessId())) return 110;
        Sleep(INFINITE);return 111;
    }
    if(wcscmp(name,L"tree")==0 || wcscmp(name,L"root-exit")==0) {
        if(!emit(GC_ROOT,GetCurrentProcessId()) || !ordinary_child(L"leaf",0,&child)) return 112;
        CloseHandle(child.hThread);CloseHandle(child.hProcess);
        if(wcscmp(name,L"root-exit")==0) return 0;
        Sleep(INFINITE);return 111;
    }
    if(wcscmp(name,L"breakaway")==0) {
        if(!emit(GC_ROOT,GetCurrentProcessId())) return 110;
        if(ordinary_child(L"leaf",CREATE_BREAKAWAY_FROM_JOB|CREATE_SUSPENDED,&child)) {
            /* Unexpected success: destroy suspended fixture without ever running it. */
            TerminateProcess(child.hProcess,126);WaitForSingleObject(child.hProcess,5000);
            CloseHandle(child.hThread);CloseHandle(child.hProcess);return 113;
        }
        if(!emit(GC_DENIED,GetLastError())) return 110;
        Sleep(INFINITE);return 111;
    }
    if(wcscmp(name,L"owner")==0) {
        DWORD read=0;unsigned char command;gc_record record;
        if(!emit(GC_OWNER,GetCurrentProcessId())) return 110;
        if(!gc_job_open(&inner)) return 114;
        if(!gc_job_spawn_fixture(&inner,module_path,L"idle") || !gc_job_resume(&inner) ||
            !gc_job_record(&inner,&record,5000) || record.kind!=GC_ROOT || record.value!=inner.pid) {
            gc_job_dispose(&inner);return 115;
        }
        if(!emit(GC_LEAF,inner.pid)) {gc_job_dispose(&inner);return 110;}
        /* X exits while inner.job remains open: OS closes it, killing the child.
         * Parent can instead TerminateProcess us to exercise the crash boundary. */
        if(!ReadFile(GetStdHandle(STD_INPUT_HANDLE),&command,1,&read,NULL) || read!=1 || command!='X') {
            gc_job_dispose(&inner);return 116;
        }
        ExitProcess(73);
    }
    return 117;
}
static int expect(gc_job *job,gc_kind kind,DWORD *value) {
    gc_record record;
    if(!gc_job_record(job,&record,7000) || record.kind!=kind) {
        if(!job->error) job->error=ERROR_INVALID_DATA;
        return 0;
    }
    *value=record.value;return 1;
}
static int wait_handle(HANDLE process) {return WaitForSingleObject(process,7000)==WAIT_OBJECT_0;}
static int wait_count(gc_job *job,DWORD expected) {
    ULONGLONG start=GetTickCount64();DWORD count;
    do {
        if(!gc_job_active(job,&count)) return 0;
        if(count==expected) return 1;
        Sleep(5);
    } while(!gc_expired(GetTickCount64(),start,7000));
    job->error=ERROR_TIMEOUT;return 0;
}
static const char *cases[]={"suspended_assignment","membership_and_limits","descendant_kill_on_close",
    "root_exit_keeps_descendant","breakaway_denied","owner_exit_kills_inner_job",
    "owner_crash_kills_inner_job","failed_spawn_no_handle_leak"};
static int run_case(size_t index,DWORD *error) {
    gc_job job;DWORD value=0,count=0,flags=0,exit_code=0;HANDLE leaf=NULL;
    int ok=0;size_t i;
    if(!gc_job_open(&job)) {*error=job.error;return 0;}
#define REQUIRE(expr) do {if(!(expr)) {if(!job.error) job.error=ERROR_INVALID_DATA;goto cleanup;}} while(0)
    if(index==7) {
        DWORD before=0,after=0;
        REQUIRE(GetProcessHandleCount(GetCurrentProcess(),&before));
        for(i=0;i<20;i++) {
            /* Invalid absolute module; failure must release all pipes/attributes. */
            REQUIRE(!gc_job_spawn_fixture(&job,L"?:\\genesis-missing-executable.exe",L"idle"));
            REQUIRE(!job.process && !job.thread && !job.input && !job.output);
            REQUIRE(gc_job_active(&job,&count) && count==0);
        }
        REQUIRE(GetProcessHandleCount(GetCurrentProcess(),&after));REQUIRE(before==after);
        job.error=0;ok=1;goto cleanup;
    }
    REQUIRE(gc_job_spawn_fixture(&job,module_path,
        index==2?L"tree":index==3?L"root-exit":index==4?L"breakaway":index==5 || index==6?L"owner":L"idle"));
    REQUIRE(gc_job_contains(&job,job.process));
    REQUIRE(gc_job_limits(&job,&flags));
    REQUIRE(flags==JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE);
    REQUIRE(wait_count(&job,1));
    if(index==0) {
        DWORD available=0;
        REQUIRE(PeekNamedPipe(job.output,NULL,0,NULL,&available,NULL) && available==0);
        REQUIRE(!job.resumed);
        REQUIRE(gc_job_terminate(&job,120));REQUIRE(wait_handle(job.process));
        REQUIRE(gc_job_wait_empty(&job,7000));ok=1;goto cleanup;
    }
    REQUIRE(gc_job_resume(&job));
    REQUIRE(expect(&job,index==5 || index==6?GC_OWNER:GC_ROOT,&value) && value==job.pid);
    if(index==1) {
        REQUIRE(wait_count(&job,1));
        REQUIRE(gc_job_terminate(&job,121));REQUIRE(wait_handle(job.process));
        REQUIRE(gc_job_wait_empty(&job,7000));ok=1;goto cleanup;
    }
    if(index==4) {
        REQUIRE(expect(&job,GC_DENIED,&value) && value==ERROR_ACCESS_DENIED);
        REQUIRE(wait_count(&job,1));
        REQUIRE(gc_job_terminate(&job,122));REQUIRE(wait_handle(job.process));
        REQUIRE(gc_job_wait_empty(&job,7000));ok=1;goto cleanup;
    }
    REQUIRE(expect(&job,GC_LEAF,&value));REQUIRE(value!=job.pid);
    leaf=OpenProcess(SYNCHRONIZE|PROCESS_QUERY_LIMITED_INFORMATION,FALSE,value);REQUIRE(leaf!=NULL);
    REQUIRE(gc_job_contains(&job,leaf));REQUIRE(WaitForSingleObject(leaf,0)==WAIT_TIMEOUT);
    if(index==2) {
        REQUIRE(wait_count(&job,2));
        gc_job_close_handle(&job);
        REQUIRE(wait_handle(job.process));REQUIRE(wait_handle(leaf));ok=1;goto cleanup;
    }
    if(index==3) {
        REQUIRE(wait_handle(job.process));REQUIRE(GetExitCodeProcess(job.process,&exit_code) && exit_code==0);
        REQUIRE(WaitForSingleObject(leaf,0)==WAIT_TIMEOUT);
        REQUIRE(wait_count(&job,1));
        REQUIRE(gc_job_terminate(&job,123));REQUIRE(wait_handle(leaf));
        REQUIRE(gc_job_wait_empty(&job,7000));ok=1;goto cleanup;
    }
    /* Keep outer job open. Only inner owner's last-handle close kills leaf. */
    if(index==5) REQUIRE(gc_job_send(&job,'X'));
    else REQUIRE(TerminateProcess(job.process,74));
    REQUIRE(wait_handle(job.process));REQUIRE(wait_handle(leaf));
    REQUIRE(GetExitCodeProcess(job.process,&exit_code) && exit_code==(index==5?73u:74u));
    REQUIRE(gc_job_wait_empty(&job,7000));ok=1;
 cleanup:
    *error=ok?0:job.error;
    gc_job_dispose(&job);if(leaf) CloseHandle(leaf);
    return ok;
#undef REQUIRE
}
static int selftest(void) {
    size_t i;int all=1;
    printf("{\"schema\":43,\"mode\":\"diagnostic_fixtures_only\",\"install_authority\":false,\"cases\":[");
    for(i=0;i<sizeof(cases)/sizeof(cases[0]);i++) {
        DWORD error=0;int pass=run_case(i,&error);if(!pass) all=0;
        printf("%s{\"case\":\"%s\",\"pass\":%s,\"win32_error\":%lu}",i?",":"",cases[i],pass?"true":"false",(unsigned long)error);
        fflush(stdout);
    }
    printf("],\"all_passed\":%s}\n",all?"true":"false");return all?0:1;
}
int wmain(int argc,wchar_t **argv) {
    DWORD length=GetModuleFileNameW(NULL,module_path,(DWORD)(sizeof(module_path)/sizeof(module_path[0])));
    if(!length || length>=sizeof(module_path)/sizeof(module_path[0])) return 118;
    if(argc==4 && wcscmp(argv[1],L"--java-session46")==0)return java_owner46(argv[2],argv[3]);
    if(argc==2 && wcscmp(argv[1],L"--owner-session")==0) return go_owner(module_path);
    if(argc==2 && wcscmp(argv[1],L"--probe")==0) {
        puts("{\"schema\":43,\"backend\":\"windows_job\",\"mode\":\"diagnostic_fixtures_only\",\"install_authority\":false}");return 0;
    }
    if(argc==2 && wcscmp(argv[1],L"--selftest")==0) return selftest();
    if(argc==3 && wcscmp(argv[1],L"--fixture")==0) {
        BOOL inside=FALSE;
        /* Refuse accidental manual fixture launch outside any job. */
        if(!IsProcessInJob(GetCurrentProcess(),NULL,&inside) || !inside) return 119;
        return fixture(argv[2]);
    }
    fputs("Genesis native helper 43: --probe | --selftest. No server launch/install interface.\n",stderr);return 2;
}
