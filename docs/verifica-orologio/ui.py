import sys,re,xml.etree.ElementTree as ET
d=float(sys.argv[2])/160
for n in ET.parse(sys.argv[1]).getroot().iter('node'):
    rid=n.get('resource-id','').split('/')[-1]; t=n.get('text',''); cd=n.get('content-desc','')
    if not (rid or t or cd): continue
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    print(f"{rid:28} {t[:24]!r:26} {cd[:40]!r:42} {x1/d:.0f},{y1/d:.0f} {(x2-x1)/d:.0f}x{(y2-y1)/d:.0f}dp clk={n.get('clickable')}")
