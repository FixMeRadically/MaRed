#ifndef GENESIS_JAVA_CAPSULE45_H
#define GENESIS_JAVA_CAPSULE45_H
#include <stdint.h>
#include <stddef.h>
#define JC_MAX_BYTES 262144u
#define JC_MAX_ARGS 128u
#define JC_MAX_ENV 32u
#define JC_MAX_PINS 17u
#define JC_COMMAND_UNITS 32760u
typedef struct {const unsigned char *bytes;uint32_t units;} jc_text;
typedef struct {
 uint32_t kind,grace_ms,force_ms,orphan_force,lease_ms,argc,envc,pinc;
 uint64_t epoch;char run[37],nonce[65];jc_text module,cwd,args[JC_MAX_ARGS],names[JC_MAX_ENV],values[JC_MAX_ENV],files[JC_MAX_PINS];
 unsigned char hashes[JC_MAX_PINS][32];
} jc_capsule;
int jc_decode(const unsigned char *data,size_t bytes,jc_capsule *out);
int jc_equal_ascii(jc_text text,const char *ascii);
/* Always quote every argument; returns UTF16 units excluding final NUL, 0 on error. */
size_t jc_command(const jc_capsule *capsule,uint16_t *out,size_t capacity);
size_t jc_environment(const jc_capsule *capsule,uint16_t *out,size_t capacity);
/* Pure orphan/stop policy, shared by the real native loop and portable tests. */
#define JP_STOP 1u
#define JP_FORCE 2u
#define JP_EXIT 4u
typedef struct {uint32_t grace_ms,orphan_force;uint64_t stopped_at;int requested,attempted,stop_sent,force_attempted,force_sent,owner_lost;} jp_policy;
unsigned jp_step(jp_policy *p,uint64_t now,int owner_lost,int root_alive,int active_known,uint32_t active,int stop,int force);
#endif
