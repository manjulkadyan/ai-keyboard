import re,sys,html
for p in sys.argv[1:]:
    t=open(p).read()
    s=re.sub(r'<script.*?</script>|<style.*?</style>','',t,flags=re.S); s=html.unescape(re.sub(r'<[^>]+>','|',s)); s=re.sub(r'\|+','|',s)
    def sect(a,b):
        i=s.find(a); 
        if i<0: return '-'
        j=s.find(b,i+len(a)); seg=s[i+len(a):j if j>0 else i+3000]
        heads=re.findall(r'\|([A-Z][A-Za-z ,&\-]+)\|expand_more',seg)
        return ', '.join(heads) if heads else seg[:200]
    shared=sect('Data shared|','Security practices') if 'Data collected|' not in s else sect('Data shared|','Data collected|')
    coll=sect('Data collected|','Security practices')
    sec=[k for k in ['Data is encrypted in transit','You can request that data be deleted','No data collected','No data shared','Committed to follow the Play Families Policy','Independent security review'] if k in s]
    print(p.split('/')[-1][:-5],'\n  SHARED:',shared,'\n  COLLECTED:',coll,'\n  FLAGS:',sec)
