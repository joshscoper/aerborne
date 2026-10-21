"""Loopback-only UI fixture. Fictional data and intercepted actions; no game server connection."""
import datetime, json, mimetypes, math, html, hashlib, uuid
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit, parse_qs

ROOT = Path(__file__).resolve().parents[2] / 'nerdsmp-paper/src/main/resources/panel'
HERE = Path(__file__).resolve().parent
NOW = datetime.datetime.now(datetime.timezone.utc)

def revision():
    digest = hashlib.sha256()
    for source in sorted(ROOT.glob('*')):
        if source.suffix in ('.html','.css','.js'): digest.update(source.read_bytes())
    return digest.hexdigest()
def time(minutes=0):
    return (NOW - datetime.timedelta(minutes=minutes)).isoformat()
def uid(number):
    return '10000000-0000-4000-8000-%012d' % number
NAMES = ['Vale', 'Ember', 'Rowan', 'Marin', 'Atlas', 'Luna', 'Finch', 'Ash']
DIRECTORY = [{'uuid':uid(i+1),'name':NAMES[i] if i<8 else 'Adventurer%03d' % (i+1),
              'firstPlayed':time(80000+i*60),'lastSeen':time(i*15),'records':1 if i==2 else 0,'banned':i==12} for i in range(123)]
PLAYERS = [dict(DIRECTORY[i],ping=[24,38,86,42,29,64,31,52][i],world='Skyhold' if i%2==0 else 'Aerborne',
                x=124+i*7,y=74,z=-32+i*3,gamemode='SURVIVAL',health=20,maxHealth=20,food=20,level=15+i,
                op=i==0,flying=False,brand='vanilla',lastLogin=int(NOW.timestamp()*1000)-2500000,
                protocol=774,sharedAccounts=0,score=12 if i==2 else 0,muted=False,frozen=False,
                groups='admin' if i==0 else 'helper' if i==1 else '',allowFlight=False,effects=[],
                inventory=[{'slot':0,'type':'DIAMOND_PICKAXE','name':'Wayfarer’s pickaxe','enchanted':True,'amount':1},
                           {'slot':1,'type':'COBBLESTONE','name':'Cobblestone','enchanted':False,'amount':64}],
                enderChest=[]) for i in range(8)]
EVIDENCE = [{'time':time(2+i*5),'uuid':uid(3),'player':'Rowan','check':['SPEED','TIMER','REACH'][i%3],
             'detail':['Movement exceeded the simulated sprint range. Review the surrounding evidence.',
                       'Client timing drift recorded during a combat session.',
                       'Reach sample retained for staff review.'][i%3],
             'score':12+i*2,'ping':86,'action':'BUFFERED'} for i in range(4)]
REPORTS = [{'id':'sample-report-%d'%i,'shortId':'R%03d'%i,'targetId':uid(j+1),'target':NAMES[j],
            'reporterId':uid(4),'reporter':'Marin','reason':reason,'time':time(8+i*6),'status':'OPEN'}
           for i,j,reason in [(1,2,'Unusual movement near the eastern bridge. Please review before taking action.'),
                             (2,4,'A disagreement over a shared island trade. Both players are waiting for help.'),
                             (3,6,'A player needs assistance recovering access to their island.')]]
AUDIT = [{'time':time(3),'actor':'Ember','target':'Atlas','targetId':uid(5),'action':'NOTE','detail':'Opened a support investigation.'},
         {'time':time(17),'actor':'Vale','target':'Rowan','targetId':uid(3),'action':'WARNING','detail':'Reminder about community rules.'},
         {'time':time(30),'actor':'Ember','target':'Marin','targetId':uid(4),'action':'REPORT_RESOLVED','detail':'Answered an island support request.'}]
SERVER = dict(name='Aerborne',version='Paper 26.2 build 121',minecraft='26.2',plugin='0.1.0',java='25.0.4.1',
              os='Linux amd64',cores=8,uptime=172800000,online=8,maxPlayers=50,whitelist=False,onlineMode=True,
              viewDistance=8,simulationDistance=6,tps1=19.98,tps5=19.96,tps15=19.97,mspt=24.8,msptP95=32.1,
              msptMax=46.2,memoryUsed=1824,memoryAllocated=4096,memoryMax=8192,cpu=23.4,systemCpu=31.2,
              threads=42,gcCount=146,gcMillis=7800)
DATA = dict(status='online',updated=time(),server=SERVER,players=PLAYERS,directory=DIRECTORY,
            bans=[dict(uuid=uid(13),name='Adventurer013',reason='Sample policy review',expires=time(-120))],
            worlds=[dict(name='Skyhold',environment='NORMAL',players=4,entities=238,tileEntities=42,chunks=186,storm=False),
                    dict(name='Aerborne',environment='NORMAL',players=4,entities=324,tileEntities=63,chunks=248,storm=False)],
            geography=dict(enabled=True,recordsAddresses=True,located=108,unlocated=15,onlineLocated=8,
                           database=dict(state='ready',database='Sample location database',built=time(15000),autoDownload=False),
                           countries=[dict(name=name,code=code,players=count,online=online,regions=[],cities=[])
                                      for name,code,count,online in [('United States','US',62,4),('United Kingdom','GB',24,2),('Germany','DE',14,1),('Canada','CA',8,1)]]),
            watchdog=dict(mode='alerts-only',alertScore=10,setbackScore=30,kickScore=50,decayPerSecond=.2,
                          checks=['SPEED','FLIGHT','REACH','TIMER'],packetLayer='Ready',packetErrors=0,
                          live=[dict(uuid=uid(3),name='Rowan',score=12,counts=dict(SPEED=2,TIMER=1),probability=.34,exempt=False)],
                          evidence=EVIDENCE,model=dict(enabled=True,autonomous=False,autonomyAllowed=False,note='Collecting labelled combat samples. Staff remain in control.',
                                windows=42,baselineSamples=640,cheatWindows=6,cheatPlayers=2,legitWindows=36,legitPlayers=8,
                                requirements=dict(cheatWindows=100,cheatPlayers=10,legitWindows=200,legitPlayers=20))),
            desk=dict(reports=REPORTS,audit=AUDIT,mutes=[],freezes=[]),
            staff=dict(users=[dict(uuid=p['uuid'],name=p['name'],online=True,groups=p['groups']) for p in PLAYERS[:2]],
                       groups=[dict(id='admin',parents='moderator',permissions=24),dict(id='moderator',parents='helper',permissions=16),
                               dict(id='helper',parents='',permissions=8)],audit=[dict(time=time(40),actor='Sample operator',change='Added Ember to helper')]))
DATA['history'] = {key:[] for key in ['t','tps','mspt','players','memory','cpu','entities','chunks']}
DATA['chat'] = dict(started=time(),capacity=300,paused=False,slowSeconds=0,messages=[
    dict(id='sample-chat-'+str(i),time=time(4-i*.4),channel=channel,uuid=uid(player+1),sender=NAMES[player],
         message=message,world='Skyhold',target=None,recipients=8 if channel!='staff' else 2,panel=False)
    for i,(channel,player,message) in enumerate([
        ('global',3,'Hello everyone! Where can I find the starter island guide?'),
        ('global',1,'Welcome! Open the Game Guide on the website, or ask us here.'),
        ('trade',4,'Looking to trade oak logs for stone. Anyone interested?'),
        ('local',2,'The bridge on this side of Skyhold is almost finished.'),
        ('staff',1,'I am reviewing the island support queue. Can someone cover chat?'),
        ('global',5,'The floating gardens look amazing today.'),
        ('global',6,'Can someone help me recover access to my island?')])])
for i in range(30):
    for key,value in dict(t=int((NOW.timestamp()-(29-i)*120)*1000),tps=19.8+.18*abs(math.sin(i*.45)),
                          mspt=23+math.sin(i*.4)*5,players=4+i//7,memory=1500+i*11,cpu=20+math.sin(i*.5)*8,
                          entities=450+i*4,chunks=390+i*2).items():
        DATA['history'][key].append(value)

class Preview(BaseHTTPRequestHandler):
    def send(self, code, body, content_type):
        if isinstance(body,str): body=body.encode()
        self.send_response(code)
        self.send_header('Content-Type',content_type)
        self.send_header('Cache-Control','no-store')
        self.send_header('Content-Length',str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def do_GET(self):
        path = urlsplit(self.path).path
        query = parse_qs(urlsplit(self.path).query)
        if path=='/preview-only.css':
            return self.send(200,'.preview-only{padding:8px 20px;background:#30496b;color:#eaf4ff;text-align:center;font:600 10px system-ui;letter-spacing:.1em}@media(min-width:981px){.nav{height:calc(100vh - 28px)}}', 'text/css')
        if path=='/preview-revision':
            return self.send(200,json.dumps(dict(preview=True,revision=revision())),'application/json')
        if path=='/preview-reload.js':
            initial = query.get('revision',[''])[0]
            return self.send(200,"const revision="+json.dumps(initial)+";setInterval(async()=>{try{const data=await(await fetch('/preview-revision')).json();if(revision!==data.revision)location.reload();}catch{}},2000);",'text/javascript')
        if path=='/api/session':
            return self.send(200,json.dumps(dict(user='Sample operator',csrf='preview-only-token')),'application/json')
        if path=='/api/status':
            if (HERE/'offline.flag').exists():
                return self.send(503,'{"error":"Sample connection interrupted."}','application/json')
            return self.send(200,json.dumps(DATA),'application/json')
        if path.startswith('/api/player/'):
            player = next((p for p in DIRECTORY if p['uuid']==path.split('/')[-1]),None)
            if not player: return self.send(404,'{"error":"Unknown sample player."}','application/json')
            live = next((p for p in PLAYERS if p['uuid']==player['uuid']),None)
            detail = dict(player,online=bool(live),live=live,ban=None,groups=[live['groups']] if live and live['groups'] else [],
                          allGroups=['helper','moderator','admin'],stats=dict(playTicks=250000,deaths=4,playerKills=0,mobKills=128),
                          desk=dict(mute=None,freeze=None,history=[dict(type='NOTE',actor='Sample operator',time=time(12),detail='Sample support note.')],
                                    reports=[r for r in REPORTS if r['targetId']==player['uuid']]),
                          watchdog=dict(score=live['score'] if live else 0,counts={'SPEED':2} if player['uuid']==uid(3) else {},
                                        evidence=[e for e in EVIDENCE if e['uuid']==player['uuid']],model=dict(windows=[])),
                          connection=dict(sessions=12,averageMillis=3600000,longestMillis=7200000,trackedSince=time(20000),
                                          trackedMillis=42000000,brand='vanilla',protocol=774,serverProtocol=774,locale='en_us',
                                          viewDistance=10,host='play.aerborne.net',channels=[],mods=[],recordsAddresses=False,
                                          locatesAddresses=False,addresses=[],lastJoin=time(20),lastQuit=time(80)))
            return self.send(200,json.dumps(detail),'application/json')
        if path=='/mobile':
            width = max(320,min(900,int(query.get('width',['390'])[0])))
            return self.send(200,'<!doctype html><title>Staff console phone preview</title><iframe title="Console phone preview" width="%d" height="1100" src="/"></iframe>'%width,'text/html')
        relative = 'index.html' if path=='/' else path.lstrip('/')
        target = (ROOT/relative).resolve()
        if not target.is_relative_to(ROOT.resolve()) or not target.is_file():
            return self.send(404,'Not found','text/plain')
        data=target.read_bytes()
        if path=='/':
            data=data.replace(b'<body>',b'<body><div class="preview-only">LAYOUT PREVIEW &middot; FICTIONAL DATA &middot; NO GAME SERVER CONNECTED</div>')
            data=data.replace(b'</head>',b'<link rel="stylesheet" href="/preview-only.css"></head>')
            data=data.replace(b'</head>',('<script src="/preview-reload.js?revision='+revision()+'" defer></script></head>').encode())
        return self.send(200,data,mimetypes.guess_type(str(target))[0] or 'application/octet-stream')
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))))
        with (HERE/'preview-actions.jsonl').open('a',encoding='utf-8') as log: log.write(json.dumps(body)+'\n')
        action = body.get('action')
        if action=='chat-send':
            DATA['chat']['messages'].append(dict(id=str(uuid.uuid4()),time=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                channel=body.get('channel','global'),uuid=None,sender='Panel (Sample operator)',message=body.get('message',''),
                world=None,target=None,recipients=2 if body.get('channel')=='staff' else 8,panel=True))
            DATA['chat']['messages'] = DATA['chat']['messages'][-300:]
        if action=='chat-policy':
            DATA['chat']['paused'] = bool(body.get('paused'))
            DATA['chat']['slowSeconds'] = body.get('slowSeconds',0)
        return self.send(200,json.dumps(dict(message='Preview only. No server action was performed.')),'application/json')
ThreadingHTTPServer(('127.0.0.1',64140),Preview).serve_forever()

