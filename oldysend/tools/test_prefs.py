import sys,time; sys.path.insert(0,'tools')
import shots
shots.set_prefs(sys.argv[1],'system','<boolean name="quickSave" value="true" />' + (sys.argv[2] if len(sys.argv)>2 else ''))
shots.adb('shell','am','start','-n',shots.PKG+'/.ui.MainActivity'); time.sleep(4)
