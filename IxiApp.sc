/*
by www.ixi-audio.net
license GPL
*/

IxiLaukiControl {
	var win, gcontrols, sfs, >target, main, <gstate;

	*new { |name="Control", rect, exepath, main|
		^super.new.init(name, rect, exepath, main)
	}

	init {|name, rect, exepath, amain|
		var fpaths, files=List.new;

		gstate = Dictionary.new;
		gstate[\pitchrange] = [-2,2];
		gstate[\sound] = "";
		gstate[\in] = 0;
		gstate[\out] = 0;
		gstate[\amp] = 1;

		main = amain; // ref to main win

		if (exepath.isNil, {
			try { ~path = thisProcess.nowExecutingPath.dirname} { ~path = Platform.userHomeDir }
		},{
			~path = exepath;
		});
		exepath.postln;

		~ixibuffers = Dictionary.new; // filename_str -> buffer
		Server.default.waitForBoot({
			var path =  ~path ++ Platform.pathSeparator ++ "sounds"++ Platform.pathSeparator;
			PathName.new(path).files.do( {|file|
				~ixibuffers.add( file.fileName -> Buffer.read(Server.default, file.fullPath) );
				("loading"+file.fileName+"to ~ixibuffers").postln;
			})
		});

		~inversepan = 1;

		gcontrols = Dictionary.new;

		win = Window(name, rect, resizable: false);
		win.view.decorator = FlowLayout(win.view.bounds);
		win.view.decorator.gap=2@2;
		win.onClose = {};

		// GLOBAL
		StaticText(win, 13@18).align_(\left).string_("In").resize_(7);
		gcontrols[\in] = PopUpMenu(win, Rect(10, 10, 42, 17))
		.items_( Array.fill(16, { arg i; i }) )
		.action_({|m|
			gstate[\in] = m.value;
			main.boxes.do({|b|b.in(m.value)})
		})
		.value_(gstate[\in]); // default to sound in

		StaticText(win, 22@18).align_(\left).string_("Out").resize_(7);
		gcontrols[\out] = PopUpMenu(win, Rect(10, 10, 42, 17))
		.items_( Array.fill(16, { arg i; i }) )
		.action_({|m|
			gstate[\out] = m.value;
			main.boxes.do({|b|b.out(m.value)})
		})
		.value_(gstate[\out]); // default to sound in

		//win.view.decorator.nextLine;

		IxiSimpleButton(win,"VU",{
			Server.default.meter(2,2)
		});

		IxiSimpleButton(win,"HELP",{
			var help = Window.new("Help", Rect(~stagewidth/2, ~stageheight/2, 280, 100) ).background_(Color.white).front;
			StaticText(help, 270@90).string_(
				"Lauki by www.ixi-audio.net \n"++
				"- Click boxes to trigger selected sound \n"++
				"- SPACE + drag to move objects \n"++
				"- Right click objects for context menu \n"++
				"- Right click background for creation menu"
			);
		});

		win.view.decorator.nextLine;

		StaticText(win, 30@18).string_("Amp");

		win.view.decorator.nextLine;

		gcontrols[\amp] = Slider(win, 190@20)
		.action_({ |sl|
			gstate[\amp] = sl.value;
			main.boxes.do({|box| box.amp(sl.value)}) // THIS MUST CONTROL A BUS not individual synths
		})
		.value_(gstate[\amp]);

		gcontrols[\range_label] = StaticText(win, 190@18).string_("Pitch range: -2 : 2");
		gcontrols[\range] = RangeSlider(win, 190@20)
		.lo_(0)
		.range_(1)
		.action_({ |sl|
			gstate[\pitchrange] = [sl.lo.linlin(0,1,-2,2).asFloat, sl.hi.linlin(0,1,-2,2).asFloat]; // map 0:1 to -2:2
			gcontrols[\range_label].string = "Pitch range" + gstate[\pitchrange][0].asStringPrec(2) + ":" + gstate[\pitchrange][1].asStringPrec(2);
			main.boxes.do({|box| box.updaterate });
			main.updatelines;
		});

		win.view.decorator.nextLine;

		Button.new(win, 100@18).states_([
			["Inverse paning", Color.black,],
			["Inverse paning", Color.green,]
		]).action_({
			~inversepan = ~inversepan * 1.neg;
			//main.boxes.do({|box| box.dopan });
			main.boxes.collect(_.updatepan);
		});

		win.view.decorator.nextLine;

		StaticText(win, 50@18).string_("Samples");


		gcontrols[\snd] = PopUpMenu(win, 190@20)
		.items_( // produce a list with the filenames
			//~ixibuffers.values.collect({|it| PathName(it.path).fileName})
			{var path = ~path ++ Platform.pathSeparator ++ "sounds"++ Platform.pathSeparator;
				PathName.new(path).files.collect({|i|i.fileName})
			}.value // buffers havent been loaded yet to server
		)
		.action_({ |menu|
			/*			menu.items_(// TO DO: items should be updated every time it is opened
			{var path = ~path ++ Platform.pathSeparator ++ "sounds"++ Platform.pathSeparator;
			PathName.new(path).files.collect({|i|i.fileName})
			}.value
			);*/
			~laukibuffer = menu.item;
			gstate[\sound] = menu.item;
		});

		{gcontrols[\snd].valueAction = 0}.defer(2);// BAD SOLUTION


		IxiSimpleButton(win, "import",{
			FileDialog({ |apath| // open import
				this.updatefileimport(apath);
				/*				var file = PathName.new(apath);

				// add item to ~ixibuffers
				if (~ixibuffers[file.fileName]==nil, { // not there already
				~ixibuffers.add( file.fileName -> Buffer.read(Server.default, file.fullPath) );
				// and update pulldown menu
				gcontrols[\snd].items_(
				~ixibuffers.values.collect({|it| PathName(it.path).fileName})
				//PathName.new(~path).files.collect({|i|i.fileName})
				)
				});*/
			},
			fileMode: 0,
			stripResult: true,
			path: (~path ++ Platform.pathSeparator ++ "sounds"++ Platform.pathSeparator)
			);
		});

		IxiSimpleButton(win,"sample",{
			// REC: create a buffer in ~ixibuffers and stream sound input into it
			~ixibuffers.add(Date.getDate.stamp -> Buffer.new( Server.default, Server.default.sampleRate*4, 2 ));
			/*			(
			SynthDef(\help_RecordBuf, { arg out = 0, bufnum = 0;
			var in = SoundIn.ar(0);
			RecordBuf.ar(in, bufnum, doneAction: Done.freeSelf, loop: 0);
			}).play(s,[\out, 0, \bufnum, b]);
			)*/
			// STOP: stop stream and update controls[\snd].items. and save as wav into sound folder
			gcontrols[\snd].items_(
				~ixibuffers.values.collect({|it| PathName(it.path).fileName})
			)
		});

		win.view.decorator.nextLine;



		// SESSIONS
		gcontrols[\pat_label] = StaticText(win, win.bounds.width@18).string_("Sessions");

		win.view.decorator.nextLine;

		IxiSimpleButton(win,"S",{
			var data = Dictionary.new, boxdata = Dictionary.new,  filename;
			filename = Date.getDate.stamp++".session";

			// save buffer abs paths to be able to import them again?

			data.put(\state, gstate);

			data.put(\buffers, ~ixibuffers );

			main.boxes.do{|box, n|
				boxdata.put(("box"++n).asSymbol, box.state)
			};

			data.put(\boxdata, boxdata);

			("saving sessions into" + ~path ++ Platform.pathSeparator ++ "sessions" ++ Platform.pathSeparator ++ filename).postln;

			data.writeArchive(~path ++ Platform.pathSeparator ++ "sessions" ++ Platform.pathSeparator ++ filename);
		});


		IxiSimpleButton(win,"O",{
			FileDialog({ |apath| // open import
				var	data = Object.readArchive(apath);
				("reading session"+apath).postln;

				gcontrols[\pat_label].string = "Sessions:" + PathName(apath).fileName.split($.)[0];

				main.clear; // killem all

				data[\buffers].do{|buf|
					this.updatefileimport(buf.path) // read if not already
				};

				gstate = data[\state];

				gcontrols[\range].activeLo = gstate[\pitchrange][0];
				gcontrols[\range].activeHi = gstate[\pitchrange][1];
				gcontrols[\amp].valueAction = gstate[\amp];
				gcontrols[\in].valueAction = gstate[\in];
				gcontrols[\out].valueAction = gstate[\out];

				data[\boxdata].do{|next|
					main.newbox( next[\rect].center, next )
				};
			},
			fileMode: 0,
			stripResult: true,
			path: (~path ++ Platform.pathSeparator ++ "sessions"++ Platform.pathSeparator)
			)
		});

		IxiSimpleButton(win,"clear",{
			gcontrols[\pat_label].string = "Sessions";
			main.clear;
		});
		IxiSimpleButton(win,"grid",{
			gcontrols[\pat_label].string = "Sessions";
			main.dogrid;
		});
		IxiSimpleButton(win,"rloc",{
			gcontrols[\pat_label].string = "Sessions";
			main.rand
		});

		win.front;
	}

	updatefileimport {|apath|
		var file = PathName.new(apath);

		// add item to ~ixibuffers
		if (~ixibuffers[file.fileName]==nil, { // not there already
			~ixibuffers.add( file.fileName -> Buffer.read(Server.default, file.fullPath) );
			// and update pulldown menu
			gcontrols[\snd].items_(
				~ixibuffers.values.collect({|it| PathName(it.path).fileName})
				//PathName.new(~path).files.collect({|i|i.fileName})
			)
		});
	}

	close { win.close }
}









Lauki : IxiWin {
	var <boxes, selected, selection, buffer, uniqueid, lines;

	*new { |width=1024, height=750, path|
		^super.new.init(width, height, path);//.init(name, rect);
	}

	init {|width, height, path|
		super.init("Lauki", Rect(0,0,width,height));

		boxes = List.new;

		OSCdef.freeAll;

		uniqueid = 0;

		selection = IxiSelection.new(this);

		~laukicontrol = IxiLaukiControl.new(rect: Rect(win.bounds.right,win.bounds.height, 200, 230),
			main: this, exepath: path);

		Server.default.waitForBoot{
			SynthDef( \laukiplayer, { arg out=0, buffer=0, amp=1, pan=0, start=0, end=1, rate=0, loop=1, reset=0, index=999, trig=1;
				var length, left, right, phasor, dur;

				dur = BufFrames.kr(buffer);

				phasor = Phasor.ar( trig, rate * BufRateScale.kr(buffer), start*dur, end*dur, resetPos: reset*dur);
				SendReply.ar( HPZ1.ar(HPZ1.ar(phasor).sign), '/loop', 1, index); //loop
				SendReply.kr( LFPulse.kr(12, 0), '/pos', phasor/dur, index); //fps 12

				#left, right = BufRd.ar( 2, buffer, phasor, loop:loop ) * amp;
				Out.ar(out, Balance2.ar(left, right, pan));
			}).load;
			Server.default.sync;
		};

		try { this.loaddefault }; // default session

		this.updatelines;
	}

	loaddefault {
		var path, data;
		path = ~path ++ Platform.pathSeparator ++ "sessions"++ Platform.pathSeparator++"default.session";
		data = Object.readArchive(path);
		data[\boxdata].do{|next|
			this.newbox( next[\rect].center, next )
		};
	}

	getselectables {
		^boxes;
	}

	newbox {|point, state|
		var box = LaukiBox.new( point, index: uniqueid, state: state, main: this );
		boxes.add(box);
		uniqueid = uniqueid + 1;
		^box;
	}

	removebox {|box|
		boxes.removeAt( boxes.indexOf(box) );
	}

	dogrid {
		var x=25,y=25, xgap=27, ygap=27;

		this.clear;

		999.do({|n| // drop some boxes
			var box = this.newbox(Point(x,y));
			x = x + xgap;
			if (x > (win.bounds.width-box.size), {
				x = 25;
				y = y + ygap
			})
		})
	}

	rand {
		boxes.do({|box| // MUST have a ref to the window or similar to know the size
			box.rect.origin = Point(canvas.bounds.width.rand, canvas.bounds.height.rand)
		})
	}

	clear {
		boxes.collect(_.close);
		boxes.collect(_.kill); // removes them from stack. this is the very last thing to do
		boxes = List.new;
	}

	close {
		~laukicontrol.close;
		boxes.collect(_.close);
		boxes.collect(_.kill);
		super.close;
	}

	mouseDown {|x, y, mod|
		boxes.do({|box|
			if (box.inside(x,y)==true,{
				selected=box;
				box.mouseDown(x,y, keypressed);
			})
		});
		selected ?? { selection.start(x,y) }
	}

	mouseUp {|x, y, mod |
		if (selected.isNil, {
			selection.stop
		},{
			selected.mouseUp(x,y);
			selected = nil
		});
	}

	rightMouseDown {|x, y, mod|
		var sel;
		boxes.do({|box|
			if (box.inside(x,y)==true,{
				sel = box;
				box.rightMouseDown(x,y)
			})
		});

		sel ?? {IxiLaukiMenu.new(this)};
	}

	rightMouseUp {|x, y, mod|

	}

	mouseMoved {|x, y, mod| // while down
		if ( selected.notNil, { // if there is a selection
			if (keypressed==32,{
				selected.dragged(x,y);
			}, {
				boxes.do({|box|
					if (box.inside(x,y)==true,{
						if (box!=selected, {
							box.play
						})
					})
				})
			})
		},
		{selection.mouseDragged(x,y) })
	}

	/*	keyDown { |char, modifiers, unicode, keycode, key|
	super.keyDown(char, modifiers, unicode, keycode, key);
	}

	keyUp { |char, modifiers, unicode, keycode, key|
	super.keyUp(char, modifiers, unicode, keycode, key);
	}*/

	updatelines {
		lines = [
			[this.calcline(0), Color(1,0,0,0.8), "0"],
			[this.calcline(1), Color(0,1,0,0.8), "1"],
			[this.calcline(-1), Color(0,0,1,0.8), "-1"],
		];
	}
	calcline{|val|
		^val.linlin(~laukicontrol.gstate[\pitchrange][0], ~laukicontrol.gstate[\pitchrange][1], ~stageheight, 0)
	}

	draw {
		super.draw;
		boxes.collect(_.draw);
		selection.draw;

		Pen.lineDash = FloatArray[0.4, 0];
		lines.do{|line|
			Pen.color = line[1];
			Pen.stringAtPoint(line[2], Point(4, line[0]));
			Pen.line(
				Point(0, line[0]),
				Point(~stagewidth, line[0]) );
			Pen.stroke;
		};

	}

	amp {|val|
		selection.selected.collect(_.amp(val));
	}
}





IxiLaukiMenu {
	*new {|main|
		^super.new.init(main);
	}

	init { |main|
		Menu(
			MenuAction("new Lauki", { main.newbox(~mouseloc) }), //center object on mouse loc
			MenuAction("new Spin", {
				"not yet".postln;
				//main.newspin(~mouseloc)
			});

		).front
	}
}







IxiLaukiBoxMenu {
	*new { |box|
		^super.new.init(box);
	}

	init { |box|
		var items = ~ixibuffers.values.collect({|it| PathName(it.path).fileName});//sounds

		Menu(
			MenuAction("box"+box.id, {}).enabled_(false),
			MenuAction("pitch:"+box.dorate.asStringPrec(4)+"/ pan:"+box.dopan.asStringPrec(1), {}).enabled_(false),
			CustomViewAction( //SND
				PopUpMenu()
				.items_(items)
				.action_({ |menu| box.setsound(menu.item) })
				.value_( items.indexOfEqual(box.state[\snd]) )
			),
			CustomViewAction( //AMP
				Slider().orientation_(\horizontal)
				.action_({ |sl|
					box.amp(sl.value)
				}).valueAction_(box.state[\amp])
			),
			CustomViewAction( // PITCH RANGE
				RangeSlider().orientation_(\horizontal)
				.lo_(box.state[\range][0])
				.range_(box.state[\range][1])
				.action_({ |sl|
					box.range(sl.lo, sl.hi)
				})
			),

			//to do friends too
			MenuAction("loop", { box.loop }).checked_(box.state[\loop]),
			MenuAction("tigger", { box.trigger }).checked_(box.state[\trigger]),
			MenuAction("hlock", { box.hlock }).checked_(box.state[\hlock]),
			MenuAction("vlock", { box.vlock }).checked_(box.state[\vlock]),
			MenuAction.separator,
			MenuAction("delete", {
				box.close;
				box.kill // remove me from main stack
			})

		).front;
	}
}





LaukiBox : IxiBox {
	var <state, main, synth=nil, <id, curpos=0, loopcount=0, loopOSC, playhOSC;

	*new { |point, index=0, state, main|
		^super.new.init(point, index, state, main);
	}

	init {|point, index, astate, amain|
		super.init(point);

		main = amain;

		synth.free;
		loopOSC.free;
		playhOSC.free;

		id = index;

		if (astate.isNil, {
			state = Dictionary.new;
			state[\snd] = "";
			state[\loop] = true;
			state[\trigger] = true;
			state[\amp] = 1;
			state[\range] = [0,1];
			state[\hlock] = false;
			state[\vlock] = false;
			state[\rate] = 1;
			state[\pan] = 0;
			state[\rect] = rect;
			state[\playing] = false;
		}, {
			state = astate;
			if(state[\playing], {
				Server.default.waitForBoot( {
					{this.play}.defer(2) // until samples had been loading. not very elegant
				});
			});
		});
	}

	mouseDown {|x,y, keypressed| //play
		if(keypressed == 32, { //space
			super.mouseDown(x,y);
		}, {
			this.play
		})
	}
	mouseUp {|x, y, mod, button|
		if(state[\trigger].not, {
			this.close;
		})
	}

	rightMouseDown{|x,y|
		super.rightMouseDown(x,y);
		IxiLaukiBoxMenu.new(this)
	}

	dorate {
		var rate, prange;
		prange = ~laukicontrol.gstate[\pitchrange];
		rate = ( ((~stageheight-rect.center.y)/~stageheight) * (prange[1]-prange[0]) ) + prange[0];
		if( (rate<0.005) && (rate>0.005.neg), {rate = 0.008}); // not too slow
		^rate
	}

	updaterate {
		state[\rate] = this.dorate;
		synth !? synth.set(\rate, state[\rate]);
	}

	updatepan {
		state[\pan] = this.dopan;
		synth !? synth.set(\pan, state[\pan]);
	}

	dopan {
		^((((rect.center.x/~stagewidth) * 2) - 1 ) * ~inversepan);
	}

	in {|chan|
		synth !? synth.set(\in, chan)
	}

	out {|chan|
		synth !? synth.set(\out, chan)
	}

	amp {|val| //to do friends too
		state[\amp] = val;
		synth !? synth.set(\amp, val);
	}

	hlock {
		state[\hlock] = state[\hlock].not;
		friends.do({|friend|
			if (friend!=this, {friend.state[\hlock] = state[\hlock]})
		})
	}
	vlock {
		state[\vlock] = state[\vlock].not;
		friends.do({|friend|
			if (friend!=this, {friend.state[\vlock] = state[\vlock]})
		})
	}
	loop {
		state[\loop] = state[\loop].not;
		friends.do({|friend|
			if (friend!=this, {friend.state[\loop] = state[\loop]})
		})
	}
	trigger {
		state[\trigger] = state[\trigger].not;
		friends.do({|friend|
			if (friend!=this, {friend.state[\trigger] = state[\trigger]})
		})
	}

	range {|start, end| //to do friends too
		state[\range] = [start, end];
		if (synth.notNil, {
			synth.set(\start, start);
			synth.set(\end, end);
		})
	}

	move {|delta|
		super.move(delta);
		state[\rect] = rect; // check if hlock or vlock
		this.update;
	}

	dragged {|x,y|
		super.dragged(x,y);
		state[\rect] = rect;
		this.update;
	}

	update {|delta| // called from dragged
		if (synth.notNil, {
			state[\rate] = this.dorate;
			state[\pan] = this.dopan;
			synth.set(\pan, state[\pan]);
			synth.set(\rate, state[\rate]);
		})
	}

	newrect {|x,y| // overwrite default newrect
		var nrect = Rect(x,y, size, size) - offset;
		if (state[\hlock], { nrect.left = rect.left	});
		if (state[\vlock], { nrect.top = rect.top	});
		state[\rect] = rect;
		^nrect
	}

	setsound {|file| //to do friends too
		state[\snd] = file;
		synth !? synth.set(\buffer, ~ixibuffers[file].bufnum)
	}

	play {
		if (synth.notNil,{ // already playing. just stop
			this.close;
		},{ // play
			this.close;// stop first to reset

			bgcolor = Color(0,1,0,0.5);

			state[\playing] = true;
			if (state[\snd]=="", { state[\snd] = ~laukibuffer });
			state[\rate] = this.dorate;
			state[\pan] = this.dopan;

			synth = Synth(\laukiplayer, [
				\buffer, ~ixibuffers[state[\snd]].bufnum,
				\rate, state[\rate],
				\pan, state[\pan],
				\start, state[\range][0],
				\end, state[\range][1],
				//\loop, state[\loop].asInteger, // int required by synth
				\amp, state[\amp],
				\index, id
			]);

			loopOSC = OSCdef(\loop++id, { |msg|
				if (id==msg[2], {
					bgcolor = Color.green; // flash
					{bgcolor = Color(0,1,0,0.2)}.defer(0.1);
					if( (state[\loop].not && (loopcount>0) ), {this.close});
					loopcount = loopcount + 1;
				});
			}, '/loop');

			playhOSC = OSCdef(\playhead++id, {|msg, time, addr, recvPort|
				if (id==msg[2], { curpos = msg[3] });
			}, '/pos');

		});
	}

	close {
		synth.free;
		{ bgcolor = Color(1,1,1,0) }.defer(0.1); // because loop flash is defer as well
		state[\playing] = false;
		loopOSC.free;
		playhOSC.free;
		loopcount = 0;
		synth = nil;
	}

	kill { // Make sure it is also removed from the main stack
		main.removebox(this); // remove from stack
		this.release;
	}

	lock {
		color = Color.blue
	}

	unlock {
		color = initcolor
	}

	draw { // overwrites super
		if (visible == true, {
			//super.draw;
			Pen.color = color;

			//Pen.stringAtPoint( state[\rate]+"/"+state[\pan], rect.rightBottom);

			if (state[\loop].not, {
				Pen.line(
					Point(rect.left+(0.85*size), rect.top),
					Point(rect.left+(0.85*size), rect.bottom) );
				Pen.stroke;
			});

			if (state[\trigger], {
				Pen.strokeRect( rect );
				Pen.color = bgcolor;
				Pen.fillRect(rect);
			},{
				Pen.strokeOval( rect );
				Pen.color = bgcolor;
				Pen.fillOval(rect);
			});

			if (synth.notNil,{ //playhead
				Pen.color = Color.red;
				Pen.line(
					Point(rect.left+(curpos*size), rect.top),
					Point(rect.left+(curpos*size), rect.bottom) );
				Pen.stroke;
			})
		})
	}
}
