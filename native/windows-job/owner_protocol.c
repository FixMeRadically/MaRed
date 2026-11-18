#include "owner_protocol.h"
#include <string.h>
static int number(const char *s,uint64_t *out,int zero) {
    uint64_t value=0;size_t i,n=strlen(s);
    if(!n || n>19 || (n>1 && s[0]=='0'))return 0;
    for(i=0;i<n;i++) {
        uint64_t d;if(s[i]<'0'||s[i]>'9')return 0;d=(uint64_t)(s[i]-'0');
        if(value>(GO_EPOCH_MAX-d)/10)return 0;
        value=value*10+d;
    }
    if(!zero && !value)return 0;
    *out=value;return 1;
}
static int hex(char c) {return (c>='0' && c<='9')||(c>='a' && c<='f');}
static int uuid(const char *s) {
    size_t i;if(strlen(s)!=36)return 0;
    for(i=0;i<36;i++)if(i==8||i==13||i==18||i==23) {if(s[i]!='-')return 0;}else if(!hex(s[i]))return 0;
    return strcmp(s,"00000000-0000-0000-0000-000000000000")!=0;
}
int go_parse(const char *line,size_t length,go_command *out) {
    char copy[GO_LINE],*tokens[8];size_t i,n=1;go_command result;
    if(!line || !out || !length || length>=GO_LINE)return 0;
    memcpy(copy,line,length);copy[length]=0;tokens[0]=copy;
    for(i=0;i<length;i++) {
        if((unsigned char)copy[i]<32 || (unsigned char)copy[i]>126)return 0;
        if(copy[i]==' ') {
            if(!i || i==length-1 || copy[i-1]==' ' || n>=8)return 0;
            copy[i]=0;tokens[n++]=copy+i+1;
        }
    }
    /* The previous byte may have been nulled: empty tokens rejected below. */
    for(i=0;i<n;i++)if(!tokens[i][0])return 0;
    if(n!=5 && n!=7)return 0;
    if(strcmp(tokens[1],"44") || !uuid(tokens[2]) || strlen(tokens[4])!=64)return 0;
    memset(&result,0,sizeof(result));
    for(i=0;i<64;i++)if(!hex(tokens[4][i]))return 0;
    strcpy(result.run,tokens[2]);strcpy(result.nonce,tokens[4]);
    if(!number(tokens[3],&result.epoch,0))return 0;
    if(n==5) {if(strcmp(tokens[0],"HELLO"))return 0;result.verb=GO_HELLO;}
    else {
        static const char *names[]={"START","STATUS","STOP","FORCE","CLOSE"};
        if(strcmp(tokens[0],"COMMAND") || !number(tokens[5],&result.seq,0))return 0;
        for(i=0;i<5;i++)if(!strcmp(tokens[6],names[i])) {result.verb=(go_verb)(GO_START+i);break;}
        if(!result.verb)return 0;
    }
    *out=result;return 1;
}
int go_accept(go_fence *f,const go_command *c) {
    if(!f || !c)return -1;
    if(!f->bound) {
        if(c->verb!=GO_HELLO || c->seq)return -1;
        f->binding=*c;f->last=*c;f->bound=1;f->seq=0;return 1;
    }
    if(c->epoch!=f->binding.epoch || strcmp(c->run,f->binding.run) || strcmp(c->nonce,f->binding.nonce))return -1;
    if(c->seq==f->seq && c->verb==f->last.verb)return 2;
    if(c->verb==GO_HELLO || f->seq==GO_EPOCH_MAX || c->seq!=f->seq+1)return -1;
    f->seq=c->seq;f->last=*c;return 1;
}
