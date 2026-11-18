#ifndef GENESIS_JOB_RECORD_H
#define GENESIS_JOB_RECORD_H
#include <stddef.h>
#include <stdint.h>
/* Bounded ASCII fixture records; neither a console protocol nor game authority. */
typedef enum { GC_ROOT=1, GC_LEAF=2, GC_OWNER=3, GC_DENIED=4 } gc_kind;
typedef struct { gc_kind kind; uint32_t value; } gc_record;
typedef struct { char line[64]; size_t used; int failed; } gc_reader;
/* 0=incomplete, 1=record, -1=permanently invalid. One byte per call. */
int gc_feed(gc_reader *reader, unsigned char byte, gc_record *record);
int gc_parse(const char *line, size_t size, gc_record *record);
/* Wrap-safe unsigned monotonic elapsed duration, never wall clock. */
int gc_expired(uint64_t now, uint64_t start, uint32_t timeout_ms);
#endif
