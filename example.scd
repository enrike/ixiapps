Lauki.new // just run this

Lauki.new(800, 600) // set win size








(// it can be controlled with code too
l = Lauki.new;
l.boxes.do({|box| box.rect.origin = Point.rand * Point(~stagewidth, ~stageheight)}); // random position all
l.boxes.scramble[0..5].collect(_.play) // random play 5
l.boxes.collect(_.play) // too extreme maybe
l.boxes.choose.play

~ixibuffers.postln
~ixibuffers["oren2.wav"].bufnum.postln;

l.removebox(l.boxes.choose)
l.boxes.size
l.dogrid
)






/* to do
- reverse pan option
- right menu delete must remove from stack and kill the instance
- right menu file selection is not working again atm
- patterns save/open not saving or opening properly
- globals only for inter app communication
- sound pool? how?
- sample into a buffer and overwrite. sound in chanel set
- synths group into a bus and control de master volumen from the control panel
- patters save global parameters as well
- pattern load make sure it can alway finds the soundfiles
- right menu: pause? mute?
*/





( // we plan to have a launcher to start the available apps
var path = thisProcess.nowExecutingPath.dirname;
var utils = List.new;

w = Window.new("Launcher", 120@100).alwaysOnTop=true;
w.view.decorator = FlowLayout(w.view.bounds);
w.view.decorator.gap=2@2;
w.onClose = {
	utils.do{|u| u.close};
};

w.view.decorator.nextLine;

ActionButton(w,"Lauki",{
	path.postln;
	utils.add( Lauki.new( path: path) );
});
w.front
)





