/* . . . . . . . . . . . . . . . . . . . . . . . . . . . .
 * (c) Stefan Kral 2011 (http://www.redfibre.net/orbital)
 *                     _   _ _       _
 *             ___ ___| |_|_| |_ ___| |
 * _______    | . |  _| . | |  _| .'| |     _____________
 *       /____|___|_| |___|_|_| |__,|_|____/
 *
 * This program is free software and you are welcome to
 * modify and/or redistribute it under the terms of the
 * GNU General Public License http://www.gnu.org/licenses.
 * . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

#include "ExoSimApp.h"

#define WIN32_LEAN_AND_MEAN
#include "windows.h"
 
extern "C" 
{
	/** The main function for compile this application as stand alone executable. */
    INT WINAPI WinMain(HINSTANCE hInst, HINSTANCE, LPSTR strCmdLine, INT)
    {
        ExoSimApp app;
 
        try {
            app.go();
        } catch(Ogre::Exception& e) {
            MessageBox(NULL, e.getFullDescription().c_str(), "An exception has occured!", 
				MB_OK | MB_ICONERROR | MB_TASKMODAL);
        }
 
        return 0;
    }
}
