#include "capsule.h"
#include <string.h>
typedef struct {const unsigned char *data;size_t at,size;int bad;} cursor;
static uint32_t u32(cursor *c){uint32_t n=0;unsigned i;if(c->bad||c->size-c->at<4){c->bad=1;return 0;}for(i=0;i<4;i++)n|=(uint32_t)c->data[c->at++]<<(8*i);return n;}
static uint64_t u64(cursor *c){uint64_t lo=u32(c),hi=u32(c);return lo|(hi<<32);}
static int take(cursor *c,void *out,size_t n){if(c->bad||n>c->size-c->at){c->bad=1;return 0;}memcpy(out,c->data+c->at,n);c->at+=n;return 1;}
static uint16_t unit(jc_text t,uint32_t i){return (uint16_t)((uint16_t)t.bytes[i*2]|((uint16_t)t.bytes[i*2+1]<<8));}
static jc_text text(cursor *c,uint32_t min,uint32_t max){
 jc_text t={NULL,0};uint32_t i,n=u32(c);
 if(c->bad||n<min||n>max||(size_t)n*2>c->size-c->at){c->bad=1;return t;}
 t.bytes=c->data+c->at;t.units=n;c->at+=(size_t)n*2;
 for(i=0;i<n;i++){
  uint16_t ch=unit(t,i);if(ch<32 || (ch>=0xdc00&&ch<=0xdfff)){c->bad=1;break;}
  if(ch>=0xd800&&ch<=0xdbff){if(++i>=n||unit(t,i)<0xdc00||unit(t,i)>0xdfff){c->bad=1;break;}}
 }
 return t;
}
static int hex(char c){return (c>='0'&&c<='9')||(c>='a'&&c<='f');}
static int upper(int c){return c>='a'&&c<='z'?c-'a'+'A':c;}
static int compare_name(jc_text a,jc_text b){uint32_t i,n=a.units<b.units?a.units:b.units;for(i=0;i<n;i++){int d=upper(unit(a,i))-upper(unit(b,i));if(d)return d;}return a.units<b.units?-1:a.units>b.units?1:0;}
int jc_equal_ascii(jc_text t,const char *s){uint32_t i;if(strlen(s)!=t.units)return 0;for(i=0;i<t.units;i++)if(unit(t,i)!=(unsigned char)s[i])return 0;return 1;}
static int allowed_name(jc_text t){
 static const char *names[]={"GENESIS_SERVER_RUN_DIR","GENESIS_SERVER_RUN_ID","GENESIS_SERVER_SUPERVISED","JAVA_HOME","PATH","SYSTEMROOT","TEMP","TMP","WINDIR"};
 uint32_t i,j;for(i=0;i<sizeof(names)/sizeof(names[0]);i++){if(strlen(names[i])!=t.units)continue;for(j=0;j<t.units;j++)if(upper(unit(t,j))!=names[i][j])break;if(j==t.units)return 1;}return 0;
}
int jc_decode(const unsigned char *data,size_t size,jc_capsule *out){
 cursor c;uint32_t i;unsigned char magic[8];jc_capsule result;
 if(!data||!out||size>JC_MAX_BYTES)return 0;
 memset(&result,0,sizeof(result));c.data=data;c.at=0;c.size=size;c.bad=0;
 if(!take(&c,magic,8)||memcmp(magic,"GJAVA45\0",8))return 0;
 result.kind=u32(&c);result.epoch=u64(&c);result.grace_ms=u32(&c);result.force_ms=u32(&c);result.orphan_force=u32(&c);result.lease_ms=u32(&c);
 if((result.kind!=1&&result.kind!=2)||!result.epoch||result.epoch>INT64_MAX||result.grace_ms<100||result.grace_ms>300000||result.force_ms<100||result.force_ms>30000||result.orphan_force>1||result.lease_ms<1000||result.lease_ms>300000)return 0;
 if(!take(&c,result.run,36)||!take(&c,result.nonce,64))return 0;
 for(i=0;i<36;i++)if(i==8||i==13||i==18||i==23){if(result.run[i]!='-')return 0;}else if(!hex(result.run[i]))return 0;
 if(!strcmp(result.run,"00000000-0000-0000-0000-000000000000"))return 0;
 for(i=0;i<64;i++)if(!hex(result.nonce[i]))return 0;
 result.argc=u32(&c);result.envc=u32(&c);result.pinc=u32(&c);
 if(!result.argc||result.argc>JC_MAX_ARGS||!result.envc||result.envc>JC_MAX_ENV||!result.pinc||result.pinc>JC_MAX_PINS)return 0;
 result.module=text(&c,1,4096);result.cwd=text(&c,1,4096);
 for(i=0;i<result.argc;i++)result.args[i]=text(&c,0,4096);
 for(i=0;i<result.envc;i++){result.names[i]=text(&c,1,128);result.values[i]=text(&c,0,4096);if(c.bad||!allowed_name(result.names[i])||(i&&compare_name(result.names[i-1],result.names[i])>=0))return 0;}
 for(i=0;i<result.pinc;i++){uint32_t j;result.files[i]=text(&c,1,4096);if(!take(&c,result.hashes[i],32))return 0;for(j=0;j<i;j++)if(compare_name(result.files[i],result.files[j])==0)return 0;}
 if(c.bad||c.at!=c.size||compare_name(result.files[0],result.module)!=0)return 0;
 *out=result;return 1;
}
static int append(uint16_t *out,size_t *used,size_t cap,uint16_t value){if(*used>=cap)return 0;out[(*used)++]=value;return 1;}
static int quote(jc_text t,uint16_t *out,size_t *used,size_t cap){
 uint32_t i=0;if(!append(out,used,cap,'"'))return 0;
 while(i<t.units){uint32_t n=0,j;while(i<t.units&&unit(t,i)=='\\'){n++;i++;}
  if(i==t.units){for(j=0;j<2*n;j++)if(!append(out,used,cap,'\\'))return 0;break;}
  for(j=0;j<n;j++)if(!append(out,used,cap,'\\'))return 0;
  if(unit(t,i)=='"')for(j=0;j<=n;j++)if(!append(out,used,cap,'\\'))return 0;
  if(!append(out,used,cap,unit(t,i++)))return 0;
 }
 return append(out,used,cap,'"');
}
size_t jc_command(const jc_capsule *p,uint16_t *out,size_t cap){
 size_t used=0;uint32_t i;if(!p||!out||cap<1||!quote(p->module,out,&used,cap-1))return 0;
 for(i=0;i<p->argc;i++)if(!append(out,&used,cap-1,' ')||!quote(p->args[i],out,&used,cap-1))return 0;
 if(used>JC_COMMAND_UNITS)return 0;
 out[used]=0;return used;
}
size_t jc_environment(const jc_capsule *p,uint16_t *out,size_t cap){
 size_t used=0;uint32_t i,j;if(!p||!out||!p->envc)return 0;
 for(i=0;i<p->envc;i++){
  for(j=0;j<p->names[i].units;j++)if(!append(out,&used,cap,unit(p->names[i],j)))return 0;
  if(!append(out,&used,cap,'='))return 0;
  for(j=0;j<p->values[i].units;j++)if(!append(out,&used,cap,unit(p->values[i],j)))return 0;
  if(!append(out,&used,cap,0))return 0;
 }
 if(!append(out,&used,cap,0)||used>JC_COMMAND_UNITS)return 0;
 return used;
}
unsigned jp_step(jp_policy *p,uint64_t now,int lost,int root,int known,uint32_t active,int stop,int force){
 unsigned actions=0;if(lost)p->owner_lost=1;
 if((p->owner_lost||stop||force)&&!p->requested){p->requested=1;p->stopped_at=now;}
 if(known&&active==0&&!root)return p->owner_lost?JP_EXIT:0;
 if(p->requested&&root&&!p->attempted){p->attempted=1;actions|=JP_STOP;}
 if((force||(p->owner_lost&&p->orphan_force&&p->requested&&(uint64_t)(now-p->stopped_at)>=p->grace_ms))&&!p->force_attempted){p->force_attempted=1;actions|=JP_FORCE;}
 return actions;
}
