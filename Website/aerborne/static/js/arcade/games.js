// Six games share the same logical 480 x 640 playfield and fixed-step simulation.
(function () {
  "use strict";
  var GEMS = ["might", "precision", "aegis", "ward", "celerity", "vitality", "resolve", "affinity", "fortune"];
  var PALETTE = ["#ffad86", "#95dfff", "#b6d5ee", "#ceb0ff", "#a8edc2", "#eea9cc", "#e9d7a0", "#bc9ef1", "#ffda82"];
  function clamp(n, lo, hi) { return Math.max(lo, Math.min(hi, n)); }
  function rand(lo, hi) { return lo + Math.random() * (hi - lo); }
  function rect(c, x,y,w,h,r,fill,stroke) {
    if (w <= 0 || h <= 0) return;
    r = Math.min(r,w/2,h/2); c.beginPath(); c.moveTo(x+r,y); c.arcTo(x+w,y,x+w,y+h,r); c.arcTo(x+w,y+h,x,y+h,r); c.arcTo(x,y+h,x,y,r); c.arcTo(x,y,x+w,y,r); c.closePath();
    c.fillStyle=fill; c.fill(); if(stroke) { c.strokeStyle=stroke; c.lineWidth=1.5; c.stroke(); }
  }
  function text(c,t,x,y,size,col) { c.font="800 "+size+"px Nunito,sans-serif"; c.textAlign="center"; c.fillStyle=col||"#eaf4ff"; c.fillText(t,x,y); }
  function cloud(c,x,y,s,colour) {
    c.fillStyle=colour||"#e8f5ff"; c.beginPath();
    [[0,0,22],[24,-10,27],[50,0,22],[25,8,23]].forEach(function(v){c.moveTo(x+v[0]*s+v[2]*s,y+v[1]*s);c.arc(x+v[0]*s,y+v[1]*s,v[2]*s,0,Math.PI*2);}); c.fill();
  }
  function backdrop(env,kind,time) {
    var c=env.ctx, g=c.createLinearGradient(0,0,0,640);
    g.addColorStop(0,kind==="ember"?"#281e35":kind==="violet"?"#211c43":"#163956");
    g.addColorStop(1,kind==="ember"?"#57343b":kind==="violet"?"#142c49":"#5a9cae"); c.fillStyle=g;c.fillRect(0,0,480,640);
    var image=env.img("landscape"); if(image) { c.save(); c.globalAlpha=kind==="sky"?.2:.08; var cover=Math.max(480/image.width,640/image.height);c.drawImage(image,(480-image.width*cover)/2,(640-image.height*cover)/2,image.width*cover,image.height*cover);c.restore(); }
    for(var i=0;i<25;i++){c.fillStyle=i%4?"#d6eeff35":"#d6eeff88";c.fillRect((i*137+35)%480,(i*83+16)%640,i%4?1.5:2.5, i%4?1.5:2.5);}
    if(kind==="sky") for(var n=0;n<4;n++){ c.save();c.globalAlpha=.1+n*.025;cloud(c,((n*158-time*(env.reduced?0:8+n))+600)%700-120,160+n*134,.8+n*.2);c.restore(); }
  }
  function gem(env,i,x,y,size,alpha) {
    var c=env.ctx, image=env.img(GEMS[(i+9)%9]); c.save(); c.globalAlpha=alpha==null?1:alpha;
    if(image) c.drawImage(image,x-size/2,y-size/2,size,size);
    else {c.fillStyle=PALETTE[i%9];c.beginPath();c.moveTo(x,y-size*.45);c.lineTo(x+size*.34,y);c.lineTo(x,y+size*.45);c.lineTo(x-size*.34,y);c.closePath();c.fill();}
    c.restore();
  }
  function loop(update, draw, env) {
    var id=0,last=null,acc=0,alive=true,step=1/120;
    function frame(t) {
      if(!alive)return;
      var dt=last===null?0:Math.min(.1,Math.max(0,(t-last)/1000));last=t;
      if(!env.paused()) {
        acc+=dt;
        while(acc>=step&&alive){update(step);acc-=step;}
        draw();
      } else acc=0;
      if(alive)id=requestAnimationFrame(frame);
    }
    return {start:function(){draw();id=requestAnimationFrame(frame);},stop:function(){alive=false;cancelAnimationFrame(id);}};
  }
  function controls(env, action) {
    env.on(env.canvas,"pointerdown",function(e){if(env.paused())return;e.preventDefault();action(e);});
    env.on(env.canvas,"keydown",function(e){if(!e.repeat&&!env.paused()&&(e.code==="Space"||e.code==="Enter"||e.code==="ArrowUp")){e.preventDefault();action(e);}});
  }
  function point(env,e) {var r=env.canvas.getBoundingClientRect();return {x:(e.clientX-r.left)*480/r.width,y:(e.clientY-r.top)*640/r.height};}
  function mover(env,min,max,initial) {
    var keys={},x=initial,target=initial,drag=false;
    function move(e){if(env.paused()||(e.pointerType==="touch"&&!drag))return;target=clamp(point(env,e).x,min,max);x=target;}
    env.on(env.canvas,"pointerdown",function(e){if(env.paused())return;drag=true;env.canvas.setPointerCapture(e.pointerId);move(e);e.preventDefault();});
    env.on(env.canvas,"pointermove",move);
    env.on(env.canvas,"pointerup",function(){drag=false;});
    env.on(env.canvas,"pointercancel",function(){drag=false;});
    env.on(env.canvas,"keydown",function(e){if(!env.paused()&&(e.code==="ArrowLeft"||e.code==="ArrowRight")){keys[e.code]=true;e.preventDefault();}});
    env.on(window,"keyup",function(e){delete keys[e.code];});
    return {tick:function(dt){x=clamp(x+((keys.ArrowRight?1:0)-(keys.ArrowLeft?1:0))*420*dt,min,max);return x;},release:function(){keys={};drag=false;}};
  }
  function effects(env) {
    var items=[];
    return {
      burst:function(x,y,col,label) {
        if(label)items.push({x:x,y:y,vx:0,vy:-26,life:1,max:1,label:label,col:col});
        if(!env.reduced)for(var i=0;i<10;i++)items.push({x:x,y:y,vx:rand(-100,100),vy:rand(-160,40),life:.5,max:.5,col:col});
        if(items.length>120)items.splice(0,items.length-120);
      },
      tick:function(dt){items.forEach(function(p){p.life-=dt;p.x+=p.vx*dt;p.y+=p.vy*dt;if(!p.label)p.vy+=200*dt;});items=items.filter(function(p){return p.life>0;});},
      draw:function(){var c=env.ctx;items.forEach(function(p){c.save();c.globalAlpha=p.life/p.max;if(p.label)text(c,p.label,p.x,p.y,22,p.col);else{c.fillStyle=p.col;c.fillRect(p.x,p.y,3,3);}c.restore();});}
    };
  }
  // Board rules are independent of rendering so dead boards and cascades can be verified.
  var Rules={
    matches:function(b,n) {
      var hit=new Set();
      for(var r=0;r<n;r++)for(var c=0;c<n;c++){
        var k=r*n+c,v=b[k];if(v<0)continue;
        if(c<n-2&&v===b[k+1]&&v===b[k+2]){var x=c;while(x<n&&b[r*n+x]===v)hit.add(r*n+x++);}
        if(r<n-2&&v===b[k+n]&&v===b[k+2*n]){var y=r;while(y<n&&b[y*n+c]===v)hit.add(y++*n+c);}
      }
      return Array.from(hit);
    },
    legal:function(b,n) {
      for(var i=0;i<b.length;i++)for(var d=0;d<2;d++){
        var j=d?i+n:i+1;if(j>=b.length||(!d&&i%n===n-1))continue;
        var v=b[i];b[i]=b[j];b[j]=v;var yes=Rules.matches(b,n).length>0;
        v=b[i];b[i]=b[j];b[j]=v;if(yes)return [i,j];
      }return null;
    },
    board:function(n) {
      var b=[];
      for(var i=0;i<n*n;i++){var choices=[0,1,2,3,4,5].filter(function(v){return !(i%n>1&&b[i-1]===v&&b[i-2]===v)&&!(i>=2*n&&b[i-n]===v&&b[i-2*n]===v);});b.push(choices[Math.floor(Math.random()*choices.length)]);}
      if(!Rules.legal(b,n)){ // Force a legal opening without creating a pre-existing match.
        b[0]=0;b[1]=1;b[2]=0;b[n+1]=0;b[n]=2;b[n+2]=3;
        if(Rules.matches(b,n).length)return Rules.board(n);
      }
      return b;
    }
  };
  if(typeof module!=="undefined"&&module.exports)module.exports=Rules;

  Arcade.register("sky-hop",function(env){
    var c=env.ctx,y=315,vy=0,started=false,elapsed=0,score=0,gates=[],spawn=.7,dead=false,fx=effects(env),flapAge=0;
    controls(env,function(){if(dead)return;started=true;vy=-300;flapAge=.16;});
    function island(x,y,w,h,up){
      if(h<1)return;
      var g=c.createLinearGradient(x,y,x+w,y);g.addColorStop(0,"#365176");g.addColorStop(.55,"#60728b");g.addColorStop(1,"#263e60");
      rect(c,x,y,w,h,8,g,"#9ac7dd44");
      c.strokeStyle="#abc9d233";c.beginPath();c.moveTo(x+w*.5,y);c.lineTo(x+w*.25,y+h*.45);c.lineTo(x+w*.68,y+h*.8);c.stroke();
      rect(c,x-5,up?y+h-16:y,w+10,16,5,"#73b6a0","#afe5ba");
      gem(env,1,x+w/2,up?y+h-38:y+40,28,.7);
    }
    var l=loop(function(dt){
      elapsed+=dt;fx.tick(dt);flapAge=Math.max(0,flapAge-dt);
      if(!started){env.status("Tap or Space to fly");return;}
      vy+=780*dt;y+=vy*dt;spawn-=dt;
      var speed=155+Math.min(45,score*1.4),gap=Math.max(176,208-score*1.1);
      if(spawn<=0){spawn=1.85;gates.push({x:515,top:rand(95,640-gap-110),gap:gap,scored:false});}
      gates.forEach(function(p){
        p.x-=speed*dt;
        if(!p.scored&&p.x+70<105){p.scored=true;score++;env.score(score);fx.burst(125,y,"#ffe1a1","+1");}
        if(125+15>p.x&&125-15<p.x+70&&(y-14<p.top||y+14>p.top+p.gap))dead=true;
      });gates=gates.filter(function(p){return p.x>-90;});
      if(y<16||y>620)dead=true;
      env.status(score+" gaps cleared");
      if(dead)env.end("Flight complete");
    },function(){
      backdrop(env,"sky",elapsed);
      gates.forEach(function(p){island(p.x,0,70,p.top,true);island(p.x,p.top+p.gap,70,640-p.top-p.gap,false);});
      c.save();c.translate(125,y);c.rotate(started?clamp(vy/900,-.4,.65):Math.sin(elapsed*2)*.04);
      c.shadowBlur=env.reduced?0:flapAge*100;c.shadowColor="#b4efff";
      var im=env.img("pufflet");if(im){c.beginPath();c.arc(0,0,25,0,Math.PI*2);c.clip();c.drawImage(im,-29,-29,58,58);}else cloud(c,-21,0,.65);
      c.restore();fx.draw();
      if(!started){rect(c,78,410,324,56,12,"#132e48dc","#b1dbed55");text(c,"Tap or press Space to take flight",240,444,17);}
    },env);return l;
  });

  Arcade.register("gem-rush",function(env){
    var c=env.ctx,n=7,cell=60,ox=30,oy=130,b=Rules.board(n),picked=-1,cursor=0,score=0,time=60,phase="ready",delay=0,hits=[],chain=0,swap=null,drop=new Array(49).fill(0),idle=0,hint=null,fx=effects(env),message="Match three. Make it a cascade.";
    function swapCells(a,z){var v=b[a];b[a]=b[z];b[z]=v;}
    function choose(i){
      if(phase!=="ready"||i<0||i>=49||env.paused())return;
      idle=0;hint=null;
      if(picked<0){picked=i;return;}
      if(picked===i){picked=-1;return;}
      var adjacent=(Math.abs(picked-i)===7)||(Math.floor(picked/7)===Math.floor(i/7)&&Math.abs(picked-i)===1);
      if(!adjacent){picked=i;return;}
      swap=[picked,i];picked=-1;phase="swap";delay=.16;chain=0;swapCells(swap[0],swap[1]);
    }
    env.on(env.canvas,"pointerdown",function(e){if(env.paused())return;var p=point(env,e),x=Math.floor((p.x-ox)/cell),y=Math.floor((p.y-oy)/cell);if(x>=0&&x<7&&y>=0&&y<7){cursor=y*7+x;choose(cursor);}e.preventDefault();});
    env.on(env.canvas,"keydown",function(e){
      if(env.paused()||e.repeat)return;
      var r=Math.floor(cursor/7),x=cursor%7;
      if(e.code==="ArrowLeft")cursor=r*7+Math.max(0,x-1);else if(e.code==="ArrowRight")cursor=r*7+Math.min(6,x+1);
      else if(e.code==="ArrowUp")cursor=Math.max(0,r-1)*7+x;else if(e.code==="ArrowDown")cursor=Math.min(6,r+1)*7+x;
      else if(e.code==="Enter"||e.code==="Space")choose(cursor);else return;e.preventDefault();
    });
    function settle(){
      hits=Rules.matches(b,n);
      if(hits.length){
        chain++;var points=hits.length*20*Math.min(chain,4);score=Math.min(20000,score+points);env.score(score);
        message=chain>1?"Cascade ×"+Math.min(chain,4):"Nice match!";fx.burst(240,103,"#b6f0d4","+"+points);
        phase="clear";delay=.23;
      }else{
        phase="ready";chain=0;
        if(!Rules.legal(b,n)){b=Rules.board(n);message="Fresh gems! No moves were left.";fx.burst(240,340,"#b6f0d4","Shuffled");}
      }
    }
    var l=loop(function(dt){
      time=Math.max(0,time-dt);idle+=dt;fx.tick(dt);drop=drop.map(function(v){return Math.min(0,v+dt*1000);});
      env.status(Math.ceil(time)+"s · "+(phase==="ready"?"Find a match":"Cascade"));
      if(time<=0){env.end("Time's up");return;}
      if(phase==="ready"){if(idle>6)hint=Rules.legal(b,n);return;}
      delay-=dt;if(delay>0)return;
      if(phase==="swap"){
        if(Rules.matches(b,n).length)settle();
        else{phase="undo";delay=.18;message="Try a different neighbour.";}
      }else if(phase==="undo"){swapCells(swap[0],swap[1]);swap=null;phase="ready";}
      else if(phase==="clear"){
        hits.forEach(function(i){b[i]=-1;});
        for(var col=0;col<n;col++){
          var vals=[];for(var row=n-1;row>=0;row--)if(b[row*n+col]>=0)vals.push({v:b[row*n+col],row:row});
          for(var dest=n-1;dest>=0;dest--){var item=vals[n-1-dest];b[dest*n+col]=item?item.v:Math.floor(rand(0,6));drop[dest*n+col]=item?(item.row-dest)*cell:-(n-vals.length)*cell;}
        }hits=[];phase="fall";delay=.28;swap=null;
      }else if(phase==="fall")settle();
    },function(){
      backdrop(env,"violet",0);text(c,"THE ASPECT VAULT",240,45,15,"#a8c9e3");
      text(c,message,240,80,17,"#b6edcc");rect(c,30,102,420,5,2,"#101b31");rect(c,30,102,420*time/60,5,2,"#b1e3c3");
      rect(c,22,122,436,436,14,"#101d34bb","#86b0a24a");
      for(var i=0;i<49;i++){
        var x=ox+(i%7)*cell,y=oy+Math.floor(i/7)*cell;
        rect(c,x+2,y+2,56,56,9,i===picked?"#3e6b70":i%2?"#263552":"#293b58",i===picked?"#d8ffdd":i===cursor?"#8fb7cf77":null);
        if(hint&&hint.indexOf(i)>=0&&phase==="ready")rect(c,x+4,y+4,52,52,8,"#b9f5ce12","#a8ebbf");
        var alpha=phase==="clear"&&hits.indexOf(i)>=0?clamp(delay/.23,.15,1):1,dx=0,dy=0;
        if(swap&&phase==="swap"){
          var other=i===swap[0]?swap[1]:i===swap[1]?swap[0]:-1;
          if(other>=0){dx=((other%7)-(i%7))*cell*(delay/.16);dy=(Math.floor(other/7)-Math.floor(i/7))*cell*(delay/.16);}
        }
        c.save();c.beginPath();c.rect(30,130,420,420);c.clip();gem(env,b[i],x+30+dx,y+30+dy+drop[i],48,alpha);c.restore();
      }
      text(c,"Select two neighbouring gems to swap",240,597,15,"#acbdd9");fx.draw();
    },env);return l;
  });

  Arcade.register("cloud-catch",function(env){
    var c=env.ctx,motion=mover(env,50,430,240),x=240,time=90,elapsed=0,spawn=.3,items=[],score=0,lives=3,shield=0,fx=effects(env);
    var l=loop(function(dt){
      time=Math.max(0,time-dt);elapsed+=dt;shield=Math.max(0,shield-dt);fx.tick(dt);x=motion.tick(dt);spawn-=dt;
      if(spawn<=0){spawn=Math.max(.34,.75-elapsed*.004);items.push({x:rand(38,442),y:-35,vy:rand(145,195)+elapsed*1.1,storm:Math.random()<.22,kind:Math.floor(rand(0,9)),spin:rand(-.15,.15)});}
      items.forEach(function(p){
        p.y+=p.vy*dt;
        if(p.y>551&&p.y<593&&Math.abs(p.x-x)<(p.storm?40:46)){
          p.done=true;
          if(!p.storm){score++;env.score(score);fx.burst(p.x,551,"#c6f7ee","+1");}
          else if(shield<=0){lives--;shield=1.4;fx.burst(x,544,"#ffb19d","Storm hit");}
        }
      });items=items.filter(function(p){return !p.done&&p.y<680;});
      env.status(Math.ceil(time)+"s · "+lives+(lives===1?" heart":" hearts"));
      if(lives<=0||time<=0)env.end(lives<=0?"Storms caught up":"Time's up");
    },function(){
      backdrop(env,"sky",elapsed);text(c,"GEMS ARE GOOD. STORMS ARE NOT.",240,45,14,"#d3e9ef");
      items.forEach(function(p){
        if(p.storm){cloud(c,p.x-22,p.y,.65,"#263a60");c.fillStyle="#ffe18e";c.beginPath();c.moveTo(p.x+2,p.y-3);c.lineTo(p.x-9,p.y+16);c.lineTo(p.x,p.y+13);c.lineTo(p.x-5,p.y+31);c.lineTo(p.x+12,p.y+7);c.lineTo(p.x+2,p.y+9);c.closePath();c.fill();}
        else gem(env,p.kind,p.x,p.y,40);
      });
      c.save();if(shield>0&&!env.reduced)c.globalAlpha=.5+Math.sin(elapsed*24)*.25;
      cloud(c,x-28,583,.85,"#e2f4ff");rect(c,x-44,553,88,28,9,"#9c7c59","#ffdb96");
      rect(c,x-47,548,94,9,3,"#d5a963","#ffe7ad");
      var im=env.img("kitekit");if(im){c.save();c.beginPath();c.arc(x,534,23,0,Math.PI*2);c.clip();c.drawImage(im,x-26,508,52,52);c.restore();}
      if(shield>0){c.strokeStyle="#b9eaff";c.lineWidth=2;c.beginPath();c.arc(x,558,58,Math.PI,2*Math.PI);c.stroke();}c.restore();
      text(c,"♥ ".repeat(lives),240,627,21,"#ffd7c5");fx.draw();
    },env);l.release=motion.release;return l;
  });

  Arcade.register("codex-memory",function(env){
    var c=env.ctx,cards=[],open=[],found=0,moves=0,score=0,time=120,preview=2.5,wait=0,cursor=0,elapsed=0,fx=effects(env);
    for(var i=0;i<18;i++)cards.push({kind:i%9,matched:false,reveal:0});
    for(var j=cards.length-1;j>0;j--){var k=Math.floor(rand(0,j+1)),v=cards[j];cards[j]=cards[k];cards[k]=v;}
    function choose(i){
      if(preview>0||wait>0||env.paused()||!cards[i]||cards[i].matched||open.indexOf(i)>=0)return;
      open.push(i);
      if(open.length===2){
        moves++;
        if(cards[open[0]].kind===cards[open[1]].kind){
          cards[open[0]].matched=cards[open[1]].matched=true;found++;score+=30;
          fx.burst(240,92,"#d5b5ff","Pair found +30");open=[];env.score(score);
          if(found===9){score+=Math.floor(time)+Math.max(0,18-moves)*10;env.score(score);env.end("All nine pairs found");}
        }else wait=.7;
      }
    }
    env.on(env.canvas,"pointerdown",function(e){if(env.paused())return;var p=point(env,e),x=Math.floor((p.x-36)/138),y=Math.floor((p.y-126)/78);if(x>=0&&x<3&&y>=0&&y<6&&(p.x-36)%138<132&&(p.y-126)%78<72){cursor=y*3+x;choose(cursor);}e.preventDefault();});
    env.on(env.canvas,"keydown",function(e){
      if(env.paused()||e.repeat)return;
      var r=Math.floor(cursor/3),x=cursor%3;
      if(e.code==="ArrowLeft")cursor=r*3+Math.max(0,x-1);else if(e.code==="ArrowRight")cursor=r*3+Math.min(2,x+1);
      else if(e.code==="ArrowUp")cursor=Math.max(0,r-1)*3+x;else if(e.code==="ArrowDown")cursor=Math.min(5,r+1)*3+x;
      else if(e.code==="Space"||e.code==="Enter")choose(cursor);else return;e.preventDefault();
    });
    var l=loop(function(dt){
      elapsed+=dt;fx.tick(dt);
      if(preview>0)preview=Math.max(0,preview-dt);
      else{time=Math.max(0,time-dt);if(wait>0){wait-=dt;if(wait<=0)open=[];}}
      cards.forEach(function(card,i){var target=preview>0||card.matched||open.indexOf(i)>=0?1:0;card.reveal=env.reduced?target:clamp(card.reveal+(target?1:-1)*dt*8,0,1);});
      env.status(preview>0?"Remember the gems":Math.ceil(time)+"s · "+found+"/9 pairs");
      if(time<=0)env.end("Time's up");
    },function(){
      backdrop(env,"violet",elapsed);text(c,preview>0?"A MOMENT TO REMEMBER":"THE MEMORY CODEX",240,43,16,"#d7baff");
      text(c,preview>0?"Take a look. Find the pairs.":moves+" moves · "+found+" pairs found",240,80,18,"#b9cce4");
      rect(c,36,103,408,4,2,"#ffffff18");rect(c,36,103,408*(preview>0?preview/2.5:time/120),4,2,"#c4a7ee");
      cards.forEach(function(card,i){
        var x=36+(i%3)*138,y=126+Math.floor(i/3)*78,reveal=card.reveal;
        var scale=env.reduced?1:Math.max(.08,Math.abs(reveal-.5)*2);
        c.save();c.translate(x+66,y+36);c.scale(scale,1);
        rect(c,-66,-36,132,72,10,card.matched?"#324a67":reveal>.5?"#344568":"#283450",card.matched?"#a9d7b4":i===cursor?"#d0b2ff":"#665b8a");
        if(reveal>.5)gem(env,card.kind,0,0,58,card.matched?.65:1);
        else{var im=env.img("crest");if(im)c.drawImage(im,-23,-23,46,46);else text(c,"✦",0,9,28,"#bfb0e1");}
        c.restore();
      });fx.draw();
    },env);return l;
  });

  Arcade.register("forge-strike",function(env){
    var c=env.ctx,pos=.1,dir=1,speed=.6,centre=.5,zone=.26,score=0,hits=0,lives=3,time=60,elapsed=0,cooldown=.35,pulse=0,word="Find the gold. Feel the rhythm.",fx=effects(env);
    function strike(){
      if(cooldown>0||env.paused())return;
      cooldown=.4;pulse=.4;
      var dist=Math.abs(pos-centre);
      if(dist<=zone/2){
        var perfect=dist<=zone*.16;score+=perfect?20:10;hits++;env.score(score);
        word=perfect?"Perfect strike +20":"Clean hit +10";fx.burst(240,365,perfect?"#ffedb0":"#ffc68a",perfect?"PERFECT":"+10");
        speed=Math.min(1.6,.6+hits*.035);zone=Math.max(.13,.26-hits*.003);centre=rand(.19+zone/2,.81-zone/2);
      }else{lives--;word="Missed the gold";fx.burst(240,365,"#eaa6b0","Miss");if(lives<=0)env.end("Forge cooled");}
    }
    controls(env,strike);
    var l=loop(function(dt){
      elapsed+=dt;time=Math.max(0,time-dt);cooldown=Math.max(0,cooldown-dt);pulse=Math.max(0,pulse-dt);fx.tick(dt);
      if(cooldown<=0){pos+=dir*speed*dt;if(pos>1){pos=2-pos;dir=-1;}if(pos<0){pos=-pos;dir=1;}}
      env.status(Math.ceil(time)+"s · "+lives+(lives===1?" strike left":" strikes left"));
      if(time<=0)env.end("Time's up");
    },function(){
      backdrop(env,"ember",elapsed);text(c,"THE SKY FORGE",240,46,16,"#efbd95");text(c,word,240,95,19,"#ffdfb1");
      var glow=c.createRadialGradient(240,365,10,240,365,230);glow.addColorStop(0,"#fb9e3338");glow.addColorStop(1,"#fb9e3300");c.fillStyle=glow;c.fillRect(0,140,480,430);
      c.save();c.translate(240,285);c.rotate(-.55+Math.max(0,pulse-.15)*3);
      rect(c,-4,-75,15,128,5,"#bd8c59","#e6bd86");rect(c,-48,-96,102,49,8,"#7e92aa","#d5deea");rect(c,-42,-92,87,12,4,"#b9cad9");c.restore();
      c.fillStyle="#506582";c.beginPath();c.moveTo(140,370);c.lineTo(330,370);c.lineTo(374,337);c.lineTo(337,331);c.lineTo(139,331);c.lineTo(112,345);c.closePath();c.fill();
      rect(c,186,370,110,72,10,"#405674","#91a8c0");rect(c,150,425,188,25,6,"#61758e","#a5bacb");
      gem(env,0,245,332,44);rect(c,40,499,400,48,13,"#141b2b","#a68b7166");
      rect(c,48+(centre-zone/2)*384,505,zone*384,36,6,"#b8893d","#f9d58a");
      rect(c,48+(centre-zone*.16)*384,505,zone*.32*384,36,4,"#ffe3a2");
      rect(c,45+pos*384,492,7,62,3,cooldown>0?"#c1b5a4":"#fff5d7");
      text(c,cooldown>0?"Ready…":"Tap or Space to strike",240,588,18,"#f7d8b3");
      text(c,"◆ ".repeat(lives),240,625,18,"#ffd08a");fx.draw();
    },env);return l;
  });

  Arcade.register("rune-breaker",function(env){
    var c=env.ctx,motion=mover(env,57,423,240),x=240,ball={x:240,y:558,vx:145,vy:-280},waiting=true,bricks=[],lives=3,score=0,level=1,time=90,elapsed=0,fx=effects(env),trail=[];
    function wall(){bricks=[];for(var r=0;r<5;r++)for(var col=0;col<6;col++)bricks.push({x:24+col*73,y:110+r*34,w:67,h:27,kind:(r+level)%9,alive:true});waiting=true;trail=[];}
    wall();
    function launch(){if(env.paused()||!waiting)return;x=motion.tick(0);ball.x=x;ball.y=558;waiting=false;ball.vx=rand(-110,110);ball.vy=-Math.sqrt(Math.pow(310+Math.min(110,level*15),2)-ball.vx*ball.vx);}
    controls(env,launch);
    function collide(b,px,py){
      var closeX=clamp(ball.x,b.x,b.x+b.w),closeY=clamp(ball.y,b.y,b.y+b.h);
      if(Math.pow(ball.x-closeX,2)+Math.pow(ball.y-closeY,2)>64)return false;
      b.alive=false;score+=10;env.score(score);fx.burst(ball.x,ball.y,PALETTE[b.kind]);
      if(px+8<=b.x||px-8>=b.x+b.w)ball.vx*=-1;else if(py+8<=b.y||py-8>=b.y+b.h)ball.vy*=-1;else ball.vy*=-1;
      return true;
    }
    var l=loop(function(dt){
      elapsed+=dt;time=Math.max(0,time-dt);fx.tick(dt);x=motion.tick(dt);
      if(waiting){ball.x=x;ball.y=558;}
      else{
        // Small substeps prevent the ball tunnelling through thin runes on a slow frame.
        var steps=Math.max(1,Math.ceil(Math.hypot(ball.vx,ball.vy)*dt/5));
        for(var s=0;s<steps;s++){
          var px=ball.x,py=ball.y;ball.x+=ball.vx*dt/steps;ball.y+=ball.vy*dt/steps;
          if(ball.x<12){ball.x=12;ball.vx=Math.abs(ball.vx);}if(ball.x>468){ball.x=468;ball.vx=-Math.abs(ball.vx);}
          if(ball.y<62){ball.y=62;ball.vy=Math.abs(ball.vy);}
          if(ball.vy>0&&py+8<=579&&ball.y+8>=579&&Math.abs(ball.x-x)<=62){
            var offset=clamp((ball.x-x)/56,-.95,.95),speed=Math.min(460,Math.hypot(ball.vx,ball.vy)+5);
            ball.vx=speed*offset*.85;ball.vy=-Math.sqrt(speed*speed-ball.vx*ball.vx);ball.y=570;fx.burst(ball.x,577,"#b2deff");
          }
          for(var i=0;i<bricks.length;i++)if(bricks[i].alive&&collide(bricks[i],px,py))break;
          if(ball.y>657){lives--;waiting=true;trail=[];fx.burst(x,566,"#f0b0c0","Life lost");break;}
        }
        if(!env.reduced){trail.push({x:ball.x,y:ball.y});if(trail.length>12)trail.shift();}
        if(!bricks.some(function(b){return b.alive;})){score+=100;env.score(score);level++;wall();fx.burst(240,360,"#ffe3aa","Wall cleared +100");}
      }
      env.status(Math.ceil(time)+"s · "+lives+" lives · Wall "+level);
      if(lives<=0||time<=0)env.end(lives<=0?"Out of lives":"Time's up");
    },function(){
      backdrop(env,"violet",elapsed);text(c,"THE RUNE SANCTUM",240,42,15,"#c3b0fa");
      rect(c,14,68,452,541,15,"#11223838","#abc3ec33");
      bricks.forEach(function(b){if(!b.alive)return;rect(c,b.x,b.y,b.w,b.h,6,"#485575",PALETTE[b.kind]+"aa");rect(c,b.x+3,b.y+3,b.w-6,4,2,"#ffffff22");gem(env,b.kind,b.x+b.w/2,b.y+b.h/2,23);});
      trail.forEach(function(p,i){c.fillStyle="rgba(167,210,255,"+(i/trail.length*.3)+")";c.beginPath();c.arc(p.x,p.y,3+i*.28,0,Math.PI*2);c.fill();});
      rect(c,x-56,579,112,15,7,"#a3d2ec","#e9f8ff");rect(c,x-27,579,54,5,2,"#ffdfa0");
      gem(env,1,ball.x,ball.y,26);
      if(waiting){rect(c,76,414,328,62,12,"#172b49ee","#b8c9ff66");text(c,"Tap or Space to launch",240,442,19);text(c,"Move the paddle to aim",240,464,13,"#b4c9e5");}
      text(c,"◆ ".repeat(lives),240,630,17,"#e6cfa2");fx.draw();
    },env);l.release=motion.release;return l;
  });
})();
