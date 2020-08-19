/*
by www.ixi-audio.net
license GPL
*/

IxiLaukiControl {
	var win, controls, gcontrols, sfs, >target, main, <gstate;

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

		controls = Dictionary.new;
		gcontrols = Dictionary.new;

		win = Window(name, rect, resizable: false);
		win.view.decorator = FlowLayout(win.view.bounds);
		win.view.decorator.gap=2@2;
		win.onClose = {};

		// GLOBAL
		StaticText(win, 15@18).align_(\left).string_("In").resize_(7);
		gcontrols[\in] = PopUpMenu(win, Rect(10, 10, 42, 17))
		.items_( Array.fill(16, { arg i; i }) )
		.action_({|m|
			gstate[\in] = m.value;
			main.boxes.collect({|b|b.in(m.value)})
		})
		.value_(gstate[\in]); // default to sound in

		StaticText(win, 15@18).align_(\left).string_("Out").resize_(7);
		gcontrols[\out] = PopUpMenu(win, Rect(10, 10, 42, 17))
		.items_( Array.fill(16, { arg i; i }) )
		.action_({|m|
			gstate[\out] = m.value;
			main.boxes.collect({|b|b.out(m.value)})
		})
		.value_(gstate[\out]); // default to sound in

		//win.view.decorator.nextLine;

		ActionButton(win,"VU",{
			Server.default.meter(2,2)
		});

		ActionButton(win,"HELP",{
			var help = Window.new("Help", Rect(~stagewidth/2, ~stageheight/2, 280, 100) ).front;
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
			main.boxes.collect({|box| box.amp(sl.value)}) // THIS MUST CONTROL A BUS not individual synths
		})
		.value_(gstate[\amp]);

		gcontrols[\range_label] = StaticText(win, 190@18).string_("Pitch range: -2 : 2");
		gcontrols[\range] = RangeSlider(win, 190@20)
		.lo_(gstate[\pitchrange][0])
		.range_(gstate[\pitchrange][1])
		.action_({ |sl|
			gstate[\pitchrange] = [sl.lo, sl.hi];
			gcontrols[\range_label].string = "Pitch range" + sl.lo.asStringPrec(2) + ":" + sl.hi.asStringPrec(2);
			main.boxes.collect({|box| box.range( sl.lo, sl.hi )})
		});

		win.view.decorator.nextLine;

		StaticText(win, 50@18).string_("Samples");


		controls[\snd] = PopUpMenu(win, 190@20)
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

		{controls[\snd].valueAction = 0}.defer(2);// BAD SOLUTION


		ActionButton(win, "import",{
			FileDialog({ |apath| // open import
				var file = PathName.new(apath);

				// add item to ~ixibuffers
				if (~ixibuffers[file.fileName]==nil, { // not there already
					~ixibuffers.add( file.fileName -> Buffer.read(Server.default, file.fullPath) );
					// and update pulldown menu
					controls[\snd].items_(
						~ixibuffers.values.collect({|it| PathName(it.path).fileName})
						//PathName.new(~path).files.collect({|i|i.fileName})
					)
				});
			},
			fileMode: 0,
			stripResult: true,
			path: (~path ++ Platform.pathSeparator ++ "sounds"++ Platform.pathSeparator)
			);
		});

		ActionButton(win,"sample",{
			// REC: create a buffer in ~ixibuffers and stream sound input into it
			~ixibuffers.add(Date.getDate.stamp -> Buffer.new( Server.default, Server.default.sampleRate*4, 2 ));
			/*			(
			SynthDef(\help_RecordBuf, { arg out = 0, bufnum = 0;
			var in = SoundIn.ar(0);
			RecordBuf.ar(in, bufnum, doneAction: Done.freeSelf, loop: 0);
			}).play(s,[\out, 0, \bufnum, b]);
			)*/
			// STOP: stop stream and update controls[\snd].items. and save as wav into sound folder
			controls[\snd].items_(
				~ixibuffers.values.collect({|it| PathName(it.path).fileName})
			)
		});

		win.view.decorator.nextLine;



		// SESSIONS
		controls[\pat_label] = StaticText(win, win.bounds.width@18).string_("Sessions");

		win.view.decorator.nextLine;

		ActionButton(win,"S",{
			var data = Dictionary.new, boxdata = Dictionary.new,  filename;
			filename = Date.getDate.stamp++".session";

			//data.put(\prange, gstate[\pitchrange]);
			//data.put(\amp, gstate[\amp]);

			main.boxes.do{|box, n|
				data.put("box"++n, box.state)
			};

			//data.put(\boxdata, boxdata);

			("saving sessions into" + ~path ++ Platform.pathSeparator ++ "sessions" ++ Platform.pathSeparator ++ filename).postln;

			data.writeArchive(~path ++ Platform.pathSeparator ++ "sessions" ++ Platform.pathSeparator ++ filename);
		});


		ActionButton(win,"O",{
			FileDialog({ |apath| // open import
				var	data = Object.readArchive(apath);
				("reading session"+apath.fileName).postln;

				controls[\pat_label].string = "Sessions:"+PathName(apath).fileName.split($.)[0];

				main.clear; // killem all

				data.do{|next|
					main.newbox( next[\rect].origin, next )
				};
				main.updateselectables;// let know
			},
			fileMode: 0,
			stripResult: true,
			path: (~path ++ Platform.pathSeparator ++ "sessions"++ Platform.pathSeparator)
			)
		});

		ActionButton(win,"clear",{
			controls[\pat_label].string = "Sessions";
			main.clear;
		});
		ActionButton(win,"grid",{
			controls[\pat_label].string = "Sessions";
			main.dogrid;
		});
		ActionButton(win,"rloc",{
			controls[\pat_label].string = "Sessions";
			main.rand
		});

		win.front;
	}

	close { win.close }
}





Lauki : IxiWin {
	var <boxes, selected, selection, ctrlw, buffer;

	*new { |width=1024, height=750, path|
		^super.new.init(width, height, path);//.init(name, rect);
	}

	init {|width, height, path|
		super.init("Lauki", Rect(0,0,width,height));

		boxes = List.new;

		OSCdef.freeAll;

		//this.dogrid;

		selection = IxiSelection.new;

		~laukicontrol = IxiLaukiControl.new(rect: Rect(win.bounds.right,win.bounds.height, 200, 230),
			main: this, exepath: path);
		//~laukicontrol.boxes = boxes; // keep a ref
		//ctrlw.alwaysOnTop=true;


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

		this.loaddefault // default session
	}

	loaddefault {
		var path, data;
		path = ~path ++ Platform.pathSeparator ++ "sessions"++ Platform.pathSeparator++"default.session";
		data = Object.readArchive(path);
		data.do{|next|
			this.newbox( next[\rect].origin, next )
		};
		selection.updateselectables(boxes);
	}

	updateselectables {
		selection.updateselectables(boxes);
	}

	newbox {|point, state|
		var box = LaukiBox.new( point, index: boxes.size+1, state: state );
		boxes.add(box);
		^box;
	}

	dogrid {
		var x=11,y=11, xgap=27, ygap=27;

		this.clear;

		999.do({|n| // drop some boxes
			var box = this.newbox(Point(x,y));
			x = x + xgap;
			if (x > (win.bounds.width-box.size), {
				x = 10;
				y = y + ygap
			})
		});
		selection.updateselectables(boxes);
	}

	rand {
		boxes.do({|box| // MUST have a ref to the window or similar to know the size
			box.rect.origin = Point(canvas.bounds.width.rand, canvas.bounds.height.rand)
		})
	}

	clear {
		boxes.collect({|box|
			box.close;
			box = nil;
		});
		boxes = List.new;
		selection.updateselectables(boxes);
	}

	close {
		~laukicontrol.close;
		boxes.collect(_.close);
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
		sel.postln;
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

	draw {
		super.draw;
		boxes.collect(_.draw);
		selection.draw;
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
			MenuAction("new Lauki", { main.newbox(~mouseloc) }),
			MenuAction("new Spin", { main.newspin(~mouseloc) });
		).front
	}
}




IxiLaukiBoxMenu {
	*new { |box|
		^super.new.init(box);
	}
	// PAUSE, MUTE ??

	init { |box|
		var items = ~ixibuffers.values.collect({|it| PathName(it.path).fileName});//sounds

		Menu(
			MenuAction("box:"+box.id, {}).enabled_(false),
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

			MenuAction("loop", { box.state[\loop] = box.state[\loop].not }).checked_(box.state[\loop]),
			MenuAction("tigger", {
				box.state[\trigger] = box.state[\trigger].not }).checked_(box.state[\trigger]),
			MenuAction("hlock", { box.state[\hlock] = box.state[\hlock].not }).checked_(box.state[\hlock]),
			MenuAction("vlock", { box.state[\vlock] = box.state[\vlock].not }).checked_(box.state[\vlock]),
			MenuAction.separator,
			MenuAction("delete", {box.close}) // TO DO: THIS MUST remove it from the stack

		).front;
	}
}





LaukiBox : IxiBox {
	var <state, synth=nil, <id, curpos=0, loopcount=0, loopOSC, playhOSC;

	*new { |point, index=0, state|
		^super.new.init(point, index, state);
	}

	init {|point, index, astate|
		super.init(point);

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

	dopan {
		^((rect.center.x/~stagewidth) * 2) - 1
	}

	in {|chan|
		synth ?? synth.set(\in, chan)
	}

	out {|chan|
		synth ?? synth.set(\out, chan)
	}

	amp {|val|
		state[\amp] = val;
		synth ?? synth.set(\amp, val);
	}

	range {|start, end|
		state[\range] = [start, end];
		if (synth.notNil, {
			synth.set(\start, start);
			synth.set(\end, end);
		})
	}

	update {|delta| // called from dragged
		//super.update(delta);
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

	setsound {|filename|
		if (synth.notNil,{ // already playing
			state[\snd] = filename;
			synth.set(\buffer, ~ixibuffers[filename].bufnum)
		})
	}

	play {
		if (synth.notNil,{ // already playing. just stop
			this.close;
		},{ // play
			this.close;// stop first to reset

			bgcolor = Color(0,1,0,0.5);

			state[\playing] = true;
			state[\snd] = ~laukibuffer;
			state[\rate] = this.dorate;
			state[\pan] = this.dopan;

			synth = Synth(\laukiplayer, [
				\buffer, ~ixibuffers[~laukibuffer].bufnum,
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

	close { // Make sure it is also removed from the main stack
		{ bgcolor = Color(1,1,1,0) }.defer(0.1); // because loop flash is defer as well
		state[\playing] = false;
		loopOSC.free;
		playhOSC.free;
		loopcount = 0;
		synth.free;
		synth = nil;
		this.release;
	}

	lock {
		color = Color.blue
	}

	unlock {
		color = initcolor
	}

	draw{
		if (visible == true, {
			//super.draw;
			Pen.color = color;

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
