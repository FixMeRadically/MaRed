#include "record.h"
#include <string.h>
int gc_parse(const char *line, size_t size, gc_record *record) {
    static const char *names[]={"ROOT ","LEAF ","OWNER ","DENIED "};
    size_t i, j, prefix=0; uint32_t value=0; gc_kind kind=0;
    if (!line || !record || size<6 || size>63) return -1;
    for (i=0;i<4;i++) {
        size_t n=strlen(names[i]);
        if(size>n && memcmp(line,names[i],n)==0) {prefix=n;kind=(gc_kind)(i+1);break;}
    }
    if (!kind || line[prefix]=='0') return -1;
    for (j=prefix;j<size;j++) {
        uint32_t digit;
        if(line[j]<'0' || line[j]>'9') return -1;
        digit=(uint32_t)(line[j]-'0');
        if(value>(UINT32_MAX-digit)/10u) return -1;
        value=value*10u+digit;
    }
    if(!value) return -1;
    record->kind=kind;record->value=value;return 1;
}
int gc_feed(gc_reader *reader, unsigned char byte, gc_record *record) {
    int result;
    if(!reader || !record || reader->failed) return -1;
    if(byte=='\n') {
        result=gc_parse(reader->line,reader->used,record);reader->used=0;
        if(result<0) reader->failed=1;
        return result;
    }
    if(byte<32 || byte>126 || reader->used>=sizeof(reader->line)-1) {
        reader->failed=1;return -1;
    }
    reader->line[reader->used++]=(char)byte;return 0;
}
int gc_expired(uint64_t now,uint64_t start,uint32_t timeout_ms) {
    return (uint64_t)(now-start)>=(uint64_t)timeout_ms;
}
