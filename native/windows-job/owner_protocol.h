#ifndef GENESIS_OWNER_PROTOCOL44_H
#define GENESIS_OWNER_PROTOCOL44_H
#include <stdint.h>
#include <stddef.h>
#define GO_LINE 256
#define GO_EPOCH_MAX UINT64_C(9223372036854775807)
typedef enum { GO_HELLO=1,GO_START,GO_STATUS,GO_STOP,GO_FORCE,GO_CLOSE } go_verb;
typedef struct {char run[37],nonce[65];uint64_t epoch,seq;go_verb verb;} go_command;
typedef struct {int bound;go_command binding,last;uint64_t seq;} go_fence;
/* Strict ASCII command, without LF. No arbitrary arguments or paths. */
int go_parse(const char *line,size_t length,go_command *out);
/* 1=new, 2=exact last request replay, -1=invalid/reused/out of order.
 * Changes state only for accepted fresh requests. */
int go_accept(go_fence *fence,const go_command *command);
#endif
