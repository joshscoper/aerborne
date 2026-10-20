// Sprite-based Aerborne games. Fixed-step rules and input are independent of rendering.
(function () {
  "use strict";
  var GEM_TYPES = ["gem_ruby", "gem_sapphire", "gem_emerald", "gem_topaz", "gem_amethyst", "gem_aquamarine"];
  var MEMORY_FACES = ["card_fire", "card_water", "card_leaf", "card_star", "card_moon", "card_cloud", "card_crystal", "card_wind"];
  var MEMORY_NAMES = ["FIRE", "WATER", "LEAF", "STAR", "MOON", "CLOUD", "CRYSTAL", "WIND"];
  var COLOURS = ["#ff9eac", "#99dfff", "#9be8b2", "#ffe18a", "#d1b5ff", "#a4f0ec"];
  function clamp(n, lo, hi) { return Math.max(lo, Math.min(hi, n)); }
  function rand(lo, hi) { return lo + Math.random() * (hi - lo); }
  function rect(c, x, y, w, h, radius, colour, stroke) {
    if (w <= 0 || h <= 0) return;
    var r = Math.min(radius, w / 2, h / 2);
    c.beginPath(); c.moveTo(x+r,y); c.arcTo(x+w,y,x+w,y+h,r); c.arcTo(x+w,y+h,x,y+h,r);
    c.arcTo(x,y+h,x,y,r); c.arcTo(x,y,x+w,y,r); c.closePath(); c.fillStyle=colour; c.fill();
    if (stroke) { c.lineWidth=1.5; c.strokeStyle=stroke; c.stroke(); }
  }
  function text(c, value, x, y, size, colour) {
    c.font="800 "+size+"px Nunito,sans-serif"; c.textAlign="center";
    c.fillStyle=colour || "#eff7ff"; c.fillText(value,x,y);
  }
  // Fit the measured visible silhouette, rather than the transparent presentation canvas.
  // Collision boxes remain separate and deliberately forgiving around glows and wings.
  function paint(env, name, x, y, w, h, options) {
    var image=env.img(name), c=env.ctx;
    if (!image) return false;
    options=options || {};
    var box=!options.full && env.bounds ? env.bounds(name) : null;
    var b=box || [0,0,512,512], scale=image.width/512;
    var ratio=Math.min(w/b[2],h/b[3]), dw=b[2]*ratio, dh=b[3]*ratio;
    c.save(); c.translate(x,y);
    if (options.angle) c.rotate(options.angle);
    c.globalAlpha=options.alpha == null ? 1 : options.alpha;
    c.drawImage(image,b[0]*scale,b[1]*scale,b[2]*scale,b[3]*scale,-dw/2,-dh/2,dw,dh);
    c.restore(); return true;
  }
  function backdrop(env, theme, time) {
    var c=env.ctx, g=c.createLinearGradient(0,0,0,640);
    g.addColorStop(0,theme==="ember"?"#28213b":theme==="violet"?"#221c43":"#184964");
    g.addColorStop(1,theme==="ember"?"#53333d":theme==="violet"?"#142e4a":"#76acbc");
    c.fillStyle=g; c.fillRect(0,0,480,640);
    var image=env.img("landscape");
    if (image) {
      var cover=Math.max(480/image.width,640/image.height);
      c.save(); c.globalAlpha=theme==="sky"?.09:.035;
      c.drawImage(image,(480-image.width*cover)/2,(640-image.height*cover)/2,image.width*cover,image.height*cover);
      c.restore();
    }
    if (theme==="sky") {
      var cloud=env.img("cloud_soft")?"cloud_soft":"cloud_white";
      for (var i=0;i<4;i++) paint(env,cloud,((i*170+80-time*(env.reduced?0:9+i))+740)%740-130,110+i*151,140+i*15,90,{alpha:.16});
    } else {
      for (var j=0;j<24;j++) { c.fillStyle=j%4?"#b6d6ff25":"#d9ddff77"; c.fillRect((j*137+35)%480,(j*83+16)%640,j%4?1.5:2.5,j%4?1.5:2.5); }
    }
  }
  function loop(update, draw, env) {
    var id=0, last=null, acc=0, alive=true, step=1/120;
    function frame(time) {
      if (!alive) return;
      var dt=last===null?0:Math.min(.1,Math.max(0,(time-last)/1000)); last=time;
      if (!env.paused()) {
        acc+=dt;
        while (acc>=step && alive) { update(step); acc-=step; }
        draw();
      } else acc=0;
      if (alive) id=requestAnimationFrame(frame);
    }
    return {start:function(){draw();id=requestAnimationFrame(frame);},stop:function(){alive=false;cancelAnimationFrame(id);}};
  }
  function point(env, e) {
    var r=env.canvas.getBoundingClientRect();
    return {x:(e.clientX-r.left)*480/r.width,y:(e.clientY-r.top)*640/r.height};
  }
  function trigger(env, action) {
    env.on(env.canvas,"pointerdown",function(e){if(env.paused())return;e.preventDefault();action(e);});
    env.on(env.canvas,"keydown",function(e){
      if (!e.repeat && !env.paused() && (e.code==="Space" || e.code==="Enter" || e.code==="ArrowUp")) { e.preventDefault(); action(e); }
    });
  }
  function mover(env, minimum, maximum, initial) {
    var keys={}, x=initial, dragging=false;
    function move(e) {
      if (env.paused() || (e.pointerType==="touch" && !dragging)) return;
      x=clamp(point(env,e).x,minimum,maximum);
    }
    env.on(env.canvas,"pointerdown",function(e){
      if(env.paused())return; dragging=true; env.canvas.setPointerCapture(e.pointerId); move(e); e.preventDefault();
    });
    env.on(env.canvas,"pointermove",move);
    env.on(env.canvas,"pointerup",function(){dragging=false;});
    env.on(env.canvas,"pointercancel",function(){dragging=false;});
    env.on(env.canvas,"keydown",function(e){
      if(!env.paused() && (e.code==="ArrowLeft" || e.code==="ArrowRight")) {keys[e.code]=true;e.preventDefault();}
    });
    env.on(window,"keyup",function(e){delete keys[e.code];});
    return {
      tick:function(dt){x=clamp(x+((keys.ArrowRight?1:0)-(keys.ArrowLeft?1:0))*420*dt,minimum,maximum);return x;},
      release:function(){keys={};dragging=false;}
    };
  }
  function effects(env) {
    var items=[];
    return {
      burst:function(x,y,colour,label,sprite) {
        if(label)items.push({x:x,y:y,vy:-24,life:.9,max:.9,label:label,colour:colour});
        if(sprite)items.push({x:x,y:y,vy:0,life:.45,max:.45,sprite:sprite,colour:colour});
        if(!env.reduced)for(var i=0;i<7;i++)items.push({x:x,y:y,vx:rand(-80,80),vy:rand(-130,30),life:.4,max:.4,colour:colour});
        if(items.length>100)items.splice(0,items.length-100);
      },
      tick:function(dt){
        items.forEach(function(p){p.life-=dt;p.x+=(p.vx||0)*dt;p.y+=p.vy*dt;});
        items=items.filter(function(p){return p.life>0;});
      },
      draw:function(){
        var c=env.ctx;
        items.forEach(function(p){
          var alpha=clamp(p.life/p.max,0,1);
          if(p.sprite)paint(env,p.sprite,p.x,p.y,env.reduced?64:64+(1-alpha)*28,env.reduced?64:64+(1-alpha)*28,{alpha:alpha});
          else {
            c.save();c.globalAlpha=alpha;
            if(p.label)text(c,p.label,p.x,p.y,20,p.colour);
            else {c.fillStyle=p.colour;c.fillRect(p.x,p.y,3,3);}
            c.restore();
          }
        });
      }
    };
  }
  var Rules = {
    memoryFaces:MEMORY_FACES,
    matches:function(board,n) {
      var found=new Set();
      for(var row=0;row<n;row++)for(var col=0;col<n;col++){
        var k=row*n+col,value=board[k]; if(value<0)continue;
        if(col<n-2 && value===board[k+1] && value===board[k+2]){
          var x=col;while(x<n && board[row*n+x]===value)found.add(row*n+x++);
        }
        if(row<n-2 && value===board[k+n] && value===board[k+2*n]){
          var y=row;while(y<n && board[y*n+col]===value)found.add(y++*n+col);
        }
      }
      return Array.from(found);
    },
    legal:function(board,n) {
      for(var i=0;i<board.length;i++)for(var d=0;d<2;d++){
        var j=d?i+n:i+1; if(j>=board.length || (!d && i%n===n-1))continue;
        var v=board[i];board[i]=board[j];board[j]=v;
        var legal=Rules.matches(board,n).length>0;
        v=board[i];board[i]=board[j];board[j]=v;
        if(legal)return [i,j];
      }
      return null;
    },
    board:function(n) {
      var board=[];
      for(var i=0;i<n*n;i++){
        var choices=[0,1,2,3,4,5].filter(function(v){
          return !(i%n>1 && board[i-1]===v && board[i-2]===v) && !(i>=n*2 && board[i-n]===v && board[i-n*2]===v);
        });
        board.push(choices[Math.floor(Math.random()*choices.length)]);
      }
      if(!Rules.legal(board,n)){
        board[0]=0;board[1]=1;board[2]=0;board[n+1]=0;board[n]=2;board[n+2]=3;
        if(Rules.matches(board,n).length)return Rules.board(n);
      }
      return board;
    },
    runeWall:function(level){
      var bricks=[];
      for(var row=0;row<5;row++)for(var col=0;col<6;col++){
        var type="normal";
        if(row===0)type="enchanted";
        else if(row===1 && col%2===level%2)type="shield";
        else if(row===2 && (col===1 || col===4))type="special";
        else if(row===3 && (col===1 || col===4))type="explosive";
        else if(row===4 && col%3===0)type="cracked";
        var hp=type==="enchanted" || type==="shield"?2:1;
        bricks.push({x:27+col*73,y:102+row*56,w:60,h:50,row:row,col:col,type:type,hp:hp,initial:hp,alive:true});
      }
      return bricks;
    },
    damageRunes:function(bricks,index){
      var queue=[index], visited=new Set(), result={points:0,broken:[],shield:false,wide:false,blast:false};
      while(queue.length){
        var k=queue.shift(), b=bricks[k]; if(!b || !b.alive || visited.has(k))continue;
        visited.add(k);b.hp--;result.points+=10;
        if(b.hp>0)continue;
        b.alive=false;result.broken.push(b);
        if(b.type==="enchanted")result.points+=10;
        if(b.type==="special"){result.points+=20;result.wide=true;}
        if(b.type==="shield")result.shield=true;
        if(b.type==="explosive"){
          result.blast=true;
          bricks.forEach(function(other,i){if(other.alive && Math.abs(other.row-b.row)<=1 && Math.abs(other.col-b.col)<=1)queue.push(i);});
        }
      }
      return result;
    }
  };
  if(typeof module!=="undefined" && module.exports)module.exports=Rules;

  Arcade.register("sky-hop",function(env){
    var c=env.ctx,y=315,vy=0,started=false,age=0,score=0,gates=[],spawn=.65,dead=false,flap=.1,fx=effects(env);
    trigger(env,function(){if(dead)return;started=true;vy=-295;flap=.18;});
    function column(gate,upper){
      var edge=upper?gate.top:gate.top+gate.gap;
      // Overlapping terrain sprites make visible rock columns; avoid invisible solid rectangles.
      for(var centre=upper?edge-74:edge+59;upper?centre>-160:centre<800;centre+=upper?-110:88){
        paint(env,upper?"island_tall":"island_large",gate.x+56,centre,144,158);
      }
      if(!upper)paint(env,"foliage_bloom",gate.x+62,edge+9,44,28);
    }
    var l=loop(function(dt){
      age+=dt;flap=Math.max(0,flap-dt);fx.tick(dt);
      if(!started){env.status("Tap or Space to fly");return;}
      vy+=760*dt;y+=vy*dt;spawn-=dt;
      var speed=150+Math.min(38,score*.75),gap=Math.max(195,226-score*.65);
      if(spawn<=0){
        spawn=1.95;
        gates.push({x:540,top:rand(116,640-gap-126),gap:gap,scored:false,taken:false,crystal:Math.random()<.2});
      }
      gates.forEach(function(gate){
        gate.x-=speed*dt;
        var cx=gate.x+56,cy=gate.top+gate.gap/2;
        if(!gate.taken && Math.abs(cx-125)<32 && Math.abs(cy-y)<43){
          gate.taken=true;var points=gate.crystal?2:1;score+=points;env.score(score);
          fx.burst(125,y,"#fff0ac","+"+points,"spark_burst");
        }
        if(!gate.scored && gate.x+98<108){gate.scored=true;score++;env.score(score);fx.burst(125,y,"#e8faff","+1","cloud_puff_effect");}
        if(125+14>gate.x+25 && 125-14<gate.x+87 && (y-13<gate.top-9 || y+13>gate.top+gate.gap+9))dead=true;
      });
      gates=gates.filter(function(gate){return gate.x>-160;});
      if(y<16 || y>622)dead=true;
      env.status(score+" points");
      if(dead)env.end("Flight complete");
    },function(){
      backdrop(env,"sky",age);
      gates.forEach(function(gate){
        column(gate,true);column(gate,false);
        if(!gate.taken)paint(env,gate.crystal?"crystal_collectible":"star_collectible",gate.x+56,gate.top+gate.gap/2,38,44,{angle:env.reduced?0:Math.sin(age*2)*.12});
      });
      var pose=flap>0?"pufflet_flight_pose_02":started&&vy>80?"pufflet_flight_pose_04":"pufflet_flight_pose_01";
      paint(env,pose,125,y,82,78,{angle:started?clamp(vy/1600,-.16,.28):0});
      if(!started){
        paint(env,"launch_ring",125,369,80,42,{alpha:.65});
        rect(c,67,427,346,62,12,"#153449eb","#b1dce966");
        text(c,"Tap or Space to take flight",240,453,18);
        text(c,"Clear gaps. Collect stars and crystals.",240,476,13,"#c6e4f0");
      }
      fx.draw();
    },env);
    return l;
  });

  Arcade.register("gem-rush",function(env){
    var c=env.ctx,n=7,cell=60,ox=30,oy=130,board=Rules.board(n),picked=-1,cursor=0,score=0,time=60;
    var phase="ready",delay=0,hits=[],chain=0,swap=null,drop=new Array(49).fill(0),idle=0,hint=null;
    var charge=0,bonusUsed=false,bonusFlash=0,fx=effects(env),message="Match three. Let the gems tumble.";
    var updateBomb=env.action("Burst [B] · 0/8","B",bomb);
    function refreshAction(){updateBomb(charge>=8?"Use burst [B]":"Burst [B] · "+charge+"/8",charge>=8&&phase==="ready");}
    refreshAction();
    function swapCells(a,b){var value=board[a];board[a]=board[b];board[b]=value;}
    function choose(i){
      if(phase!=="ready" || i<0 || i>=49 || env.paused())return;
      idle=0;hint=null;
      if(picked<0){picked=i;return;}
      if(picked===i){picked=-1;return;}
      var adjacent=Math.abs(picked-i)===7 || (Math.floor(picked/7)===Math.floor(i/7)&&Math.abs(picked-i)===1);
      if(!adjacent){picked=i;return;}
      swap=[picked,i];picked=-1;phase="swap";delay=.16;chain=0;
      swapCells(swap[0],swap[1]);refreshAction();
      fx.burst(ox+(i%7)*cell+30,oy+Math.floor(i/7)*cell+30,"#a5ecfa",null,"fx_swap");
    }
    function bomb(){
      if(charge<8 || phase!=="ready" || env.paused())return;
      var centre=picked>=0?picked:cursor,r=Math.floor(centre/7),col=centre%7;
      hits=[];
      for(var row=Math.max(0,r-1);row<=Math.min(6,r+1);row++)for(var x=Math.max(0,col-1);x<=Math.min(6,col+1);x++)hits.push(row*7+x);
      charge=0;chain=1;phase="clear";delay=.3;picked=-1;idle=0;hint=null;
      score=Math.min(20000,score+hits.length*20);env.score(score);message="Sky burst!";
      fx.burst(ox+col*cell+30,oy+r*cell+30,"#ffe19b","BURST","fx_row_clear");
      refreshAction();
    }
    env.on(env.canvas,"pointerdown",function(e){
      if(env.paused())return;
      var p=point(env,e),col=Math.floor((p.x-ox)/cell),row=Math.floor((p.y-oy)/cell);
      if(col>=0&&col<7&&row>=0&&row<7){cursor=row*7+col;choose(cursor);}
      e.preventDefault();
    });
    env.on(env.canvas,"keydown",function(e){
      if(env.paused() || e.repeat)return;
      var row=Math.floor(cursor/7),col=cursor%7;
      if(e.code==="ArrowLeft")cursor=row*7+Math.max(0,col-1);
      else if(e.code==="ArrowRight")cursor=row*7+Math.min(6,col+1);
      else if(e.code==="ArrowUp")cursor=Math.max(0,row-1)*7+col;
      else if(e.code==="ArrowDown")cursor=Math.min(6,row+1)*7+col;
      else if(e.code==="Enter" || e.code==="Space")choose(cursor);
      else if(e.code==="KeyB")bomb();
      else return;
      e.preventDefault();
    });
    function settle(){
      hits=Rules.matches(board,n);
      if(hits.length){
        chain++;var points=hits.length*20*Math.min(chain,4);
        score=Math.min(20000,score+points);env.score(score);charge=Math.min(8,charge+1);
        message=chain>1?"Cascade ×"+Math.min(chain,4):"Nice match!";
        fx.burst(240,92,"#c4f4df","+"+points,chain>1?"fx_combo":"fx_match");
        if(chain>=3 && !bonusUsed){
          time=Math.min(65,time+5);bonusUsed=true;bonusFlash=2.5;
          fx.burst(240,115,"#ffe6ad","+5 SECONDS","power_hourglass");
        }
        phase="clear";delay=.23;
      }else{
        phase="ready";chain=0;hint=null;
        if(!Rules.legal(board,n)){board=Rules.board(n);message="Fresh gems. A new way through.";fx.burst(240,340,"#b6f0d4","Shuffled","fx_shards");}
      }
      refreshAction();
    }
    var l=loop(function(dt){
      time=Math.max(0,time-dt);idle+=dt;bonusFlash=Math.max(0,bonusFlash-dt);fx.tick(dt);
      drop=drop.map(function(value){return Math.min(0,value+dt*1000);});
      env.status(Math.ceil(time)+"s · "+(phase==="ready"?"Your move":"Cascade"));
      if(time<=0){env.end("Time's up");return;}
      if(phase==="ready"){if(idle>6 && !hint)hint=Rules.legal(board,n);return;}
      delay-=dt;if(delay>0)return;
      if(phase==="swap"){
        if(Rules.matches(board,n).length)settle();
        else{phase="undo";delay=.18;message="That swap needs a match.";}
      }else if(phase==="undo"){
        swapCells(swap[0],swap[1]);swap=null;phase="ready";refreshAction();
      }else if(phase==="clear"){
        hits.forEach(function(i){board[i]=-1;});
        for(var col=0;col<n;col++){
          var values=[];
          for(var row=n-1;row>=0;row--)if(board[row*n+col]>=0)values.push({value:board[row*n+col],row:row});
          for(var dest=n-1;dest>=0;dest--){
            var item=values[n-1-dest];
            board[dest*n+col]=item?item.value:Math.floor(rand(0,6));
            drop[dest*n+col]=item?(item.row-dest)*cell:-(n-values.length)*cell;
          }
        }
        hits=[];phase="fall";delay=.28;swap=null;
      }else if(phase==="fall")settle();
    },function(){
      backdrop(env,"violet",0);
      text(c,"THE GEM VAULT",240,39,15,"#b9cce5");
      text(c,message,240,75,18,"#c1f1d5");
      rect(c,30,103,420,5,2,"#0e1a2c");rect(c,30,103,420*Math.min(1,time/60),5,2,"#ade3cc");
      rect(c,21,121,438,438,15,"#101c34b8","#8bc2c344");
      for(var i=0;i<49;i++){
        var x=ox+i%7*cell,y=oy+Math.floor(i/7)*cell;
        paint(env,i===picked?"tile_highlight":i%14===0?"tile_moss":"tile_empty",x+30,y+30,58,58);
        if(i===cursor)rect(c,x+3,y+3,54,54,8,"#ffffff00","#d1ebff99");
        if(hint && hint.indexOf(i)>=0 && phase==="ready"){
          paint(env,"tile_highlight",x+30,y+30,58,58,{alpha:.85});
          paint(env,"fx_hint",x+49,y+12,17,19);
        }
        var alpha=phase==="clear"&&hits.indexOf(i)>=0?clamp(delay/.23,.12,1):1,dx=0,dy=0;
        if(swap && phase==="swap"){
          var other=i===swap[0]?swap[1]:i===swap[1]?swap[0]:-1;
          if(other>=0){dx=((other%7)-(i%7))*cell*delay/.16;dy=(Math.floor(other/7)-Math.floor(i/7))*cell*delay/.16;}
        }
        c.save();c.beginPath();c.rect(30,130,420,420);c.clip();
        paint(env,GEM_TYPES[board[i]],x+30+dx,y+30+dy+drop[i],46,48,{alpha:alpha});
        c.restore();
      }
      paint(env,"power_bomb",58,598,42,42,{alpha:charge>=8?1:.45});
      text(c,charge>=8?"Burst ready · B or button below":"Charge a burst with 8 matches",270,599,15,"#d4deed");
      if(bonusFlash>0)paint(env,"power_hourglass",421,75,35,40);
      fx.draw();
    },env);
    return l;
  });

  Arcade.register("cloud-catch",function(env){
    var c=env.ctx,motion=mover(env,48,432,240),x=240,lastX=240,dx=0,time=90,age=0,spawn=.3;
    var items=[],score=0,lives=3,invulnerable=0,shield=0,magnet=0,catchPose=0,powerAt=11,powerNumber=0,fx=effects(env);
    var drops=[
      {sprite:"gem_blue",value:1},{sprite:"gem_gold",value:2},{sprite:"gem_purple",value:3},
      {sprite:"star_small",value:2},{sprite:"star_large",value:3},{sprite:"star_rainbow",value:5}
    ];
    function makeDrop(){
      var p={x:rand(35,445),y:-35,vy:rand(135,180)+age*1.1,done:false};
      if(age>=powerAt){
        p.sprite=powerNumber++%2?"power_magnet":"power_shield";p.power=p.sprite;powerAt=age+17;
      }else if(Math.random()<.23){
        p.sprite=Math.random()<.35?"cloud_thunder":"cloud_storm";p.hazard=true;
      }else{
        var type=drops[Math.floor(rand(0,drops.length))];p.sprite=type.sprite;p.value=type.value;
      }
      items.push(p);
    }
    var l=loop(function(dt){
      time=Math.max(0,time-dt);age+=dt;fx.tick(dt);
      invulnerable=Math.max(0,invulnerable-dt);shield=Math.max(0,shield-dt);
      magnet=Math.max(0,magnet-dt);catchPose=Math.max(0,catchPose-dt);
      x=motion.tick(dt);dx=x-lastX;lastX=x;spawn-=dt;
      if(spawn<=0){spawn=Math.max(.32,.68-age*.003);makeDrop();}
      items.forEach(function(p){
        p.y+=p.vy*dt;
        if(magnet>0 && !p.hazard && Math.abs(p.x-x)<160 && p.y>130)p.x+=clamp(x-p.x,-180*dt,180*dt);
        if(p.y>537 && p.y<590 && Math.abs(p.x-x)<(p.hazard?33:45)){
          p.done=true;
          if(p.power){
            if(p.power==="power_shield"){shield=8;fx.burst(x,521,"#b7edff","SHIELD","fx_dodge");}
            else{magnet=7;fx.burst(x,521,"#d4c8ff","MAGNET","fx_collect");}
          }else if(p.hazard){
            if(shield>0){shield=0;invulnerable=.7;fx.burst(x,540,"#c5f6ff","Blocked","fx_dodge");}
            else if(invulnerable<=0){lives--;invulnerable=1.4;fx.burst(x,520,"#ffb6c3","Storm hit","fx_hit");}
          }else{
            score+=p.value;env.score(score);catchPose=.4;
            fx.burst(x,515,p.value>2?"#ffe7ac":"#c5f7ed","+"+p.value,"fx_collect");
          }
        }
      });
      items=items.filter(function(p){return !p.done && p.y<680;});
      env.status(Math.ceil(time)+"s · "+lives+(lives===1?" heart":" hearts")+(shield>0?" · Shield":magnet>0?" · Magnet":""));
      if(lives<=0 || time<=0)env.end(lives<=0?"Storms caught up":"Time's up");
    },function(){
      backdrop(env,"sky",age);
      text(c,"GEMS + STARS · AVOID DARK STORMS",240,39,14,"#e8f5ff");
      if(shield>0){paint(env,"power_shield",38,80,27,33);text(c,Math.ceil(shield)+"s",75,86,14,"#d5f7ff");}
      if(magnet>0){paint(env,"power_magnet",127,80,29,31);text(c,Math.ceil(magnet)+"s",163,86,14,"#e8dcff");}
      items.forEach(function(p){
        paint(env,p.sprite,p.x,p.y,p.hazard?72:p.power?42:39,p.hazard?54:44);
      });
      if(shield>0)paint(env,"fx_dodge",x,556,129,113,{alpha:.8});
      var pose=invulnerable>0?"pufflet_hit":catchPose>0?"pufflet_catch":Math.abs(dx)>.6?"pufflet_dodge":"pufflet_idle";
      paint(env,pose,x,559,94,87,{alpha:invulnerable>0&&!env.reduced?.55+Math.sin(age*24)*.2:1});
      text(c,"♥ ".repeat(Math.max(0,lives)),240,629,20,"#ffe0d3");
      fx.draw();
    },env);
    l.release=motion.release;
    return l;
  });

  Arcade.register("codex-memory",function(env){
    var c=env.ctx,cards=[],opened=[],found=0,moves=0,score=0,time=120,preview=2.5,wait=0,cursor=0,age=0,fx=effects(env);
    for(var i=0;i<16;i++)cards.push({kind:i%8,matched:false,reveal:0});
    for(var j=cards.length-1;j>0;j--){var k=Math.floor(rand(0,j+1)),value=cards[j];cards[j]=cards[k];cards[k]=value;}
    function choose(index){
      if(preview>0 || wait>0 || env.paused() || !cards[index] || cards[index].matched || opened.indexOf(index)>=0)return;
      opened.push(index);
      if(opened.length!==2)return;
      moves++;
      if(cards[opened[0]].kind===cards[opened[1]].kind){
        cards[opened[0]].matched=cards[opened[1]].matched=true;found++;score+=40;env.score(score);
        fx.burst(240,88,"#e5ceff","PAIR +40","fx_match");opened=[];
        if(found===8){
          score+=Math.floor(time)+Math.max(0,16-moves)*10;env.score(score);
          fx.burst(240,320,"#fff0b2","CODEX COMPLETE","icon_perfect");env.end("All eight pairs found");
        }
      }else{
        wait=.72;fx.burst(240,90,"#eeb4d0","Try another pair","fx_mismatch");
      }
    }
    env.on(env.canvas,"pointerdown",function(e){
      if(env.paused())return;
      var p=point(env,e),col=Math.floor((p.x-32)/104),row=Math.floor((p.y-113)/118);
      if(col>=0&&col<4&&row>=0&&row<4){cursor=row*4+col;choose(cursor);}
      e.preventDefault();
    });
    env.on(env.canvas,"keydown",function(e){
      if(env.paused() || e.repeat)return;
      var row=Math.floor(cursor/4),col=cursor%4;
      if(e.code==="ArrowLeft")cursor=row*4+Math.max(0,col-1);
      else if(e.code==="ArrowRight")cursor=row*4+Math.min(3,col+1);
      else if(e.code==="ArrowUp")cursor=Math.max(0,row-1)*4+col;
      else if(e.code==="ArrowDown")cursor=Math.min(3,row+1)*4+col;
      else if(e.code==="Space" || e.code==="Enter")choose(cursor);
      else return;
      e.preventDefault();
    });
    var l=loop(function(dt){
      age+=dt;fx.tick(dt);
      if(preview>0)preview=Math.max(0,preview-dt);
      else{
        time=Math.max(0,time-dt);
        if(wait>0){wait-=dt;if(wait<=0)opened=[];}
      }
      cards.forEach(function(card,index){
        var target=preview>0 || card.matched || opened.indexOf(index)>=0?1:0;
        card.reveal=env.reduced?target:clamp(card.reveal+(target?1:-1)*dt*8,0,1);
      });
      env.status(preview>0?"Remember the cards":Math.ceil(time)+"s · "+found+"/8 pairs");
      if(time<=0)env.end("Time's up");
    },function(){
      backdrop(env,"violet",age);
      text(c,preview>0?"A MOMENT TO REMEMBER":"THE MEMORY CODEX",240,36,15,"#ddc5ff");
      text(c,preview>0?"Eight symbols. Find their twins.":moves+" moves · "+found+" pairs found",240,71,17,"#cad9ef");
      rect(c,35,94,410,4,2,"#ffffff15");rect(c,35,94,410*(preview>0?preview/2.5:time/120),4,2,"#cbb0f5");
      cards.forEach(function(card,index){
        var x=32+index%4*104,y=113+Math.floor(index/4)*118;
        rect(c,x+4,y+3,96,109,10,card.matched?"#45577855":"#25304b55",index===cursor?"#cbb2f3":card.matched?"#b1dac166":"#70648e33");
        var reveal=card.reveal,flip=env.reduced?1:Math.max(.08,Math.abs(reveal-.5)*2);
        c.save();c.translate(x+52,y+53);c.scale(flip,1);
        // Shared 512px registration preserves the card body size between fronts and backs.
        paint(env,reveal>.5?MEMORY_FACES[card.kind]:"card_back",0,0,132,132,{full:true,alpha:card.matched?.68:1});
        c.restore();
        if(reveal>.5)text(c,MEMORY_NAMES[card.kind],x+52,y+111,12,card.matched?"#bddbc8":"#f1eaff");
      });
      text(c,"Match the symbol names, too — some gems look alike.",240,620,12,"#b6c7df");
      fx.draw();
    },env);
    return l;
  });

  Arcade.register("forge-strike",function(env){
    var c=env.ctx,pos=.1,dir=1,speed=.6,centre=.5,zone=.26,score=0,hits=0,lives=3,time=60,age=0;
    var cooldown=.35,swing=0,lastHit=true,word="Find the gold. Feel the rhythm.",ingots=[],fx=effects(env);
    function strike(){
      if(cooldown>0 || env.paused())return;
      cooldown=.44;swing=.44;
      var distance=Math.abs(pos-centre);
      lastHit=distance<=zone/2;
      if(lastHit){
        var perfect=distance<=zone*.16;score+=perfect?20:10;hits++;env.score(score);
        word=perfect?"Perfect strike +20":"Clean hit +10";
        fx.burst(240,345,perfect?"#ffedb6":"#ffd39a",perfect?"PERFECT":"+10",perfect?"fx_perfect":"fx_impact");
        ingots.push({x:240,y:346,vx:rand(-80,80),vy:-100,life:1.2,sky:hits%3===0});
        if(hits%8===0)fx.burst(240,394,"#e3c1ff","FORGE UPGRADED","fx_upgrade");
        speed=Math.min(1.6,.6+hits*.035);zone=Math.max(.13,.26-hits*.003);centre=rand(.19+zone/2,.81-zone/2);
      }else{
        lives--;word="Missed the gold";
        fx.burst(240,345,"#f6adbc","Miss","fx_miss");
        if(lives<=0)env.end("Forge cooled");
      }
    }
    trigger(env,strike);
    var l=loop(function(dt){
      age+=dt;time=Math.max(0,time-dt);cooldown=Math.max(0,cooldown-dt);swing=Math.max(0,swing-dt);fx.tick(dt);
      ingots.forEach(function(p){p.life-=dt;p.x+=p.vx*dt;p.y+=p.vy*dt;p.vy+=180*dt;});
      ingots=ingots.filter(function(p){return p.life>0;});
      if(cooldown<=0){
        pos+=dir*speed*dt;
        if(pos>1){pos=2-pos;dir=-1;}if(pos<0){pos=-pos;dir=1;}
      }
      env.status(Math.ceil(time)+"s · "+lives+(lives===1?" strike left":" strikes left"));
      if(time<=0)env.end("Time's up");
    },function(){
      backdrop(env,"ember",age);
      text(c,"THE SKY FORGE",240,37,16,"#f2c692");
      text(c,word,240,82,18,"#ffe6b6");
      var glow=c.createRadialGradient(240,366,10,240,366,220);
      glow.addColorStop(0,"#ff9e3e33");glow.addColorStop(1,"#ff9e3e00");
      c.fillStyle=glow;c.fillRect(0,130,480,360);
      paint(env,hits>=8?"anvil_upgraded":swing>0&&lastHit?"anvil_hot":cooldown<=0?"anvil_ready":"anvil_idle",240,394,281,201);
      if(swing<=0)paint(env,hits%3===2?"ore_ember":"ore_crystal",240,326,49,51);
      var frame="hammer_idle",hx=279,hy=225;
      if(swing>0){
        var t=1-swing/.44;
        if(t<.23){frame="hammer_swing_01";hx=266;hy=250;}
        else if(t<.48){frame="hammer_swing_02";hx=233;hy=301;}
        else if(t<.73){frame="hammer_impact";hx=205;hy=366;}
        else{hx=235+(t-.73)/.27*44;hy=301-(t-.73)/.27*76;}
      }
      paint(env,frame,hx,hy,238,238,{full:true});
      ingots.forEach(function(p){paint(env,p.sky?"ingot_sky":"ingot_gold",p.x,p.y,48,31,{alpha:clamp(p.life/.5,0,1)});});
      text(c,"Forge level "+(1+Math.floor(hits/8)),120,483,13,"#c8b7bc");
      paint(env,"ui_heat_meter",342,475,131,33,{alpha:.75});
      rect(c,277,496,131,4,2,"#1b2139");rect(c,277,496,131*(hits%8)/8,4,2,"#ffc277");
      rect(c,40,528,400,47,12,"#141a2c","#a68b7166");
      rect(c,48+(centre-zone/2)*384,534,zone*384,35,6,"#b8893d","#ffe3a1");
      rect(c,48+(centre-zone*.16)*384,534,zone*.32*384,35,4,"#ffe4a0");
      paint(env,"ui_target_zone",48+centre*384,551,27,29,{alpha:.9});
      rect(c,45+pos*384,521,7,61,3,cooldown>0?"#bfb4a7":"#fff8e8");
      text(c,cooldown>0?"Ready…":"Tap or Space to strike",240,612,17,"#f7ddbb");
      text(c,"◆ ".repeat(Math.max(0,lives)),240,636,16,"#ffdb98");
      fx.draw();
    },env);
    return l;
  });

  Arcade.register("rune-breaker",function(env){
    var c=env.ctx,motion=mover(env,76,404,240),x=240,ball={x:240,y:553,vx:0,vy:-325},waiting=true;
    var bricks=[],lives=3,score=0,level=1,time=90,age=0,wide=0,shield=0,fx=effects(env),trail=[];
    var ORBS=["orb_amethyst","orb_cyan","orb_fire","orb_nature","orb_golden"];
    function wall(){
      bricks=Rules.runeWall(level);waiting=true;trail=[];
      if(level>1)fx.burst(240,380,"#dbbcff","NEXT WALL","fx_portal");
    }
    wall();
    function launch(){
      if(env.paused() || !waiting)return;
      x=motion.tick(0);ball.x=x;ball.y=553;waiting=false;
      ball.vx=rand(-110,110);
      ball.vy=-Math.sqrt(Math.pow(310+Math.min(110,level*15),2)-ball.vx*ball.vx);
    }
    trigger(env,launch);
    function damage(index){
      var result=Rules.damageRunes(bricks,index);
      score+=result.points;env.score(score);
      result.broken.forEach(function(b){fx.burst(b.x+b.w/2,b.y+b.h/2,"#d1c3ff",null,"fx_break");});
      if(result.blast)fx.burst(ball.x,ball.y,"#ffe1b0","CHAIN","fx_chain");
      if(result.shield){shield=8;fx.burst(240,604,"#a7e6ff","SHIELD","fx_shield");}
      if(result.wide){wide=8;fx.burst(x,565,"#ffe4a4","WIDE PADDLE","fx_bounce");}
    }
    function collide(b,index,previousX,previousY){
      var cx=clamp(ball.x,b.x,b.x+b.w),cy=clamp(ball.y,b.y,b.y+b.h);
      if(Math.pow(ball.x-cx,2)+Math.pow(ball.y-cy,2)>64)return false;
      damage(index);
      if(previousX+8<=b.x || previousX-8>=b.x+b.w)ball.vx*=-1;
      else if(previousY+8<=b.y || previousY-8>=b.y+b.h)ball.vy*=-1;
      else ball.vy*=-1;
      // Place the orb back on the incoming side before the next substep.
      ball.x=previousX;ball.y=previousY;
      return true;
    }
    var l=loop(function(dt){
      age+=dt;time=Math.max(0,time-dt);wide=Math.max(0,wide-dt);shield=Math.max(0,shield-dt);
      fx.tick(dt);x=motion.tick(dt);
      if(waiting){ball.x=x;ball.y=553;}
      else{
        var steps=Math.max(1,Math.ceil(Math.hypot(ball.vx,ball.vy)*dt/5));
        for(var s=0;s<steps;s++){
          var px=ball.x,py=ball.y;
          ball.x+=ball.vx*dt/steps;ball.y+=ball.vy*dt/steps;
          if(ball.x<12){ball.x=12;ball.vx=Math.abs(ball.vx);}
          if(ball.x>468){ball.x=468;ball.vx=-Math.abs(ball.vx);}
          if(ball.y<67){ball.y=67;ball.vy=Math.abs(ball.vy);}
          var half=wide>0?73:56;
          if(ball.vy>0 && py+8<=576 && ball.y+8>=576 && Math.abs(ball.x-x)<=half+8){
            var offset=clamp((ball.x-x)/half,-.95,.95),speed=Math.min(460,Math.hypot(ball.vx,ball.vy)+5);
            ball.vx=speed*offset*.85;ball.vy=-Math.sqrt(speed*speed-ball.vx*ball.vx);ball.y=567;
            fx.burst(ball.x,577,"#b7e8ff",null,"fx_bounce");
          }
          for(var i=0;i<bricks.length;i++)if(bricks[i].alive && collide(bricks[i],i,px,py))break;
          if(shield>0 && ball.vy>0 && ball.y>615){
            ball.y=615;ball.vy=-Math.abs(ball.vy);shield=0;
            fx.burst(ball.x,615,"#c3efff","SAVED","fx_shield");
          }
          if(ball.y>657){
            lives--;waiting=true;trail=[];fx.burst(x,547,"#f0b5c7","Life lost");break;
          }
        }
        if(!env.reduced && !waiting){
          trail.push({x:ball.x,y:ball.y});if(trail.length>10)trail.shift();
        }
        if(!bricks.some(function(b){return b.alive;})){
          score+=100;env.score(score);level++;wall();
          fx.burst(240,375,"#ffe8b2","WALL CLEAR +100","fx_portal");
        }
      }
      env.status(Math.ceil(time)+"s · "+lives+(lives===1?" life":" lives")+" · Wall "+level);
      if(lives<=0 || time<=0)env.end(lives<=0?"Out of lives":"Time's up");
    },function(){
      backdrop(env,"violet",age);
      text(c,"THE RUNE SANCTUM",240,37,15,"#d0bbff");
      rect(c,14,62,452,549,15,"#11223822","#abc3ec22");
      bricks.forEach(function(b){
        if(!b.alive)return;
        var sprite=b.hp<b.initial?"brick_cracked":"brick_"+b.type;
        paint(env,sprite,b.x+b.w/2,b.y+b.h/2,b.w+3,b.h+7);
      });
      trail.forEach(function(p,i){
        c.fillStyle="rgba(194,177,255,"+(i/trail.length*.3)+")";c.beginPath();c.arc(p.x,p.y,2+i*.3,0,Math.PI*2);c.fill();
      });
      if(shield>0){
        rect(c,21,616,438,4,2,"#a9eaff");
        paint(env,"fx_shield",440,593,24,28);
      }
      paint(env,wide>0?"paddle_gold":"paddle_blue",x,582,wide>0?146:112,28);
      paint(env,ORBS[(level-1)%ORBS.length],ball.x,ball.y,27,27);
      if(waiting){
        rect(c,69,420,342,74,12,"#172b49ef","#b8c9ff55");
        text(c,"Tap or Space to launch",240,448,18);
        text(c,"Move the paddle to aim the bounce",240,474,13,"#c0d1e9");
      }
      text(c,"◆ ".repeat(Math.max(0,lives)),240,636,16,"#e6d0a6");
      if(wide>0)text(c,"Wide "+Math.ceil(wide)+"s",77,637,12,"#ffdfa2");
      fx.draw();
    },env);
    l.release=motion.release;
    return l;
  });
})();
