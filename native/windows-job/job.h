#ifndef GENESIS_WINDOWS_JOB_H
#define GENESIS_WINDOWS_JOB_H
#ifndef _WIN32
#error Windows Job Object backend requires Windows
#endif
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00
#endif
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include "record.h"
_Static_assert(sizeof(void *)==8,"Genesis native diagnostics require a 64-bit build");
typedef struct {
    HANDLE job, process, thread, input, output;
    DWORD pid, error;
    int resumed;
    gc_reader reader;
} gc_job;
/* Caller owns one unnamed, non-inheritable kill-on-close Job Object.
 * Creation never falls back to an unassigned running process. */
int gc_job_open(gc_job *state);
/* Only this executable's hard-coded diagnostic fixture names are accepted. */
int gc_job_spawn_fixture(gc_job *state,const wchar_t *module,const wchar_t *fixture);
/* Internal primitive; executable dispatcher admits only fixed diagnostic capsule kinds. */
int gc_job_spawn_command(gc_job *state,const wchar_t *module,wchar_t *command,const wchar_t *cwd,void *environment);
int gc_job_resume(gc_job *state);
int gc_job_active(gc_job *state,DWORD *active);
int gc_job_contains(gc_job *state,HANDLE process);
int gc_job_limits(gc_job *state,DWORD *flags);
int gc_job_record(gc_job *state,gc_record *record,DWORD timeout_ms);
int gc_job_send(gc_job *state,unsigned char byte);
int gc_job_terminate(gc_job *state,DWORD code);
int gc_job_wait_empty(gc_job *state,DWORD timeout_ms);
/* Closing the last job handle is intentional: KILL_ON_JOB_CLOSE. */
void gc_job_close_handle(gc_job *state);
void gc_job_dispose(gc_job *state);
#endif
