Lauki.new // just run this




Lauki.new(800, 600)






( // plan to have a launcher with all the apps available
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





