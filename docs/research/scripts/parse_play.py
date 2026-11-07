import re, json, sys, datetime, html as H
def ds(text, key):
    m = re.search(r"AF_initDataCallback\(\{key: '"+key+r"'.*?data:(.*?), sideChannel: \{\}\}\);", text, re.S)
    return json.loads(m.group(1)) if m else None
def g(o, *p):
    try:
        for i in p: o = o[i]
        return o
    except Exception: return None
def find_app_ds(text):
    for k in range(0,20):
        d = ds(text, 'ds:%d'%k)
        if d and g(d,1,2,0,0) and g(d,1,2,72) is not None: return d
    return None
def strip(s): return H.unescape(re.sub(r'<br\s*/?>', '\n', re.sub(r'<(?!br)[^>]+>','',s or ''))) if s else s
def parse(path):
    t = open(path, encoding='utf-8', errors='replace').read()
    d = find_app_ds(t); a = g(d,1,2) if d else None
    out = {'file': path}
    if a:
        upd = g(a,145,0,1,0)
        out.update(title=g(a,0,0), developer=g(a,68,0), dev_email=g(a,69,1,0), website=g(a,69,0,5,2),
          installs=g(a,13,0), real_installs=g(a,13,2), rating=g(a,51,0,1), ratings=g(a,51,2,1), reviews=g(a,51,3,1),
          price=g(a,57,0,0,0,0,1,0,0), iap=g(a,19,0), contains_ads=bool(g(a,48)), content_rating=g(a,9,0),
          genre=g(a,79,0,0,0), released=g(a,10,0), updated=datetime.datetime.utcfromtimestamp(upd).strftime('%Y-%m-%d') if upd else None,
          version=g(a,140,0,0,0), min_android=g(a,140,1,1,0,0,1), screenshots=len(g(a,78,0) or []),
          summary=strip(g(a,73,0,1)), description=strip(g(a,72,0,1)), whats_new=strip(g(a,144,1,1)))
    ids = []
    for m in re.findall(r'/store/apps/details\?id=([A-Za-z0-9_.]+)', t):
        if m not in ids: ids.append(m)
    out['linked_ids'] = ids
    devs = list(dict.fromkeys(re.findall(r'/store/apps/dev(?:eloper)?\?id=([^"&]+)', t)))
    out['dev_links'] = devs
    return out
if __name__ == '__main__':
    res = [parse(p) for p in sys.argv[1:]]
    print(json.dumps(res, indent=1, ensure_ascii=False))
